package com.nbk.insights.controller

import com.nbk.insights.dto.CashFlowCategorizedResponse
import com.nbk.insights.dto.RecurringPaymentResponse
import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.RecurringPaymentService
import com.nbk.insights.service.TransactionsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.*

@RequestMapping("/retrieve")
@RestController
class TransactionController(
    private val transactionsService: TransactionsService,
    private val userRepository: UserRepository,
    private val recurringPaymentService: RecurringPaymentService
) {

    @GetMapping("/user/transactions")
    fun fetchUserTransactions(
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) mccId: Long? = null,
        @RequestParam(required = false, defaultValue = "none") period: String,
        @RequestParam(required = false) year: Int? = null,
        @RequestParam(required = false) month: Int? = null
    ): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found")
        val result = transactionsService.fetchUserTransactions(user.id, mccId, period, category, year, month)
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
            ?: throw UsernameNotFoundException("User not found")
        val result =
            transactionsService.fetchAccountTransactions(accountId, user.id, mccId, period, category, year, month)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/account/recurring-payments/{accountId}")
    fun detectRecurringPaymentsForAccount(
        @PathVariable accountId: Long
    ): ResponseEntity<List<RecurringPaymentResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found")

        val payments = recurringPaymentService.detectRecurringPaymentsDynamic(accountId, user.id)
        return ResponseEntity.ok(payments)
    }

    @GetMapping("/cash-flow/last/month")
    fun retrieveLastMonth(): ResponseEntity<CashFlowCategorizedResponse> {

        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found")

        val flow = transactionsService.getUserPreviousMonthCashFlow(userId = userId)
        return ResponseEntity.ok(flow)

    }

    @GetMapping("/cash-flow/this/month")
    fun retrieveThisMonth(): ResponseEntity<CashFlowCategorizedResponse> {

        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found")

        val flow = transactionsService.getUserCurrentMonthCashFlow(userId = userId)
        return ResponseEntity.ok(flow)

    }

    @GetMapping("/cash-flow/this/month/{accountId}")
    fun retrieveAccountThisMonth(@PathVariable accountId: Long): ResponseEntity<CashFlowCategorizedResponse> {

        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found")

        val flow = transactionsService.getAccountCurrentMonthCashFlow(userId = userId, accountId = accountId)
        return ResponseEntity.ok(flow)

    }
}