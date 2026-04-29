package com.source.epcheck

import com.source.epcheck.service.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mock.web.MockMultipartFile
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import com.source.epcheck.repository.*

class PdfParsingIntegrationTest {

    @Test
    fun testUnredactedFlightLogsParsing() {
        println("=== Starting PDF Parsing Integration Test ===")
        
        // 1. Manually instantiate services (bypassing Spring/Neo4j entirely)
        val nerService = NERService(0.60).apply { init() }
        val ocrService = OcrService("", "eng").apply { init() }
        val sentimentClassifierService = SentimentClassifierService()
        val nameNormalizationService = NameNormalizationService()
        val aliasDiscoveryService = AliasDiscoveryService(nameNormalizationService)
        
        // Mock repositories
        val documentRepository = mock(DocumentRepository::class.java)
        val personRepository = mock(PersonRepository::class.java)
        val organizationRepository = mock(OrganizationRepository::class.java)
        val flightLogRepository = mock(FlightLogRepository::class.java)
        
        val flightLogParserService = FlightLogParserService()
        
        // We will just print what GraphPersistenceService receives instead of saving to DB!
        val graphPersistenceService = mock(GraphPersistenceService::class.java)
        
        val registry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        val metrics = com.source.epcheck.config.IngestionMetrics(registry)

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

        // 2. Load the PDF file
        val file = File("C:\\Users\\Utente\\Desktop\\Ep-tool\\epcheck\\EPSTEIN FLIGHT LOGS UNREDACTED.pdf")
        assert(file.exists()) { "File not found!" }
        
        val multipartFile = MockMultipartFile(
                "file",
                file.name,
                "application/pdf",
                Files.readAllBytes(file.toPath())
        )

        // 3. Run ingestion
        println("Ingesting document: ${file.name} (${file.length() / 1024} KB)")
        
        val report = runBlocking {
            ingestionService.ingestDocument(multipartFile)
        }
        
        // 4. Capture and print results
        println("=== Ingestion Complete ===")
        println("Status: ${report.status}")
        println("Detected Type: ${report.detectedType}")
        println("Extracted Person Count: ${report.extractedPersonCount}")
        println("Extracted Org Count: ${report.extractedOrgCount}")
        
        // Since we mocked GraphPersistenceService, we can verify it was called and capture the args
        // Actually, we don't need to capture the args if the report counts are > 0, it means it worked.
        assert(report.status == "SUCCESS")
        assert(report.extractedPersonCount > 0)
        
        println("Test Passed Successfully!")
    }
}
