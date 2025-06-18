package com.nbk.insights.dto

import java.math.BigDecimal

data class LimitsRequest(
    val category: String,
    val amount: BigDecimal,
    val accountId: Long
)

data class ListOfLimitsResponse(
    val accountLimits: List<Limits?>
)

data class Limits(
    val category: String,
    val amount: BigDecimal,
    val accountId: Long
)