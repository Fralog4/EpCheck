package com.source.epcheck.dto

import java.time.LocalDate

enum class RiskStatus {
    RED,
    ORANGE,
    YELLOW,
    GREEN
}

data class EvidenceItem(
        val date: LocalDate?,
        val sourcePdf: String,
        val page: Int,
        val snippet: String
)

data class RiskReportDTO(
        val targetName: String,
        val status: RiskStatus,
        val evidence: List<EvidenceItem>,
        val flightCount: Int
)

data class IngestionReport(
        val fileHash: String,
        val filename: String,
        val status: String,
        val detectedType: String,
        val extractedPersonCount: Int
)
