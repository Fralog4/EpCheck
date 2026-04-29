package com.source.epcheck.controller

import com.source.epcheck.dto.ErrorDTO
import com.source.epcheck.service.EntityNotFoundException
import com.source.epcheck.service.InvalidFileException
import com.source.epcheck.service.PageLimitExceededException
import com.source.epcheck.service.SanitizationException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(InvalidFileException::class)
    fun handleInvalidFile(e: InvalidFileException, request: HttpServletRequest): ResponseEntity<ErrorDTO> {
        logger.warn { "Invalid file uploaded: ${e.message}" }
        val error = ErrorDTO(
                code = "INVALID_FILE",
                message = e.message ?: "The uploaded file is invalid",
                path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    @ExceptionHandler(PageLimitExceededException::class)
    fun handlePageLimitExceeded(e: PageLimitExceededException, request: HttpServletRequest): ResponseEntity<ErrorDTO> {
        logger.warn { "Page limit exceeded: ${e.message}" }
        val error = ErrorDTO(
                code = "PAGE_LIMIT_EXCEEDED",
                message = e.message ?: "The document exceeds the maximum allowed pages",
                path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error)
    }

    @ExceptionHandler(SanitizationException::class)
    fun handleSanitization(e: SanitizationException, request: HttpServletRequest): ResponseEntity<ErrorDTO> {
        logger.warn { "Sanitization failed: ${e.message}" }
        val error = ErrorDTO(
                code = "SANITIZATION_FAILED",
                message = "The document contains invalid or malformed content that could not be processed safely",
                path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error)
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(e: EntityNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorDTO> {
        logger.debug { "Entity not found: ${e.message}" }
        val error = ErrorDTO(
                code = "NOT_FOUND",
                message = e.message ?: "The requested entity was not found",
                path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException, request: HttpServletRequest): ResponseEntity<ErrorDTO> {
        logger.warn { "Upload size exceeded: ${e.message}" }
        val error = ErrorDTO(
                code = "FILE_TOO_LARGE",
                message = "The uploaded file exceeds the maximum allowed size (50MB)",
                path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorDTO> {
        logger.error(e) { "Unhandled exception processing request ${request.method} ${request.requestURI}" }
        val error = ErrorDTO(
                code = "INTERNAL_SERVER_ERROR",
                message = "An unexpected error occurred processing your request",
                path = request.requestURI
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }
}
