package com.source.epcheck.controller

import com.source.epcheck.dto.CommunityDTO
import com.source.epcheck.dto.PageRankDTO
import com.source.epcheck.service.GdsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/gds")
class GdsController(
        private val gdsService: GdsService
) {

    @GetMapping("/pagerank")
    fun getPageRank(): ResponseEntity<List<PageRankDTO>> {
        val results = gdsService.runPageRank()
        return ResponseEntity.ok(results)
    }

    @GetMapping("/communities")
    fun getCommunities(): ResponseEntity<List<CommunityDTO>> {
        val results = gdsService.runLouvainCommunities()
        return ResponseEntity.ok(results)
    }
}
