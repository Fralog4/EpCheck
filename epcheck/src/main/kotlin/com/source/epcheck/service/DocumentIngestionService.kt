package com.source.epcheck.service

import com.source.epcheck.domain.*
import com.source.epcheck.dto.IngestionReport
import com.source.epcheck.repository.DocumentRepository
import com.source.epcheck.repository.FlightLogRepository
import com.source.epcheck.repository.PersonRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DocumentIngestionService(
        private val documentRepository: DocumentRepository,
        private val personRepository: PersonRepository,
        private val flightLogRepository: FlightLogRepository,
        private val nerService: NERService
) {

    private val logger = KotlinLogging.logger {}

    suspend fun ingestDocument(file: MultipartFile): IngestionReport =
            withContext(Dispatchers.IO) {
                val filename = file.originalFilename ?: "unknown.pdf"
                logger.info { "Starting ingestion for $filename" }

                // Step A: Hashing
                val contentBytes = file.bytes
                val hash = calculateSha256(contentBytes)

                if (documentRepository.existsById(hash)) {
                    logger.warn { "Document $filename ($hash) already exists. Aborting." }
                    return@withContext IngestionReport(hash, filename, "SKIPPED", "UNKNOWN", 0)
                }

                // Step B: PDF Parsing
                val (text, pageOffsetMap) = extractTextWithPageNumbers(contentBytes, filename)
                if (text.isBlank() || text.length < 50) {
                    // TODO: Implement OCR (Tesseract) for scanned documents
                    logger.warn { "Text extracted is empty or garbage. OCR required." }
                }

                // Step C: Classification
                val docType = classifyDocument(text)

                // Step D: NER & Persistence
                val existingDocument = Document(hash, filename, LocalDate.now(), docType)
                documentRepository.save(existingDocument)

                var extractedCount = 0

                // We process page by page to get correct page numbers for relationships
                // Re-parsing per page or splitting the full text could be complex.
                // For efficiency, let's just use the full text for NER and then find snippets?
                // OR, better: extract text page by page.

                // Let's re-use the pageOffsetMap logic which I haven't fully implemented yet.
                // Actually PDFTextStripper can process range.

                // Revised Step B/D strategy: Iterate pages, extract text, run NER, save.

                Loader.loadPDF(contentBytes).use { document ->
                    val stripper = PDFTextStripper()
                    val totalPages = document.numberOfPages

                    for (page in 1..totalPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val pageText = stripper.getText(document)

                        if (pageText.isBlank()) continue

                        val names = nerService.extractEntities(pageText)
                        names.forEach { rawName ->
                            val normalized = normalizeName(rawName)

                            // Step E: Graph Persistence (Upsert Person)
                            // We need to handle this carefully to avoid race conditions if
                            // parallel,
                            // but here we are sequential per document.
                            // We should check if Person exists in DB.

                            val person =
                                    personRepository.findByNormalizedName(normalized)
                                            ?: Person(
                                                    name = rawName,
                                                    normalizedName = normalized,
                                                    riskScore = 0
                                            ) // Default score

                            // Create MENTIONED_IN
                            val snippet = getSnippet(pageText, rawName)
                            val mention =
                                    MentionedIn(
                                            document = existingDocument,
                                            pageNumber = page,
                                            snippet = snippet,
                                            sentiment = "NEUTRAL" // Placeholder
                                    )

                            person.mentionedIn.add(mention)

                            // Flight Logic Handling (implied requirement for Analysis)
                            if (docType == DocType.FLIGHT_LOG) {
                                // Create FlightLog node if needed or link to generic one
                                // For this task, assuming 1 flight per document or heuristic
                                val flightLog =
                                        FlightLog(
                                                flightDate = LocalDate.now(), // parsing todo
                                                aircraftId =
                                                        if (text.contains("N908JE")) "N908JE"
                                                        else "UNKNOWN",
                                                origin = "Unknown",
                                                destination = "Unknown"
                                        )
                                // In a real app we'd de-dupe FlightLogs. Here just creating one per
                                // mention
                                // or finding existing is tricky without more data.
                                // I will create the dedicated FlightLog node first then link.
                                val savedLog = flightLogRepository.save(flightLog)

                                val flewOn =
                                        FlewOn(
                                                flightLog = savedLog,
                                                flightDate = LocalDate.now(), // parsing todo
                                                aircraftId =
                                                        if (text.contains("N908JE")) "N908JE"
                                                        else "UNKNOWN",
                                                origin = "Unknown",
                                                destination = "Unknown"
                                        )
                                person.flights.add(flewOn)
                            }

                            personRepository.save(person)
                            extractedCount++
                        }
                    }
                }

                logger.info { "Ingestion complete. Extracted $extractedCount persons." }
                IngestionReport(hash, filename, "SUCCESS", docType.name, extractedCount)
            }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    // Step B Helper (actually integrated into main flow for page awareness)
    private fun extractTextWithPageNumbers(
            bytes: ByteArray,
            filename: String
    ): Pair<String, Map<Int, String>> {
        // Just for classification check we need full text
        Loader.loadPDF(bytes).use { document ->
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            return Pair(text, emptyMap())
        }
    }

    private fun classifyDocument(text: String): DocType {
        return if (text.contains("N908JE") || text.contains("Cessna", ignoreCase = true)) {
            DocType.FLIGHT_LOG
        } else {
            DocType.DEPOSITION
        }
    }

    private fun normalizeName(raw: String): String {
        val lower = raw.trim().lowercase()
        // Mock map
        val map =
                mapOf(
                        "bill clinton" to "william jefferson clinton",
                        "prince andrew" to "andrew albert christian edward"
                )
        return map[lower] ?: lower
    }

    private fun getSnippet(text: String, keyword: String): String {
        val idx = text.indexOf(keyword)
        if (idx == -1) return text.take(200)

        val start = (idx - 100).coerceAtLeast(0)
        val end = (idx + keyword.length + 100).coerceAtMost(text.length)
        return text.substring(start, end).replace("\n", " ")
    }
}
