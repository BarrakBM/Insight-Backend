package com.nbk.insights.controller

import com.nbk.insights.service.ApiHealthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/health")
class ApiHealthController(
    private val apiHealthService: ApiHealthService
) {

    @GetMapping("/status")
    fun getHealthStatus(): ResponseEntity<Map<String, Any>> {
        val healthStatus = apiHealthService.getAllHealthStatus()

        val overallHealth = if (healthStatus.values.all {
                (it["isAvailable"] as? Boolean) ?: false
            }) "HEALTHY" else "DEGRADED"

        return ResponseEntity.ok(mapOf(
            "status" to overallHealth,
            "services" to healthStatus,
            "timestamp" to System.currentTimeMillis()
        ))
    }

    @GetMapping("/circuit-breakers")
    fun getCircuitBreakerStatus(): ResponseEntity<Map<String, Map<String, Any>>> {
        return ResponseEntity.ok(apiHealthService.getAllHealthStatus())
    }
}