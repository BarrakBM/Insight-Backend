package com.nbk.insights.service

import com.nbk.insights.dto.MCC
import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.MccRepository
import com.nbk.insights.repository.TransactionRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TransactionsService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val mccRepository: MccRepository
) {

    fun fetchUserTransactions(userId: Long?): List<TransactionResponse> {
        val accounts = accountRepository.findByUserId(userId)
        if (accounts.isEmpty()) {
            throw EntityNotFoundException("No accounts found for userId: $userId")
        }

        val accountIds = accounts.mapNotNull { it.id }

        val transactions = transactionRepository
            .findAllBySourceAccountIdInOrDestinationAccountIdIn(accountIds, accountIds)

        val mccIds = transactions.mapNotNull { it.mccId }.toSet()
        val mccMap = mccRepository.findAllById(mccIds).associateBy { it.id }

        return transactions.map { tx ->
            val mccEntity = tx.mccId?.let { mccMap[it] }
            val mccDto = MCC(
                category = mccEntity?.category ?: "Transfer",
                subCategory = mccEntity?.subCategory ?: "Account"
            )

            TransactionResponse(
                id = tx.id,
                sourceAccountId = tx.sourceAccountId,
                destinationAccountId = tx.destinationAccountId,
                amount = tx.amount,
                transactionType = tx.transactionType,
                mcc = mccDto,
                createdAt = tx.createdAt
            )
        }
    }

    fun fetchAccountTransactions(
        accountId: Long?,
        userId: Long?,
        mccId: Long?,
        period: String,
        category: String?,
        year: Int?,
        month: Int?
    ): List<TransactionResponse> {
        val userAccounts = accountRepository.findByUserId(userId)
        if (userAccounts.none { it.id == accountId }) {
            throw IllegalArgumentException("Account $accountId not found or does not belong to the user")
        }

        val now = LocalDateTime.now()
        val startDate = when {
            year != null && month != null -> LocalDateTime.of(year, month, 1, 0, 0)
            year != null && month == null -> LocalDateTime.of(year, 1, 1, 0, 0)
            period.equals("monthly", ignoreCase = true) -> now.withDayOfMonth(1).toLocalDate().atStartOfDay()
            period.equals("yearly", ignoreCase = true) -> now.withDayOfYear(1).toLocalDate().atStartOfDay()
            period.equals("none", ignoreCase = true) -> null
            else -> throw IllegalArgumentException("Invalid period: $period")
        }

        val endDate = when {
            year != null && month != null -> startDate?.plusMonths(1)?.minusSeconds(1)
            year != null && month == null -> startDate?.plusYears(1)?.minusSeconds(1)
            else -> null
        }

        val transactions = transactionRepository.findFilteredTransactionsInRange(
            accountId = accountId,
            category = category,
            mccId = mccId,
            startDate = startDate,
            endDate = endDate
        )

        val mccIds = transactions.mapNotNull { it.mccId }.toSet()
        val mccMap = mccRepository.findAllById(mccIds).associateBy { it.id }

        return transactions.map { tx ->
            val mccEntity = tx.mccId?.let { mccMap[it] }
            TransactionResponse(
                id = tx.id,
                sourceAccountId = tx.sourceAccountId,
                destinationAccountId = tx.destinationAccountId,
                amount = tx.amount,
                transactionType = tx.transactionType,
                mcc = MCC(
                    category = mccEntity?.category ?: "unknown",
                    subCategory = mccEntity?.subCategory ?: "unknown"
                ),
                createdAt = tx.createdAt
            )
        }
    }
}