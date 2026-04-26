package com.source.epcheck.service

import edu.stanford.nlp.pipeline.CoreDocument
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import java.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class NERService(
        @Value("\${ner.confidence-threshold:0.60}") private val confidenceThreshold: Double
) {

    private val logger = KotlinLogging.logger {}
    private lateinit var pipeline: StanfordCoreNLP

    @PostConstruct
    fun init() {
        logger.info { "Initializing Stanford CoreNLP (confidence threshold: $confidenceThreshold)..." }
        val props = Properties()
        // Efficient configuration: tokenize, ssplit, pos, lemma, ner
        // Avoid 'parse', 'sentiment' to keep memory usage low
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner")
        props.setProperty(
                "ner.applyFineGrained",
                "false"
        ) // We only need PERSON, ORGANIZATION, etc.
        pipeline = StanfordCoreNLP(props)
        logger.info { "Stanford CoreNLP initialized successfully." }
    }

    /**
     * Extracts PERSON entities from text with confidence scores.
     * Entities below [confidenceThreshold] are filtered out.
     *
     * @param text the page text to analyze
     * @return list of [NerResult] with name and confidence, deduplicated by name
     */
    fun extractEntitiesWithConfidence(text: String): List<NerResult> {
        if (text.isBlank()) return emptyList()

        val document = CoreDocument(text)
        pipeline.annotate(document)

        return document.entityMentions()
                .filter { it.entityType() == "PERSON" }
                .map { mention ->
                    val confidence = mention.entityTypeConfidences()
                            ?.getOrDefault("PERSON", 0.0) ?: 0.0
                    NerResult(name = mention.text(), confidence = confidence)
                }
                .filter { it.confidence >= confidenceThreshold }
                .distinctBy { it.name }
    }

    /**
     * Legacy method for backward compatibility — returns names only.
     */
    fun extractEntities(text: String): List<String> =
            extractEntitiesWithConfidence(text).map { it.name }
}

/**
 * A named entity extraction result with its confidence score.
 */
data class NerResult(
        val name: String,
        val confidence: Double
)
