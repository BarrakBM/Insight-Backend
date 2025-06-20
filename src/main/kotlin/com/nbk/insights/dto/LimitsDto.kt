package com.nbk.insights.dto

import java.math.BigDecimal
import java.time.LocalDate

data class LimitsRequest(
    val category: String,
    val amount: BigDecimal,
    val accountId: Long,
    val renewsAt: LocalDate?,
)

data class ListOfLimitsResponse(
    val accountLimits: List<Limits?>
)

data class Limits(
    val category: String,
    val amount: BigDecimal,
    val accountId: Long,
    val limitId: Long,
    val renewsAt: LocalDate,
)