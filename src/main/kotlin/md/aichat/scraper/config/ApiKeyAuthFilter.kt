package md.aichat.scraper.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.slf4j.LoggerFactory

@Component
class ApiKeyAuthFilter(
    @Value("\${api.key}")
    private val apiKey: String,
    @Value("\${swagger.username}")
    private val swaggerUsername: String,
    @Value("\${swagger.password}")
    private val swaggerPassword: String
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val logger = LoggerFactory.getLogger(ApiKeyAuthFilter::class.java)
        val path = request.requestURI
        logger.debug("Incoming request URI: {}", path)
        val isSwagger = request.getHeader("Referer")?.contains("swagger-ui") == true ||
                path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
        if (isSwagger) {
            val authHeader = request.getHeader("Authorization")
            logger.debug("Swagger UI request. Authorization header: {}", authHeader)
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                logger.warn("Missing or invalid Swagger Basic Auth header.")
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.setHeader("WWW-Authenticate", "Basic realm=Swagger")
                response.writer.write("Unauthorized: Swagger Basic Auth required")
                return
            }
            val base64Credentials = authHeader.substringAfter("Basic ").trim()
            val credentials = String(java.util.Base64.getDecoder().decode(base64Credentials))
            val (username, password) = credentials.split(":", limit = 2)
            if (username != swaggerUsername || password != swaggerPassword) {
                logger.warn("Invalid Swagger credentials: username={}, password=****", username)
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.setHeader("WWW-Authenticate", "Basic realm=Swagger")
                response.writer.write("Unauthorized: Invalid Swagger credentials")
                return
            }
            logger.info("Swagger authentication successful for user: {}", username)
            filterChain.doFilter(request, response)
            return
        }
        val requestApiKey = request.getHeader("X-API-KEY")
        logger.debug("API request X-API-KEY header: {}", requestApiKey)
        if (requestApiKey == null || requestApiKey != apiKey) {
            logger.warn("Unauthorized API request. Invalid or missing API Key: {}", requestApiKey)
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.writer.write("Unauthorized: Invalid or missing API Key")
            return
        }
        logger.info("API key authentication successful.")
        filterChain.doFilter(request, response)
    }
}
