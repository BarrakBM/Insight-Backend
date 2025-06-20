package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface LimitsRepository:JpaRepository<LimitsEntity,Long>{
    fun findByAccountId(accountId: Long): LimitsEntity?
    fun findAllByAccountId(accountId: Long): List<LimitsEntity>?
    fun findByAccountIdAndCategory(accountId: Long, category: String): LimitsEntity?
    fun findAllByRenewsAtBefore(date: LocalDate): List<LimitsEntity>

}

@Entity
@Table(name = "limits")
data class LimitsEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val category: String,
    val amount: BigDecimal,
    val accountId: Long,
    var isActive: Boolean = true,
    val createdAt: LocalDate = LocalDate.now(),
    var renewsAt: LocalDate = LocalDate.now().plusMonths(1)
){
    constructor(): this(0, "", BigDecimal.ZERO, 0)
    @PrePersist
    @PreUpdate
    fun validateBalance() {
        if (amount < BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount cannot be negative")
        }
    }

}