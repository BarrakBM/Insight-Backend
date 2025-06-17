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
import org.springframework.web.bind.annotation.RestController

@RestController
class TransactionController(
    private val transactionsService: TransactionsService,
    private val userRepository: UserRepository
) {

    @GetMapping("/retrieve/user/transactions")
    fun fetchUserTransactions(): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")
        return transactionsService.fetchUserTransactions(user.id)
    }

    @GetMapping("/retrieve/account/transactions/{accountId}")
    fun fetchAccountTransactions(@PathVariable accountId: Long?): ResponseEntity<List<TransactionResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")

        return transactionsService.fetchAccountTransactions(accountId)
    }
}