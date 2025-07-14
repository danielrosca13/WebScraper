package md.aichat.scraper.controller

import md.aichat.scraper.ScraperConfig
import md.aichat.scraper.service.ScrapingService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.core.io.FileSystemResource

@RestController
@RequestMapping("/api/scraper")
class ScraperController(
    private val scrapingService: ScrapingService = ScrapingService()
) {
    @PostMapping("/start/whole-site")
    fun startWholeSiteScrape(
        @RequestParam baseUrl: String,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false,
        @RequestParam(required = false) maxVisitedLinks: Int?,
        @RequestParam(required = false) maxDepth: Int?
    ): String {
        val config = ScraperConfig(
            baseUrl = baseUrl,
            crawlWholeSite = true,
            maxVisitedLinks = maxVisitedLinks,
            maxDepth = maxDepth
        )
        return scrapingService.startScrape(config, isReturningText)
    }

    @PostMapping("/start/product-page")
    fun startProductPageScrape(
        @RequestParam baseUrl: String,
        @RequestParam productUrlPattern: String,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false,
        @RequestParam(required = false) maxVisitedLinks: Int?,
        @RequestParam(required = false) maxDepth: Int?
    ): String {
        val config = ScraperConfig(
            baseUrl = baseUrl,
            isPageWithProducts = true,
            productUrlPattern = productUrlPattern,
            maxVisitedLinks = maxVisitedLinks,
            maxDepth = maxDepth
        )
        return scrapingService.startScrape(config, isReturningText)
    }

    @PostMapping("/start/page")
    fun startPageScrape(
        @RequestParam baseUrl: String,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false,
        @RequestParam(required = false) maxVisitedLinks: Int?,
        @RequestParam(required = false) maxDepth: Int?
    ): String {
        val config = ScraperConfig(
            baseUrl = baseUrl,
            maxVisitedLinks = maxVisitedLinks,
            maxDepth = maxDepth
        )
        return scrapingService.startScrape(config, isReturningText)
    }

    @GetMapping("/logs/{id}")
    fun getLogs(@PathVariable id: String): List<String> = scrapingService.getLogLines(id)

    @PostMapping("/force-end/{id}")
    fun forceEnd(@PathVariable id: String): String {
        scrapingService.forceEnd(id)
        return "Job $id force-ended and results saved."
    }

    @GetMapping("/result/{id}")
    fun getResult(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false
    ): ResponseEntity<*> {
        return if (isReturningText) {
            val text = scrapingService.getResultText(id)
            if (text != null) ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(text)
            else ResponseEntity.notFound().build()
        } else {
            val filePath = scrapingService.getResultFilePath(id)
            if (filePath != null) {
                val resource = FileSystemResource(filePath)
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(resource)
            } else ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/status/{id}")
    fun getStatus(@PathVariable id: String): String = scrapingService.getStatus(id)
}
