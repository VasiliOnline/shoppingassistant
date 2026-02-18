package com.example.shoppingassistant.server.config

import java.util.concurrent.TimeUnit

data class TrackTop10RefreshConfig(
    val enabled: Boolean,
    val pollIntervalMs: Long,
    val batchSize: Int,
    val ttlMs: Long,
    val lockTtlMs: Long,
    val baseBackoffMs: Long,
    val maxBackoffMs: Long,
) {
    companion object {
        fun fromEnv(): TrackTop10RefreshConfig {
            val enabled = (System.getenv("TRACK_TOP10_REFRESH_ENABLED") ?: "true")
                .equals("true", ignoreCase = true)

            val pollIntervalMs = System.getenv("TRACK_TOP10_REFRESH_POLL_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.SECONDS.toMillis(10)

            val batchSize = System.getenv("TRACK_TOP10_REFRESH_BATCH")
                ?.toIntOrNull()
                ?.takeIf { it in 1..500 }
                ?: 50

            val ttlMs = System.getenv("TRACK_TOP10_REFRESH_TTL_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 10_000L }
                ?: TimeUnit.MINUTES.toMillis(5)

            val lockTtlMs = System.getenv("TRACK_TOP10_REFRESH_LOCK_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.SECONDS.toMillis(30)

            val baseBackoffMs = System.getenv("TRACK_TOP10_REFRESH_BACKOFF_BASE_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 1_000L }
                ?: TimeUnit.SECONDS.toMillis(5)

            val maxBackoffMs = System.getenv("TRACK_TOP10_REFRESH_BACKOFF_MAX_MS")
                ?.toLongOrNull()
                ?.takeIf { it >= 10_000L }
                ?: TimeUnit.MINUTES.toMillis(10)

            return TrackTop10RefreshConfig(
                enabled = enabled,
                pollIntervalMs = pollIntervalMs,
                batchSize = batchSize,
                ttlMs = ttlMs,
                lockTtlMs = lockTtlMs,
                baseBackoffMs = baseBackoffMs,
                maxBackoffMs = maxBackoffMs,
            )
        }
    }
}
