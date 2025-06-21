package com.nbk.insights.controller

import com.nbk.insights.dto.RecurringPaymentResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.RecurringPaymentService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/insights")

class InsightsController(
    private val recurringPaymentService: RecurringPaymentService,
    private val userRepository: UserRepository

) {
    @GetMapping("/account/recurring-payments/{accountId}")
    fun detectRecurringPaymentsForAccount(
        @PathVariable accountId: Long
    ): ResponseEntity<List<RecurringPaymentResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found")

        val result = recurringPaymentService.detectRecurringPaymentsDynamic(accountId, user.id)
        return ResponseEntity.ok(result)

    }
}