package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal

@Repository
interface LimitsRepository:JpaRepository<LimitsEntity,Long>{
    fun findByAccountId(accountId: Long): LimitsEntity?
    fun findAllByAccountId(accountId: Long): List<LimitsEntity>?

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