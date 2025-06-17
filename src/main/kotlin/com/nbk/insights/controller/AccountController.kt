package com.nbk.insights.controller

import com.nbk.insights.dto.AccountsResponse
import com.nbk.insights.dto.TotalBalanceResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.AccountService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AccountController(private val accountService: AccountService, private val userRepository: UserRepository) {

    @GetMapping("/accounts")
    fun retrieveUserAccounts(): ResponseEntity<AccountsResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with email: $username")
        return ResponseEntity.ok(accountService.retrieveUserAccounts(userId = userId))
    }

    @GetMapping("/accounts/total-balance")
    fun retrieveTotalBalance(): ResponseEntity<TotalBalanceResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
        ?: throw UsernameNotFoundException("User not found with username: $username")
        return ResponseEntity.ok(accountService.retrieveTotalBalance(userId = userId))
    }

}