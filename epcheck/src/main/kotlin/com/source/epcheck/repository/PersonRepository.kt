package com.source.epcheck.repository

import com.source.epcheck.domain.Person
import org.springframework.data.neo4j.repository.Neo4jRepository
import org.springframework.data.neo4j.repository.query.Query
import org.springframework.data.repository.query.Param

interface PersonRepository : Neo4jRepository<Person, Long> {

    fun findByNormalizedName(normalizedName: String): Person?

    @Query("MATCH (p:Person {normalized_name: \$name})-[r]->(d:Document) RETURN p, r, d")
    fun findNetworkDepth(@Param("name") name: String): Person?
}
