package com.source.epcheck.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Analyzes NER output to discover potential name aliases by detecting
 * co-occurrence patterns in document text.
 *
 * Strategy:
 * 1. When two PERSON entities share a surname (last word), they are likely aliases
 *    (e.g., "Bill" and "Bill Clinton" on the same page).
 * 2. When a single-word name (e.g., "Bill") appears near a known canonical
 *    surname from the alias database, a link is suggested.
 * 3. Proximity-based: if two names appear within 200 chars of each other in
 *    the raw text, they have a higher chance of being co-referent.
 *
 * This is a **suggestion engine** — it stores suggestions for later review.
 */
@Service
class AliasDiscoveryService(
        private val nameNormalizationService: NameNormalizationService
) {

    private val logger = KotlinLogging.logger {}
    private val _suggestions = mutableListOf<AliasSuggestion>()

    /** Returns all accumulated alias suggestions. Thread-safe snapshot. */
    val suggestions: List<AliasSuggestion>
        get() = synchronized(_suggestions) { _suggestions.toList() }

    /**
     * Analyzes a set of PERSON names from the same page for potential aliases.
     *
     * @param names list of PERSON entity names from a single page
     * @param pageText the raw page text for proximity checking
     */
    fun analyzeCoOccurrences(names: List<String>, pageText: String) {
        if (names.size < 2) return

        val pairs = names.flatMapIndexed { i, a ->
            names.drop(i + 1).map { b -> a to b }
        }

        for ((nameA, nameB) in pairs) {
            // Strategy 1: Shared surname detection (handles inversions)
            val partsA = nameA.trim().lowercase().split("\\s+".toRegex())
            val partsB = nameB.trim().lowercase().split("\\s+".toRegex())
            
            val potentialSurnamesA = setOf(partsA.first(), partsA.last())
            val potentialSurnamesB = setOf(partsB.first(), partsB.last())
            
            val commonSurname = potentialSurnamesA.intersect(potentialSurnamesB)
                .filter { it.length > 2 } // Avoid short initials matching
                .firstOrNull()

            if (commonSurname != null && nameA != nameB) {
                val shorter = if (nameA.length < nameB.length) nameA else nameB
                val longer = if (nameA.length < nameB.length) nameB else nameA
                addSuggestion(shorter, longer, 0.85, "Shared surname: '$commonSurname'")
                continue
            }

            // Strategy 2: Single-word name near a multi-word name
            val surnameA = partsA.last()
            val surnameB = partsB.last()

            if (partsA.size == 1 && partsB.size > 1 && surnameB == partsA[0]) {
                // e.g., "Clinton" appearing near "Bill Clinton"
                addSuggestion(nameA, nameB, 0.75, "Single name matches surname")
            } else if (partsB.size == 1 && partsA.size > 1 && surnameA == partsB[0]) {
                addSuggestion(nameB, nameA, 0.75, "Single name matches surname")
            }

            // Strategy 3: Proximity-based linkage
            if (partsA.size == 1 || partsB.size == 1) {
                val idxA = pageText.indexOf(nameA)
                val idxB = pageText.indexOf(nameB)
                if (idxA >= 0 && idxB >= 0 && kotlin.math.abs(idxA - idxB) < 200) {
                    val single = if (partsA.size == 1) nameA else nameB
                    val multi = if (partsA.size == 1) nameB else nameA
                    if (single != multi) {
                        // Boost confidence if the multi-word name is already canonical
                        val baseConfidence = if (partsA.size > 1 && partsB.size > 1) 0.80 else 0.50
                        addSuggestion(single, multi, baseConfidence, "Proximity (<200 chars)")
                    }
                }
            }
        }
    }

    private fun addSuggestion(rawName: String, suggestedCanonical: String, confidence: Double, reason: String) {
        val normalized = nameNormalizationService.normalize(suggestedCanonical)
        val suggestion = AliasSuggestion(
                rawName = rawName,
                suggestedCanonical = suggestedCanonical,
                normalizedCanonical = normalized,
                confidence = confidence,
                reason = reason
        )

        synchronized(_suggestions) {
            // Avoid duplicates
            if (_suggestions.none { it.rawName == rawName && it.normalizedCanonical == normalized }) {
                _suggestions.add(suggestion)
                logger.info { "Alias suggestion: '$rawName' → '$suggestedCanonical' ($reason, confidence=$confidence)" }
            }
        }
    }

    /** Clears all suggestions (useful for testing). */
    fun clearSuggestions() = synchronized(_suggestions) { _suggestions.clear() }
}

/**
 * A suggested alias linkage discovered during NER analysis.
 */
data class AliasSuggestion(
        val rawName: String,
        val suggestedCanonical: String,
        val normalizedCanonical: String,
        val confidence: Double,
        val reason: String
)
