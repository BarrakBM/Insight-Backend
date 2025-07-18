package com.nbk.insights.controller

import com.hazelcast.logging.Logger
import com.nbk.insights.dto.*
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.service.AccountService
import com.nbk.insights.service.LimitsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.web.bind.annotation.*

@RequestMapping("/accounts")
@RestController
class AccountController(
    private val accountService: AccountService,
    private val userRepository: UserRepository,
    private val limitAdherenceService: LimitsService
) {

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
    fun setAccountLimit(@RequestBody request: LimitsRequest): ResponseEntity<Map<String, String>?> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        accountService.setOrUpdateAccountLimit(
            userId = userId,
            category = request.category,
            amount = request.amount,
            accountId = request.accountId,
            renewsAt = request.renewsAt
        )
        return ResponseEntity.ok(mapOf("message" to "limit set successfully"))
    }

    // ADD THIS NEW ENDPOINT FOR UPDATING LIMITS
    @PutMapping("/limit/{limitId}")
    fun updateAccountLimit(
        @PathVariable limitId: Long,
        @RequestBody request: LimitsRequest
    ): ResponseEntity<Map<String, String>?> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")

        accountService.updateAccountLimit(
            userId = userId,
            limitId = limitId,
            category = request.category,
            amount = request.amount,
            accountId = request.accountId,
            renewsAt = request.renewsAt
        )
        return ResponseEntity.ok(mapOf("message" to "limit updated successfully"))
    }

    @GetMapping("/limits/{accountId}")
    fun getAccountLimits(@PathVariable accountId: Long): ResponseEntity<ListOfLimitsResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        val limits = accountService.retrieveAccountLimits(userId = userId, accountId = accountId)
        return ResponseEntity.ok(limits)
    }

    @PostMapping("/limits/deactivate/{limitId}")
    fun deactivateLimit(@PathVariable limitId: Long): ResponseEntity<Map<String, String>?> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        accountService.deactivateLimit(userId = userId, limitId = limitId)
        return ResponseEntity.ok(mapOf("message" to "Limit deactivated successfully"))
    }

    @GetMapping("/adherence")
    fun getBudgetAdherence(): ResponseEntity<BudgetAdherenceResponse> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        val adherence = limitAdherenceService.checkBudgetAdherence(userId)
        return ResponseEntity.ok(adherence)
    }

    @GetMapping("/adherence/trends")
    fun getSpendingTrends(): ResponseEntity<List<SpendingTrendResponse>> {
        val username = SecurityContextHolder.getContext().authentication.name
        val userId = userRepository.findByUsername(username)?.id
            ?: throw UsernameNotFoundException("User not found with username: $username")
        val adherence = limitAdherenceService.checkBudgetAdherence(userId)

        val trends = adherence.categoryAdherences.map { category ->
            SpendingTrendResponse(
                category = category.category,
                currentSpent = category.spentAmount,
                lastMonthSpent = category.lastMonthSpentAmount,
                spendingChange = category.spendingChange,
                spendingChangePercentage = category.spendingChangePercentage,
                trend = category.spendingTrend.displayName,
                budgetAmount = category.budgetAmount,
                adherenceLevel = category.adherenceLevel.displayName
            )
        }
        return ResponseEntity.ok(trends)
    }
}