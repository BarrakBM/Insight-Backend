package com.nbk.insights.service

import com.nbk.insights.dto.*
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.LimitsEntity
import com.nbk.insights.repository.LimitsRepository
import com.nbk.insights.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val limitsRepository: LimitsRepository,
    private val userRepository: UserRepository
) {

    fun retrieveUserAccounts(userId: Long): AccountsResponse {
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

        val existingLimit = limitsRepository.findByAccountIdAndCategory(accountId, category)

        val newLimit = existingLimit?.copy(
            amount = amount,
            renewsAt = renewsAt ?: calculateNextRenewalDate()
        ) ?: LimitsEntity(
            category = category,
            amount = amount,
            accountId = accountId,
            renewsAt = renewsAt ?: calculateNextRenewalDate()
        )

        limitsRepository.save(newLimit)
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

        for (account in accounts) {
            val limit = limitsRepository.findByAccountId(account.id!!)

            if (limit != null && limit.id != limitId) {
                throw IllegalAccessException("Limit ID mismatch")
            }
        }

        val limitEntity = limitsRepository.findById(limitId).get()
        limitEntity.isActive = false
        limitsRepository.save(limitEntity)
    }

}