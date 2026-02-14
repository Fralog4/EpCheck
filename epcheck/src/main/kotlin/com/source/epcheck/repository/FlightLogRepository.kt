package com.source.epcheck.repository

import com.source.epcheck.domain.FlightLog
import org.springframework.data.neo4j.repository.Neo4jRepository

interface FlightLogRepository : Neo4jRepository<FlightLog, Long>
