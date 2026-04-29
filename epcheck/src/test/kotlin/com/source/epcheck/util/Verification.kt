package com.source.epcheck.util

import com.source.epcheck.service.*
import com.source.epcheck.repository.*
import com.source.epcheck.domain.*
import com.source.epcheck.config.IngestionMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.mockito.Mockito.mock
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

object Verification {
    @JvmStatic
    fun main(args: Array<String>) {
        println("=== Starting Standalone PDF Verification ===")
        
        val nerService = NERService(0.60).apply { init() }
        val ocrService = OcrService("", "eng").apply { init() }
        val sentimentClassifierService = SentimentClassifierService()
        val nameNormalizationService = NameNormalizationService()
        val aliasDiscoveryService = AliasDiscoveryService(nameNormalizationService)
        
        val documentRepository = mock(DocumentRepository::class.java)
        val personRepository = mock(PersonRepository::class.java)
        val organizationRepository = mock(OrganizationRepository::class.java)
        val flightLogRepository = mock(FlightLogRepository::class.java)
        
        val flightLogParserService = FlightLogParserService()
        
        // Use a fake instead of a mock to avoid Mockito's null safety issues with Kotlin non-nullable parameters
        val graphPersistenceService = object : GraphPersistenceService(
            documentRepository,
            personRepository,
            organizationRepository,
            flightLogRepository,
            flightLogParserService
        ) {
            override fun persistIngestionResults(
                hash: String,
                filename: String,
                docType: DocType,
                pageResults: List<PageExtractionResult>
            ): IngestionEntityCounts {
                return IngestionEntityCounts(0, 0)
            }
        }
        
        val registry = SimpleMeterRegistry()
        val metrics = IngestionMetrics(registry)

        val ingestionService = DocumentIngestionService(
                documentRepository,
                nerService,
                ocrService,
                sentimentClassifierService,
                nameNormalizationService,
                aliasDiscoveryService,
                graphPersistenceService,
                metrics
        )

        val file = File("C:\\Users\\Utente\\Desktop\\Ep-tool\\epcheck\\EPSTEIN FLIGHT LOGS UNREDACTED.pdf")
        if (!file.exists()) {
            println("ERROR: File not found at ${file.absolutePath}")
            return
        }
        
        val multipartFile = MockMultipartFile(
                "file",
                file.name,
                "application/pdf",
                Files.readAllBytes(file.toPath())
        )

        println("Ingesting document: ${file.name} (${file.length() / 1024} KB)")
        
        try {
            val report = runBlocking {
                ingestionService.ingestDocument(multipartFile)
            }
            
            println("=== Ingestion Complete ===")
            println("Status: ${report.status}")
            println("Detected Type: ${report.detectedType}")
            println("Extracted Person Count: ${report.extractedPersonCount}")
            println("Extracted Org Count: ${report.extractedOrgCount}")
        } catch (e: Exception) {
            println("ERROR during ingestion:")
            e.printStackTrace()
        }
    }
}
