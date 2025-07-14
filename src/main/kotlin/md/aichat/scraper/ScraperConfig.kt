package md.aichat.scraper

/**
 * Holds configuration for the WebScraper.
 */
data class ScraperConfig(
    val baseUrl: String,
    val shouldUseRetry: Boolean = false,
    val isPageWithProducts: Boolean = false,
    val productUrlPattern: String? = null,
    val crawlWholeSite: Boolean = false,
    val maxVisitedLinks: Int? = null,
    val maxDepth: Int? = null,
)
