package com.source.epcheck.service

import com.source.epcheck.dto.*
import com.source.epcheck.repository.DocumentRepository
import com.source.epcheck.repository.OrganizationRepository
import com.source.epcheck.repository.PersonRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnalysisService(
        private val personRepository: PersonRepository,
        private val documentRepository: DocumentRepository,
        private val organizationRepository: OrganizationRepository,
        private val nameNormalizationService: NameNormalizationService
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    fun analyzeProfile(targetName: String): RiskReportDTO {
        val normalized = nameNormalizationService.normalize(targetName)

        val person =
                personRepository.findByNormalizedName(normalized)
                        ?: return RiskReportDTO(targetName, RiskStatus.GREEN, emptyList(), 0, 0)

        val flights = person.flights
        val mentions = person.mentionedIn

        val evidence =
                mentions.map {
                    EvidenceItem(
                            date = it.document.ingestionDate,
                            sourcePdf = it.document.filename,
                            page = it.pageNumber,
                            snippet = it.snippet,
                            sentiment = it.sentiment
                    )
                } +
                        flights.map {
                            EvidenceItem(
                                    date = it.flightDate,
                                    sourcePdf = "Flight Log ${it.flightLog.id}",
                                    page = 0,
                                    snippet =
                                            "Flew on ${it.aircraftId} from ${it.origin} to ${it.destination}",
                                    sentiment = null
                            )
                        }

        // Risk Logic
        val hasAccusatorySentiment = mentions.any { it.sentiment == "ACCUSATORY" }
        val hasRiskyKeywords =
                mentions.any {
                    val s = it.snippet.lowercase()
                    s.contains("massage") || s.contains("recruit")
                }

        val status = when {
            flights.isNotEmpty() -> RiskStatus.RED
            mentions.size > 5 || hasRiskyKeywords || hasAccusatorySentiment -> RiskStatus.ORANGE
            else -> RiskStatus.YELLOW
        }

        val score = when (status) {
            RiskStatus.RED -> 100
            RiskStatus.ORANGE -> 75
            RiskStatus.YELLOW -> 25
            RiskStatus.GREEN -> 0
        }

        // Persist risk score back to the Person node
        if (person.riskScore != score) {
            person.riskScore = score
            personRepository.save(person)
            logger.info { "Updated risk score for '${person.name}': $score (${status.name})" }
        }

        return RiskReportDTO(
                targetName = person.name,
                status = status,
                evidence = evidence.sortedByDescending { it.date },
                flightCount = flights.size,
                riskScore = score
        )
    }

    /**
     * Finds all persons connected to the target via shared documents.
     * Computes relationship strength scores based on:
     * - Co-occurrence frequency (shared documents) × 1.0
     * - Shared flight count × 3.0
     * - Accusatory sentiment overlap × 2.0
     */
    @Transactional(readOnly = true)
    fun analyzeNetwork(targetName: String): NetworkReportDTO {
        val normalized = nameNormalizationService.normalize(targetName)

        // Native Cypher query computes the strength directly in the database
        val connectedProjections = personRepository.findNetworkStrength(normalized)

        val connectedDtos = connectedProjections.map { projection ->
            ConnectedPersonDTO(
                    name = projection.name,
                    normalizedName = projection.normalizedName,
                    riskScore = projection.riskScore,
                    strengthScore = projection.strengthScore
            )
        }

        logger.info { "Network for '$targetName': ${connectedDtos.size} connected persons (computed via Cypher)" }

        return NetworkReportDTO(
                targetName = targetName,
                connectedPersons = connectedDtos,
                connectionCount = connectedDtos.size
        )
    }

    /**
     * Returns a chronological timeline of a person's appearances across documents.
     */
    @Transactional(readOnly = true)
    fun getTimeline(targetName: String): TimelineDTO {
        val normalized = nameNormalizationService.normalize(targetName)

        val person = personRepository.findByNormalizedName(normalized)
                ?: return TimelineDTO(targetName, emptyList(), 0)

        val events = person.mentionedIn.map { mention ->
            TimelineEventDTO(
                    date = mention.document.ingestionDate,
                    documentName = mention.document.filename,
                    pageNumber = mention.pageNumber,
                    snippet = mention.snippet,
                    sentiment = mention.sentiment
            )
        }.sortedBy { it.date }

        logger.info { "Timeline for '$targetName': ${events.size} events" }

        return TimelineDTO(
                targetName = person.name,
                events = events,
                eventCount = events.size
        )
    }

    /**
     * Builds the full knowledge graph for frontend visualization.
     * Returns Person, Organization, and Document nodes with edges.
     */
    @Transactional(readOnly = true)
    fun getFullGraph(): FullGraphDTO {
        // Use bounded queries to prevent OutOfMemory errors
        val persons = personRepository.findAllPersonsWithMentionsBounded()
        val documents = documentRepository.findAll().take(300) // Bound document fetch
        val organizations = organizationRepository.findAll().take(100) // Bound org fetch

        val nodes = mutableListOf<GraphNodeDTO>()
        val edges = mutableListOf<GraphEdgeDTO>()

        // Person nodes + edges
        persons.forEach { person ->
            nodes.add(GraphNodeDTO(
                    id = "p-${person.id}",
                    label = person.name,
                    type = "PERSON",
                    riskScore = person.riskScore
            ))
            person.mentionedIn.forEach { mention ->
                edges.add(GraphEdgeDTO(
                        source = "p-${person.id}",
                        target = "d-${mention.document.fileHash}",
                        label = "MENTIONED_IN"
                ))
            }
        }

        // Organization nodes + edges
        organizations.forEach { org ->
            nodes.add(GraphNodeDTO(
                    id = "o-${org.id}",
                    label = org.name,
                    type = "ORGANIZATION"
            ))
            org.mentionedIn.forEach { mention ->
                edges.add(GraphEdgeDTO(
                        source = "o-${org.id}",
                        target = "d-${mention.document.fileHash}",
                        label = "MENTIONED_IN"
                ))
            }
        }

        // Document nodes
        documents.forEach { doc ->
            nodes.add(GraphNodeDTO(
                    id = "d-${doc.fileHash}",
                    label = doc.filename,
                    type = "DOCUMENT"
            ))
        }

        logger.info { "Full graph: ${persons.size} persons, ${organizations.size} orgs, ${documents.size} documents, ${edges.size} edges" }

        return FullGraphDTO(
                nodes = nodes,
                edges = edges,
                personCount = persons.size,
                documentCount = documents.size,
                organizationCount = organizations.size
        )
    }
}
