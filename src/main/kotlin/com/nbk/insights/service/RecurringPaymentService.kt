package com.nbk.insights.service

import com.nbk.insights.dto.MCC
import com.nbk.insights.dto.RecurringPaymentResponse
import com.nbk.insights.helper.RecurringPaymentCandidate
import com.nbk.insights.helper.RecurringPaymentHelpers
import com.nbk.insights.repository.AccountRepository
import com.nbk.insights.repository.MccRepository
import com.nbk.insights.repository.TransactionRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RecurringPaymentService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val mccRepository: MccRepository
) {
    fun detectRecurringPaymentsDynamic(
        accountId: Long,
        userId: Long?,
        monthsBack: Int = 6,
        amountBand: Int = 10,
        minMonths: Int = 2,
        minTxCount: Int = 3,
        minConfidence: Double = 0.6
    ): List<RecurringPaymentResponse> {

        val account = accountRepository.findById(accountId).orElseThrow {
            EntityNotFoundException("Account not found with id: $accountId")
        }

        if (account.userId != userId) {
            throw IllegalAccessException("You do not have access to this account")
        }

        val rawResults = transactionRepository.findRecurringPaymentCandidates(
            accountId = accountId,
            minMonths = minMonths,
            minTxCount = minTxCount,
            monthsBack = monthsBack,
            amountBand = amountBand
        )

        if (rawResults.isEmpty()) {
            throw NoSuchElementException("No recurring payments found for account id: $accountId")
        }

        val candidates = rawResults.map { row ->
            val amounts = (row["amounts"] as Array<*>).map { it as BigDecimal }
            val transactionDates = (row["transactiondates"] as Array<*>).map {
                (it as java.sql.Timestamp).toLocalDateTime()
            }

            RecurringPaymentCandidate(
                accountId = (row["accountid"] as Number).toLong(),
                mccId = (row["mccid"] as Number).toLong(),
                amountGroup = BigDecimal(row["amountgroup"].toString()),
                amounts = amounts,
                latestAmount = amounts.first(),
                transactionDates = transactionDates,
                txCount = (row["txcount"] as Number).toInt(),
                lastDetected = (row["lastdetected"] as java.sql.Timestamp).toLocalDateTime(),
                firstDetected = (row["firstdetected"] as java.sql.Timestamp).toLocalDateTime(),
                monthsWith = (row["monthswith"] as Number).toInt()
            )
        }

        return candidates.map { candidate ->
            val mccEntity = mccRepository.findById(candidate.mccId).orElseThrow {
                EntityNotFoundException("MCC not found with ID: ${candidate.mccId}")
            }

            val confidence = RecurringPaymentHelpers.confidenceScore(candidate)
                .let { String.format("%.1f", it).toDouble() }

            RecurringPaymentResponse(
                accountId = candidate.accountId,
                mcc = MCC(
                    category = mccEntity.category,
                    subCategory = mccEntity.subCategory
                ),
                amountGroup = candidate.amountGroup,
                amounts = candidate.amounts,
                latestAmount = candidate.latestAmount,
                transactionCount = candidate.txCount,
                monthsWithPayments = candidate.monthsWith,
                detectedIntervalRegular = RecurringPaymentHelpers.checkIntervalRegularity(candidate),
                confidenceScore = confidence,
                lastDetected = candidate.lastDetected,
                skippedPaymentEstimate = RecurringPaymentHelpers.detectSkippedPayments(candidate)
            )
        }.filter { it.confidenceScore >= minConfidence }
    }
}
