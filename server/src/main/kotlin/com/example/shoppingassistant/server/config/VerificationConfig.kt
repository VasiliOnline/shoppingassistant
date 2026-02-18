package com.example.shoppingassistant.server.config

/**
 * Конфигурация верификации email/телефона.
 *
 * Читается из окружения:
 *  - VERIFY_TOKEN_TTL_SECONDS — TTL токена подтверждения (по умолчанию 24 часа);
 *  - VERIFY_RESEND_COOLDOWN_SECONDS — пауза между отправками (по умолчанию 60 секунд).
 */
data class VerificationConfig(
    val tokenTtlSeconds: Long,
    val resendCooldownSeconds: Long,
) {
    companion object {
        fun fromEnv(): VerificationConfig {
            val ttlSeconds = System.getenv("VERIFY_TOKEN_TTL_SECONDS")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: 24L * 60L * 60L

            val resendCooldown = System.getenv("VERIFY_RESEND_COOLDOWN_SECONDS")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: 60L

            return VerificationConfig(
                tokenTtlSeconds = ttlSeconds,
                resendCooldownSeconds = resendCooldown,
            )
        }
    }
}
