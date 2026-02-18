package com.example.shoppingassistant.server.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * Простая in-memory защита от спама: не даёт чаще, чем раз в cooldownMillis по ключу.
 */
class ResendCooldownTracker(
    private val cooldownMillis: Long,
) {
    private val lastSent = ConcurrentHashMap<String, Long>()

    fun tryAcquire(key: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastSent[key]
        if (previous != null && (now - previous) < cooldownMillis) {
            return false
        }
        lastSent[key] = now
        return true
    }
}
