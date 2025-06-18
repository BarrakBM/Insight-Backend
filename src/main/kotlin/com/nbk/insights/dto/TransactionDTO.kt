package com.nbk.insights.dto

import com.nbk.insights.repository.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransactionResponse(
    val id: Long? = null,
    val sourceAccountId: Long?,
    val destinationAccountId: Long?,
    val amount: BigDecimal,
    val transactionType: TransactionType?,
    val mcc: MCC,
    val createdAt: LocalDateTime
)

data class MCC(
    val category: String,
    val subCategory: String?
)