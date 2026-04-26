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
     * Finds all persons connected to the target within N hops via shared Documents.
     * A "hop" is: Person → MENTIONED_IN → Document ← MENTIONED_IN ← Person.
     * Returns distinct connected persons (excluding the target).
     */
    @Query("""
        MATCH (source:Person {normalized_name: ${'$'}name})
              -[:MENTIONED_IN*1..2]-(connected:Person)
        WHERE connected <> source
        RETURN DISTINCT connected
    """)
    fun findConnectedPersons(@Param("name") name: String): List<Person>
}
