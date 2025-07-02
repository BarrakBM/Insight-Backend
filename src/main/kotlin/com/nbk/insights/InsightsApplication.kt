package com.nbk.insights

import com.hazelcast.core.Hazelcast
import com.hazelcast.config.Config
import com.hazelcast.core.HazelcastInstance
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InsightsApplication

fun main(args: Array<String>) {
	runApplication<InsightsApplication>(*args)

	// Transaction caches - 6 minutes TTL
	insightsCacheConfig.getMapConfig("transactions-user").setTimeToLiveSeconds(360)
	insightsCacheConfig.getMapConfig("transactions-account").setTimeToLiveSeconds(360)

	// Category recommendations - 28-31 days (will be checked by schedule)
	insightsCacheConfig.getMapConfig("recommendation-category").setTimeToLiveSeconds(2592000)

	// Offer recommendations - 7 days
	insightsCacheConfig.getMapConfig("recommendation-offers").setTimeToLiveSeconds(604800)

	// Quick insights - 24 hours
	insightsCacheConfig.getMapConfig("quick-insights").setTimeToLiveSeconds(86400)
}

val insightsCacheConfig = Config("insights-cache")
val serverInsightsCache: HazelcastInstance = Hazelcast.newHazelcastInstance(insightsCacheConfig)