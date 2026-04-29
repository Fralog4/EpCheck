package com.source.epcheck.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.source.epcheck.config.KafkaConfig
import com.source.epcheck.dto.IngestionMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class IngestionConsumer(
        private val documentIngestionService: DocumentIngestionService,
        private val jobStatusService: JobStatusService,
        private val objectMapper: ObjectMapper
) {
    private val logger = KotlinLogging.logger {}

    @KafkaListener(topics = [KafkaConfig.TOPIC_INGESTION], groupId = "epsteinlens-group")
    fun consumeIngestionJob(payload: String) {
        val message = try {
            objectMapper.readValue(payload, IngestionMessage::class.java)
        } catch (e: Exception) {
            logger.error(e) { "Failed to deserialize Kafka message" }
            return
        }

        val jobId = message.jobId
        val filePath = message.filePath
        logger.info { "Kafka Consumer picked up job $jobId for file ${message.originalFilename}" }

        jobStatusService.updateStatus(jobId, "PROCESSING")

        val file = File(filePath)
        if (!file.exists()) {
            val errorMsg = "File not found at path: $filePath"
            logger.error { errorMsg }
            jobStatusService.updateStatus(jobId, "FAILED", null, errorMsg)
            return
        }

        try {
            // Adapt the local file to MultipartFile for the ingestion service
            val multipartFile = LocalFileMultipartFile(file, message.originalFilename)
            
            // ingestDocument is a suspend function, we run it synchronously here
            // since Kafka listeners run on their own threads and we want to process sequentially
            val report = runBlocking {
                documentIngestionService.ingestDocument(multipartFile)
            }
            
            jobStatusService.updateStatus(jobId, "COMPLETED", report, null)
            logger.info { "Job $jobId completed successfully" }
            
            // Clean up temporary file
            file.delete()
        } catch (e: Exception) {
            logger.error(e) { "Job $jobId failed during processing" }
            jobStatusService.updateStatus(jobId, "FAILED", null, e.message)
            // Clean up temporary file on failure
            file.delete()
        }
    }
}

/** Simple adapter to pass a java.io.File as a MultipartFile to existing services. */
class LocalFileMultipartFile(
        private val file: File,
        private val originalName: String
) : MultipartFile {
    override fun getName(): String = file.name
    override fun getOriginalFilename(): String = originalName
    override fun getContentType(): String = "application/pdf"
    override fun isEmpty(): Boolean = file.length() == 0L
    override fun getSize(): Long = file.length()
    override fun getBytes(): ByteArray = file.readBytes()
    override fun getInputStream(): InputStream = FileInputStream(file)
    override fun transferTo(dest: File) {
        file.copyTo(dest, overwrite = true)
    }
}
