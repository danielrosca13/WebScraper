package md.aichat.scraper.service

import kotlinx.coroutines.*
import md.aichat.scraper.WebScraper
import md.aichat.scraper.ScraperConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

class ScrapingService {
    data class ScrapeJob(
        val id: String,
        val config: ScraperConfig,
        val isReturningText: Boolean,
        val logLines: MutableList<String> = mutableListOf(),
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
            val scraper = WebScraper(config)
            job.scraper = scraper
            val logBuffer = job.logLines
            val logInterceptor = { line: String ->
                synchronized(logBuffer) {
                    logBuffer.add(line)
                    if (logBuffer.size > 100) logBuffer.removeAt(0)
                }
            }
            val originalOut = System.out
            try {
                // Redirect println to logInterceptor for this job
                System.setOut(java.io.PrintStream(object : java.io.OutputStream() {
                    override fun write(b: Int) { }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        val str = String(b, off, len)
                        logInterceptor(str.trim())
                    }
                }))
                scraper.scrape()
                val resultPath = "scrape_result_${id}.txt"
                scraper.saveResults(resultPath)
                job.resultPath = resultPath
                if (isReturningText) {
                    job.resultText = scraper.getAllText()
                }
            } catch (e: Exception) {
                logInterceptor("Error: ${e.message}")
            } finally {
                job.isRunning.set(false)
                System.setOut(originalOut)
            }
        }
        return id
    }

    fun getLogLines(id: String): List<String> = jobs[id]?.logLines?.toList() ?: emptyList()

    fun isRunning(id: String): Boolean = jobs[id]?.isRunning?.get() ?: false

    fun getResultText(id: String): String? = jobs[id]?.resultText

    fun getResultFilePath(id: String): String? = jobs[id]?.resultPath

    fun getStatus(id: String): String = when {
        !jobs.containsKey(id) -> "not_existing"
        jobs[id]?.isRunning?.get() == true -> "in_progress"
        else -> "finished"
    }

    fun forceEnd(id: String) {
        jobs[id]?.job?.cancel()
        jobs[id]?.isRunning?.set(false)
        jobs[id]?.resultPath?.let { /* already saved */ } ?: run {
            // Save whatever is available from the current scraper instance
            jobs[id]?.scraper?.saveResults("scrape_result_${id}.txt")
            jobs[id]?.resultPath = "scrape_result_${id}.txt"
        }
    }
}
