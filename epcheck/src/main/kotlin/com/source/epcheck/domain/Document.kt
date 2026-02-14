package com.source.epcheck.domain

import java.time.LocalDate
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node

enum class DocType {
    DEPOSITION,
    FLIGHT_LOG,
    EMAIL_CHAIN
}

@Node("Document")
data class Document(
        @Id var fileHash: String,
        var filename: String,
        var ingestionDate: LocalDate,
        var docType: DocType
)
