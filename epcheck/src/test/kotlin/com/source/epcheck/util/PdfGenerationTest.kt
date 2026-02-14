package com.source.epcheck.util

import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Test

class PdfGenerationTest {

    @Test
    fun generateTestPdf() {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val contentStream = PDPageContentStream(document, page)
        // PDFBox 3.0.x API fix
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        contentStream.setFont(font, 12f)
        contentStream.beginText()
        contentStream.newLineAtOffset(100f, 700f)

        val text =
                """
            FLIGHT LOG SUMMARY - DATE: 2002-05-10
            Aircraft: N908JE
            Passengers: Kevin Spacey, Ghislaine Maxwell.
            Destination: Little St. James.
        """.trimIndent()

        text.lines().forEach { line ->
            contentStream.showText(line)
            contentStream.newLineAtOffset(0f, -15f)
        }

        contentStream.endText()
        contentStream.close()

        val file = File("test_flight_log.pdf")
        document.save(file)
        document.close()
        println("Generated PDF at: ${file.absolutePath}")
    }
}
