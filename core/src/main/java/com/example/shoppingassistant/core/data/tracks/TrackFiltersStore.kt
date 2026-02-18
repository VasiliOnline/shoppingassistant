package com.example.shoppingassistant.core.data.tracks

import android.content.Context
import androidx.core.content.edit
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface TrackFiltersStore {
    suspend fun get(trackId: TrackId): TrackFilters?
    suspend fun set(trackId: TrackId, filters: TrackFilters)
    suspend fun clear(trackId: TrackId)
}

class TrackFiltersStoreImpl(
    context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : TrackFiltersStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun get(trackId: TrackId): TrackFilters? = withContext(Dispatchers.IO) {
        val raw = prefs.getString(key(trackId), null) ?: return@withContext null
        runCatching { json.decodeFromString(TrackFilters.serializer(), raw) }.getOrNull()
    }

    override suspend fun set(trackId: TrackId, filters: TrackFilters) {
        withContext(Dispatchers.IO) {
            val raw = json.encodeToString(TrackFilters.serializer(), filters)
            prefs.edit { putString(key(trackId), raw) }
        }
    }

    override suspend fun clear(trackId: TrackId) {
        withContext(Dispatchers.IO) {
            prefs.edit { remove(key(trackId)) }
        }
    }

    private fun key(trackId: TrackId): String = "track.filters.$trackId"

    private companion object {
        private const val PREFS_NAME = "track_filters.prefs"
    }
}
