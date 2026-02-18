package com.example.shoppingassistant.server.config

import java.util.concurrent.TimeUnit

data class SubscriptionsEngineConfig(
    val enabled: Boolean,
    val intervalMillis: Long,
    val batchSize: Int,
    val windowDays: Int,
    val cooldownMinutes: Int,
) {
    companion object {
        fun fromEnv(): SubscriptionsEngineConfig {
            val enabled = (System.getenv("SUBSCRIPTIONS_ENGINE_ENABLED") ?: "true")
                .equals("true", ignoreCase = true)

            val intervalMillis = System.getenv("SUBSCRIPTIONS_ENGINE_INTERVAL_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.MINUTES.toMillis(1)

            val batchSize = System.getenv("SUBSCRIPTIONS_ENGINE_BATCH_SIZE")
                ?.toIntOrNull()
                ?.takeIf { it in 1..5_000 }
                ?: 200

            val windowDays = System.getenv("SUBSCRIPTIONS_ENGINE_WINDOW_DAYS")
                ?.toIntOrNull()
                ?.takeIf { it in 1..365 }
                ?: 7

            val cooldownMinutes = System.getenv("SUBSCRIPTIONS_ENGINE_COOLDOWN_MINUTES")
                ?.toIntOrNull()
                ?.takeIf { it in 0..(24 * 60) }
                ?: 60

            return SubscriptionsEngineConfig(
                enabled = enabled,
                intervalMillis = intervalMillis,
                batchSize = batchSize,
                windowDays = windowDays,
                cooldownMinutes = cooldownMinutes,
            )
        }
    }
}

