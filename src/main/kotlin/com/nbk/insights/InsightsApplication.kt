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
	insightsCacheConfig.getMapConfig("transactions-user").setTimeToLiveSeconds(360)
	insightsCacheConfig.getMapConfig("transactions-account").setTimeToLiveSeconds(360)


}

val insightsCacheConfig = Config("insights-cache")
val serverInsightsCache: HazelcastInstance = Hazelcast.newHazelcastInstance(insightsCacheConfig)
