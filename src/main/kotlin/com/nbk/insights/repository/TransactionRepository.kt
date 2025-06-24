package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.Query

@Repository
interface TransactionRepository : JpaRepository<TransactionEntity, Long> {
    fun findAllBySourceAccountIdInOrDestinationAccountIdIn(
        sourceAccountIds: List<Long>,
        destinationAccountIds: List<Long>
    ): List<TransactionEntity>

    fun findFilteredBySourceAccountIdAndMccIdAndCreatedAt(
        accountId: Long?, mccId: Long, createdAt: LocalDateTime
    ): List<TransactionEntity>

    fun findFilteredBySourceAccountIdAndMccId(
        accountId: Long?, mccId: Long
    ): List<TransactionEntity>

    fun findFilteredBySourceAccountIdAndCreatedAt(
        accountId: Long?, createdAt: LocalDateTime
    ): List<TransactionEntity>

    fun findAllBySourceAccountIdOrDestinationAccountId(
        sourceAccountId: Long?,
        destinationAccountId: Long?
    ): List<TransactionEntity>


    @Query("""
    SELECT t.* FROM transactions t
    LEFT JOIN mcc m ON t.mcc_id = m.id
    WHERE t.source_account_id = :accountId
      AND (COALESCE(:category, '') = '' OR m.category = :category)
      AND (CAST(:startDate AS TIMESTAMP) IS NULL OR t.created_at >= :startDate)
      AND (CAST(:endDate AS TIMESTAMP) IS NULL OR t.created_at <= :endDate)
      AND (COALESCE(:mccId, -1) = -1 OR t.mcc_id = :mccId)
    ORDER BY t.created_at DESC
""", nativeQuery = true)
    fun findFilteredTransactionsInRange(
        @Param("accountId") accountId: Long?,
        @Param("category") category: String?,
        @Param("mccId") mccId: Long?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?
    ): List<TransactionEntity>

    @Query("""
    SELECT t.* FROM transactions t
    LEFT JOIN mcc m ON t.mcc_id = m.id
    WHERE (t.source_account_id IN :accountIds OR t.destination_account_id IN :accountIds)
      AND (COALESCE(:category, '') = '' OR m.category = :category)
      AND (CAST(:startDate AS TIMESTAMP) IS NULL OR t.created_at >= :startDate)
      AND (CAST(:endDate AS TIMESTAMP) IS NULL OR t.created_at <= :endDate)
      AND (COALESCE(:mccId, -1) = -1 OR t.mcc_id = :mccId)
    ORDER BY t.created_at DESC
""", nativeQuery = true)
    fun findFilteredUserTransactionsInRange(
        @Param("accountIds") accountIds: List<Long>,
        @Param("category") category: String?,
        @Param("mccId") mccId: Long?,
        @Param("startDate") startDate: LocalDateTime?,
        @Param("endDate") endDate: LocalDateTime?
    ): List<TransactionEntity>


    @Query(
        """
            SELECT
                t.source_account_id      AS accountId,
                t.mcc_id                 AS mccId,
                ROUND(CAST(t.amount AS numeric) / :amountBand) * :amountBand as amountGroup,
                ARRAY_AGG(t.amount ORDER BY t.created_at DESC) AS amounts,  -- all, most recent is first
                ARRAY_AGG(t.created_at ORDER BY t.created_at DESC)  AS transactionDates,
                COUNT(*)                 AS txCount,
                MAX(t.amount)            AS latestAmount, -- <-- get the max by date (assuming dates are unique, else see below)
                MAX(t.created_at)        AS lastDetected,
                MIN(t.created_at)        AS firstDetected,
                COUNT(DISTINCT DATE_TRUNC('month', t.created_at)) AS monthsWith
            FROM transactions t
            WHERE t.source_account_id = :accountId
              AND t.transaction_type = 'DEBIT'
              AND t.created_at > NOW() - make_interval(months => :monthsBack)
            GROUP BY t.source_account_id, t.mcc_id, amountGroup
            HAVING COUNT(DISTINCT DATE_TRUNC('month', t.created_at)) >= :minMonths
               AND COUNT(*) >= :minTxCount
            ORDER BY lastDetected DESC
        """,
        nativeQuery = true
    )
    fun findRecurringPaymentCandidates(
        @Param("accountId") accountId: Long,
        @Param("minMonths") minMonths: Int = 2,
        @Param("minTxCount") minTxCount: Int = 3,
        @Param("monthsBack") monthsBack: Int = 6,
        @Param("amountBand") amountBand: Int = 10
    ): List<Map<String, Any>>

}

@Entity
@Table(name = "transactions")
data class TransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val sourceAccountId: Long?,
    val destinationAccountId: Long?,
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    val transactionType: TransactionType?,
    val mccId: Long?,
    val createdAt: LocalDateTime,

    ) {
    constructor() : this(
        id = null,
        sourceAccountId = null,
        destinationAccountId = null,
        amount = BigDecimal.ZERO,
        transactionType = null,
        mccId = 0,
        createdAt = LocalDateTime.now()
    )
}

enum class TransactionType {
    DEBIT, CREDIT, TRANSFER, WITHDRAW
}