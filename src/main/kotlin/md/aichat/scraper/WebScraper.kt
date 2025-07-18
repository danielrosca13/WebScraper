package md.aichat.scraper

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.net.URL
import com.google.gson.GsonBuilder

class WebScraper(
    private val config: ScraperConfig,
    private val jobId: String? = null) {
    init {
        if (config.maxVisitedLinks != null && config.maxVisitedLinks < 0) {
            throw IllegalArgumentException("maxVisitedLinks cannot be negative")
        }
    }

    private val logger = LoggerFactory.getLogger(WebScraper::class.java)
    private val visitedUrls = ConcurrentHashMap.newKeySet<String>()
    private val allTextData = ConcurrentHashMap.newKeySet<String>()
    private val products = mutableListOf<Product>()

    suspend fun scrape() = coroutineScope {
        crawl(this, config.baseUrl, 0)
    }

    private fun crawl(scope: CoroutineScope, url: String, depth: Int) {
        val urlToVerify = url.replace("asc", "").replace("desc", "")
        if (!shouldVisit(urlToVerify, depth)) return
        logger.debug("[Job ${jobId ?: "N/A"}] Crawling: $url (depth $depth)")
        fetchAndProcess(scope, url, depth)
    }

    private fun shouldVisit(url: String, depth: Int): Boolean {
        if (config.maxVisitedLinks != null && config.maxVisitedLinks > 0 && visitedUrls.size >= config.maxVisitedLinks) return false
        if (!visitedUrls.add(url)) {
            logger.debug("[Job $jobId] URL already visited: $url (depth $depth)")
            return false
        }
        if (config.maxDepth != null && depth > config.maxDepth) return false
        return true
    }

    private fun fetchAndProcess(scope: CoroutineScope, url: String, depth: Int) {
        val maxRetries = 3
        val retryDelayMillis = 5_000L
        var attempt = 0
        while (true) {
            try {
                val document = Jsoup.connect(url).timeout(100_000).get()
                processDocument(document)
                launchLinks(scope, document, depth)
                break
            } catch (e: IOException) {
                attempt++
                if (shouldRetry(e) && attempt < maxRetries) {
                    logger.warn("[Job ${jobId ?: "N/A"}] Error crawling $url: ${e.message}. Retrying ($attempt/$maxRetries)...")
                    Thread.sleep(retryDelayMillis)
                } else {
                    logger.error("[Job ${jobId ?: "N/A"}] Error crawling $url: ${e.message}. Giving up after $attempt attempts.")
                    break
                }
            }
        }
    }

    public fun getAllText(): String {
        return allTextData.joinToString("\n\n")
    }

    public fun getProductsAsJson(): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(products)
    }

    private fun shouldRetry(e: IOException): Boolean {
        val msg = e.message ?: return false
        return config.shouldUseRetry && (msg.contains("500") || msg.contains("502") || msg.contains("508") || msg.contains("timed out"))
    }

    private fun processDocument(document: Document) {
        allTextData.add(DataExtractor.extractAllText(document))
        extractProductIfMatch(document)
    }

    private fun extractProductIfMatch(document: Document) {
        val selectors = config.productFieldSelectors ?: return
        val name = selectors["name"]?.let { document.select(it).firstOrNull()?.text() }
        val price = selectors["price"]?.let { document.select(it).firstOrNull()?.text() }
        val description = selectors["description"]?.let { document.select(it).firstOrNull()?.text() }
        val availability = selectors["availability"]?.let { document.select(it).firstOrNull()?.text() }
        val specifications = selectors["specifications"]?.let { document.select(it).firstOrNull()?.text() }
        val brand = selectors["brand"]?.let { document.select(it).firstOrNull()?.text() }
        val imageRaw = selectors["main_image_link"]?.let { document.select(it).firstOrNull()?.attr("src") }
        val image = if (imageRaw != null && imageRaw.startsWith("/")) {
            try {
                val base = URL(config.baseUrl)
                "${base.protocol}://${base.host}$imageRaw"
            } catch (e: Exception) {
                imageRaw
            }
        } else {
            imageRaw
        }
// Only add if at least name and price are present
        if (!name.isNullOrBlank() && !price.isNullOrBlank()) {
            val url = document.location()
            products.add(Product(name, price, description, availability, specifications, brand, image, url))
            logger.info("[Job ${jobId ?: "N/A"}] Product detected: $name, $price, $url")
        }
    }

    private fun launchLinks(scope: CoroutineScope, document: Document, depth: Int) {
        val links = document.select("a[href]")
        for (link in links) {
            val absLink = link.absUrl("href")
            if (absLink.isEmpty() || !isValidResource(absLink)) continue
            if (shouldFollowLink(absLink)) {
                scope.launch(Dispatchers.IO) {
                    crawl(this, absLink, depth + 1)
                }
            }
        }
    }

    private fun shouldFollowLink(absLink: String): Boolean {
        val matchesProductPattern = config.productUrlPattern != null && absLink.contains(config.productUrlPattern)
        val isSameDomain = absLink.startsWith(config.baseUrl) || !config.crawlWholeSite && (try { URL(absLink).host == URL(config.baseUrl).host } catch (e: Exception) { false })
        return when {
            config.crawlWholeSite -> isSameDomain
            config.isPageWithProducts -> absLink.startsWith(config.baseUrl) || matchesProductPattern
            else -> absLink.startsWith(config.baseUrl)
        }
    }

    private fun isValidResource(url: String): Boolean {
        val lower = url.lowercase()
        return !(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".mp3") || lower.endsWith(".mp4"))
    }

    fun saveResults(allInfoPath: String, productsPath: String) {
        try {
            val file = java.io.File(allInfoPath)
            file.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            FileWriter(allInfoPath).use { writer ->
                allTextData.forEach { writer.write(it + "\n\n") }
            }
            logger.info("[Job ${jobId ?: "N/A"}] All text data saved to $allInfoPath")
            // Save products as JSON
            if (products.isNotEmpty() && jobId != null) {
                val productsFile = java.io.File(productsPath)
                productsFile.parentFile?.let { parent ->
                    if (!parent.exists()) parent.mkdirs()
                }
                val gson = GsonBuilder().setPrettyPrinting().create()
                FileWriter(productsFile).use { writer ->
                    writer.write(gson.toJson(products))
                }
                logger.info("[Job $jobId] Products saved to $productsPath")
            }
        } catch (e: IOException) {
            logger.error("[Job ${jobId ?: "N/A"}] Error saving results: ${e.message}")
        }
    }
}
