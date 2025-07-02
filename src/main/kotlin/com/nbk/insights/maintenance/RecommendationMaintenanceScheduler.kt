package com.nbk.insights.service

import com.hazelcast.logging.Logger
import com.nbk.insights.repository.RecommendationScheduleRepository
import com.nbk.insights.repository.RecommendationType
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@EnableScheduling
class RecommendationSchedulerTask(
    private val recommendationScheduleRepository: RecommendationScheduleRepository
) {

    private val logger = Logger.getLogger("recommendation-scheduler")

    /**
     * This task runs daily at 2 AM to clean up old schedules
     * and perform any maintenance tasks
     */
    @Scheduled(cron = "0 0 2 * * *")
    fun performScheduleMaintenance() {
        logger.info("Starting recommendation schedule maintenance")

        // Clean up schedules for users that haven't been active in 90 days
        val cutoffDate = LocalDateTime.now().minusDays(90)
        val allSchedules = recommendationScheduleRepository.findAll()

        var cleanedCount = 0
        allSchedules.forEach { schedule ->
            if (schedule.lastGeneratedAt != null && schedule.lastGeneratedAt!!.isBefore(cutoffDate)) {
                recommendationScheduleRepository.delete(schedule)
                cleanedCount++
            }
        }

        if (cleanedCount > 0) {
            logger.info("Cleaned up $cleanedCount inactive recommendation schedules")
        }

        logger.info("Recommendation schedule maintenance completed")
    }

    /**
     * Optional: You could also add a method to pre-warm caches for users
     * whose recommendations are due soon
     */
    @Scheduled(cron = "0 0 6 * * *") // Run at 6 AM daily
    fun checkUpcomingRecommendations() {
        val now = LocalDateTime.now()
        val upcoming = now.plusHours(1) // Check for recommendations due in the next hour

        val dueSchedules = recommendationScheduleRepository.findAll()
            .filter { it.nextScheduledAt.isAfter(now) && it.nextScheduledAt.isBefore(upcoming) }

        if (dueSchedules.isNotEmpty()) {
            logger.info("Found ${dueSchedules.size} recommendations due in the next hour")
            // You could send notifications or pre-warm caches here
        }
    }
}