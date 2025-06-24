package com.nbk.insights.repository

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Repository
interface RecommendationRepository: JpaRepository<RecommendationEntity, Long> {}

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
