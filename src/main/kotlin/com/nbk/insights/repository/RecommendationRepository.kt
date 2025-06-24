package com.nbk.insights.repository

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Repository
interface RecommendationRepository: JpaRepository<RecommendationEntity, Long> {

    @Query("""
    SELECT r.* FROM recommendations r
    INNER JOIN (
        SELECT category, MAX(created_at) AS max_created_at
        FROM (
            SELECT SUBSTRING(reason FROM '\[(.*?)\]') AS category, created_at
            FROM recommendations
            WHERE user_id = :userId
        ) grouped
        GROUP BY category
    ) latest ON SUBSTRING(r.reason FROM '\[(.*?)\]') = latest.category
    AND r.created_at = latest.max_created_at
    WHERE r.user_id = :userId
""", nativeQuery = true)
    fun findLatestRecommendationsPerCategory(userId: Long): List<RecommendationEntity>

}

@Entity
@Table(name = "recommendations")
data class RecommendationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val userId: Long,
    val reason: String,
    val createdAt: LocalDateTime
    )
{
    constructor(): this(id = null, userId = 0, reason = "", createdAt = LocalDateTime.now())
}
