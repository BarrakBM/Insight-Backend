package com.nbk.insights.controller

import com.nbk.insights.dto.CategoryRecommendationResponse
import com.nbk.insights.dto.OfferResponse
import com.nbk.insights.dto.OffersRecommendationResponse
import com.nbk.insights.dto.QuickInsightsResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.OffersService
import com.nbk.insights.service.RecommendationsService
import org.springframework.data.repository.query.Param
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
    private val userRepository: UserRepository,
    private val offersService: OffersService
) {

    @PostMapping("/get/recommendations")
    fun getRecommendations(): ResponseEntity<List<CategoryRecommendationResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")

        val response = openAIService.getCategoryRecommendations(userId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/get/offers/recommendation")
    fun getOffersRecommendation(): ResponseEntity<OffersRecommendationResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")

        val response = openAIService.getOffersRecommendation(userId)
        return ResponseEntity.ok(response)
    }


    @GetMapping("/offer/category")
    fun getOffers(@RequestParam("category") category: String): ResponseEntity<List<OfferResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        val offers = offersService.getOffersByCategory(category)
        return ResponseEntity.ok(offers)
    }

    @GetMapping("/quick-insights")
    fun getQuickInsights(): ResponseEntity<QuickInsightsResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found")

        val insights = openAIService.getQuickInsights(userId)
        return ResponseEntity.ok(insights)
    }

}