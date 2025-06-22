package com.nbk.insights.controller

import com.nbk.insights.dto.RecurringPaymentResponse
import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.RecurringPaymentService
import com.nbk.insights.service.TransactionsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/retrieve")
@RestController
class TransactionController(
    private val transactionsService: TransactionsService,
    private val userRepository: UserRepository,
    private val recurringPaymentService: RecurringPaymentService
) {

    @GetMapping("/user/transactions")
    fun fetchUserTransactions(): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")
        val result = transactionsService.fetchUserTransactions(user.id)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/account/transactions/{accountId}")
    fun fetchAccountTransactions(
        @PathVariable accountId: Long?,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) mccId: Long?,
        @RequestParam(required = false, defaultValue = "none") period: String,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?
    ): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")
        val result = transactionsService.fetchAccountTransactions(accountId, user.id, mccId, period, category, year, month)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/account/recurring-payments/{accountId}")
    fun detectRecurringPaymentsForAccount(
        @PathVariable accountId: Long
    ): ResponseEntity<List<RecurringPaymentResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")

        val payments = recurringPaymentService.detectRecurringPaymentsDynamic(accountId, user.id)
        return ResponseEntity.ok(payments)
    }
}