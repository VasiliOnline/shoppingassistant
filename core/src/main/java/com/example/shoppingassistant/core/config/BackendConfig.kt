// Last synced: 2025-11-19 17:46
package com.example.shoppingassistant.core.config

/**
 * Конфигурация доступа к backend-серверу.
 *
 * Здесь держим только базовый URL, а конкретные пути
 * собираем уже в репозиториях/клиентах.
 */
object BackendConfig {

    /**
     * Базовый URL Ktor-сервера.
     *
     * Для Android-эмулятора:
     *  - 10.0.2.2 — это "localhost" хоста;
     *  - порт должен совпадать с портом Ktor-сервера (ServerConfig.fromEnv()).
     *
     * Сейчас сервер по умолчанию слушает порт 8081,
     * поэтому здесь указываем именно его.
     */
    const val BASE_URL: String = "http://10.0.2.2:8081"
}
