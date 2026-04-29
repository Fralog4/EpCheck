package com.source.epcheck.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {

    companion object {
        const val TOPIC_INGESTION = "epsteinlens.ingestion"
    }

    @Bean
    fun ingestionTopic(): NewTopic {
        return TopicBuilder.name(TOPIC_INGESTION)
                .partitions(1)
                .replicas(1)
                .build()
    }
}
