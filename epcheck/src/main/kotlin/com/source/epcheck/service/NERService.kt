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
        props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger")
        props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz")
        props.setProperty("ner.useSUTime", "false")
        props.setProperty(
                "ner.applyFineGrained",
                "false"
        ) // We only need PERSON, ORGANIZATION, etc.
        pipeline = StanfordCoreNLP(props)
        logger.info { "Stanford CoreNLP initialized successfully." }
    }

    /** Supported entity types for extraction. */
    companion object {
        val SUPPORTED_ENTITY_TYPES = setOf("PERSON", "ORGANIZATION")
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
                    NerResult(name = mention.text(), confidence = confidence, entityType = "PERSON")
                }
                .filter { it.confidence >= confidenceThreshold }
                .distinctBy { it.name }
    }

    /**
     * Extracts both PERSON and ORGANIZATION entities from text with confidence scores.
     * Entities below [confidenceThreshold] are filtered out.
     *
     * @param text the page text to analyze
     * @return list of [NerResult] with name, confidence, and entityType
     */
    fun extractAllEntitiesWithConfidence(text: String): List<NerResult> {
        if (text.isBlank()) return emptyList()

        val document = CoreDocument(text)
        pipeline.annotate(document)

        return document.entityMentions()
                .filter { it.entityType() in SUPPORTED_ENTITY_TYPES }
                .map { mention ->
                    val type = mention.entityType()
                    val confidence = mention.entityTypeConfidences()
                            ?.getOrDefault(type, 0.0) ?: 0.0
                    NerResult(name = mention.text(), confidence = confidence, entityType = type)
                }
                .filter { it.confidence >= confidenceThreshold }
                .groupBy { "${it.entityType}:${it.name}" }
                .mapNotNull { (_, entities) -> entities.maxByOrNull { it.confidence } }
    }

    /**
     * Legacy method for backward compatibility — returns PERSON names only.
     */
    fun extractEntities(text: String): List<String> =
            extractEntitiesWithConfidence(text).map { it.name }
}

/**
 * A named entity extraction result with its confidence score and entity type.
 */
data class NerResult(
        val name: String,
        val confidence: Double,
        val entityType: String = "PERSON"
)
