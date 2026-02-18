package com.example.shoppingassistant.server.auth

/**
 * Контракт простого rate limiter для auth-ручек.
 */
interface RateLimiter {
    /**
    * @return true если запрос разрешён, false если превышен лимит.
    */
    fun allow(key: String): Boolean
}
