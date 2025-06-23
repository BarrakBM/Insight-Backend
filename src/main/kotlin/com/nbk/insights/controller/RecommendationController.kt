package com.nbk.insights.controller

import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.RecommendationsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RecommendationController(
    private val openAIService: RecommendationsService,
    private val userRepository: UserRepository
) {

        @PostMapping("/ask")
        fun ask(): ResponseEntity<String> {
            val username = SecurityContextHolder.getContext().authentication.name
            val userId = userRepository.findByUsername(username)?.id
                ?: throw UsernameNotFoundException("User not found with username: $username")
            val response = openAIService.getCategoryRecommendations(userId)
            return ResponseEntity.ok(response)
        }
}