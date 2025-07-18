package md.aichat.scraper.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.slf4j.LoggerFactory

@Configuration
@EnableWebMvc
open class WebConfig : WebMvcConfigurer {
    private val logger = LoggerFactory.getLogger(WebConfig::class.java)

    @Value("\${app.server.url:http://localhost:8080}")
    lateinit var serverUrl: String

    @Value("\${app.server.backend.url:http://localhost:7070}")
    lateinit var backendServerUrl: String
    
    override fun addCorsMappings(registry: CorsRegistry) {
        logger.info("Configuring CORS with allowed origins: {}, {}", serverUrl, backendServerUrl)
        registry.addMapping("/**")
            .allowedOrigins(serverUrl, backendServerUrl)
            .allowedMethods("GET", "POST", "PATCH")
            .allowedHeaders("X-API-KEY", "Content-Type", "Accept", "Authorization", "Origin", "X-Requested-With")
            .allowCredentials(true)
    }

    @Bean
    open fun apiKeyAuthFilterRegistration(apiKeyAuthFilter: ApiKeyAuthFilter): FilterRegistrationBean<ApiKeyAuthFilter> {
        logger.info("Registering ApiKeyAuthFilter with order 1 for all URL patterns.")
        val registration = FilterRegistrationBean<ApiKeyAuthFilter>()
        registration.filter = apiKeyAuthFilter
        registration.addUrlPatterns("/*")
        registration.order = 1 // Ensure it runs early
        return registration
    }
}