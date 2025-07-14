package md.aichat.scraper

import org.jsoup.nodes.Document

object DataExtractor {
    fun extractAllText(document: Document): String = document.text()
}
