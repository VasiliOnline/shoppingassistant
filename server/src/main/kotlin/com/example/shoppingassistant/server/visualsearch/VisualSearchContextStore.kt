package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundQuery
import com.example.shoppingassistant.server.config.VisualSearchConfig
import java.util.concurrent.ConcurrentHashMap

interface VisualSearchContextStore {
    fun getReusableContext(
        fingerprint: String,
        nowMs: Long = System.currentTimeMillis(),
    ): StoredVisualSearchContext?

    fun saveReusableContext(
        fingerprint: String,
        boundQuery: VisualSearchBoundQuery,
        nowMs: Long = System.currentTimeMillis(),
    )
}

data class StoredVisualSearchContext(
    val boundQuery: VisualSearchBoundQuery,
    val savedAtMs: Long,
) {
    fun ageSeconds(nowMs: Long): Long = ((nowMs - savedAtMs).coerceAtLeast(0L)) / 1_000L
}

class InMemoryVisualSearchContextStore(
    private val config: VisualSearchConfig,
) : VisualSearchContextStore {
    private val entries = ConcurrentHashMap<String, StoredVisualSearchContext>()

    override fun getReusableContext(
        fingerprint: String,
        nowMs: Long,
    ): StoredVisualSearchContext? {
        val normalized = fingerprint.trim()
        if (normalized.isEmpty()) return null
        val stored = entries[normalized] ?: return null
        val ttlMs = config.contextReuseTtlSeconds * 1_000L
        if (nowMs - stored.savedAtMs > ttlMs) {
            entries.remove(normalized)
            return null
        }
        return stored
    }

    override fun saveReusableContext(
        fingerprint: String,
        boundQuery: VisualSearchBoundQuery,
        nowMs: Long,
    ) {
        val normalized = fingerprint.trim()
        if (normalized.isEmpty()) return
        entries[normalized] = StoredVisualSearchContext(
            boundQuery = boundQuery,
            savedAtMs = nowMs,
        )
    }
}
