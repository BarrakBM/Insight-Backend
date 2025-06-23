package com.nbk.insights.controller

import com.nbk.insights.service.RecommendationsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RecommendationController(
    private val openAIService: RecommendationsService
) {

        @PostMapping("/ask")
        fun ask(@RequestParam prompt: String): ResponseEntity<String> {
            val response = openAIService.askChatGPT(prompt)
            return ResponseEntity.ok(response)
        }
}