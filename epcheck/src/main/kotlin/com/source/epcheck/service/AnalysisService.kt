package com.source.epcheck.service

import com.source.epcheck.dto.ConnectedPersonDTO
import com.source.epcheck.dto.EvidenceItem
import com.source.epcheck.dto.NetworkReportDTO
import com.source.epcheck.dto.RiskReportDTO
import com.source.epcheck.dto.RiskStatus
import com.source.epcheck.repository.PersonRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnalysisService(
        private val personRepository: PersonRepository,
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
     * Uses a multi-hop Cypher query: Person → MENTIONED_IN → Document ← MENTIONED_IN ← Person.
     */
    @Transactional(readOnly = true)
    fun analyzeNetwork(targetName: String): NetworkReportDTO {
        val normalized = nameNormalizationService.normalize(targetName)

        val connected = personRepository.findConnectedPersons(normalized)
        val connectedDtos = connected.map { person ->
            ConnectedPersonDTO(
                    name = person.name,
                    normalizedName = person.normalizedName,
                    riskScore = person.riskScore
            )
        }.sortedByDescending { it.riskScore }

        logger.info { "Network for '$targetName': ${connected.size} connected persons" }

        return NetworkReportDTO(
                targetName = targetName,
                connectedPersons = connectedDtos,
                connectionCount = connected.size
        )
    }
}

