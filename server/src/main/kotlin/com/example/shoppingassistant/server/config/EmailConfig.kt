package com.example.shoppingassistant.server.config

/**
 * Конфигурация SMTP-отправки писем.
 *
 * Читается из окружения:
 * - SMTP_HOST (обязателен для включения);
 * - SMTP_PORT (по умолчанию 587);
 * - SMTP_USERNAME / SMTP_PASSWORD;
 * - SMTP_FROM (обязателен при enabled);
 * - SMTP_TLS_ENABLED (true/false, по умолчанию true);
 * - RESET_BASE_URL (базовый URL фронта для формирования ссылки со сбросом пароля).
 *
 * enabled == true только если задан SMTP_HOST и SMTP_FROM.
 */
data class EmailConfig(
    val enabled: Boolean,
    val host: String?,
    val port: Int,
    val username: String?,
    val password: String?,
    val from: String?,
    val tlsEnabled: Boolean,
    val resetBaseUrl: String,
) {
    companion object {
        fun fromEnv(): EmailConfig {
            val host = System.getenv("SMTP_HOST")?.trim().takeUnless { it.isNullOrEmpty() }
            val from = System.getenv("SMTP_FROM")?.trim().takeUnless { it.isNullOrEmpty() }
            val port = System.getenv("SMTP_PORT")?.toIntOrNull() ?: 587
            val username = System.getenv("SMTP_USERNAME")?.trim().takeUnless { it.isNullOrEmpty() }
            val password = System.getenv("SMTP_PASSWORD")?.trim().takeUnless { it.isNullOrEmpty() }
            val tlsEnabled = System.getenv("SMTP_TLS_ENABLED")?.lowercase()?.toBoolean() ?: true
            val resetBaseUrl = System.getenv("RESET_BASE_URL")?.trim()
                ?.takeUnless { it.isNullOrEmpty() }
                ?: "https://shoppingassistant.app"

            return EmailConfig(
                enabled = host != null && from != null,
                host = host,
                port = port,
                username = username,
                password = password,
                from = from,
                tlsEnabled = tlsEnabled,
                resetBaseUrl = resetBaseUrl,
            )
        }
    }
}
