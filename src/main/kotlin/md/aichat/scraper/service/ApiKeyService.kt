package md.aichat.scraper.service

import md.aichat.scraper.entity.ApiKey
import md.aichat.scraper.repository.ApiKeyRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Service for API key validation and retrieval.
 *
 * This service is responsible for checking if an API key is valid (active and not expired)
 * and for retrieving the corresponding ApiKey entity from the repository.
 */
@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository
) {
    /**
     * Validates the provided API key.
     *
     * @param key The API key string to validate.
     * @return true if the key is active and not expired, false otherwise.
     */
    fun validateApiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        // Find the API key in the repository and check if it is active and not expired
        val apiKey = apiKeyRepository.findByKeyAndIsActiveTrue(key).orElse(null)
        return apiKey != null && (apiKey.expiryDate == null || apiKey.expiryDate.isAfter(LocalDateTime.now()))
    }

    /**
     * Retrieves the ApiKey entity for the given key if it is active.
     *
     * @param key The API key string.
     * @return The ApiKey entity if found and active, null otherwise.
     */
    fun getApiKey(key: String): ApiKey? =
        apiKeyRepository.findByKeyAndIsActiveTrue(key).orElse(null)
}