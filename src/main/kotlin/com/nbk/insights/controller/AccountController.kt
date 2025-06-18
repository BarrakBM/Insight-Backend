package com.nbk.insights.controller

import com.nbk.insights.dto.AccountsResponse
import com.nbk.insights.dto.LimitsRequest
import com.nbk.insights.dto.ListOfLimitsResponse
import com.nbk.insights.dto.TotalBalanceResponse
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.AccountService
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.*

@RequestMapping("/accounts")
@RestController
class AccountController(private val accountService: AccountService, private val userRepository: UserRepository) {

    @GetMapping()
    fun retrieveUserAccounts(): ResponseEntity<AccountsResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with email: $username")
        return ResponseEntity.ok(accountService.retrieveUserAccounts(userId = userId))
    }

    @GetMapping("/total-balance")
    fun retrieveTotalBalance(): ResponseEntity<TotalBalanceResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        return ResponseEntity.ok(accountService.retrieveTotalBalance(userId = userId))
    }

    @PostMapping("/limit")
    fun setAccountLimit(@RequestBody request: LimitsRequest): ResponseEntity<String> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")

        return try {
            accountService.setOrUpdateAccountLimit(
                userId = userId,
                category = request.category,
                amount = request.amount,
                accountId = request.accountId
            )
            ResponseEntity.ok("Limit set/updated successfully")
        } catch (e: IllegalAccessException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.message)
        } catch (e: EntityNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.message)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred")
        }
    }

    @GetMapping("/limits/{accountId}")
    fun getAccountLimits(@PathVariable accountId: Long): ResponseEntity<Any> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")

        return try {
            val limits = accountService.retrieveAccountLimits(userId = userId, accountId = accountId)
           return ResponseEntity.ok(limits)
        } catch (e: IllegalAccessException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to e.message))
        } catch (e: EntityNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "An unexpected error occurred"))
        }
    }
}