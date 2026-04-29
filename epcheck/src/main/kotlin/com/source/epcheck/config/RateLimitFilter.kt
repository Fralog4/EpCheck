package com.source.epcheck.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Simple sliding-window rate limiter for the ingestion endpoints.
 *
 * Limits `POST /api/ingest` and `POST /api/ingest/batch` to a configurable
 * number of requests per minute per client IP address.
 *
 * Uses in-memory tracking — suitable for single-instance deployments.
 * For multi-instance, replace with Redis-based rate limiting.
 */
@Component
class RateLimitFilter(
        @Value("\${rate-limit.requests-per-minute:10}") private val maxRequestsPerMinute: Int
) : OncePerRequestFilter() {

    private val log = KotlinLogging.logger {}

    // IP → (count, windowStart)
    private val requestCounts = ConcurrentHashMap<String, RateLimitWindow>()

    override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain
    ) {
        val path = request.requestURI
        val method = request.method

        // Only rate-limit POST ingestion endpoints
        if (method != "POST" || (!path.startsWith("/api/ingest"))) {
            filterChain.doFilter(request, response)
            return
        }

        val clientIp = getClientIp(request)
        val now = System.currentTimeMillis()
        val window = requestCounts.compute(clientIp) { _, existing ->
            if (existing == null || now - existing.windowStart > 60_000) {
                // New window
                RateLimitWindow(windowStart = now, count = AtomicInteger(1))
            } else {
                existing.count.incrementAndGet()
                existing
            }
        }!!

        val currentCount = window.count.get()

        // Add rate limit headers
        response.setHeader("X-RateLimit-Limit", maxRequestsPerMinute.toString())
        response.setHeader("X-RateLimit-Remaining", (maxRequestsPerMinute - currentCount).coerceAtLeast(0).toString())
        response.setHeader("X-RateLimit-Reset", ((window.windowStart + 60_000 - now) / 1000).toString())

        if (currentCount > maxRequestsPerMinute) {
            log.warn { "Rate limit exceeded for $clientIp: $currentCount/$maxRequestsPerMinute requests/min" }
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write("""{"error":"Rate limit exceeded","message":"Maximum $maxRequestsPerMinute uploads per minute. Try again later.","retryAfterSeconds":${(window.windowStart + 60_000 - now) / 1000}}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun getClientIp(request: HttpServletRequest): String {
        // Check common proxy headers
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        val realIp = request.getHeader("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp.trim()
        }
        return request.remoteAddr
    }

    private data class RateLimitWindow(
            val windowStart: Long,
            val count: AtomicInteger
    )
}
