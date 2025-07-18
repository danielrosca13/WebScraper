package md.aichat.scraper.service

import md.aichat.scraper.entity.ApiKey
import md.aichat.scraper.repository.ApiKeyRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository
) {
    fun validateApiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val apiKey = apiKeyRepository.findByKeyAndIsActiveTrue(key).orElse(null)
        return apiKey != null && (apiKey.expiryDate == null || apiKey.expiryDate.isAfter(LocalDateTime.now()))
    }

    fun getApiKey(key: String): ApiKey? =
        apiKeyRepository.findByKeyAndIsActiveTrue(key).orElse(null)
}