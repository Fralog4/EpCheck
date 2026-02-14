package com.source.epcheck.domain

import org.springframework.data.neo4j.core.schema.RelationshipId
import org.springframework.data.neo4j.core.schema.RelationshipProperties
import org.springframework.data.neo4j.core.schema.TargetNode

@RelationshipProperties
data class MentionedIn(
        @RelationshipId var id: Long? = null,
        @TargetNode var document: Document,
        var pageNumber: Int,
        var snippet: String, // Immediate 200 chars
        var sentiment: String? = null // OPTIONAL, e.g. "ACCUSATORY"
)
