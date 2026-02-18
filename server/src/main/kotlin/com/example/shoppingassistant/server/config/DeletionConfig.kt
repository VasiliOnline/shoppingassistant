package com.example.shoppingassistant.server.config

/**
 * Конфигурация задержки перед финальным удалением аккаунта.
 *
 * ACCOUNT_DELETE_TTL_SECONDS — длительность окна восстановления (по умолчанию 7 дней).
 */
data class DeletionConfig(
    val deleteTtlSeconds: Long,
) {
    companion object {
        fun fromEnv(): DeletionConfig {
            val ttlSeconds = System.getenv("ACCOUNT_DELETE_TTL_SECONDS")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: 7L * 24L * 60L * 60L

            return DeletionConfig(deleteTtlSeconds = ttlSeconds)
        }
    }
}
