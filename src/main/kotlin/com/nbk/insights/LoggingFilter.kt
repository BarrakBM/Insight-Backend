package com.nbk.insights

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import org.slf4j.LoggerFactory

@Component
class LoggingFilter : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(LoggingFilter::class.java)
    private val objectMapper = ObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    private val RESET = "\u001B[0m"
    private val GREEN = "\u001B[32m"
    private val YELLOW = "\u001B[33m"
    private val RED = "\u001B[31m"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val cachedRequest = ContentCachingRequestWrapper(request)
        val cachedResponse = ContentCachingResponseWrapper(response)

        filterChain.doFilter(cachedRequest, cachedResponse)

        logRequest(cachedRequest)
        logResponse(cachedResponse)

        cachedResponse.copyBodyToResponse()
    }

    private fun logRequest(request: ContentCachingRequestWrapper) {
        val requestBody = request.contentAsByteArray.toString(Charsets.UTF_8).trim()

        logger.info(
            """
            |[*] Incoming Request
            |Method: ${request.method}
            |URI: ${request.requestURI}
            |Body: ${formatJsonIfPossible(requestBody)}
            |------------------------------------------------------------------------------------------------
            """.trimMargin()
        )
    }

    private fun logResponse(response: ContentCachingResponseWrapper) {
        val responseBody = response.contentAsByteArray.toString(Charsets.UTF_8).trim()
        val color = getColorForStatus(response.status)

        logger.info(
            """
            |[*] Outgoing Response
            |Status: $color${response.status}$RESET
            |Body: ${formatJsonIfPossible(responseBody)}
            |===============================================================================================
            """.trimMargin()
        )
    }

    private fun formatJsonIfPossible(content: String): String {
        return try {
            if (content.isBlank()) {
                "(empty body)"
            } else {
                val jsonNode = objectMapper.readTree(content)
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode)
            }
        } catch (ex: Exception) {
            content
        }
    }

    private fun getColorForStatus(status: Int): String {
        return when {
            status in 200..299 -> GREEN
            status in 400..499 -> YELLOW
            status >= 500 -> RED
            else -> RESET
        }
    }
}