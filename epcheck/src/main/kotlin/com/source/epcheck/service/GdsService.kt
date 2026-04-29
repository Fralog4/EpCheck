package com.source.epcheck.service

import com.source.epcheck.dto.CommunityDTO
import com.source.epcheck.dto.PageRankDTO
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.neo4j.core.Neo4jClient
import org.springframework.stereotype.Service

@Service
class GdsService(
        private val neo4jClient: Neo4jClient
) {
    private val logger = KotlinLogging.logger {}

    private fun projectGraph() {
        val checkExists = """
            CALL gds.graph.exists('epsteinlens-graph') YIELD exists
            RETURN exists
        """.trimIndent()
        
        val exists = neo4jClient.query(checkExists)
                .fetchAs(Boolean::class.java)
                .mappedBy { _, record -> record.get("exists").asBoolean() }
                .one()
                .orElse(false)

        if (!exists) {
            logger.info { "Projecting graph for GDS..." }
            val projectQuery = """
                CALL gds.graph.project(
                    'epsteinlens-graph',
                    ['Person', 'Document', 'FlightLog'],
                    ['MENTIONED_IN', 'FLEW_ON']
                )
            """.trimIndent()
            neo4jClient.query(projectQuery).run()
        }
    }

    fun runPageRank(): List<PageRankDTO> {
        projectGraph()
        
        logger.info { "Running PageRank algorithm..." }
        val query = """
            CALL gds.pageRank.stream('epsteinlens-graph')
            YIELD nodeId, score
            WITH gds.util.asNode(nodeId) AS n, score
            WHERE 'Person' IN labels(n)
            RETURN n.name AS name, n.normalized_name AS normalizedName, score
            ORDER BY score DESC
            LIMIT 50
        """.trimIndent()

        return neo4jClient.query(query)
                .fetchAs(PageRankDTO::class.java)
                .mappedBy { _, record ->
                    PageRankDTO(
                            name = record.get("name").asString(),
                            normalizedName = record.get("normalizedName").asString(),
                            score = record.get("score").asDouble()
                    )
                }
                .all()
                .toList()
    }

    fun runLouvainCommunities(): List<CommunityDTO> {
        projectGraph()
        
        logger.info { "Running Louvain community detection..." }
        val query = """
            CALL gds.louvain.stream('epsteinlens-graph')
            YIELD nodeId, communityId
            WITH gds.util.asNode(nodeId) AS n, communityId
            WHERE 'Person' IN labels(n)
            RETURN communityId, collect(n.name) AS members
            ORDER BY size(members) DESC
            LIMIT 20
        """.trimIndent()

        return neo4jClient.query(query)
                .fetchAs(CommunityDTO::class.java)
                .mappedBy { _, record ->
                    val members = record.get("members").asList { it.asString() }
                    CommunityDTO(
                            communityId = record.get("communityId").asLong(),
                            members = members
                    )
                }
                .all()
                .toList()
    }
}
