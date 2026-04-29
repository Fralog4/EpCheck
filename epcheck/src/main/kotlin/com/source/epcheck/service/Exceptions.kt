package com.source.epcheck.service

/**
 * Thrown when an uploaded file fails validation checks:
 * - Not a valid PDF (wrong MIME type or missing PDF header)
 * - Exceeds maximum page count
 * - Empty or corrupt file
 */
class InvalidFileException(message: String) : RuntimeException(message)

/**
 * Thrown when a PDF exceeds the configured maximum page count.
 */
class PageLimitExceededException(
        val pageCount: Int,
        val maxPages: Int
) : RuntimeException("PDF has $pageCount pages, exceeding the maximum of $maxPages")

/**
 * Thrown when a requested entity (person, document) is not found in the graph.
 */
class EntityNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when text sanitization detects potentially malicious content.
 */
class SanitizationException(message: String) : RuntimeException(message)
