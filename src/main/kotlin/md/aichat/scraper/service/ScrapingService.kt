package md.aichat.scraper.service

import kotlinx.coroutines.*
import md.aichat.scraper.WebScraper
import md.aichat.scraper.ScraperConfig
import md.aichat.scraper.config.JobLogBufferRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC

class ScrapingService {
    private val logger = LoggerFactory.getLogger(ScrapingService::class.java)
    data class ScrapeJob(
        val id: String,
        val config: ScraperConfig,
        val isReturningText: Boolean,
        val isRunning: AtomicBoolean = AtomicBoolean(true),
        var resultPath: String? = null,
        var resultText: String? = null,
        var job: Job? = null,
        var scraper: WebScraper? = null
    )

    private val jobs = ConcurrentHashMap<String, ScrapeJob>()

    fun startScrape(config: ScraperConfig, isReturningText: Boolean): String {
        val id = UUID.randomUUID().toString()
        val job = ScrapeJob(id, config, isReturningText)
        jobs[id] = job
        job.job = CoroutineScope(Dispatchers.IO).launch {
            MDC.put("jobId", id)
            val scraper = WebScraper(config, id)
            job.scraper = scraper
            try {
                logger.info("[Job $id] Starting scrape job.")
                scraper.scrape()
                val resultPath = "./results/scrape_result_${id}.txt"
                scraper.saveResults(resultPath)
                job.resultPath = resultPath
                if (isReturningText) {
                    job.resultText = scraper.getAllText()
                }
                logger.info("[Job $id] Scrape job finished.")
            } catch (e: Exception) {
                logger.error("[Job $id] Error during scrape job: ${e.message}", e)
            } finally {
                job.isRunning.set(false)
                logger.info("Scrape job resources cleaned up.")
                MDC.remove("jobId")
                logger.info("[Job $id] Scrape job resources cleaned up.")
            }
        }
        return id
    }

    fun getLogLines(id: String): List<String> = JobLogBufferRegistry.get(id)

    fun isRunning(id: String): Boolean = jobs[id]?.isRunning?.get() ?: false

    fun getResultText(id: String): String? = jobs[id]?.resultText

    fun getResultFilePath(id: String): String? = jobs[id]?.resultPath

    fun getStatus(id: String): String = when {
        !jobs.containsKey(id) -> "not_existing"
        jobs[id]?.isRunning?.get() == true -> "in_progress"
        else -> "finished"
    }

    fun forceEnd(id: String) {
        logger.info("[Job $id] Force-ending job.")
        jobs[id]?.job?.cancel()
        jobs[id]?.isRunning?.set(false)
        jobs[id]?.resultPath?.let { /* already saved */ } ?: run {
            // Save whatever is available from the current scraper instance
            jobs[id]?.scraper?.saveResults("scrape_result_${id}.txt")
            jobs[id]?.resultPath = "scrape_result_${id}.txt"
        }
        logger.info("[Job $id] Job force-ended and results saved.")
    }
}
