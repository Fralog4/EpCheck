package com.source.epcheck.domain

import java.time.LocalDate
import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node

@Node("FlightLog")
data class FlightLog(
        @Id @GeneratedValue var id: Long? = null,
        var flightDate: LocalDate,
        var aircraftId: String,
        var origin: String,
        var destination: String
)
