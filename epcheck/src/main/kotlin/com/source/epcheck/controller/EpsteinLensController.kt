package com.source.epcheck.controller

import com.source.epcheck.dto.IngestionReport
import com.source.epcheck.dto.RiskReportDTO
import com.source.epcheck.service.AnalysisService
import com.source.epcheck.service.DocumentIngestionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api")
class EpsteinLensController(
        private val ingestionService: DocumentIngestionService,
        private val analysisService: AnalysisService
) {

    @PostMapping("/ingest")
    suspend fun ingestDocument(
            @RequestParam("file") file: MultipartFile
    ): ResponseEntity<IngestionReport> {
        val report = ingestionService.ingestDocument(file)
        return ResponseEntity.ok(report)
    }

    @GetMapping("/analyze/{name}")
    fun analyzeProfile(@PathVariable name: String): ResponseEntity<RiskReportDTO> {
        val report = analysisService.analyzeProfile(name)
        return ResponseEntity.ok(report)
    }
}
