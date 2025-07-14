package md.aichat.scraper

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val config = ScraperConfig(
        baseUrl = "https://pizzamania.md/",
        shouldUseRetry = false,
        isPageWithProducts = false,
        crawlWholeSite = true,
        productUrlPattern = "https://ultra.md/product/",
    )
    val scraper = WebScraper(config)
    val allInfoPath = "all_website_info33.txt"

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nShutdown signal received. Saving results...")
        scraper.saveResults(allInfoPath)
        println("Results saved. Shutdown complete.")
    })

    try {
        scraper.scrape()
    } finally {
        scraper.saveResults(allInfoPath)
        println("Scraping complete.")
    }
}
