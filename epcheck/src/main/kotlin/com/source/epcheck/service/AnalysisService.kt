package com.source.epcheck.service

import com.source.epcheck.dto.EvidenceItem
import com.source.epcheck.dto.RiskReportDTO
import com.source.epcheck.dto.RiskStatus
import com.source.epcheck.repository.PersonRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AnalysisService(private val personRepository: PersonRepository) {

    @Transactional(readOnly = true)
    fun analyzeProfile(targetName: String): RiskReportDTO {
        val normalized = targetName.trim().lowercase()
        // We assume mappings are done or we search by normalized
        // In reality we might not have the mapping here, just the input.
        // But let's assume input matches normalized format or logic.
        val person =
                personRepository.findByNormalizedName(normalized)
                        ?: return RiskReportDTO(targetName, RiskStatus.GREEN, emptyList(), 0)

        val flights = person.flights
        val mentions = person.mentionedIn

        val evidence =
                mentions.map {
                    EvidenceItem(
                            date = it.document.ingestionDate,
                            sourcePdf = it.document.filename,
                            page = it.pageNumber,
                            snippet = it.snippet
                    )
                } +
                        flights.map {
                            EvidenceItem(
                                    date = it.flightDate,
                                    sourcePdf = "Flight Log ${it.flightLog.id}", // Generic source
                                    page = 0,
                                    snippet =
                                            "Flew on ${it.aircraftId} from ${it.origin} to ${it.destination}"
                            )
                        }

        // Risk Logic
        var status = RiskStatus.YELLOW // Default if found

        val hasRiskyKeywords =
                mentions.any {
                    val s = it.snippet.lowercase()
                    s.contains("massage") || s.contains("recruit")
                }

        if (flights.isNotEmpty()) {
            status = RiskStatus.RED
        } else if (mentions.size > 5 || hasRiskyKeywords) {
            status = RiskStatus.ORANGE
        }

        // Update score in DB? Step 4 says "The Algorithm", doesn't explicitly say "save score".
        // But Person entity has "riskScore".
        // Let's map Status to Score: RED=100, ORANGE=75, YELLOW=25, GREEN=0
        val score =
                when (status) {
                    RiskStatus.RED -> 100
                    RiskStatus.ORANGE -> 75
                    RiskStatus.YELLOW -> 25
                    RiskStatus.GREEN -> 0
                }

        // We could save it here, but it's "AnalysisService", typically implies on-demand.
        // I won't save it to avoid side effects in a GET request unless requested.

        return RiskReportDTO(
                targetName = person.name,
                status = status,
                evidence = evidence.sortedByDescending { it.date },
                flightCount = flights.size
        )
    }
}
