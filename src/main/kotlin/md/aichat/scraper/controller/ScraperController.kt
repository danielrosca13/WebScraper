package md.aichat.scraper.controller

import md.aichat.scraper.dto.Job
import md.aichat.scraper.dto.ScrapeRequest
import md.aichat.scraper.repository.JobRepository
import md.aichat.scraper.service.ScrapingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api")
class ScraperController(
    private val scrapingService: ScrapingService,
    private val jobRepository: JobRepository
) {

    @PostMapping("/scrape")
    fun startScraping(@RequestBody request: ScrapeRequest): ResponseEntity<Map<String, String>> {
        val job = Job(url = request.url)
        jobRepository.save(job)
        scrapingService.startScraping(job, request.maxPages)
        return ResponseEntity.accepted().body(mapOf("jobId" to job.id))
    }

    @GetMapping("/status/{jobId}")
    fun getJobStatus(@PathVariable jobId: String): ResponseEntity<Map<String, Any>> {
        val job = jobRepository.findById(jobId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found")

        val response = mutableMapOf<String, Any>(
            "jobId" to job.id,
            "status" to job.status
        )
        job.error?.let { response["error"] = it }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/result/{jobId}")
    fun getScrapeResult(@PathVariable jobId: String): ResponseEntity<Job> {
        val job = jobRepository.findById(jobId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found")

        if (job.status != md.aichat.scraper.dto.JobStatus.COMPLETED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Job not yet completed. Status is ${job.status}")
        }

        return ResponseEntity.ok(job)
    }
}