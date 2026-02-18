package com.example.shoppingassistant.server.price

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class SourceRateLimiter(
    private val baseCooldownMs: Long,
    private val maxCooldownMs: Long,
    private val errorCooldownMs: Long,
) {
    private data class State(
        var cooldownUntil: Long = 0L,
        var backoffMs: Long = 0L,
    )

    private val states = ConcurrentHashMap<String, State>()

    fun isCoolingDown(sourceId: String, now: Long = System.currentTimeMillis()): Boolean {
        val state = states[sourceId] ?: return false
        return state.cooldownUntil > now
    }

    fun onBlocked(sourceId: String, now: Long = System.currentTimeMillis()) {
        val state = states.getOrPut(sourceId) { State() }
        val next = if (state.backoffMs <= 0L) baseCooldownMs else min(state.backoffMs * 2, maxCooldownMs)
        state.backoffMs = max(baseCooldownMs, next)
        state.cooldownUntil = now + state.backoffMs
    }

    fun onError(sourceId: String, now: Long = System.currentTimeMillis()) {
        val state = states.getOrPut(sourceId) { State() }
        val next = max(state.backoffMs, errorCooldownMs)
        state.backoffMs = min(next, maxCooldownMs)
        state.cooldownUntil = now + state.backoffMs
    }

    fun onSuccess(sourceId: String) {
        states.remove(sourceId)
    }
}
