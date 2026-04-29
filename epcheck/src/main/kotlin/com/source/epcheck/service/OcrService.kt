package com.source.epcheck.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import java.awt.image.BufferedImage
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Wraps Tesseract OCR for extracting text from scanned PDF pages.
 *
 * Used as a fallback when PDFBox's `PDFTextStripper` extracts little or
 * no text from a page (indicating a scanned/image-based document).
 *
 * Supports multi-language OCR via the `ocr.language` property.
 * Use `+` to combine languages: `eng+fra+spa` for English + French + Spanish.
 * Requires corresponding tessdata files to be installed.
 *
 * Configure the tessdata path via `ocr.tessdata-path` in application.properties
 * (defaults to Tess4J's bundled data).
 */
@Service
class OcrService(
        @Value("\${ocr.tessdata-path:}") private val tessdataPath: String,
        @Value("\${ocr.language:eng}") private val language: String
) {

    private val logger = KotlinLogging.logger {}
    private lateinit var tesseract: Tesseract

    @PostConstruct
    fun init() {
        val languages = language.replace(",", "+") // normalize comma-separated to Tesseract format
        logger.info { "Initializing Tesseract OCR (languages=$languages)" }
        tesseract = Tesseract()
        tesseract.setLanguage(languages)

        if (tessdataPath.isNotBlank()) {
            tesseract.setDatapath(tessdataPath)
            logger.info { "Tesseract using custom tessdata path: $tessdataPath" }
        }

        // Page segmentation mode 3 = Fully automatic page segmentation (default)
        tesseract.setPageSegMode(3)
        // OCR Engine Mode 1 = LSTM neural net only (fastest + most accurate)
        tesseract.setOcrEngineMode(1)

        logger.info { "Tesseract OCR initialized successfully with language(s): $languages" }

        // Log available languages info
        val langList = languages.split("+")
        if (langList.size > 1) {
            logger.info { "Multi-language OCR enabled: ${langList.joinToString(", ")}" }
        }
    }

    /**
     * Performs OCR on a rendered PDF page image.
     *
     * @param image the page rendered as a [BufferedImage] via PDFBox's `PDFRenderer`
     * @param pageNumber the page number (for logging)
     * @return the OCR'd text, or empty string if OCR fails
     */
    fun extractText(image: BufferedImage, pageNumber: Int): String {
        return try {
            val text = tesseract.doOCR(image)
            logger.debug { "OCR page $pageNumber: extracted ${text.length} chars" }
            text
        } catch (e: TesseractException) {
            logger.warn(e) { "OCR failed on page $pageNumber" }
            ""
        }
    }
}
