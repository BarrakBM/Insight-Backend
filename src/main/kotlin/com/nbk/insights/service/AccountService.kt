package com.nbk.insights.service

import com.hazelcast.logging.Logger
import com.nbk.insights.dto.*
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.LimitsEntity
import com.nbk.insights.repository.LimitsRepository
import com.nbk.insights.repository.UserRepository
import com.nbk.insights.serverInsightsCache
import jakarta.persistence.EntityNotFoundException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

private val loggerUserAccounts = Logger.getLogger("accounts-user")

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val limitsRepository: LimitsRepository,
    private val userRepository: UserRepository
) {

    fun retrieveUserAccounts(userId: Long): AccountsResponse {

        val accountCache = serverInsightsCache.getMap<Long, AccountsResponse>("transactions-user")

        accountCache[userId]?.let {
            loggerUserAccounts.info("Returning cached user accounts for userId=$userId")
            return it
        }

        if (userId <= 0) {
            throw IllegalArgumentException("Invalid user ID: must be positive.")
        }

        val accountEntities = accountRepository.findByUserId(userId)

        if (accountEntities.isEmpty()) {
            throw EntityNotFoundException("No accounts found for user ID")
        }

        val accounts = accountEntities.map { entity ->
            val id = entity.id ?: throw IllegalStateException("Account ID cannot be null")
            val type = entity.accountType
            val number = entity.accountNumber ?: throw IllegalStateException("Account number is missing")
            val card = entity.cardNumber ?: ""

            Account(
                accountId = id,
                accountType = type,
                accountNumber = number,
                balance = entity.balance,
                cardNumber = card
            )
        }

        loggerUserAccounts.info("No account(s) found, caching new data...")
        accountCache[userId] = AccountsResponse(accounts)
        return AccountsResponse(accounts)
    }

    fun retrieveTotalBalance(userId: Long): TotalBalanceResponse {
        require(userId > 0) { "Invalid user ID: must be greater than 0." }

        val accountEntities = accountRepository.findByUserId(userId)

        if (accountEntities.isEmpty()) {
            throw NoSuchElementException("No accounts found for user ID: $userId")
        }

        val total = accountEntities
            .map { it.balance }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        return TotalBalanceResponse(total)
    }

    private fun calculateNextRenewalDate(): LocalDate {
        val now = LocalDate.now()
        return now.withDayOfMonth(1).plusMonths(1) // First day of next month
    }

    fun setOrUpdateAccountLimit(
        userId: Long,
        category: String,
        amount: BigDecimal,
        accountId: Long,
        renewsAt: LocalDate?
    ) {
        val account = accountRepository.findById(accountId).orElseThrow {
            EntityNotFoundException("Account with ID $accountId not found")
        }

        if (account.userId != userId) {
            throw IllegalAccessException("User ID mismatch")
        }

        // Check if there's an existing limit for this category and account
        val existingLimit = limitsRepository.findByAccountIdAndCategory(accountId, category)

        if (existingLimit != null) {
            // Update the existing limit by modifying its properties
            existingLimit.amount = amount
            existingLimit.renewsAt = renewsAt ?: calculateNextRenewalDate()
            existingLimit.isActive = true // Reactivate if it was deactivated
            limitsRepository.save(existingLimit)
        } else {
            // Create a new limit
            val newLimit = LimitsEntity(
                category = category,
                amount = amount,
                accountId = accountId,
                renewsAt = renewsAt ?: calculateNextRenewalDate()
            )
            limitsRepository.save(newLimit)
        }
    }

    // ADD THIS NEW METHOD FOR UPDATING SPECIFIC LIMITS
    fun updateAccountLimit(
        userId: Long,
        limitId: Long,
        category: String,
        amount: BigDecimal,
        accountId: Long,
        renewsAt: LocalDate?
    ) {
        // Verify the account belongs to the user
        val account = accountRepository.findById(accountId).orElseThrow {
            EntityNotFoundException("Account with ID $accountId not found")
        }

        if (account.userId != userId) {
            throw IllegalAccessException("User ID mismatch")
        }

        // Find the specific limit to update
        val limitEntity = limitsRepository.findById(limitId).orElseThrow {
            EntityNotFoundException("Limit with ID $limitId not found")
        }

        // Verify the limit belongs to the correct account
        if (limitEntity.accountId != accountId) {
            throw IllegalAccessException("Limit does not belong to the specified account")
        }

        // Update the limit properties directly (don't use copy())
        limitEntity.amount = amount
        limitEntity.renewsAt = renewsAt ?: calculateNextRenewalDate()
        limitEntity.isActive = true // Ensure it's active

        // Save the updated entity
        limitsRepository.save(limitEntity)
    }

    fun retrieveAccountLimits(userId: Long, accountId: Long): ListOfLimitsResponse {
        val account = accountRepository.findById(accountId).orElseThrow {
            EntityNotFoundException("Account with ID $accountId not found")
        }

        if (account.userId != userId) {
            throw IllegalAccessException("User ID mismatch")
        }

        val limitsEntities = limitsRepository.findAllByAccountId(accountId)
            ?.filter { it.isActive }
            ?: return ListOfLimitsResponse(emptyList())

        val limits = limitsEntities.map { entity ->
            Limits(
                category = entity.category,
                amount = entity.amount,
                accountId = entity.accountId,
                limitId = entity.id ?: 0L,
                renewsAt = entity.renewsAt
            )
        }

        return ListOfLimitsResponse(accountLimits = limits)
    }

    fun deactivateLimit(userId: Long, limitId: Long) {
        require(userId > 0) { "Invalid user ID: must be greater than 0." }
        require(limitId > 0) { "Invalid limit ID: must be greater than 0." }

        val accounts = accountRepository.findByUserId(userId)

        val belongsToUser = accounts.any { account ->
            limitsRepository.findAllByAccountId(account.id!!)?.any { it.id == limitId } ?: false
        }

        if (!belongsToUser) {
            throw IllegalAccessException("Limit ID does not belong to this user")
        }

        val limitEntity = limitsRepository.findById(limitId)
            .orElseThrow { IllegalArgumentException("Limit not found") }

        // Update the isActive flag directly
        limitEntity.isActive = false
        limitsRepository.save(limitEntity)
    }
}