package md.aichat.scraper.dto

data class ScrapeRequest(val url: String, val maxPages: Int = 100)