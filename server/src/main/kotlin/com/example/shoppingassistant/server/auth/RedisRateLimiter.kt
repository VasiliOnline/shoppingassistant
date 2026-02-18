package com.example.shoppingassistant.server.auth

import io.lettuce.core.api.sync.RedisCommands
import java.util.concurrent.TimeUnit

/**
 * Redis-based rate limiter (fixed window).
 */
class RedisRateLimiter(
    private val commands: RedisCommands<String, String>,
    private val limit: Int = 10,
    private val windowSeconds: Long = 60,
    private val keyPrefix: String = "rl:",
) : RateLimiter {

    override fun allow(key: String): Boolean {
        val redisKey = "$keyPrefix$key"
        val current = commands.incr(redisKey)
        if (current == 1L) {
            commands.expire(redisKey, windowSeconds)
        }
        return current <= limit
    }
}
