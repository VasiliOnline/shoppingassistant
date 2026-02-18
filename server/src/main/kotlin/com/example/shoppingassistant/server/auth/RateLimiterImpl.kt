package com.example.shoppingassistant.server.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * In-memory rate limiter с окном windowMs и лимитом limit.
 * Используем для защиты login/register/forgot/reset от брутфорса.
 */
class InMemoryRateLimiter(
    private val limit: Int = 10,
    private val windowMs: Long = TimeUnit.MINUTES.toMillis(1),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RateLimiter {

    private val buckets = ConcurrentHashMap<String, MutableList<Long>>()

    override fun allow(key: String): Boolean {
        val now = clock()
        val windowStart = now - windowMs
        val bucket = buckets.computeIfAbsent(key) { mutableListOf() }
        synchronized(bucket) {
            // чистим старые отметки
            val filtered = bucket.filter { it >= windowStart }
            bucket.clear()
            bucket.addAll(filtered)

            if (bucket.size >= limit) {
                return false
            }
            bucket.add(now)
            return true
        }
    }
}
