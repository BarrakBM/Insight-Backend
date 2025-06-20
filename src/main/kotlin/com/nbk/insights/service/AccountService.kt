package com.nbk.insights.service

import com.nbk.insights.dto.Account
import com.nbk.insights.dto.AccountsResponse
import com.nbk.insights.dto.Limits
import com.nbk.insights.dto.ListOfLimitsResponse
import com.nbk.insights.dto.TotalBalanceResponse
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.LimitsEntity
import com.nbk.insights.repository.LimitsRepository
import jakarta.persistence.EntityNotFoundException
import org.hibernate.query.spi.Limit
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountService(
    val accountRepository: AccountRepository,
    val limitsRepository: LimitsRepository
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

                Limits(
                    category = category,
                    amount = amount,
                    accountId = entityAccountId
                )
            }
        }

        return ListOfLimitsResponse(accountLimits = limits)
    }


}