package com.source.epcheck.service

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.springframework.stereotype.Service

/**
 * Parses structured flight data from raw flight log text using regex patterns.
 *
 * Handles common formats found in Epstein-related flight logs:
 * - Dates: `DATE: 2002-05-10`, `Date: 01/15/2003`, inline ISO dates
 * - Aircraft: FAA N-number format (e.g. `N908JE`, `N256BA`)
 * - Origin/Destination: `Origin: Teterboro`, `From: KTEB`, `Departure: Miami`
 */
@Service
class FlightLogParserService {

    private val logger = KotlinLogging.logger {}

    // ── Date patterns ──
    // "DATE: 2002-05-10" or "Date: 2002-05-10"
    private val dateIsoPattern = Regex(
            """(?:DATE|Date)\s*:\s*(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE
    )
    // "Date: 01/15/2003" (MM/DD/YYYY)
    private val dateUsPattern = Regex(
            """(?:DATE|Date)\s*:\s*(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE
    )
    // Standalone ISO date anywhere in text (fallback)
    private val dateInlinePattern = Regex("""(\d{4}-\d{2}-\d{2})""")

    // ── Aircraft pattern ──
    // FAA N-number: N followed by 1-5 digits and 0-2 uppercase letters
    private val aircraftPattern = Regex("""(N\d{1,5}[A-Z]{0,2})""")

    // ── Origin patterns ──
    private val originPattern = Regex(
            """(?:Origin|From|Departure)\s*:\s*([^\n\r]{1,100})""", RegexOption.IGNORE_CASE
    )

    // ── Destination patterns ──
    private val destinationPattern = Regex(
            """(?:Destination|To|Arrival)\s*:\s*([^\n\r]{1,100})""", RegexOption.IGNORE_CASE
    )

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val usFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    /**
     * Parses a single [ParsedFlightRecord] from a page of flight log text.
     *
     * Extracts date, aircraft ID, origin, and destination using regex.
     * Returns a record with parsed values where found, and sensible
     * defaults (`"UNKNOWN"`) where not.
     *
     * @param pageText raw text extracted from a single PDF page
     * @return parsed flight record
     */
    fun parseFlightRecord(pageText: String): ParsedFlightRecord {
        val date = parseDate(pageText)
        val aircraft = parseAircraftId(pageText)
        val origin = parseOrigin(pageText)
        val destination = parseDestination(pageText)

        logger.debug {
            "Parsed flight: date=$date, aircraft=$aircraft, origin=$origin, dest=$destination"
        }

        return ParsedFlightRecord(
                flightDate = date,
                aircraftId = aircraft,
                origin = origin,
                destination = destination
        )
    }

    private fun parseDate(text: String): LocalDate? {
        // Try "DATE: 2002-05-10" (ISO)
        dateIsoPattern.find(text)?.let { match ->
            return tryParseDate(match.groupValues[1], isoFormatter)
        }

        // Try "Date: 01/15/2003" (US format)
        dateUsPattern.find(text)?.let { match ->
            return tryParseDate(match.groupValues[1], usFormatter)
        }

        // Fallback: any inline ISO date
        dateInlinePattern.find(text)?.let { match ->
            return tryParseDate(match.groupValues[1], isoFormatter)
        }

        return null
    }

    private fun tryParseDate(value: String, formatter: DateTimeFormatter): LocalDate? {
        return try {
            LocalDate.parse(value, formatter)
        } catch (e: DateTimeParseException) {
            logger.warn { "Failed to parse date: '$value'" }
            null
        }
    }

    private fun parseAircraftId(text: String): String {
        return aircraftPattern.find(text)?.groupValues?.get(1) ?: "UNKNOWN"
    }

    private fun parseOrigin(text: String): String {
        return originPattern.find(text)
                ?.groupValues?.get(1)
                ?.trim()
                ?.trimEnd('.', ',')
                ?: "UNKNOWN"
    }

    private fun parseDestination(text: String): String {
        return destinationPattern.find(text)
                ?.groupValues?.get(1)
                ?.trim()
                ?.trimEnd('.', ',')
                ?: "UNKNOWN"
    }
}

/**
 * Holds flight data extracted from a page of flight log text.
 * Fields default to `null`/`"UNKNOWN"` when parsing fails.
 */
data class ParsedFlightRecord(
        val flightDate: LocalDate?,
        val aircraftId: String,
        val origin: String,
        val destination: String
)
