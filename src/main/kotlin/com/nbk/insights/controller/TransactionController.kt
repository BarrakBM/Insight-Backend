package com.nbk.insights.controller

import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.TransactionsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.*
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/retrieve")
@RestController
class TransactionController(
    private val transactionsService: TransactionsService,
    private val userRepository: UserRepository
) {

    @GetMapping("/user/transactions")
    fun fetchUserTransactions(): ResponseEntity<Any> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")
        return transactionsService.fetchUserTransactions(user.id)
    }

    @GetMapping("/account/transactions/{accountId}")
    fun fetchAccountTransactions(
        @PathVariable accountId: Long?,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) mccId: Long?,
        @RequestParam(required = false, defaultValue = "none") period: String,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?
    ): ResponseEntity<Any> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")

        return transactionsService.fetchAccountTransactions(accountId, user.id, mccId, period, category, year, month)
    }

}