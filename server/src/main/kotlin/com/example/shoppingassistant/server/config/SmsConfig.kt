package com.example.shoppingassistant.server.config

/**
 * Конфигурация SMS-провайдера.
 *
 * Читается из окружения:
 *  - SMS_API_URL — полный URL endpoint отправки SMS;
 *  - SMS_API_KEY — секрет/токен авторизации;
 *  - SMS_SENDER  — подпись отправителя (если поддерживается провайдером);
 *  - SMS_ENABLED — true/false, включает отправку только когда есть все данные;
 *  - SMS_TIMEOUT_MS — таймаут HTTP-запроса, по умолчанию 5000 мс.
 *
 * enabled == true только если явно включено SMS_ENABLED и заданы url + apiKey.
 * Секреты никогда не логируем, только загружаем из env.
 */
data class SmsConfig(
    val enabled: Boolean,
    val apiUrl: String?,
    val apiKey: String?,
    val sender: String?,
    val timeoutMillis: Long,
) {
    companion object {
        fun fromEnv(): SmsConfig {
            val apiUrl = System.getenv("SMS_API_URL")?.trim().takeUnless { it.isNullOrEmpty() }
            val apiKey = System.getenv("SMS_API_KEY")?.trim().takeUnless { it.isNullOrEmpty() }
            val sender = System.getenv("SMS_SENDER")?.trim().takeUnless { it.isNullOrEmpty() }
            val timeoutMillis = System.getenv("SMS_TIMEOUT_MS")
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: 5000L

            val enabledFlag = (System.getenv("SMS_ENABLED") ?: "false")
                .equals("true", ignoreCase = true)

            return SmsConfig(
                enabled = enabledFlag && apiUrl != null && apiKey != null,
                apiUrl = apiUrl,
                apiKey = apiKey,
                sender = sender,
                timeoutMillis = timeoutMillis,
            )
        }
    }
}
