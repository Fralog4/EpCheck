package com.source.epcheck.service

import com.source.epcheck.dto.JobStatusResponse
import com.source.epcheck.dto.IngestionReport
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class JobStatusService {

    private val jobs = ConcurrentHashMap<String, JobStatusResponse>()

    fun createJob(jobId: String) {
        jobs[jobId] = JobStatusResponse(jobId, "PENDING", null, null)
    }

    fun updateStatus(jobId: String, status: String, result: IngestionReport? = null, error: String? = null) {
        jobs[jobId] = JobStatusResponse(jobId, status, result, error)
    }

    fun getStatus(jobId: String): JobStatusResponse? {
        return jobs[jobId]
    }
}
