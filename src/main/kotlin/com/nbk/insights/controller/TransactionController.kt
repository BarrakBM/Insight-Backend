package com.nbk.insights.controller

import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.TransactionsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.*

@RequestMapping("/retrieve")
@RestController
class TransactionController(
    private val transactionsService: TransactionsService,
    private val userRepository: UserRepository
) {

    @GetMapping("/user/transactions")
    fun fetchUserTransactions(): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found.")

        val transactions = transactionsService.fetchUserTransactions(user.id)
        return ResponseEntity.ok(transactions)
    }

    @GetMapping("/account/transactions/{accountId}")
    fun fetchAccountTransactions(
        @PathVariable accountId: Long?,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) mccId: Long? = null,
        @RequestParam(required = false, defaultValue = "none") period: String,
        @RequestParam(required = false) year: Int? = null,
        @RequestParam(required = false) month: Int? = null
    ): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found.")

        val transactions = transactionsService.fetchAccountTransactions(accountId, user.id, mccId, period, category, year, month)
        return ResponseEntity.ok(transactions)
    }
}
