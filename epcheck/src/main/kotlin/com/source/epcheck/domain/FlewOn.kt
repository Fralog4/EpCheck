package com.source.epcheck.domain

import java.time.LocalDate
import org.springframework.data.neo4j.core.schema.RelationshipId
import org.springframework.data.neo4j.core.schema.RelationshipProperties
import org.springframework.data.neo4j.core.schema.TargetNode

@RelationshipProperties
data class FlewOn(
        @RelationshipId var id: Long? = null,
        @TargetNode var flightLog: FlightLog,
        var flightDate: LocalDate,
        var aircraftId: String,
        var origin: String,
        var destination: String
)
