package com.source.epcheck.service

import edu.stanford.nlp.pipeline.CoreDocument
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import java.util.*
import org.springframework.stereotype.Service

@Service
class NERService {

    private val logger = KotlinLogging.logger {}
    private lateinit var pipeline: StanfordCoreNLP

    @PostConstruct
    fun init() {
        logger.info { "Initializing Stanford CoreNLP..." }
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

    fun extractEntities(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val document = CoreDocument(text)
        pipeline.annotate(document)

        return document.entityMentions()
                .filter { it.entityType() == "PERSON" }
                .map { it.text() }
                .distinct()
    }
}
