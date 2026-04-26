package com.source.epcheck.service

import org.springframework.stereotype.Service

/**
 * Domain-specific sentiment classifier for legal document snippets.
 *
 * Uses a keyword-based approach rather than CoreNLP's sentiment annotator
 * to keep memory usage low (~4GB saved). The categories are tailored to
 * the OSINT/legal context of EpsteinLens rather than generic positive/negative.
 *
 * Categories:
 * - **ACCUSATORY** — Snippet contains language suggesting criminal activity,
 *   abuse, or coercion (e.g. "abuse", "assault", "trafficking").
 * - **EXCULPATORY** — Snippet contains language suggesting innocence or
 *   dismissal (e.g. "denied", "acquitted", "no evidence").
 * - **INFORMATIONAL** — Neutral factual mention (default when no keywords match).
 */
@Service
class SentimentClassifierService {

    /**
     * Classifies the sentiment of a text snippet surrounding a person mention.
     *
     * @param snippet the ~200-char context surrounding the entity mention
     * @return one of "ACCUSATORY", "EXCULPATORY", or "INFORMATIONAL"
     */
    fun classify(snippet: String): String {
        val lower = snippet.lowercase()

        // Check ACCUSATORY first — stronger signal for risk analysis
        if (ACCUSATORY_KEYWORDS.any { lower.contains(it) }) {
            return ACCUSATORY
        }

        if (EXCULPATORY_KEYWORDS.any { lower.contains(it) }) {
            return EXCULPATORY
        }

        return INFORMATIONAL
    }

    companion object {
        const val ACCUSATORY = "ACCUSATORY"
        const val EXCULPATORY = "EXCULPATORY"
        const val INFORMATIONAL = "INFORMATIONAL"

        /**
         * Keywords indicating criminal activity, abuse, or coercion.
         * Ordered by specificity (multi-word phrases first to avoid false matches).
         */
        private val ACCUSATORY_KEYWORDS = listOf(
                // Multi-word phrases (checked first)
                "sexual abuse",
                "sexual assault",
                "child abuse",
                "human trafficking",
                "sex trafficking",
                "minor victim",
                "underage girl",
                "forced labor",
                "plea deal",
                "grand jury",
                "indictment",
                "criminal charge",
                "witness tampering",
                // Single-word keywords
                "abuse",
                "abused",
                "assault",
                "assaulted",
                "trafficking",
                "trafficked",
                "rape",
                "raped",
                "molest",
                "molested",
                "exploit",
                "exploited",
                "coerce",
                "coerced",
                "groom",
                "groomed",
                "grooming",
                "recruit",
                "recruited",
                "massage",
                "victim",
                "perpetrator",
                "predator",
                "complicit",
                "conspiracy",
                "blackmail",
                "extortion",
                "bribe",
                "lure",
                "lured",
                "prostitution",
                "pornograph",
                "underage",
                "minor"
        )

        /**
         * Keywords indicating innocence, denial, or dismissal of allegations.
         */
        private val EXCULPATORY_KEYWORDS = listOf(
                // Multi-word phrases
                "no evidence",
                "no wrongdoing",
                "not guilty",
                "charges dropped",
                "charges dismissed",
                "case dismissed",
                "cleared of",
                "found innocent",
                // Single-word keywords
                "denied",
                "denies",
                "acquitted",
                "exonerated",
                "vindicated",
                "dismissed",
                "unfounded",
                "baseless",
                "unsubstantiated",
                "innocent",
                "cooperat"  // cooperated, cooperating, cooperative
        )
    }
}
