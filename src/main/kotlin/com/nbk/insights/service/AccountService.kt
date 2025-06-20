package com.nbk.insights.service

import com.nbk.insights.dto.*
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.LimitsEntity
import com.nbk.insights.repository.LimitsRepository
import com.nbk.insights.repository.UserRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal

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
            throw EntityNotFoundException("No accounts found for user ID: $userId")
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

    fun setOrUpdateAccountLimit(userId: Long, category: String, amount: BigDecimal, accountId: Long) {
        val account = accountRepository.findById(accountId).orElseThrow {
            EntityNotFoundException("Account with ID $accountId not found")
        }

        if (account.userId != userId) {
            throw IllegalAccessException("User ID mismatch")
        }

        val existingLimit = limitsRepository.findByAccountId(accountId)

        val newLimit = existingLimit?.copy(category = category, amount = amount)
            ?: LimitsEntity(
                category = category,
                amount = amount,
                accountId = accountId
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

        val limitsEntities =
            limitsRepository.findAllByAccountId(accountId)?.filter { it.isActive } ?: return ListOfLimitsResponse(
                emptyList()
            )

        val limits = limitsEntities.map { entity ->
            entity.let {
                val category = it.category
                val amount = it.amount
                val entityAccountId = it.accountId
                val limitId = it.id

                Limits(
                    category = category,
                    amount = amount,
                    accountId = entityAccountId,
                    limitId = limitId ?: 0L
                )
            }
        }

        return ListOfLimitsResponse(accountLimits = limits)
    }

    fun deactivateLimit(userId: Long, limitId: Long) {

        require(userId > 0) { "Invalid user ID: must be greater than 0." }
        require(limitId > 0) { "Invalid limit ID: must be greater than 0." }

        val user =
            if (userRepository.existsById(userId))
                userRepository.findById(userId).get()
            else
                throw EntityNotFoundException("User with ID $userId not found")

        if (userId != user.id)
            throw IllegalAccessException("User ID mismatch")

        val limitEntity = limitsRepository.findById(limitId).get()
        limitEntity.isActive = false
        limitsRepository.save(limitEntity)
    }

}