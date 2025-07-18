package md.aichat.scraper.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "api_keys")
data class ApiKey(
    @Id
    @Column(name = "key", nullable = false, unique = true)
    val key: String,

    @Column(name = "key_holder", nullable = false)
    val keyHolder: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "creation_date", nullable = false)
    val creationDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "expiry_date")
    val expiryDate: LocalDateTime? = null
)