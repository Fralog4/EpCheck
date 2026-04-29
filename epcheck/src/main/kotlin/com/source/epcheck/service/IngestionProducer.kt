package com.source.epcheck.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.source.epcheck.config.KafkaConfig
import com.source.epcheck.dto.IngestionMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class IngestionProducer(
        private val kafkaTemplate: KafkaTemplate<String, String>,
        private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    fun sendIngestionJob(message: IngestionMessage) {
        val payload = objectMapper.writeValueAsString(message)
        logger.info { "Publishing ingestion job to Kafka: ${message.jobId} for file ${message.originalFilename}" }
        kafkaTemplate.send(KafkaConfig.TOPIC_INGESTION, message.jobId, payload)
    }
}
