package com.nbk.insights.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger(ApiHealthService::class.java)

@Service
class ApiHealthService {

    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()

    fun getCircuitBreaker(serviceName: String): CircuitBreaker {
        return circuitBreakers.computeIfAbsent(serviceName) {
            CircuitBreaker(serviceName)
        }
    }

    class CircuitBreaker(
        private val serviceName: String,
        private val failureThreshold: Int = 3,
        private val resetTimeoutMinutes: Long = 5,
        private val halfOpenMaxAttempts: Int = 3
    ) {
        private val failureCount = AtomicInteger(0)
        private val lastFailureTime = AtomicReference<LocalDateTime>()
        private val state = AtomicReference(State.CLOSED)
        private val halfOpenAttempts = AtomicInteger(0)

        enum class State {
            CLOSED,      // Normal operation
            OPEN,        // Failing, use fallback
            HALF_OPEN    // Testing if service recovered
        }

        fun isAvailable(): Boolean {
            return when (state.get()) {
                State.CLOSED -> true
                State.HALF_OPEN -> halfOpenAttempts.get() < halfOpenMaxAttempts
                State.OPEN -> {
                    // Check if we should transition to half-open
                    val lastFailure = lastFailureTime.get()
                    if (lastFailure != null &&
                        ChronoUnit.MINUTES.between(lastFailure, LocalDateTime.now()) >= resetTimeoutMinutes) {
                        logger.info("Circuit breaker for $serviceName transitioning to HALF_OPEN")
                        state.set(State.HALF_OPEN)
                        halfOpenAttempts.set(0)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        fun recordSuccess() {
            when (state.get()) {
                State.HALF_OPEN -> {
                    logger.info("Circuit breaker for $serviceName recovered, transitioning to CLOSED")
                    state.set(State.CLOSED)
                    failureCount.set(0)
                    halfOpenAttempts.set(0)
                }
                State.CLOSED -> {
                    // Reset failure count on success
                    if (failureCount.get() > 0) {
                        failureCount.set(0)
                    }
                }
                else -> {}
            }
        }

        fun recordFailure(error: Throwable) {
            lastFailureTime.set(LocalDateTime.now())

            when (state.get()) {
                State.CLOSED -> {
                    val failures = failureCount.incrementAndGet()
                    logger.warn("Circuit breaker for $serviceName recorded failure $failures/$failureThreshold: ${error.message}")

                    if (failures >= failureThreshold) {
                        logger.error("Circuit breaker for $serviceName OPEN after $failures failures")
                        state.set(State.OPEN)
                    }
                }
                State.HALF_OPEN -> {
                    halfOpenAttempts.incrementAndGet()
                    logger.warn("Circuit breaker for $serviceName failed in HALF_OPEN state, returning to OPEN")
                    state.set(State.OPEN)
                    failureCount.set(failureThreshold)
                }
                else -> {}
            }
        }

        fun getState(): State = state.get()

        fun getHealthStatus(): Map<String, Any> {
            return mapOf(
                "service" to serviceName,
                "state" to state.get().name,
                "failureCount" to failureCount.get(),
                "lastFailure" to (lastFailureTime.get()?.toString() ?: "none"),
                "isAvailable" to isAvailable()
            )
        }
    }

    fun getAllHealthStatus(): Map<String, Map<String, Any>> {
        return circuitBreakers.mapValues { it.value.getHealthStatus() }
    }
}