package com.example.shoppingassistant.core.data.auth

/**
 * Контракт безопасного хранения bearer-токена на клиенте.
 */
interface AuthTokenStorage {
    /**
     * Сохраняет токен (перезаписывая предыдущий).
     */
    suspend fun save(token: String)

    /**
     * Возвращает сохранённый токен или null, если его нет.
     */
    suspend fun get(): String?

    /**
     * Полностью очищает хранилище токена.
     */
    suspend fun clear()
}
