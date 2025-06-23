package com.nbk.insights.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class OpenAIConfig(@Value("\${openai.api.key}") private val apiKey: String) {

    @Bean
    fun openAIWebClient(builder: WebClient.Builder, @Value("\${openai.api.url}") apiUrl: String): WebClient =
        builder
            .baseUrl(apiUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
}
