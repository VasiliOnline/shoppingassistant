// Last synced: 2025-12-21 14:27:08
package com.example.shoppingassistant.core.data.vision

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult

class VisionCacheStorage(
    context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun get(key: String): VisionNormalizeResult? = withContext(Dispatchers.IO) {
        val entries = loadEntries().filterNot { isExpired(it) }
        if (entries.isEmpty()) return@withContext null
        val hit = entries.firstOrNull { it.key == key } ?: return@withContext null
        hit.result
    }

    suspend fun put(key: String, result: VisionNormalizeResult) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fresh = VisionCacheEntry(key = key, result = result, updatedAtMillis = now)
        val existing = loadEntries().filterNot { it.key == key }
        val pruned = (listOf(fresh) + existing)
            .filterNot { isExpired(it) }
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_ENTRIES)
        saveEntries(pruned)
    }

    private fun loadEntries(): List<VisionCacheEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(VisionCacheEntry.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    private fun saveEntries(entries: List<VisionCacheEntry>) {
        val payload = json.encodeToString(ListSerializer(VisionCacheEntry.serializer()), entries)
        prefs.edit().putString(KEY_ENTRIES, payload).apply()
    }

    private fun isExpired(entry: VisionCacheEntry): Boolean =
        System.currentTimeMillis() - entry.updatedAtMillis > MAX_AGE_MILLIS

    @Serializable
    private data class VisionCacheEntry(
        val key: String,
        val result: VisionNormalizeResult,
        val updatedAtMillis: Long,
    )

    private companion object {
        private const val PREFS_NAME = "vision.cache.prefs"
        private const val KEY_ENTRIES = "vision.cache.entries"
        private const val MAX_ENTRIES = 20
        private const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
