package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface TransactionRepository : JpaRepository<TransactionEntity, Long> {
    fun findById(id: Long?): TransactionEntity
}

@Entity
@Table(name = "transactions")
data class TransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val accountId: Long,
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    val transactionType: TransactionType?,
    val mccId: Long,
    val createdAt: LocalDateTime,

    ) {
    constructor() : this(0, 0, BigDecimal.ZERO, null, 0, LocalDateTime.now())
}

enum class TransactionType {
    DEPOSIT, DEBIT, TRANSFER, WITHDRAW
}