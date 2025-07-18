package md.aichat.scraper.repository

import md.aichat.scraper.dto.Job
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
open class JobRepository {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun findById(id: String): Job? = jobs[id]
    fun save(job: Job) {
        jobs[job.id] = job
    }
}
