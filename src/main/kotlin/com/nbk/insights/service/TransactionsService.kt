package com.nbk.insights.service

import com.nbk.insights.dto.TransactionResponse
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.TransactionEntity
import com.nbk.insights.repository.TransactionRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class TransactionsService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository
){
    fun fetchUserTransactions(userId: Long?): ResponseEntity<List<TransactionResponse>> {

        val accounts = accountRepository.findByUserId(userId)
        if (accounts.isEmpty()) {
            return ResponseEntity.ok(emptyList())
        }

        val accountIds = accounts.mapNotNull { it.id }

        val transactions = transactionRepository
            .findAllBySourceAccountIdInOrDestinationAccountIdIn(accountIds, accountIds)

        val response = transactions.map { transaction ->
            TransactionResponse(
                id                   = transaction.id,
                sourceAccountId      = transaction.sourceAccountId,
                destinationAccountId = transaction.destinationAccountId,
                amount               = transaction.amount,
                transactionType      = transaction.transactionType,
                mccId                = transaction.mccId,
                createdAt            = transaction.createdAt
            )
        }
        return ResponseEntity.ok(response)
    }

    fun fetchAccountTransactions(accountId: Long?): ResponseEntity<List<TransactionResponse>> {

        val transactions = transactionRepository.findAllBySourceAccountIdOrDestinationAccountId(accountId, accountId)

        val response = transactions.map { transaction ->
            TransactionResponse(
            id                   = transaction.id,
            sourceAccountId      = transaction.sourceAccountId,
            destinationAccountId = transaction.destinationAccountId,
            amount               = transaction.amount,
            transactionType      = transaction.transactionType,
            mccId                = transaction.mccId,
            createdAt            = transaction.createdAt
            )
        }
        return ResponseEntity.ok(response)
    }
}