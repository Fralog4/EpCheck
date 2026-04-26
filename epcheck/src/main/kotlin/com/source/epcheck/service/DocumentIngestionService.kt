package com.source.epcheck.service

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

/**
 * Orchestrates the document ingestion pipeline:
 *   Step A: Hashing & deduplication
 *   Step B: Single-pass PDF extraction (text + NER + classification)
 *   Step C: Delegates atomic graph persistence to [GraphPersistenceService]
 *
 * This service owns no repository dependencies except [DocumentRepository]
 * for the deduplication check. All graph writes are handled by
 * [GraphPersistenceService] which provides transactional atomicity and
 * retry logic for transient Neo4j failures.
 */
@Service
class DocumentIngestionService(
        private val documentRepository: DocumentRepository,
        private val nerService: NERService,
        private val ocrService: OcrService,
        private val sentimentClassifierService: SentimentClassifierService,
        private val nameNormalizationService: NameNormalizationService,
        private val graphPersistenceService: GraphPersistenceService
) {

    private val logger = KotlinLogging.logger {}

    suspend fun ingestDocument(file: MultipartFile): IngestionReport =
            withContext(Dispatchers.IO) {
                val filename = file.originalFilename ?: "unknown.pdf"
                logger.info { "Starting ingestion for $filename" }

                // ── Step A: Hashing & Deduplication ──
                val contentBytes = file.bytes
                val hash = calculateSha256(contentBytes)

                if (documentRepository.existsById(hash)) {
                    logger.warn { "Document $filename ($hash) already exists. Aborting." }
                    return@withContext IngestionReport(hash, filename, "SKIPPED", "UNKNOWN", 0)
                }

                // ── Step B: Single-Pass Extraction ──
                // Load the PDF exactly ONCE. In a single iteration we:
                //   1. Extract text per page
                //   2. Run NER per page → collect PageExtractionResult objects
                //   3. Classify the document incrementally (short-circuit on keyword match)
                // After iteration the PDDocument is closed, freeing its memory
                // BEFORE any graph persistence begins.

                val pageResults = mutableListOf<PageExtractionResult>()
                var docType: DocType = DocType.DEPOSITION  // default
                var classified = false
                var totalExtractedChars = 0

                Loader.loadPDF(contentBytes).use { pdfDocument ->
                    val stripper = PDFTextStripper()
                    val renderer = PDFRenderer(pdfDocument)
                    val totalPages = pdfDocument.numberOfPages
                    logger.info { "PDF loaded: $totalPages page(s)" }

                    for (page in 1..totalPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        var pageText = stripper.getText(pdfDocument)

                        // OCR fallback: if PDFTextStripper yields little/no text,
                        // render the page as 300 DPI image and run Tesseract OCR.
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

                        totalExtractedChars += pageText.length

                        // Incremental classification: check each page until classified
                        if (!classified) {
                            val pageDocType = classifyPageText(pageText)
                            if (pageDocType != null) {
                                docType = pageDocType
                                classified = true
                                logger.info { "Classified as $docType on page $page" }
                            }
                        }

                        // NER extraction
                        val names = nerService.extractEntities(pageText)
                        if (names.isNotEmpty()) {
                            val entities = names.map { rawName ->
                                val snippet = getSnippet(pageText, rawName)
                                ExtractedEntity(
                                        rawName = rawName,
                                        normalizedName = normalizeName(rawName),
                                        snippet = snippet,
                                        sentiment = sentimentClassifierService.classify(snippet)
                                )
                            }
                            pageResults.add(PageExtractionResult(page, entities, pageText))
                        }
                    }
                }
                // PDDocument is now closed — memory freed

                if (totalExtractedChars < 50) {
                    logger.warn { "Total text extracted is very low ($totalExtractedChars chars). Document may be unusable." }
                }

                // ── Step C: Atomic Graph Persistence (delegated) ──
                // All Neo4j writes happen in a single @Transactional method
                // with @Retryable for transient failure recovery.
                val extractedCount = graphPersistenceService.persistIngestionResults(
                        hash = hash,
                        filename = filename,
                        docType = docType,
                        pageResults = pageResults
                )

                logger.info { "Ingestion complete. Extracted $extractedCount person mentions." }
                IngestionReport(hash, filename, "SUCCESS", docType.name, extractedCount)
            }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks a single page's text for classification keywords.
     * Returns the detected [DocType] or null if no keywords matched.
     * This enables incremental classification: call on each page,
     * short-circuit as soon as a non-null result is returned.
     *
     * Priority: FLIGHT_LOG > EMAIL_CHAIN > null (defaults to DEPOSITION)
     */
    private fun classifyPageText(pageText: String): DocType? {
        // Flight log: specific aircraft identifiers
        if (pageText.contains("N908JE") || pageText.contains("Cessna", ignoreCase = true)) {
            return DocType.FLIGHT_LOG
        }

        // Email chain: requires 2+ email header patterns on a page
        val emailHeaders = listOf("From:", "To:", "Subject:", "Cc:", "Bcc:", "Date:", "Sent:")
        val headerCount = emailHeaders.count { pageText.contains(it, ignoreCase = true) }
        if (headerCount >= 2) {
            return DocType.EMAIL_CHAIN
        }

        return null // not yet classifiable — continue scanning
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

