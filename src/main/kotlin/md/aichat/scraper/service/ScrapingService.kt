package md.aichat.scraper.service

import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import md.aichat.scraper.dto.Job
import md.aichat.scraper.dto.JobStatus
import md.aichat.scraper.dto.ScrapedPage
import md.aichat.scraper.repository.JobRepository
import de.l3s.boilerpipe.extractors.ArticleExtractor
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.net.URI
import java.util.LinkedList
import java.util.Queue

@Service
class ScrapingService(private val jobRepository: JobRepository) {

    @Async
    fun startScraping(job: Job, maxPages: Int) {
        try {
            job.status = JobStatus.SCRAPING
            jobRepository.save(job)

            val results = crawlSite(job.url, maxPages)

            job.result = results
            job.status = JobStatus.COMPLETED
            jobRepository.save(job)

        } catch (e: Exception) {
            println("Scraping failed for job ${job.id}: ${e.message}")
            job.status = JobStatus.FAILED
            job.error = e.message
            jobRepository.save(job)
        }
    }

    private fun crawlSite(startUrl: String, maxPages: Int): List<ScrapedPage> {
        val scrapedPages = mutableListOf<ScrapedPage>()
        val urlsToVisit: Queue<String> = LinkedList()
        val visitedUrls = mutableSetOf<String>()
        val allowedDomain = URI(startUrl).host

        urlsToVisit.add(startUrl)
        visitedUrls.add(startUrl)

        Playwright.create().use { playwright ->
            val browser = playwright.chromium().launch() // You can also use .firefox() or .webkit()
            browser.use {
                while (urlsToVisit.isNotEmpty() && scrapedPages.size < maxPages) {
                    val currentUrl = urlsToVisit.poll()
                    println("Scraping: $currentUrl")

                    val page = it.newPage()
                    try {
                        page.navigate(currentUrl, Page.NavigateOptions().apply {
                            setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                        })

                        // 1. Extract clean text content using Boilerpipe
                        val html = page.content()
                        val textContent = ArticleExtractor.INSTANCE.getText(html)
                        if (textContent.isNotBlank()) {
                            scrapedPages.add(ScrapedPage(url = currentUrl, textContent = textContent))
                        }

                        // 2. Find all new links to crawl
                        val links = page.locator("a[href]").all()
                            .mapNotNull { it.getAttribute("href") }
                            .map { toAbsoluteUrl(currentUrl, it) }
                            .filter { it.host == allowedDomain && !visitedUrls.contains(it.toString()) }

                        links.forEach { link ->
                            val linkStr = link.toString()
                            if (!visitedUrls.contains(linkStr)) {
                                visitedUrls.add(linkStr)
                                urlsToVisit.add(linkStr)
                            }
                        }
                    } catch (e: Exception) {
                        println("Could not process page $currentUrl: ${e.message}")
                    } finally {
                        page.close()
                    }
                }
            }
        }
        return scrapedPages
    }

    private fun toAbsoluteUrl(baseUrl: String, relativeUrl: String): URI {
        return URI(baseUrl).resolve(relativeUrl)
    }
}