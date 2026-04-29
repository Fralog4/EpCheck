package com.source.epcheck.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

/**
 * Custom Micrometer metrics for EpsteinLens.
 * Tracked via Prometheus and visualized in Grafana.
 *
 * Metrics:
 * - `epsteinlens.ingestion.total` — Total documents ingested (counter, by status)
 * - `epsteinlens.ingestion.duration` — Ingestion processing time (timer)
 * - `epsteinlens.ner.entities.total` — Total NER entities extracted (counter, by type)
 * - `epsteinlens.analysis.requests.total` — Total risk analysis requests (counter)
 * - `epsteinlens.batch.total` — Total batch ingestion requests (counter)
 */
@Component
class IngestionMetrics(registry: MeterRegistry) {

    // ── Ingestion Counters ──
    val ingestionSuccess: Counter = Counter.builder("epsteinlens.ingestion.total")
            .tag("status", "success")
            .description("Total successful document ingestions")
            .register(registry)

    val ingestionSkipped: Counter = Counter.builder("epsteinlens.ingestion.total")
            .tag("status", "skipped")
            .description("Total skipped (duplicate) document ingestions")
            .register(registry)

    val ingestionFailed: Counter = Counter.builder("epsteinlens.ingestion.total")
            .tag("status", "failed")
            .description("Total failed document ingestions")
            .register(registry)

    // ── Timing ──
    val ingestionTimer: Timer = Timer.builder("epsteinlens.ingestion.duration")
            .description("Time taken to process a single document ingestion")
            .register(registry)

    // ── NER Counters ──
    val nerPersonsExtracted: Counter = Counter.builder("epsteinlens.ner.entities.total")
            .tag("type", "PERSON")
            .description("Total PERSON entities extracted by NER")
            .register(registry)

    val nerOrgsExtracted: Counter = Counter.builder("epsteinlens.ner.entities.total")
            .tag("type", "ORGANIZATION")
            .description("Total ORGANIZATION entities extracted by NER")
            .register(registry)

    // ── Analysis Counters ──
    val analysisRequests: Counter = Counter.builder("epsteinlens.analysis.requests.total")
            .description("Total risk analysis requests")
            .register(registry)

    // ── Batch Counter ──
    val batchRequests: Counter = Counter.builder("epsteinlens.batch.total")
            .description("Total batch ingestion requests")
            .register(registry)
}
