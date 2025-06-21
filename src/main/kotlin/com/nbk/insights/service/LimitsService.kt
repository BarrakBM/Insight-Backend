package com.nbk.insights.service

import com.nbk.insights.dto.AdherenceLevel
import com.nbk.insights.dto.BudgetAdherenceResponse
import com.nbk.insights.dto.CategoryAdherence
import com.nbk.insights.dto.SpendingTrend
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.LimitsEntity
import com.nbk.insights.repository.LimitsRepository
import com.nbk.insights.repository.MccRepository
import com.nbk.insights.repository.TransactionRepository
import com.nbk.insights.repository.TransactionType
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month

@Service
class LimitsService(
    private val accountRepository: AccountRepository,
    private val limitsRepository: LimitsRepository,
    private val transactionRepository: TransactionRepository,
    private val mccRepository: MccRepository
) {

    fun checkBudgetAdherence(userId: Long): BudgetAdherenceResponse {
        val userAccounts = accountRepository.findByUserId(userId)

        if (userAccounts.get(
                index = 0
            ).userId != userId) {
            throw IllegalStateException("User not found")
        }

        if (userAccounts.isEmpty()) {
            return BudgetAdherenceResponse(
                overallAdherence = AdherenceLevel.EXCELLENT,
                categoryAdherences = emptyList(),
                totalBudget = BigDecimal.ZERO,
                totalSpent = BigDecimal.ZERO,
                accountsChecked = 0
            )
        }

        val categoryAdherences = mutableListOf<CategoryAdherence>()

        userAccounts.forEach { account ->
            val accountLimits = limitsRepository.findAllByAccountId(account.id!!)
                ?.filter { it.isActive && isCurrentPeriod(it.renewsAt) }
                ?: emptyList()

            accountLimits.forEach { limit ->
                val spentAmount = calculateSpentAmount(account.id!!, limit.category, limit.renewsAt)
                val lastMonthSpentAmount = calculateLastMonthSpentAmount(account.id!!, limit.category, limit.renewsAt)
                val adherence = createCategoryAdherence(limit, spentAmount, lastMonthSpentAmount)
                categoryAdherences.add(adherence)
            }
        }

        val totalBudget = categoryAdherences.sumOf { it.budgetAmount }
        val totalSpent = categoryAdherences.sumOf { it.spentAmount }
        val overallAdherence = calculateOverallAdherence(categoryAdherences)

        return BudgetAdherenceResponse(
            overallAdherence = overallAdherence,
            categoryAdherences = categoryAdherences,
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            accountsChecked = userAccounts.size
        )
    }

    private fun calculateSpentAmount(accountId: Long, category: String, renewsAt: LocalDate): BigDecimal {
        val (periodStart, periodEnd) = calculatePeriodRange(renewsAt)

        val transactions = transactionRepository.findFilteredTransactionsInRange(
            accountId = accountId,
            category = category,
            mccId = null,
            startDate = periodStart,
            endDate = periodEnd
        )

        // Get MCC data to filter by category
        val mccIds = transactions.mapNotNull { it.mccId }.toSet()
        val mccMap = mccRepository.findAllById(mccIds).associateBy { it.id }

        return transactions
            .filter { tx ->
                val mcc = tx.mccId?.let { mccMap[it] }
                mcc?.category == category
            }
            .filter { it.transactionType == TransactionType.DEBIT } // Only count outgoing transactions
            .sumOf { it.amount.abs() }
    }

    private fun calculatePeriodRange(renewsAt: LocalDate): Pair<LocalDateTime, LocalDateTime> {
        val today = LocalDate.now()

        return when {
            // Case 1: renewsAt is in the future (newly set limit, period hasn't started yet)
            renewsAt.isAfter(today) -> {
                val periodStart = renewsAt.atStartOfDay()
                val periodEnd = renewsAt.plusDays(getDaysInMonth(renewsAt)).atTime(23, 59, 59)
                Pair(periodStart, periodEnd)
            }

            // Case 2: renewsAt is today or in the past
            else -> {
                // Find the current active period
                var currentPeriodStart = renewsAt

                // If renewsAt is more than a month ago, find the most recent period start
                while (currentPeriodStart.plusDays(getDaysInMonth(currentPeriodStart).toLong()).isBefore(today)) {
                    currentPeriodStart = currentPeriodStart.plusMonths(1)
                }

                val periodStart = currentPeriodStart.atStartOfDay()
                val periodEnd = when {
                    // If we're still within the current period, use today as end
                    today.isBefore(currentPeriodStart.plusDays(getDaysInMonth(currentPeriodStart).toLong())) -> {
                        today.atTime(23, 59, 59)
                    }
                    // Otherwise use the full period end
                    else -> {
                        currentPeriodStart.plusDays(getDaysInMonth(currentPeriodStart).toLong()).atTime(23, 59, 59)
                    }
                }

                Pair(periodStart, periodEnd)
            }
        }
    }

    private fun getDaysInMonth(date: LocalDate): Long {
        // Get the number of days for the budget period (28-30 days based on the month)
        return when (date.month) {
            Month.FEBRUARY -> if (date.isLeapYear) 29L else 28
            Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
            else -> 31
        }.coerceAtMost(30) // Cap at 30 days as per requirement
    }

    private fun isCurrentPeriod(renewsAt: LocalDate): Boolean {
        val today = LocalDate.now()
        val (periodStart, periodEnd) = calculatePeriodRange(renewsAt)

        return today.atStartOfDay().let { todayStart ->
            (todayStart.isEqual(periodStart) || todayStart.isAfter(periodStart)) &&
                    (todayStart.isBefore(periodEnd) || todayStart.isEqual(periodEnd))
        }
    }

    private fun calculateLastMonthSpentAmount(accountId: Long, category: String, renewsAt: LocalDate): BigDecimal {
        val (currentPeriodStart, currentPeriodEnd) = calculatePeriodRange(renewsAt)

        // Calculate the same period in the previous month
        val lastMonthPeriodStart = currentPeriodStart.minusMonths(1)
        val lastMonthPeriodEnd = currentPeriodEnd.minusMonths(1)

        val transactions = transactionRepository.findFilteredTransactionsInRange(
            accountId = accountId,
            category = category,
            mccId = null,
            startDate = lastMonthPeriodStart,
            endDate = lastMonthPeriodEnd
        )

        // Get MCC data to filter by category
        val mccIds = transactions.mapNotNull { it.mccId }.toSet()
        val mccMap = mccRepository.findAllById(mccIds).associateBy { it.id }

        return transactions
            .filter { tx ->
                val mcc = tx.mccId?.let { mccMap[it] }
                mcc?.category == category
            }
            .filter { it.transactionType == TransactionType.DEBIT } // Only count outgoing transactions
            .sumOf { it.amount.abs() }
    }

    private fun createCategoryAdherence(limit: LimitsEntity, spentAmount: BigDecimal, lastMonthSpentAmount: BigDecimal): CategoryAdherence {
        val (periodStart, periodEnd) = calculatePeriodRange(limit.renewsAt)
        val daysInPeriod = getDaysInMonth(limit.renewsAt)
        val today = LocalDate.now()

        val percentageUsed = if (limit.amount > BigDecimal.ZERO) {
            (spentAmount.divide(limit.amount, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
        } else {
            0.0
        }

        val adherenceLevel = when {
            percentageUsed > 100.0 -> AdherenceLevel.EXCEEDED
            percentageUsed > 90.0 -> AdherenceLevel.CRITICAL
            percentageUsed > 75.0 -> AdherenceLevel.WARNING
            percentageUsed > 50.0 -> AdherenceLevel.GOOD
            else -> AdherenceLevel.EXCELLENT
        }

        val remainingAmount = (limit.amount - spentAmount).max(BigDecimal.ZERO)

        val isActivePeriod = today.let { todayDate ->
            (todayDate.isEqual(periodStart.toLocalDate()) || todayDate.isAfter(periodStart.toLocalDate())) &&
                    (todayDate.isBefore(periodEnd.toLocalDate()) || todayDate.isEqual(periodEnd.toLocalDate()))
        }

        // Calculate spending trend and change
        val spendingChange = spentAmount - lastMonthSpentAmount
        val spendingChangePercentage = if (lastMonthSpentAmount > BigDecimal.ZERO) {
            (spendingChange.divide(lastMonthSpentAmount, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
        } else if (spentAmount > BigDecimal.ZERO) {
            100.0 // If there was no spending last month but there is now, it's 100% increase
        } else {
            0.0 // Both are zero
        }

        val spendingTrend = when {
            lastMonthSpentAmount == BigDecimal.ZERO && spentAmount == BigDecimal.ZERO -> SpendingTrend.NO_DATA
            lastMonthSpentAmount == BigDecimal.ZERO -> SpendingTrend.INCREASED
            spendingChangePercentage > 5.0 -> SpendingTrend.INCREASED
            spendingChangePercentage < -5.0 -> SpendingTrend.DECREASED
            else -> SpendingTrend.STABLE
        }

        return CategoryAdherence(
            category = limit.category,
            budgetAmount = limit.amount,
            spentAmount = spentAmount,
            adherenceLevel = adherenceLevel,
            percentageUsed = percentageUsed,
            remainingAmount = remainingAmount,
            renewsAt = limit.renewsAt,
            accountId = limit.accountId,
            periodStart = periodStart.toLocalDate(),
            periodEnd = periodEnd.toLocalDate(),
            daysInPeriod = daysInPeriod,
            isActivePeriod = isActivePeriod,
            lastMonthSpentAmount = lastMonthSpentAmount,
            spendingTrend = spendingTrend,
            spendingChange = spendingChange,
            spendingChangePercentage = spendingChangePercentage
        )
    }

    private fun calculateOverallAdherence(categoryAdherences: List<CategoryAdherence>): AdherenceLevel {
        if (categoryAdherences.isEmpty()) {
            return AdherenceLevel.EXCELLENT
        }

        val totalBudget = categoryAdherences.sumOf { it.budgetAmount }
        val totalSpent = categoryAdherences.sumOf { it.spentAmount }

        val overallPercentage = if (totalBudget > BigDecimal.ZERO) {
            (totalSpent.divide(totalBudget, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
        } else {
            0.0
        }

        return when {
            overallPercentage > 100.0 -> AdherenceLevel.EXCEEDED
            overallPercentage > 90.0 -> AdherenceLevel.CRITICAL
            overallPercentage > 75.0 -> AdherenceLevel.WARNING
            overallPercentage > 50.0 -> AdherenceLevel.GOOD
            else -> AdherenceLevel.EXCELLENT
        }
    }

    // Optional: Get adherence for a specific account
    fun checkAccountBudgetAdherence(userId: Long, accountId: Long): BudgetAdherenceResponse {
        val account = accountRepository.findById(accountId).orElseThrow {
            EntityNotFoundException("Account with ID $accountId not found")
        }

        if (account.userId != userId) {
            throw IllegalAccessException("User ID mismatch")
        }

        val accountLimits = limitsRepository.findAllByAccountId(accountId)
            ?.filter { it.isActive && isCurrentPeriod(it.renewsAt) }
            ?: emptyList()

        val categoryAdherences = accountLimits.map { limit ->
            val spentAmount = calculateSpentAmount(accountId, limit.category, limit.renewsAt)
            val lastMonthSpentAmount = calculateLastMonthSpentAmount(accountId, limit.category, limit.renewsAt)
            createCategoryAdherence(limit, spentAmount, lastMonthSpentAmount)
        }

        val totalBudget = categoryAdherences.sumOf { it.budgetAmount }
        val totalSpent = categoryAdherences.sumOf { it.spentAmount }
        val overallAdherence = calculateOverallAdherence(categoryAdherences)

        return BudgetAdherenceResponse(
            overallAdherence = overallAdherence,
            categoryAdherences = categoryAdherences,
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            accountsChecked = 1
        )
    }
}