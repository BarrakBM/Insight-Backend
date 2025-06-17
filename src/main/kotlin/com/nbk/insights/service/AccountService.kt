package com.nbk.insights.service

import com.nbk.insights.dto.Account
import com.nbk.insights.dto.AccountsResponse
import com.nbk.insights.dto.TotalBalanceResponse
import com.nbk.insights.repository.AccountRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class AccountService(
    val accountRepository: AccountRepository
){

    fun retrieveUserAccounts(userId: Long): AccountsResponse {
        val accountEntities = accountRepository.findAllByUserId(userId)
        val accounts = accountEntities.map { entity ->
            Account(
                accountId = entity.id ?: 0L,
                accountType = entity.accountType,
                accountNumber = entity.accountNumber ?: "",
                balance = entity.balance,
                cardNumber = entity.cardNumber ?: ""
            )
        }

        return AccountsResponse(accounts)
    }

    fun retrieveTotalBalance(userId: Long): TotalBalanceResponse {
        val accountEntities = accountRepository.findAllByUserId(userId)
        val total = accountEntities
            .map { it.balance }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        return TotalBalanceResponse(total)
    }
}