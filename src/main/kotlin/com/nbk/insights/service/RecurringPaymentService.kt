package com.nbk.insights.service


import com.nbk.insights.dto.MCC
import com.nbk.insights.repository.TransactionRepository
import com.nbk.insights.helper.RecurringPaymentHelpers
import com.nbk.insights.helper.RecurringPaymentCandidate
import com.nbk.insights.dto.RecurringPaymentResponse
import com.nbk.insights.repository.MccRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RecurringPaymentService(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: com.nbk.insights.repository.AccountRepository,
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
    ): ResponseEntity<Any> {


        // Validate account exists
        val account = accountRepository.findById(accountId).orElse(null)
        if (account == null) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "account not found"))
        }

//        // Check ownership
//        if (account.userId != userId) {
//            return ResponseEntity
//                .status(HttpStatus.BAD_REQUEST)
//                .body(mapOf("error" to "account not found"))
//        }


        val rawResults = transactionRepository.findRecurringPaymentCandidates(
            accountId = accountId,
            minMonths = minMonths,
            minTxCount = minTxCount,
            monthsBack = monthsBack,
            amountBand = amountBand
        )

        if (rawResults.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        val candidates = rawResults.map { row ->
            val amounts = (row["amounts"] as Array<*>).map { it as BigDecimal }
            val transactionDates = (row["transactiondates"] as Array<*>).map { (it as java.sql.Timestamp).toLocalDateTime() }
            RecurringPaymentCandidate(
                accountId = (row["accountid"] as Number).toLong(),
                mccId = (row["mccid"] as Number).toLong(),
                amountGroup = BigDecimal(row["amountgroup"].toString()),
                amounts = amounts,
                latestAmount = amounts.first(), // array is ordered DESC by date, so first is latest
                transactionDates = transactionDates,
                txCount = (row["txcount"] as Number).toInt(),
                lastDetected = (row["lastdetected"] as java.sql.Timestamp).toLocalDateTime(),
                firstDetected = (row["firstdetected"] as java.sql.Timestamp).toLocalDateTime(),
                monthsWith = (row["monthswith"] as Number).toInt()
            )
        }




        val withConfidence = candidates.mapNotNull { candidate ->
            // Fetch the full MCC object using the candidate's mccId
            val mccEntity = mccRepository.findById(candidate.mccId).orElse(null) ?: return@mapNotNull null

            // Map MCC entity to your DTO
            val mcc = MCC(
                category = mccEntity.category,
                subCategory = mccEntity.subCategory
            )
            val confidence = RecurringPaymentHelpers.confidenceScore(candidate)
                .let { String.format("%.1f", it).toDouble() }
            val intervalRegular = RecurringPaymentHelpers.checkIntervalRegularity(candidate)
            val skipped = RecurringPaymentHelpers.detectSkippedPayments(candidate)

            RecurringPaymentResponse(
                accountId = candidate.accountId,
                mcc = mcc,
                amountGroup = candidate.amountGroup,
                amounts = candidate.amounts,
                latestAmount = candidate.latestAmount,
                transactionCount = candidate.txCount,
                monthsWithPayments = candidate.monthsWith,
                detectedIntervalRegular = intervalRegular,
                confidenceScore = confidence,
                lastDetected = candidate.lastDetected,
                skippedPaymentEstimate = skipped
            )

        }.filter { it.confidenceScore >= minConfidence }


        return ResponseEntity.ok(withConfidence)
    }
}