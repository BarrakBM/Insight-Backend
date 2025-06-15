package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface LimitsRepository:JpaRepository<LimitsEntity,Long>{
    fun findById(id: Long?): LimitsEntity

}

@Entity
@Table(name = "limits")
data class LimitsEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val category: String,
    val amount: BigDecimal,
    val accountId: Long
){
    constructor(): this(0, "", BigDecimal.ZERO, 0)
}