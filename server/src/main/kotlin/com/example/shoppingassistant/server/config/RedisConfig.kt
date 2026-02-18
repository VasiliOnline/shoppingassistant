// Last synced: 2025-11-23 18:38
package com.example.shoppingassistant.server.config

/**
 * Конфигурация подключения к Redis.
 *
 * Читает значения из переменных окружения:
 * - REDIS_URL — URL подключения к Redis (например, "redis://localhost:6379/0");
 * - REDIS_SESSION_TTL_SECONDS — TTL сессии в секундах (по умолчанию 7 суток);
 * - REDIS_RESET_TTL_SECONDS — TTL reset-токенов восстановления пароля
 *   (по умолчанию 15 минут).
 *
 * enabled == true только если явно указан REDIS_URL.
 */
data class RedisConfig(
    val enabled: Boolean,
    val url: String?,
    val sessionTtlSeconds: Long,
    val resetTokenTtlSeconds: Long,
) {
    companion object {

        fun fromEnv(): RedisConfig {
            val rawUrl = System.getenv("REDIS_URL")?.trim()
            val url = rawUrl?.takeIf { it.isNotEmpty() }

            val sessionTtl = System.getenv("REDIS_SESSION_TTL_SECONDS")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: (7L * 24L * 60L * 60L) // 7 суток по умолчанию

            val resetTtl = System.getenv("REDIS_RESET_TTL_SECONDS")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: (15L * 60L) // 15 минут по умолчанию

            return RedisConfig(
                enabled = url != null,
                url = url,
                sessionTtlSeconds = sessionTtl,
                resetTokenTtlSeconds = resetTtl,
            )
        }
    }
}
