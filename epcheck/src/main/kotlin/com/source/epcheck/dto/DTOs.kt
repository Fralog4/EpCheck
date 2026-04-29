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
        val snippet: String,
        val sentiment: String? = null
)

data class RiskReportDTO(
        val targetName: String,
        val status: RiskStatus,
        val evidence: List<EvidenceItem>,
        val flightCount: Int,
        val riskScore: Int
)

data class IngestionReport(
        val fileHash: String,
        val filename: String,
        val status: String,
        val detectedType: String,
        val extractedPersonCount: Int,
        val extractedOrgCount: Int = 0
)

data class ConnectedPersonDTO(
        val name: String,
        val normalizedName: String,
        val riskScore: Int,
        val strengthScore: Double = 0.0
)

data class NetworkReportDTO(
        val targetName: String,
        val connectedPersons: List<ConnectedPersonDTO>,
        val connectionCount: Int
)

data class GraphNodeDTO(
        val id: String,
        val label: String,
        val type: String,       // "PERSON", "DOCUMENT", or "ORGANIZATION"
        val riskScore: Int? = null
)

data class GraphEdgeDTO(
        val source: String,
        val target: String,
        val label: String
)

data class FullGraphDTO(
        val nodes: List<GraphNodeDTO>,
        val edges: List<GraphEdgeDTO>,
        val personCount: Int,
        val documentCount: Int,
        val organizationCount: Int = 0
)

// ── Timeline DTOs ──

data class TimelineEventDTO(
        val date: LocalDate?,
        val documentName: String,
        val pageNumber: Int,
        val snippet: String,
        val sentiment: String? = null
)

data class TimelineDTO(
        val targetName: String,
        val events: List<TimelineEventDTO>,
        val eventCount: Int
)

// ── Alias DTOs ──

data class AliasSuggestionDTO(
        val rawName: String,
        val suggestedCanonical: String,
        val confidence: Double,
        val reason: String
)

data class AliasReportDTO(
        val suggestions: List<AliasSuggestionDTO>,
        val totalCount: Int
)

// ── Batch Ingestion ──

data class BatchIngestionReport(
        val totalFiles: Int,
        val successCount: Int,
        val skippedCount: Int,
        val failedCount: Int,
        val reports: List<IngestionReport>,
        val errors: List<String> = emptyList()
)

// ── Kafka Async DTOs ──

data class AsyncIngestionResponse(
        val jobId: String,
        val status: String,
        val message: String
)

data class JobStatusResponse(
        val jobId: String,
        val status: String,
        val result: IngestionReport?,
        val error: String?
)

data class IngestionMessage(
        val jobId: String,
        val filePath: String,
        val originalFilename: String
)

// ── GDS DTOs ──

data class PageRankDTO(
        val name: String,
        val normalizedName: String,
        val score: Double
)

data class CommunityDTO(
        val communityId: Long,
        val members: List<String>
)
