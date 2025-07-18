package md.aichat.scraper.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory

@Configuration
open class OpenApiConfig {
    private val logger = LoggerFactory.getLogger(OpenApiConfig::class.java)

    @Value("\${app.server.url:http://localhost:8080}")
    lateinit var serverUrl: String

    @Value("\${app.server.backend.url:localhost:7070}")
    lateinit var backendServerUrl: String
    
    @Bean
    open fun customOpenAPI(): OpenAPI {
        logger.info("Configuring OpenAPI with serverUrl={} and backendServerUrl={}", serverUrl, backendServerUrl)
        return OpenAPI()
            .servers(listOf(
                Server().url(backendServerUrl).description("Backend server"),
                Server().url(serverUrl).description("Application server")
            ))
    }
}