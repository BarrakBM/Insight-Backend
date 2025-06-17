package com.nbk.insights.dto

import com.nbk.insights.repository.AccountType
import java.math.BigDecimal


data class AccountsResponse(
    val Accounts: List<Account?>
)

data class Account(
    val accountId: Long,
    val accountType: AccountType,
    val accountNumber: String,
    var balance: BigDecimal,
    val cardNumber: String
    )

data class TotalBalanceResponse(
    val totalBalance: BigDecimal,
)