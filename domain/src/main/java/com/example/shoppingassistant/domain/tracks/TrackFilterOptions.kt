package com.example.shoppingassistant.domain.tracks

import kotlinx.serialization.Serializable

@Serializable
enum class TrackFilterKey { REGION, DELIVERY, CONDITION, SELLER, EXTRA_KEY, EXTRA_VALUE }

@Serializable
data class TrackFilterOption(
    val id: String,
    val label: String,
    val payload: String? = null,
    val count: Int? = null,
    val isSuggested: Boolean = false,
    val isRecent: Boolean = false,
    val isDisabled: Boolean = false,
    val disabledReason: String? = null,
)

@Serializable
data class TrackFilterOptionsBundle(
    val key: TrackFilterKey,
    val options: List<TrackFilterOption>,
    val allowCustom: Boolean,
    val allowEmpty: Boolean,
    val searchEnabled: Boolean,
    val lastUpdatedAt: Long? = null,
    val ttlSec: Int? = null,
)

interface TrackFilterOptionsRepository {
    suspend fun getOptions(
        trackId: TrackId,
        key: TrackFilterKey,
        query: String? = null,
        extraKey: String? = null,
    ): TrackFilterOptionsBundle
}

class GetTrackFilterOptionsTask(private val repo: TrackFilterOptionsRepository) {
    suspend operator fun invoke(
        trackId: TrackId,
        key: TrackFilterKey,
        query: String? = null,
        extraKey: String? = null,
    ): TrackFilterOptionsBundle = repo.getOptions(trackId, key, query, extraKey)
}
