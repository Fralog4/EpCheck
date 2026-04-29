package com.source.epcheck.domain

import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Property

@Node("Organization")
data class Organization(
        @Id @GeneratedValue var id: Long? = null,
        @Property("name") var name: String,
        @Property("normalized_name") var normalizedName: String,
        @org.springframework.data.neo4j.core.schema.Relationship(
                type = "MENTIONED_IN",
                direction =
                        org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING
        )
        var mentionedIn: MutableList<MentionedIn> = mutableListOf()
)
