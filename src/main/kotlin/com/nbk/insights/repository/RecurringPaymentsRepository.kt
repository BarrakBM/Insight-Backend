package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
interface RecurringPaymentsRepository : JpaRepository<RecurringPaymentEntity, Long> {}

@Entity
@Table(name = "recurring_payments")
data class RecurringPaymentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val userId: Long,
    val mccId: Long,
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    val frequency: FREQUENCY = FREQUENCY.MONTHLY,
    val lastDetected: LocalDateTime,
    val isActive: Boolean,
) {
    constructor() : this(
        id = null,
        userId = 0,
        mccId = 0,
        amount = BigDecimal.ZERO,
        frequency = FREQUENCY.MONTHLY,
        lastDetected = LocalDateTime.now(),
        isActive = false
    )
}
enum class FREQUENCY {
    DAILY, WEEKLY, BIWEEKLY, MONTHLY
}