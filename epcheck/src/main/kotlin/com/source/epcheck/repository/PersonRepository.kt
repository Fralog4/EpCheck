package com.source.epcheck.repository

import com.source.epcheck.domain.Person
import org.springframework.data.neo4j.repository.Neo4jRepository
import org.springframework.data.neo4j.repository.query.Query
import org.springframework.data.repository.query.Param

interface PersonRepository : Neo4jRepository<Person, Long> {

    fun findByNormalizedName(normalizedName: String): Person?

    /** Batch-fetch all Persons whose normalized_name is in the given collection. */
    fun findAllByNormalizedNameIn(names: Collection<String>): List<Person>

    /**
     * Calculates network strength scores using a native Cypher query instead of JVM memory.
     * Computes co-occurrences, shared flights, and accusatory sentiment overlap.
     */
    @Query("""
        MATCH (target:Person {normalized_name: ${'$'}name})
        MATCH (target)-[:MENTIONED_IN]->(doc:Document)<-[:MENTIONED_IN]-(connected:Person)
        WHERE connected <> target
        WITH target, connected, count(DISTINCT doc) AS coOccurrences
        
        OPTIONAL MATCH (target)-[tm:MENTIONED_IN]->(:Document)<-[cm:MENTIONED_IN]-(connected)
        WHERE tm.sentiment = 'ACCUSATORY' AND cm.sentiment = 'ACCUSATORY'
        WITH target, connected, coOccurrences, count(tm) AS sentimentOverlap
             
        OPTIONAL MATCH (target)-[:FLEW_ON]->(fl:FlightLog)<-[:FLEW_ON]-(connected)
        WITH connected, coOccurrences, sentimentOverlap, count(DISTINCT fl) AS sharedFlights
        
        WITH connected, coOccurrences, sharedFlights, sentimentOverlap,
             (coOccurrences * 1.0 + sharedFlights * 3.0 + sentimentOverlap * 2.0) AS strengthScore
        ORDER BY strengthScore DESC
        LIMIT 50
        RETURN connected.name AS name, 
               connected.normalized_name AS normalizedName, 
               coalesce(connected.risk_score, 0) AS riskScore, 
               strengthScore
    """)
    fun findNetworkStrength(@Param("name") name: String): List<NetworkStrengthProjection>

    /** Fetches a bounded subgraph (top 300 persons) to prevent OutOfMemory errors. */
    @Query("""
        MATCH (p:Person)-[m:MENTIONED_IN]->(d:Document)
        WITH p, collect(m) AS mentions, collect(d) AS docs
        ORDER BY size(mentions) DESC
        LIMIT 300
        RETURN p, mentions, docs
    """)
    fun findAllPersonsWithMentionsBounded(): List<Person>
}

/** Projection interface for Neo4j native query results. */
interface NetworkStrengthProjection {
    val name: String
    val normalizedName: String
    val riskScore: Int
    val strengthScore: Double
}
