package md.aichat.scraper.dto

import java.util.UUID

enum class JobStatus { PENDING, SCRAPING, COMPLETED, FAILED }

data class Job(
    val id: String = UUID.randomUUID().toString(),
    var status: JobStatus = JobStatus.PENDING,
    val url: String,
    var result: List<ScrapedPage>? = null,
    var error: String? = null
)
