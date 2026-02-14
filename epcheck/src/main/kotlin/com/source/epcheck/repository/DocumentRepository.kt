package com.source.epcheck.repository

import com.source.epcheck.domain.Document
import org.springframework.data.neo4j.repository.Neo4jRepository

interface DocumentRepository : Neo4jRepository<Document, String> {
    fun findByFileHash(fileHash: String): Document?
}
