package com.example.shoppingassistant.core.data.ingest.cache

import com.example.shoppingassistant.domain.ingest.IngestResult

interface IngestCache {
    fun get(url: String): IngestResult?
    fun put(url: String, result: IngestResult)
}

class InMemoryIngestCache(
    private val maxEntries: Int = 50,
    private val ttlMillis: Long = 5 * 60 * 1000L,
) : IngestCache {

    private data class CacheEntry(
        val storedAt: Long,
        val result: IngestResult,
    )

    private val cache = LinkedHashMap<String, CacheEntry>(maxEntries, 0.75f, true)

    override fun get(url: String): IngestResult? {
        val now = System.currentTimeMillis()
        val entry = cache[url] ?: return null
        if (now - entry.storedAt > ttlMillis) {
            cache.remove(url)
            return null
        }
        return entry.result
    }

    override fun put(url: String, result: IngestResult) {
        cache[url] = CacheEntry(System.currentTimeMillis(), result)
        trimIfNeeded()
    }

    private fun trimIfNeeded() {
        if (cache.size <= maxEntries) return
        val iterator = cache.entries.iterator()
        while (cache.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
}
