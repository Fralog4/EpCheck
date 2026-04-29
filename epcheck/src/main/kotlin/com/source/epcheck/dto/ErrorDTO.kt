package com.source.epcheck.dto

import java.time.Instant

/**
 * Standardized error response for all API endpoints.
 * Returned by the global [com.source.epcheck.controller.GlobalExceptionHandler].
 */
data class ErrorDTO(
        val code: String,
        val message: String,
        val timestamp: Instant = Instant.now(),
        val path: String? = null,
        val details: List<String>? = null
)
