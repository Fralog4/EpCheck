package com.source.epcheck.service

import com.source.epcheck.domain.*
import com.source.epcheck.repository.DocumentRepository
import com.source.epcheck.repository.FlightLogRepository
import com.source.epcheck.repository.OrganizationRepository
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
 * - **Atomicity**: All writes (Document, FlightLogs, Persons, Organizations + relationships)
 *   happen in a single Neo4j transaction — all succeed or all roll back.
 * - **Retry**: Transient Neo4j errors (connection timeouts, leader elections)
 *   trigger up to 3 retries with exponential backoff (500ms → 1s → 2s).
 */
@Service
class GraphPersistenceService(
        private val documentRepository: DocumentRepository,
        private val personRepository: PersonRepository,
        private val organizationRepository: OrganizationRepository,
        private val flightLogRepository: FlightLogRepository,
        private val flightLogParserService: FlightLogParserService
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Persists all ingestion results to Neo4j in a single atomic transaction.
     *
     * @return the number of entity mentions persisted (persons + organizations)
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
    ): IngestionEntityCounts {
        logger.info { "Persisting ingestion results for $filename (${pageResults.size} pages with entities)" }

        // 1. Save Document node
        val documentNode = Document(hash, filename, LocalDate.now(), docType)
        documentRepository.save(documentNode)

        // 2. Separate entities by type
        val personEntities = pageResults.flatMap { r -> r.entities.filter { it.entityType == "PERSON" }.map { r to it } }
        val orgEntities = pageResults.flatMap { r -> r.entities.filter { it.entityType == "ORGANIZATION" }.map { r to it } }

        // 3. Batch-fetch existing Persons
        val allPersonNames = personEntities.map { it.second.normalizedName }.toSet()
        val existingPersons = personRepository.findAllByNormalizedNameIn(allPersonNames)
        val personMap = existingPersons.associateBy { it.normalizedName }.toMutableMap()

        // 4. Batch-fetch existing Organizations
        val allOrgNames = orgEntities.map { it.second.normalizedName }.toSet()
        val existingOrgs = organizationRepository.findAllByNormalizedNameIn(allOrgNames)
        val orgMap = existingOrgs.associateBy { it.normalizedName }.toMutableMap()

        logger.info { "Batch-fetched ${existingPersons.size} persons, ${existingOrgs.size} orgs for ${allPersonNames.size + allOrgNames.size} unique names" }

        // 5. Build Person objects in-memory
        val flightLogsToSave = mutableListOf<FlightLog>()
        var personCount = 0

        personEntities.forEach { (result, entity) ->
            val person = personMap.getOrPut(entity.normalizedName) {
                Person(name = entity.rawName, normalizedName = entity.normalizedName, riskScore = 0)
            }
            val mention = MentionedIn(
                    document = documentNode,
                    pageNumber = result.pageNumber,
                    snippet = entity.snippet,
                    sentiment = entity.sentiment
            )
            person.mentionedIn.add(mention)

            // Parse flight data
            if (docType == DocType.FLIGHT_LOG) {
                val parsed = flightLogParserService.parseFlightRecord(result.pageText)
                val flightDate = parsed.flightDate ?: LocalDate.now()
                val flightLog = FlightLog(
                        flightDate = flightDate,
                        aircraftId = parsed.aircraftId,
                        origin = parsed.origin,
                        destination = parsed.destination
                )
                flightLogsToSave.add(flightLog)
                person.flights.add(FlewOn(
                        flightLog = flightLog,
                        flightDate = flightDate,
                        aircraftId = parsed.aircraftId,
                        origin = parsed.origin,
                        destination = parsed.destination
                ))
            }
            personCount++
        }

        // 6. Build Organization objects in-memory
        var orgCount = 0
        orgEntities.forEach { (result, entity) ->
            val org = orgMap.getOrPut(entity.normalizedName) {
                Organization(name = entity.rawName, normalizedName = entity.normalizedName)
            }
            val mention = MentionedIn(
                    document = documentNode,
                    pageNumber = result.pageNumber,
                    snippet = entity.snippet,
                    sentiment = entity.sentiment
            )
            org.mentionedIn.add(mention)
            orgCount++
        }

        // 7. Batch-save FlightLogs
        if (flightLogsToSave.isNotEmpty()) {
            flightLogRepository.saveAll(flightLogsToSave)
            logger.info { "Batch-saved ${flightLogsToSave.size} flight logs" }
        }

        // 8. Batch-save Persons + Organizations
        personRepository.saveAll(personMap.values.toList())
        logger.info { "Batch-saved ${personMap.size} persons with relationships" }

        if (orgMap.isNotEmpty()) {
            organizationRepository.saveAll(orgMap.values.toList())
            logger.info { "Batch-saved ${orgMap.size} organizations with relationships" }
        }

        return IngestionEntityCounts(personCount = personCount, orgCount = orgCount)
    }
}

/** Counts returned from persistence for the ingestion report. */
data class IngestionEntityCounts(
        val personCount: Int,
        val orgCount: Int
) {
    val total: Int get() = personCount + orgCount
}

/**
 * Represents a named entity extracted from a page, with its normalized form, snippet, and type.
 */
data class ExtractedEntity(
        val rawName: String,
        val normalizedName: String,
        val snippet: String,
        val sentiment: String,
        val entityType: String = "PERSON" // "PERSON" or "ORGANIZATION"
)

/**
 * Holds the extraction results for a single PDF page.
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
