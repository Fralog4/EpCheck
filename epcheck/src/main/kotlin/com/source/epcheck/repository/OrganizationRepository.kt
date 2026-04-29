package com.source.epcheck.repository

import com.source.epcheck.domain.Organization
import org.springframework.data.neo4j.repository.Neo4jRepository

interface OrganizationRepository : Neo4jRepository<Organization, Long> {

    fun findByNormalizedName(normalizedName: String): Organization?

    fun findAllByNormalizedNameIn(names: Collection<String>): List<Organization>
}
