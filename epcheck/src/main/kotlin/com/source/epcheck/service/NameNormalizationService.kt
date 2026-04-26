package com.source.epcheck.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

/**
 * Normalizes person names using a two-tier strategy:
 *
 * 1. **Alias Database** — Exact match against a curated map of known name
 *    variations for key individuals in the Epstein case (nicknames, titles,
 *    shortened forms). All entries map to a single canonical name.
 *
 * 2. **Jaro-Winkler Fuzzy Matching** — If no exact alias matches, computes
 *    string similarity against all known canonical names and their aliases.
 *    If the best match exceeds the threshold (0.90), returns the canonical name.
 *    This handles typos, OCR artifacts, and minor spelling variations.
 *
 * Falls back to `lowercase().trim()` when neither strategy matches.
 */
@Service
class NameNormalizationService {

    private val logger = KotlinLogging.logger {}

    /**
     * Normalizes a raw person name to a canonical form.
     *
     * @param raw the name as extracted by NER
     * @return the canonical normalized name
     */
    fun normalize(raw: String): String {
        val lower = raw.trim().lowercase()

        // Tier 1: Exact alias lookup
        ALIAS_TO_CANONICAL[lower]?.let { canonical ->
            logger.debug { "Exact alias match: '$lower' → '$canonical'" }
            return canonical
        }

        // Tier 2: Jaro-Winkler fuzzy match against all known names
        var bestCanonical: String? = null
        var bestScore = 0.0

        for ((canonical, aliases) in CANONICAL_TO_ALIASES) {
            // Check against the canonical name itself
            val canonicalScore = jaroWinklerSimilarity(lower, canonical)
            if (canonicalScore > bestScore) {
                bestScore = canonicalScore
                bestCanonical = canonical
            }

            // Check against all aliases
            for (alias in aliases) {
                val aliasScore = jaroWinklerSimilarity(lower, alias)
                if (aliasScore > bestScore) {
                    bestScore = aliasScore
                    bestCanonical = canonical
                }
            }
        }

        if (bestScore >= SIMILARITY_THRESHOLD && bestCanonical != null) {
            logger.debug { "Fuzzy match: '$lower' → '$bestCanonical' (score: ${"%.3f".format(bestScore)})" }
            return bestCanonical
        }

        // No match — return cleaned lowercase
        return lower
    }

    // ── Jaro-Winkler Implementation ──
    // Zero-dependency implementation to avoid adding Apache Commons Text.

    /**
     * Computes the Jaro-Winkler similarity between two strings.
     * Returns a value between 0.0 (no match) and 1.0 (exact match).
     *
     * The Winkler modification gives extra weight to strings that share
     * a common prefix, which is ideal for person names (first name match).
     */
    internal fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val jaro = jaroSimilarity(s1, s2)

        // Winkler: boost for common prefix (up to 4 chars)
        val prefixLength = s1.zip(s2).takeWhile { (a, b) -> a == b }.size.coerceAtMost(4)
        val winklerBoost = prefixLength * WINKLER_PREFIX_WEIGHT * (1 - jaro)

        return jaro + winklerBoost
    }

    private fun jaroSimilarity(s1: String, s2: String): Double {
        val maxDist = (maxOf(s1.length, s2.length) / 2) - 1
        if (maxDist < 0) return 0.0

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)
        var matches = 0
        var transpositions = 0

        // Find matching characters
        for (i in s1.indices) {
            val start = maxOf(0, i - maxDist)
            val end = minOf(i + maxDist + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        // Count transpositions
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        return (m / s1.length + m / s2.length + (m - transpositions / 2.0) / m) / 3.0
    }

    companion object {
        /** Minimum similarity score to consider a fuzzy match valid. */
        const val SIMILARITY_THRESHOLD = 0.90

        /** Winkler prefix scaling factor (standard value: 0.1). */
        private const val WINKLER_PREFIX_WEIGHT = 0.1

        /**
         * Canonical names mapped to their known aliases/variations.
         * Aliases include: nicknames, shortened names, titles, maiden names.
         */
        private val CANONICAL_TO_ALIASES: Map<String, List<String>> = mapOf(
                "jeffrey edward epstein" to listOf(
                        "jeffrey epstein", "jeff epstein", "j. epstein", "j epstein"
                ),
                "ghislaine noelle marion maxwell" to listOf(
                        "ghislaine maxwell", "g. maxwell", "maxwell"
                ),
                "william jefferson clinton" to listOf(
                        "bill clinton", "w. clinton", "president clinton",
                        "william clinton", "wm clinton"
                ),
                "andrew albert christian edward" to listOf(
                        "prince andrew", "duke of york", "andrew windsor",
                        "prince andrew duke of york"
                ),
                "donald john trump" to listOf(
                        "donald trump", "trump", "d. trump"
                ),
                "alan morton dershowitz" to listOf(
                        "alan dershowitz", "a. dershowitz", "dershowitz"
                ),
                "virginia louise giuffre" to listOf(
                        "virginia giuffre", "virginia roberts", "virginia roberts giuffre",
                        "v. giuffre", "v. roberts"
                ),
                "leslie herbert wexner" to listOf(
                        "leslie wexner", "les wexner", "l. wexner"
                ),
                "jean-luc brunel" to listOf(
                        "jean luc brunel", "jl brunel", "brunel"
                ),
                "sarah kellen" to listOf(
                        "sarah kellen vickers", "s. kellen"
                ),
                "nadia marcinkova" to listOf(
                        "nadia marcinko", "n. marcinkova"
                ),
                "kevin edward spacey fowler" to listOf(
                        "kevin spacey", "k. spacey"
                )
        )

        /**
         * Reverse index: alias → canonical name (for O(1) exact lookup).
         * Built automatically from [CANONICAL_TO_ALIASES].
         */
        private val ALIAS_TO_CANONICAL: Map<String, String> = buildMap {
            for ((canonical, aliases) in CANONICAL_TO_ALIASES) {
                put(canonical, canonical) // canonical maps to itself
                for (alias in aliases) {
                    put(alias, canonical)
                }
            }
        }
    }
}
