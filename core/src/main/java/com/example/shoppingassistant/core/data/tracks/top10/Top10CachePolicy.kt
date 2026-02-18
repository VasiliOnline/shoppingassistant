package com.example.shoppingassistant.core.data.tracks.top10

import com.example.shoppingassistant.domain.tracks.TrackTop10
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class Top10CachePolicy(
    private val ttlSec: Int = 60,
    private val swrSec: Int = 300,
    private val cooldownSec: Int = 10,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    private data class CacheEntry(
        val snapshot: TrackTop10,
        val fetchedAt: Long,
        val lastRefreshAt: Long,
        val inFlight: Boolean,
    )

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, CacheEntry>()

    suspend fun get(
        trackId: String,
        forceRefresh: Boolean,
        loader: suspend (forceRefresh: Boolean) -> TrackTop10,
    ): TrackTop10 {
        val now = clock()
        val entry = mutex.withLock { cache[trackId] }

        if (!forceRefresh && entry != null) {
            val ageSec = ageSec(entry, now)
            if (ageSec <= ttlSec) {
                return entry.snapshot
            }
            if (ageSec <= ttlSec + swrSec) {
                triggerBackgroundRefresh(trackId, entry, loader, now)
                return entry.snapshot
            }
        }

        return try {
            val fresh = loader(true)
            mutex.withLock {
                cache[trackId] = CacheEntry(
                    snapshot = fresh,
                    fetchedAt = now,
                    lastRefreshAt = now,
                    inFlight = false,
                )
            }
            fresh
        } catch (t: Throwable) {
            if (entry != null) return entry.snapshot
            throw t
        }
    }

    suspend fun peek(trackId: String): TrackTop10? =
        mutex.withLock { cache[trackId]?.snapshot }

    private fun ageSec(entry: CacheEntry, now: Long): Int =
        max(0, ((now - entry.fetchedAt) / 1000L).toInt())

    private fun triggerBackgroundRefresh(
        trackId: String,
        entry: CacheEntry,
        loader: suspend (forceRefresh: Boolean) -> TrackTop10,
        now: Long,
    ) {
        if (entry.inFlight) return
        val sinceLast = max(0, ((now - entry.lastRefreshAt) / 1000L).toInt())
        if (sinceLast < cooldownSec) return

        scope.launch {
            mutex.withLock {
                val current = cache[trackId] ?: return@withLock
                if (current.inFlight) return@withLock
                cache[trackId] = current.copy(inFlight = true, lastRefreshAt = now)
            }

            val updated = runCatching { loader(true) }.getOrNull()
            mutex.withLock {
                val current = cache[trackId]
                if (current != null) {
                    cache[trackId] = if (updated != null) {
                        CacheEntry(
                            snapshot = updated,
                            fetchedAt = clock(),
                            lastRefreshAt = now,
                            inFlight = false,
                        )
                    } else {
                        current.copy(inFlight = false)
                    }
                }
            }
        }
    }
}
