package md.aichat.scraper.repository

import md.aichat.scraper.entity.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, String> {
    fun findByKeyAndIsActiveTrue(key: String): Optional<ApiKey>
}