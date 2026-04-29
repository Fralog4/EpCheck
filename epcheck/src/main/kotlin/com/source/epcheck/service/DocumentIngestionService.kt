package com.source.epcheck.service

import com.source.epcheck.config.IngestionMetrics
import com.source.epcheck.domain.DocType
import com.source.epcheck.dto.IngestionReport
import com.source.epcheck.repository.DocumentRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class DocumentIngestionService(
        private val documentRepository: DocumentRepository,
        private val nerService: NERService,
        private val ocrService: OcrService,
        private val sentimentClassifierService: SentimentClassifierService,
        private val nameNormalizationService: NameNormalizationService,
        private val aliasDiscoveryService: AliasDiscoveryService,
        private val graphPersistenceService: GraphPersistenceService,
        private val ingestionMetrics: IngestionMetrics
) {

    private val logger = KotlinLogging.logger {}

    suspend fun ingestDocument(file: MultipartFile): IngestionReport =
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val filename = file.originalFilename ?: "unknown.pdf"
                logger.info { "Starting ingestion for $filename" }

                try {
                    validateFile(file, filename)

                    // ── Step A: Hashing & Deduplication ──
                    val hash = calculateSha256(file.inputStream)

                    if (documentRepository.existsById(hash)) {
                        logger.warn { "Document $filename ($hash) already exists. Aborting." }
                        ingestionMetrics.ingestionSkipped.increment()
                        return@withContext IngestionReport(hash, filename, "SKIPPED", "UNKNOWN", 0, 0)
                    }

                    // ── Step B: Single-Pass Extraction ──
                    val pageResults = mutableListOf<PageExtractionResult>()
                    var docType: DocType = DocType.DEPOSITION
                    var classified = false
                    var totalExtractedChars = 0

                    // Use RandomAccessReadBuffer to parse directly from stream efficiently
                    org.apache.pdfbox.io.RandomAccessReadBuffer(file.inputStream).use { buffer ->
                        Loader.loadPDF(buffer).use { pdfDocument ->
                            val stripper = PDFTextStripper()
                            val renderer = PDFRenderer(pdfDocument)
                            val totalPages = pdfDocument.numberOfPages
                            logger.info { "PDF loaded: $totalPages page(s)" }

                            if (totalPages > 500) {
                                throw PageLimitExceededException(totalPages, 500)
                            }

                            for (page in 1..totalPages) {
                                stripper.startPage = page
                                stripper.endPage = page
                                var pageText = stripper.getText(pdfDocument)

                                // OCR fallback
                                if (pageText.isBlank() || pageText.trim().length < 20) {
                                    logger.info { "Page $page has minimal text (${pageText.trim().length} chars) — attempting OCR" }
                                    val pageImage = renderer.renderImageWithDPI(page - 1, 300f)
                                    val ocrText = ocrService.extractText(pageImage, page)
                                    if (ocrText.isNotBlank()) {
                                        pageText = ocrText
                                        logger.info { "OCR succeeded on page $page: ${ocrText.length} chars extracted" }
                                    } else {
                                        logger.warn { "OCR produced no text for page $page — skipping" }
                                        continue
                                    }
                                }

                                pageText = sanitizeText(pageText)
                                totalExtractedChars += pageText.length

                                // Incremental classification
                                if (!classified) {
                                    val pageDocType = classifyPageText(pageText)
                                    if (pageDocType != null) {
                                        docType = pageDocType
                                        classified = true
                                        logger.info { "Classified as $docType on page $page" }
                                    }
                                }

                                // NER extraction (PERSON + ORGANIZATION)
                                val nerResults = nerService.extractAllEntitiesWithConfidence(pageText)
                                if (nerResults.isNotEmpty()) {
                                    val entities = nerResults.map { result ->
                                        val snippet = getSnippet(pageText, result.name)
                                        ExtractedEntity(
                                                rawName = result.name,
                                                normalizedName = normalizeName(result.name),
                                                snippet = snippet,
                                                sentiment = sentimentClassifierService.classify(snippet),
                                                entityType = result.entityType
                                        )
                                    }
                                    pageResults.add(PageExtractionResult(page, entities, pageText))

                                    // Alias auto-discovery on PERSON entities
                                    val personNames = nerResults.filter { it.entityType == "PERSON" }.map { it.name }
                                    if (personNames.size >= 2) {
                                        aliasDiscoveryService.analyzeCoOccurrences(personNames, pageText)
                                    }
                                }
                            }
                        }
                    }

                    if (totalExtractedChars < 50) {
                        logger.warn { "Total text extracted is very low ($totalExtractedChars chars). Document may be unusable." }
                    }

                    // ── Step C: Atomic Graph Persistence ──
                    val counts = graphPersistenceService.persistIngestionResults(
                            hash = hash,
                            filename = filename,
                            docType = docType,
                            pageResults = pageResults
                    )

                    logger.info { "Ingestion complete: $filename ($counts entities persisted)" }

                    // Record metrics
                    ingestionMetrics.ingestionSuccess.increment()
                    val duration = System.currentTimeMillis() - startTime
                    ingestionMetrics.ingestionTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS)

                    return@withContext IngestionReport(
                            fileHash = hash,
                            filename = filename,
                            status = "SUCCESS",
                            detectedType = docType.name,
                            extractedPersonCount = counts.personCount,
                            extractedOrgCount = counts.orgCount
                    )

                } catch (e: Exception) {
                    ingestionMetrics.ingestionFailed.increment()
                    logger.error(e) { "Failed to ingest document $filename" }
                    throw e
                }
            }

    private fun validateFile(file: MultipartFile, filename: String) {
        if (file.isEmpty) {
            throw InvalidFileException("File is empty: $filename")
        }

        val contentType = file.contentType
        if (contentType != "application/pdf") {
            throw InvalidFileException("Invalid file type: $contentType. Only application/pdf is allowed.")
        }

        val bytes = file.bytes
        if (bytes.size < 5 ||
                bytes[0] != 0x25.toByte() || // %
                bytes[1] != 0x50.toByte() || // P
                bytes[2] != 0x44.toByte() || // D
                bytes[3] != 0x46.toByte() || // F
                bytes[4] != 0x2D.toByte()    // -
        ) {
            throw InvalidFileException("File content does not match PDF signature.")
        }
    }

    private fun sanitizeText(input: String): String {
        var sanitized = input.replace(Regex("[^\\x20-\\x7E\\t\\n\\r]"), "")
        sanitized = sanitized.replace(Regex("\\s{3,}"), "  ")
        
        if (sanitized.length > 50000) {
            throw SanitizationException("Extracted text per page exceeds maximum safety limits.")
        }
        
        return sanitized.trim()
    }

    private fun calculateSha256(inputStream: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun classifyPageText(pageText: String): DocType? {
        if (pageText.contains("N908JE") || pageText.contains("Cessna", ignoreCase = true)) {
            return DocType.FLIGHT_LOG
        }
        val emailHeaders = listOf("From:", "To:", "Subject:", "Cc:", "Bcc:", "Date:", "Sent:")
        val headerCount = emailHeaders.count { pageText.contains(it, ignoreCase = true) }
        if (headerCount >= 2) {
            return DocType.EMAIL_CHAIN
        }
        return null
    }

    private fun normalizeName(raw: String): String =
            nameNormalizationService.normalize(raw)

    private fun getSnippet(text: String, keyword: String): String {
        val idx = text.indexOf(keyword)
        if (idx == -1) return text.take(200)

        val start = (idx - 100).coerceAtLeast(0)
        val end = (idx + keyword.length + 100).coerceAtMost(text.length)
        return text.substring(start, end).replace("\n", " ")
    }
}
