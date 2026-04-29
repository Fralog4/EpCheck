package com.source.epcheck.controller

import com.source.epcheck.dto.*
import com.source.epcheck.service.AliasDiscoveryService
import com.source.epcheck.service.AnalysisService
import com.source.epcheck.service.DocumentIngestionService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api")
class EpsteinLensController(
        private val ingestionService: DocumentIngestionService,
        private val analysisService: AnalysisService,
        private val aliasDiscoveryService: AliasDiscoveryService,
        private val jobStatusService: com.source.epcheck.service.JobStatusService,
        private val ingestionProducer: com.source.epcheck.service.IngestionProducer
) {

    private val logger = KotlinLogging.logger {}

    @PostMapping("/ingest")
    suspend fun ingestDocument(
            @RequestParam("file") file: MultipartFile
    ): ResponseEntity<IngestionReport> {
        val report = ingestionService.ingestDocument(file)
        return ResponseEntity.ok(report)
    }

    @PostMapping("/ingest/async")
    fun ingestDocumentAsync(
            @RequestParam("file") file: MultipartFile
    ): ResponseEntity<AsyncIngestionResponse> {
        val filename = file.originalFilename ?: "unknown.pdf"
        val jobId = java.util.UUID.randomUUID().toString()
        
        // Save file locally to process asynchronously
        val uploadDir = java.io.File("./uploads")
        if (!uploadDir.exists()) uploadDir.mkdirs()
        
        val destFile = java.io.File(uploadDir, "${jobId}_$filename")
        file.transferTo(destFile)

        // Initialize job status
        jobStatusService.createJob(jobId)

        // Send Kafka message
        val message = IngestionMessage(jobId, destFile.absolutePath, filename)
        ingestionProducer.sendIngestionJob(message)

        val response = AsyncIngestionResponse(jobId, "ACCEPTED", "Document ingestion started asynchronously")
        return ResponseEntity.accepted().body(response)
    }

    @GetMapping("/ingest/status/{jobId}")
    fun getIngestionStatus(@PathVariable jobId: String): ResponseEntity<JobStatusResponse> {
        val status = jobStatusService.getStatus(jobId)
        return if (status != null) {
            ResponseEntity.ok(status)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Batch ingestion endpoint — accepts a ZIP file containing multiple PDFs.
     * Extracts each PDF from the ZIP and processes them sequentially.
     */
    @PostMapping("/ingest/batch")
    suspend fun ingestBatch(
            @RequestParam("file") zipFile: MultipartFile
    ): ResponseEntity<BatchIngestionReport> {
        val filename = zipFile.originalFilename ?: "batch.zip"
        logger.info { "Batch ingestion starting: $filename (${zipFile.size} bytes)" }

        val reports = mutableListOf<IngestionReport>()
        val errors = mutableListOf<String>()
        var successCount = 0
        var skippedCount = 0
        var failedCount = 0

        ZipInputStream(ByteArrayInputStream(zipFile.bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name

                // Skip directories and non-PDF files
                if (entry.isDirectory || !entryName.lowercase().endsWith(".pdf")) {
                    logger.debug { "Skipping non-PDF entry: $entryName" }
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }

                try {
                    logger.info { "Processing ZIP entry: $entryName" }
                    val pdfBytes = zis.readAllBytes()
                    val mockFile = MockMultipartFile(
                            "file",
                            entryName,
                            "application/pdf",
                            pdfBytes
                    )
                    val report = ingestionService.ingestDocument(mockFile)
                    reports.add(report)

                    when (report.status) {
                        "SUCCESS" -> successCount++
                        "SKIPPED" -> skippedCount++
                        else -> failedCount++
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to process ZIP entry: $entryName" }
                    errors.add("$entryName: ${e.message}")
                    failedCount++
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val batchReport = BatchIngestionReport(
                totalFiles = reports.size + errors.size,
                successCount = successCount,
                skippedCount = skippedCount,
                failedCount = failedCount,
                reports = reports,
                errors = errors
        )

        logger.info { "Batch ingestion complete: ${batchReport.totalFiles} files ($successCount ok, $skippedCount skipped, $failedCount failed)" }
        return ResponseEntity.ok(batchReport)
    }

    @GetMapping("/analyze/{name}")
    fun analyzeProfile(@PathVariable name: String): ResponseEntity<RiskReportDTO> {
        val report = analysisService.analyzeProfile(name)
        return ResponseEntity.ok(report)
    }

    @GetMapping("/network/{name}")
    fun analyzeNetwork(@PathVariable name: String): ResponseEntity<NetworkReportDTO> {
        val report = analysisService.analyzeNetwork(name)
        return ResponseEntity.ok(report)
    }

    @GetMapping("/graph")
    fun getFullGraph(): ResponseEntity<FullGraphDTO> {
        val graph = analysisService.getFullGraph()
        return ResponseEntity.ok(graph)
    }

    @GetMapping("/timeline/{name}")
    fun getTimeline(@PathVariable name: String): ResponseEntity<TimelineDTO> {
        val timeline = analysisService.getTimeline(name)
        return ResponseEntity.ok(timeline)
    }

    @GetMapping("/aliases")
    fun getAliasSuggestions(): ResponseEntity<AliasReportDTO> {
        val suggestions = aliasDiscoveryService.suggestions.map { s ->
            AliasSuggestionDTO(
                    rawName = s.rawName,
                    suggestedCanonical = s.suggestedCanonical,
                    confidence = s.confidence,
                    reason = s.reason
            )
        }
        return ResponseEntity.ok(AliasReportDTO(suggestions, suggestions.size))
    }
}
