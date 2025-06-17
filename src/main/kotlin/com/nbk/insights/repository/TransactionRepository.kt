package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface TransactionRepository : JpaRepository<TransactionEntity, Long> {
    fun findAllBySourceAccountIdInOrDestinationAccountIdIn(
        sourceAccountIds: List<Long>,
        destinationAccountIds: List<Long>
    ): List<TransactionEntity>

    fun findAllBySourceAccountIdOrDestinationAccountId(
        sourceAccountId: Long?,
        destinationAccountId: Long?
    ): List<TransactionEntity>
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