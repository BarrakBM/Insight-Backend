package com.nbk.insights.helper

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.math.BigDecimal

data class RecurringPaymentCandidate(
    val accountId: Long,
    val mccId: Long,
    val amountGroup: BigDecimal,
    val amounts: List<BigDecimal>,
    val latestAmount: BigDecimal,
    val transactionDates: List<LocalDateTime>,
    val txCount: Int,
    val lastDetected: LocalDateTime,
    val firstDetected: LocalDateTime,
    val monthsWith: Int
)

object RecurringPaymentHelpers {

    fun checkIntervalRegularity(candidate: RecurringPaymentCandidate): Boolean {
        val sortedDates = candidate.transactionDates.sorted()
        val intervals = sortedDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        if (intervals.isEmpty()) return false
        val mean = intervals.average()
        return intervals.all { Math.abs(it - mean) <= 5 } // ±5 days from mean
    }

    fun confidenceScore(candidate: RecurringPaymentCandidate): Double {
        val intervalScore = if (checkIntervalRegularity(candidate)) 0.7 else 0.3
        val countScore = Math.min(candidate.txCount / 5.0, 1.0)
        val recencyScore = if (ChronoUnit.DAYS.between(candidate.lastDetected, LocalDateTime.now()) < 40) 1.0 else 0.5
        return (intervalScore + countScore + recencyScore) / 3.0
    }

    fun detectSkippedPayments(candidate: RecurringPaymentCandidate): List<LocalDateTime> {
        val sortedDates = candidate.transactionDates.sorted()
        val missed = mutableListOf<LocalDateTime>()
        if (sortedDates.size < 2) return missed
        for (i in 1 until sortedDates.size) {
            val prev = sortedDates[i-1]
            val curr = sortedDates[i]
            val gap = ChronoUnit.DAYS.between(prev, curr)
            if (gap > 40) {
                missed.add(prev.plusDays(30))
            }
        }
        return missed
    }

    fun groupByAmountBand(
        results: List<RecurringPaymentCandidate>,
        band: Double
    ): List<List<RecurringPaymentCandidate>> {
        return results.groupBy { (it.amountGroup.toDouble() / band).toInt() }
            .values.toList()
    }
}