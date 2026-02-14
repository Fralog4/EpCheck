package com.source.epcheck.domain

import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Property

@Node("Person")
data class Person(
        @Id @GeneratedValue var id: Long? = null,
        @Property("name") var name: String,
        @Property("normalized_name") var normalizedName: String,
        @Property("risk_score") var riskScore: Int = 0,
        @org.springframework.data.neo4j.core.schema.Relationship(
                type = "MENTIONED_IN",
                direction =
                        org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING
        )
        var mentionedIn: MutableList<MentionedIn> = mutableListOf(),
        @org.springframework.data.neo4j.core.schema.Relationship(
                type = "FLEW_ON",
                direction =
                        org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING
        )
        var flights: MutableList<FlewOn> = mutableListOf()
)
