// Last synced: 2025-11-21 15:00
package com.example.shoppingassistant.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Обёртка над Ktor HttpClient для общения с backend-сервером.
 *
 * Через эту обёртку можно будет позже добавлять вспомогательные методы
 * (общие заголовки, retry и т.п.), не трогая репозитории.
 */
class BackendClient(
    val client: HttpClient,
)

/**
 * Фабрика BackendClient с настроенным JSON.
 *
 * Клиент поднимается один раз через Koin в coreModule и шарится
 * между репозиториями.
 */
fun createBackendHttpClient(): BackendClient {
    val httpClient = HttpClient(OkHttp) {
        // Не бросаем исключения на 4xx/5xx, сами смотрим на status.
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json {
                    // backend может добавить новые поля
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = false
                    prettyPrint = false
                },
            )
        }

        // Тут позже можно добавить логирование, таймауты, заголовки и т.д.
        // install(Logging) { level = LogLevel.BODY }
        // install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    return BackendClient(httpClient)
}
