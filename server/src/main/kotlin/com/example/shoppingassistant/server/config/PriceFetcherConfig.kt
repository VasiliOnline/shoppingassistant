package com.example.shoppingassistant.server.config

import java.util.concurrent.TimeUnit

data class PriceFetcherConfig(
    val enabled: Boolean,
    val intervalMillis: Long,
    val batchSize: Int,
    val minIntervalMillis: Long,
    val maxPerRun: Int,
    val blockCooldownMillis: Long,
    val blockMaxCooldownMillis: Long,
    val errorCooldownMillis: Long,
) {
    companion object {
        fun fromEnv(): PriceFetcherConfig {
            val enabled = (System.getenv("PRICE_FETCHER_ENABLED") ?: "true")
                .equals("true", ignoreCase = true)

            val intervalMillis = System.getenv("PRICE_FETCHER_INTERVAL_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.MINUTES.toMillis(15)

            val batchSize = System.getenv("PRICE_FETCHER_BATCH_SIZE")
                ?.toIntOrNull()
                ?.takeIf { it in 1..10_000 }
                ?: 300

            val minIntervalMillis = System.getenv("PRICE_FETCHER_MIN_INTERVAL_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.HOURS.toMillis(6)

            val maxPerRun = System.getenv("PRICE_FETCHER_MAX_PER_RUN")
                ?.toIntOrNull()
                ?.takeIf { it in 1..10_000 }
                ?: 300

            val blockCooldownMillis = System.getenv("PRICE_FETCHER_BLOCK_COOLDOWN_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.MINUTES.toMillis(10)

            val blockMaxCooldownMillis = System.getenv("PRICE_FETCHER_BLOCK_MAX_COOLDOWN_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.HOURS.toMillis(6)

            val errorCooldownMillis = System.getenv("PRICE_FETCHER_ERROR_COOLDOWN_MILLIS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.MINUTES.toMillis(3)

            return PriceFetcherConfig(
                enabled = enabled,
                intervalMillis = intervalMillis,
                batchSize = batchSize,
                minIntervalMillis = minIntervalMillis,
                maxPerRun = maxPerRun,
                blockCooldownMillis = blockCooldownMillis,
                blockMaxCooldownMillis = blockMaxCooldownMillis,
                errorCooldownMillis = errorCooldownMillis,
            )
        }
    }
}
