package com.nbk.insights.service

import com.nbk.insights.config.OpenAIConfig
import com.nbk.insights.dto.ChatRequest
import com.nbk.insights.dto.ChatResponse
import com.nbk.insights.dto.Message
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class RecommendationsService(
    private val openAIWebClient: WebClient
) {

    fun askChatGPT(prompt: String): String {
        val request = ChatRequest(
            messages = listOf(
                Message("system", "You are a helpful assistant."),
                Message("user", prompt)
            )
        )

        val response = openAIWebClient.post()
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatResponse::class.java)
            .block() ?: throw RuntimeException("No response from OpenAI")

        return response.choices.firstOrNull()?.message?.content ?: "No response from ChatGPT"
    }
}
