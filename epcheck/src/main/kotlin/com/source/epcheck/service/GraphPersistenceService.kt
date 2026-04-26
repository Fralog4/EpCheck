package com.source.epcheck.service

import com.source.epcheck.domain.*
import com.source.epcheck.repository.DocumentRepository
import com.source.epcheck.repository.FlightLogRepository
import com.source.epcheck.repository.PersonRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import org.neo4j.driver.exceptions.Neo4jException
import org.springframework.dao.TransientDataAccessException
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Handles all Neo4j graph persistence during document ingestion.
 *
 * Extracted from [DocumentIngestionService] so that Spring AOP can proxy
 * [@Transactional] and [@Retryable] annotations (which require external method calls).
 *
 * - **Atomicity**: All writes (Document, FlightLogs, Persons + relationships)
 *   happen in a single Neo4j transaction — all succeed or all roll back.
 * - **Retry**: Transient Neo4j errors (connection timeouts, leader elections)
 *   trigger up to 3 retries with exponential backoff (500ms → 1s → 2s).
 */
@Service
class GraphPersistenceService(
        private val documentRepository: DocumentRepository,
        private val personRepository: PersonRepository,
        private val flightLogRepository: FlightLogRepository,
        private val flightLogParserService: FlightLogParserService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Persists all ingestion results to Neo4j in a single atomic transaction.
     *
     * On transient Neo4j failures, retries up to 3 additional times with
     * exponential backoff (500ms → 1s → 2s, max 5s).
     *
     * @param hash         SHA-256 hash of the PDF (used as Document PK)
     * @param filename     original filename of the uploaded PDF
     * @param docType      classified document type (DEPOSITION, FLIGHT_LOG, etc.)
     * @param pageResults  per-page extraction results (entities + text)
     * @return the number of person mentions persisted
     */
    @Transactional
    @Retryable(
            includes = [TransientDataAccessException::class, Neo4jException::class],
            maxRetries = 3,
            delay = 500,
            multiplier = 2.0,
            maxDelay = 5000
    )
    fun persistIngestionResults(
            hash: String,
            filename: String,
            docType: DocType,
            pageResults: List<PageExtractionResult>
    ): Int {
        logger.info { "Persisting ingestion results for $filename (${pageResults.size} pages with entities)" }

        // 1. Save Document node
        val documentNode = Document(hash, filename, LocalDate.now(), docType)
        documentRepository.save(documentNode)

        // 2. Collect all unique normalized names and batch-fetch existing Persons
        val allNormalizedNames = pageResults
                .flatMap { it.entities }
                .map { it.normalizedName }
                .toSet()

        val existingPersons = personRepository.findAllByNormalizedNameIn(allNormalizedNames)
        val personMap = existingPersons
                .associateBy { it.normalizedName }
                .toMutableMap()

        logger.info { "Batch-fetched ${existingPersons.size} existing persons for ${allNormalizedNames.size} unique names" }

        // 3. Build/update Person objects in-memory (no DB calls in this loop)
        val flightLogsToSave = mutableListOf<FlightLog>()
        var extractedCount = 0

        pageResults.forEach { result ->
            result.entities.forEach { entity ->
                // Get from map or create new — no DB call
                val person = personMap.getOrPut(entity.normalizedName) {
                    Person(
                            name = entity.rawName,
                            normalizedName = entity.normalizedName,
                            riskScore = 0
                    )
                }

                // Add MENTIONED_IN relationship (in-memory)
                val mention =
                        MentionedIn(
                                document = documentNode,
                                pageNumber = result.pageNumber,
                                snippet = entity.snippet,
                                sentiment = entity.sentiment
                        )
                person.mentionedIn.add(mention)

                // Parse flight data from page text
                if (docType == DocType.FLIGHT_LOG) {
                    val parsed = flightLogParserService.parseFlightRecord(result.pageText)
                    val flightDate = parsed.flightDate ?: LocalDate.now()

                    val flightLog =
                            FlightLog(
                                    flightDate = flightDate,
                                    aircraftId = parsed.aircraftId,
                                    origin = parsed.origin,
                                    destination = parsed.destination
                            )
                    flightLogsToSave.add(flightLog)

                    val flewOn =
                            FlewOn(
                                    flightLog = flightLog,
                                    flightDate = flightDate,
                                    aircraftId = parsed.aircraftId,
                                    origin = parsed.origin,
                                    destination = parsed.destination
                            )
                    person.flights.add(flewOn)
                }

                extractedCount++
            }
        }

        // 4. Batch-save FlightLogs first (they need IDs for FlewOn @TargetNode)
        if (flightLogsToSave.isNotEmpty()) {
            flightLogRepository.saveAll(flightLogsToSave)
            logger.info { "Batch-saved ${flightLogsToSave.size} flight logs" }
        }

        // 5. Batch-save all Persons (cascades MENTIONED_IN and FLEW_ON relationships)
        val personsToSave = personMap.values.toList()
        personRepository.saveAll(personsToSave)
        logger.info { "Batch-saved ${personsToSave.size} persons with relationships" }

        return extractedCount
    }
}

/**
 * Represents a named entity extracted from a page, with its normalized form and snippet.
 * Used as a transfer object between extraction and persistence phases.
 */
data class ExtractedEntity(
        val rawName: String,
        val normalizedName: String,
        val snippet: String,
        val sentiment: String
)

/**
 * Holds the extraction results for a single PDF page.
 * Shared between [DocumentIngestionService] (producer) and [GraphPersistenceService] (consumer).
 */
data class PageExtractionResult(
        val pageNumber: Int,
        val entities: List<ExtractedEntity>,
        val pageText: String
)

/**
 * Thrown when all retry attempts for graph persistence are exhausted.
 */
class IngestionPersistenceException(message: String, cause: Throwable) :
        RuntimeException(message, cause)
