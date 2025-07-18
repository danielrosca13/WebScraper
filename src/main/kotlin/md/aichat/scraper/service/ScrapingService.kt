package md.aichat.scraper.service

import kotlinx.coroutines.*
import md.aichat.scraper.WebScraper
import md.aichat.scraper.ScraperConfig
import md.aichat.scraper.config.JobLogBufferRegistry
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ScrapingService {
    private val logger = LoggerFactory.getLogger(ScrapingService::class.java)
    @Value("\${chatgpt.api.key}")
    private lateinit var apiKey: String
    @Value("\${chatgpt.base.url}")
    private lateinit var baseUrl: String
    @Value("\${scraper.results.dir}")
    private lateinit var scraperResultPath: String
    data class ScrapeJob(
        val id: String,
        val config: ScraperConfig,
        val isReturningText: Boolean,
        val isRunning: AtomicBoolean = AtomicBoolean(true),
        var resultPath: String? = null,
        var resultText: String? = null,
        var productPath: String? = null,
        var products: String? = null,
        var job: Job? = null,
        var scraper: WebScraper? = null
    )

    private val jobs = ConcurrentHashMap<String, ScrapeJob>()

    fun startScrape(config: ScraperConfig, isReturningText: Boolean): String {
        val id = UUID.randomUUID().toString()
        var selectors: Map<String, String>? = null
        // If productPageUrl is provided, fetch HTML and get selectors from OpenAI
        if (config.productPageUrl != null) {
            try {
                val html = fetchHtml(config.productPageUrl)
                selectors = getProductSelectorsFromOpenAI(id, html)
                logger.debug("[Job $id] Product selectors from OpenAI: $selectors")
            } catch (e: Exception) {
                logger.error("[Job $id] Error getting product selectors from OpenAI: ${e.message}", e)
            }
        }
        val configWithSelectors = config.copy(productFieldSelectors = selectors)
        val job = ScrapeJob(id, configWithSelectors, isReturningText)
        jobs[id] = job
        job.job = CoroutineScope(Dispatchers.IO).launch {
            MDC.put("jobId", id)
            val scraper = WebScraper(configWithSelectors, id)
            job.scraper = scraper
            try {
                logger.info("[Job $id] Starting scrape job.")
                scraper.scrape()
                val resultPath = "$scraperResultPath/${id}/scrape_result_${id}.txt"
                val productPath = "$scraperResultPath/${id}/products_${id}.json"
                scraper.saveResults(resultPath, productPath)
                job.resultPath = resultPath
                job.productPath = productPath
                if (isReturningText) {
                    job.resultText = scraper.getAllText()
                    job.products = scraper.getProductsAsJson()
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

    private fun fetchHtml(url: String): String {
        return org.jsoup.Jsoup.connect(url).timeout(100_000).get().body().toString()
    }

    private fun getProductSelectorsFromOpenAI(jobId: String, html: String): Map<String, String> {
        val prompt = """
            You are extracting product data from an e-commerce product page HTML. Based on the HTML I give you, return a valid JSON object with the following fields as keys:
            
            - name (name of the product)
            - price (price of the product)
            - description (description of the product)
            - specs (specifications of the product)
            - brand (brand of the product, not always present and is not name of the website)
            - availability (availability status of the product)
            - main_image_link (main image URL of the product)
            
            Each field must follow this structure:
            
            {
              "name": {
                "selector": "div.class",
              }
            }
            
            Formatting Rules:
            1. All string values and keys must use standard JSON double quotes `"` — never `\\\"` or any other escape.
            2. Do not escape quotes inside selectors. If you need quotes inside a selector, use single quotes `'` (e.g., `doc.select('div.class')`).
            3. Do not use Markdown formatting, code blocks, or explanations — only return the JSON object directly.
            4. All strings must be single-line. No multiline strings or line breaks inside values.
            5. Ensure this JSON is directly valid for use in Kotlin with `org.json.JSONObject`.
            
            When generating the `method`, assume it's Kotlin using Jsoup. Use valid Kotlin expressions for `.select(...)` and apply `.text()`, `.attr(...)`, or `.eachText()` as appropriate.
            
            If a field like "specs" or "brand" isn't clearly marked, use the closest reasonable selector or skip it entirely — but never put explanations like "not found" or "inferred".
            
            At the end of this prompt, I will pass in the HTML of the product page as `html`.
            
            Return only a valid JSON object, strictly following these rules.
            
            Here is the HTML: $html
            """
        val requestBody = """
        {
          "model": "gpt-4.1-mini",
          "messages": [
            {"role": "user", "content": ${org.json.JSONObject.quote(prompt)}}
          ],
          "temperature": 0.2
        }
        """.trimIndent()
        val client = okhttp3.OkHttpClient.Builder()
            .callTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(okhttp3.RequestBody.create("application/json".toMediaTypeOrNull(), requestBody))
            .build()
        logger.info("[$jobId] Sending request to OpenAI API for product selectors.")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("OpenAI API error: ${response.code} ${response.message}")
        val responseBody = response.body?.string() ?: throw Exception("No response from OpenAI")
        val json = org.json.JSONObject(responseBody)
        val content = json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trimIndent()
        // Try to extract the first JSON object from the content
        val jsonStart = content.indexOf('{')
        val jsonEnd = content.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1) throw Exception("No JSON found in OpenAI response")
        val jsonString = sanitizeJsonString(content.substring(jsonStart, jsonEnd + 1))
        val selectorsJson = org.json.JSONObject(jsonString)
        logger.debug("[$jobId] Extracted selectors JSON: $selectorsJson")
        val selectors = mutableMapOf<String, String>()
        for (key in selectorsJson.keys()) {
            selectors[key] = selectorsJson.getJSONObject(key).getString("selector")
        }
        return selectors
    }

    /**
     * Robustly sanitize a JSON string to fix common issues from LLM output:
     * - Fix outer escaped quotes
     * - Remove accidental trailing/double quotes from string values
     * - Ensure all string values are single-line and properly escaped
     * - Remove multiline breaks inside string values
     * - Remove duplicate quotes at the end of values
     */
    private fun sanitizeJsonString(json: String): String {
        var sanitized = json
        // 0. Replace all double-escaped quotes (\\\") with single-escaped (\")
        sanitized = sanitized.replace("\\\\\"", "\"")
        // 1. Fix outer escaped quotes (as before)
        sanitized = Regex(":\\s*\\\"(.*?)\\\"").replace(sanitized) { matchResult ->
            val inner = matchResult.groupValues[1]
            ": \"$inner\""
        }
        // 2. Remove accidental double quotes at the end of string values (e.g. ...\")\")
        sanitized = Regex("([^\"])\"\"").replace(sanitized) { matchResult ->
            matchResult.groupValues[1] + '\"'
        }
        // 3. Remove multiline breaks inside string values
        sanitized = Regex(":\\s*\\\"([^\"]*?)\\n([^\"]*?)\\\"", RegexOption.MULTILINE).replace(sanitized) { matchResult ->
            val merged = matchResult.groupValues[1].replace("\n", " ") + " " + matchResult.groupValues[2].replace("\n", " ")
            ": \"${merged.trim()}\""
        }
        // 4. Remove any remaining newlines inside quoted values
        sanitized = Regex(":\\s*\\\"([^\\\"]*)\\\"", RegexOption.DOT_MATCHES_ALL).replace(sanitized) { matchResult ->
            val value = matchResult.groupValues[1].replace("\n", " ").replace("\r", " ")
            ": \"${value.trim()}\""
        }
        // 5. Remove duplicate quotes at the end of values (e.g. ...\"\")
        sanitized = Regex("\"\"\\s*([,}])").replace(sanitized) { matchResult ->
            "\"${matchResult.groupValues[1]}"
        }
        // 6. Validate and re-serialize JSON to ensure correct escaping
        try {
            val jsonObj = org.json.JSONObject(sanitized)
            sanitized = jsonObj.toString()
        } catch (e: Exception) {
            throw Exception("Sanitized JSON is still invalid: ${e.message}\nSanitized: $sanitized")
        }
        return sanitized
    }

    fun getLogLines(id: String): List<String> = JobLogBufferRegistry.get(id)

    fun isRunning(id: String): Boolean = jobs[id]?.isRunning?.get() ?: false

    fun getResultText(id: String): String? = jobs[id]?.resultText

    fun getResultFilePath(id: String): String? = jobs[id]?.resultPath

    fun getProductsText(id: String): String? = jobs[id]?.products

    fun getProductsFilePath(id: String): String? = jobs[id]?.productPath

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
            jobs[id]?.scraper?.saveResults("$scraperResultPath/${id}/scrape_result_${id}.txt", "$scraperResultPath/${id}/products_${id}.json")
            jobs[id]?.resultPath = "$scraperResultPath/${id}/scrape_result_${id}.txt"
        }
        logger.info("[Job $id] Job force-ended and results saved.")
    }
}
