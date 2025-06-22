package com.nbk.insights.helper

fun buildCacheKey(
    accountId: Long?,
    category: String?,
    mccId: Long?,
    period: String,
    year: Int?,
    month: Int?
): String {
    return listOfNotNull(
        "account: $accountId",
        "category: ${category ?: "any"}",
        "mcc: ${mccId ?: "any"}",
        "period: $period",
        "year: ${year ?: "any"}",
        "month: ${month ?: "any"}"
    ).joinToString(" | ")
}