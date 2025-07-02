package com.nbk.insights.repository

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface RecommendationScheduleRepository : JpaRepository<RecommendationScheduleEntity, Long> {
    fun findByUserIdAndRecommendationType(userId: Long, recommendationType: RecommendationType): RecommendationScheduleEntity?
}

@Entity
@Table(name = "recommendation_schedules")
data class RecommendationScheduleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val userId: Long,

    @Enumerated(EnumType.STRING)
    val recommendationType: RecommendationType,

    var lastGeneratedAt: LocalDateTime? = null,

    var nextScheduledAt: LocalDateTime,

    // Store the last budget period end date for category recommendations
    var lastBudgetPeriodEnd: LocalDateTime? = null
) {
    constructor() : this(
        id = null,
        userId = 0,
        recommendationType = RecommendationType.CATEGORY,
        lastGeneratedAt = null,
        nextScheduledAt = LocalDateTime.now(),
        lastBudgetPeriodEnd = null
    )
}

enum class RecommendationType {
    CATEGORY,      // End of budget cycle (28-31 days)
    OFFERS,        // Weekly (7 days)
    QUICK_INSIGHTS // Daily (24 hours)
}