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

enum class AdherenceLevel(val displayName: String) {
    EXCELLENT("Excellent"),   // 0-50% of budget used
    GOOD("Good"),             // 51-75% of budget used
    WARNING("Warning"),       // 76-90% of budget used
    CRITICAL("Critical"),     // 91-100% of budget used
    EXCEEDED("Exceeded")      // Over 100% of budget used
}

data class CategoryAdherence(
    val category: String,
    val budgetAmount: BigDecimal,
    val spentAmount: BigDecimal,
    val adherenceLevel: AdherenceLevel,
    val percentageUsed: Double,
    val remainingAmount: BigDecimal,
    val renewsAt: LocalDate,
    val accountId: Long,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val daysInPeriod: Long,
    val isActivePeriod: Boolean,
    val lastMonthSpentAmount: BigDecimal,
    val spendingTrend: SpendingTrend,
    val spendingChange: BigDecimal,
    val spendingChangePercentage: Double
)
enum class SpendingTrend(val displayName: String) {
    INCREASED("Increased"),      // Spending went up
    DECREASED("Decreased"),      // Spending went down
    STABLE("Stable"),           // Similar spending (±5%)
    NO_DATA("No Data")          // No previous period data
}

data class BudgetAdherenceResponse(
    val overallAdherence: AdherenceLevel,
    val categoryAdherences: List<CategoryAdherence>,
    val totalBudget: BigDecimal,
    val totalSpent: BigDecimal,
    val accountsChecked: Int
)

data class SpendingTrendResponse(
    val category: String,
    val currentSpent: BigDecimal,
    val lastMonthSpent: BigDecimal,
    val spendingChange: BigDecimal,
    val spendingChangePercentage: Double,
    val trend: String,
    val budgetAmount: BigDecimal,
    val adherenceLevel: String
)