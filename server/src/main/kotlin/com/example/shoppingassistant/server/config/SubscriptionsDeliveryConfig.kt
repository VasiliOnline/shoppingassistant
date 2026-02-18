package com.example.shoppingassistant.server.config

import java.util.concurrent.TimeUnit

data class SubscriptionsDeliveryConfig(
    val enabled: Boolean,
    val intervalMillis: Long,
    val batchSize: Int,
    val maxAttempts: Int,
    val stuckSendingTimeoutMillis: Long,
) {
    companion object {
        fun fromEnv(): SubscriptionsDeliveryConfig {
            val enabled = (System.getenv("SUBSCRIPTIONS_DELIVERY_ENABLED") ?: "false")
                .equals("true", ignoreCase = true)

            val intervalMillis = System.getenv("SUBSCRIPTIONS_DELIVERY_INTERVAL_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.SECONDS.toMillis(10)

            val batchSize = System.getenv("SUBSCRIPTIONS_DELIVERY_BATCH_SIZE")
                ?.toIntOrNull()
                ?.takeIf { it in 1..500 }
                ?: 50

            val maxAttempts = System.getenv("SUBSCRIPTIONS_DELIVERY_MAX_ATTEMPTS")
                ?.toIntOrNull()
                ?.takeIf { it in 1..20 }
                ?: 5

            val stuckSendingTimeoutMillis = System.getenv("SUBSCRIPTIONS_DELIVERY_STUCK_TIMEOUT_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 5_000L }
                ?: TimeUnit.MINUTES.toMillis(5)

            return SubscriptionsDeliveryConfig(
                enabled = enabled,
                intervalMillis = intervalMillis,
                batchSize = batchSize,
                maxAttempts = maxAttempts,
                stuckSendingTimeoutMillis = stuckSendingTimeoutMillis,
            )
        }
    }
}

