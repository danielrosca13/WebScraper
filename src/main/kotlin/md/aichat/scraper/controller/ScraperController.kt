package md.aichat.scraper.controller

import md.aichat.scraper.ScraperConfig
import md.aichat.scraper.service.ScrapingService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.core.io.FileSystemResource
import org.slf4j.LoggerFactory

@RestController
@RequestMapping("/api/scraper")
class ScraperController(
    private val scrapingService: ScrapingService
) {
    private val logger = LoggerFactory.getLogger(ScraperController::class.java)
    @PostMapping("/start/whole-site")
    fun startWholeSiteScrape(
        @RequestParam baseUrl: String,
        @RequestParam(required = false, defaultValue = "false") shouldRetry: Boolean = false,
        @RequestParam(required = false) productPageUrl: String?,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false,
        @RequestParam(required = false) maxVisitedLinks: Int?,
        @RequestParam(required = false, defaultValue = "1") maxDepth: Int?
    ): String {
        val config = ScraperConfig(
            baseUrl = baseUrl,
            productPageUrl = productPageUrl,
            crawlWholeSite = true,
            shouldUseRetry = shouldRetry,
            maxVisitedLinks = maxVisitedLinks,
            maxDepth = maxDepth
        )
        val jobId = scrapingService.startScrape(config, isReturningText)
        logger.info("[Job $jobId] Started whole-site scrape for $baseUrl")
        return jobId
    }

    @PostMapping("/start/product-page")
    fun startProductPageScrape(
        @RequestParam baseUrl: String,
        @RequestParam productUrlPattern: String,
        @RequestParam(required = false) productPageUrl: String?,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false,
        @RequestParam(required = false) maxVisitedLinks: Int?,
        @RequestParam(required = false) maxDepth: Int?
    ): String {
        val config = ScraperConfig(
            baseUrl = baseUrl,
            isPageWithProducts = true,
            productUrlPattern = productUrlPattern,
            maxVisitedLinks = maxVisitedLinks,
            maxDepth = maxDepth,
            productPageUrl = productPageUrl
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
        val jobId = scrapingService.startScrape(config, isReturningText)
        logger.info("[Job $jobId] Started page scrape for $baseUrl")
        return jobId
    }

    @GetMapping("/logs/{id}")
    fun getLogs(@PathVariable id: String): List<String> {
        logger.info("[Job $id] Logs requested.")
        return scrapingService.getLogLines(id)
    }

    @PostMapping("/force-end/{id}")
    fun forceEnd(@PathVariable id: String): String {
        logger.info("[Job $id] Force-end requested.")
        scrapingService.forceEnd(id)
        return "Job $id force-ended and results saved."
    }

    @GetMapping("/result/all-text/{id}", produces = ["text/plain; charset=UTF-8"])
    fun getAllText(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false
    ): ResponseEntity<*> {
        logger.info("[Job $id] Result requested. isReturningText=$isReturningText")
        return if (isReturningText) {
            val text = scrapingService.getResultText(id)
            if (text != null) ResponseEntity.ok().contentType(MediaType.valueOf("text/plain; charset=UTF-8")).body(text)
            else ResponseEntity.notFound().build()
        } else {
            val filePath = scrapingService.getResultFilePath(id)
            if (filePath != null) {
                val resource = FileSystemResource(filePath)
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
                    .contentType(MediaType.valueOf("text/plain; charset=UTF-8"))
                    .body(resource)
            } else ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/result/all-products/{id}", produces = ["application/json; charset=UTF-8"])
    fun getAllProducts(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "false") isReturningText: Boolean = false
    ): ResponseEntity<*> {
        logger.info("[Job $id] Products requested. isReturningText=$isReturningText")
        return if (isReturningText) {
            val text = scrapingService.getProductsText(id)
            if (text != null) ResponseEntity.ok().contentType(MediaType.valueOf("application/json; charset=UTF-8")).body(text)
            else ResponseEntity.notFound().build()
        } else {
            val filePath = scrapingService.getProductsFilePath(id)
            if (filePath != null) {
                val resource = FileSystemResource(filePath)
                ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${resource.filename}\"")
                    .contentType(MediaType.valueOf("application/json; charset=UTF-8"))
                    .body(resource)
            } else ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/status/{id}")
    fun getStatus(@PathVariable id: String): String {
        logger.info("[Job $id] Status requested.")
        return scrapingService.getStatus(id)
    }
}
