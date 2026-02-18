package com.example.shoppingassistant.core.data.tracks

import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackEventsPage
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackOffersPage
import com.example.shoppingassistant.domain.tracks.TrackTop10
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackType
import kotlinx.serialization.Serializable

interface TracksRemoteDataSource {
    suspend fun listTracks(): List<Track>
    suspend fun getTrack(trackId: String): Track?
    suspend fun createTrack(request: TrackCreateRequest): Track
    suspend fun updateTrack(trackId: String, request: TrackUpdateRequest): Track?
    suspend fun updateTrackTarget(trackId: String, request: TrackTargetUpdateRequest): TrackTargetUpdateRemoteResult
    suspend fun pauseTrack(trackId: String): Track?
    suspend fun resumeTrack(trackId: String): Track?
    suspend fun deleteTrack(trackId: String): Boolean
    suspend fun getTop10(trackId: String, forceRefresh: Boolean = false, preferCache: Boolean = true): TrackTop10
    suspend fun refreshTop10(trackId: String): TrackTop10
    suspend fun listOffers(
        trackId: String,
        limit: Int,
        offset: Int,
        sort: TrackOfferSort,
    ): TrackOffersPage
    suspend fun listEvents(trackId: String, limit: Int, offset: Int): TrackEventsPage
    suspend fun markEventRead(eventId: String): Boolean
    suspend fun markAllEventsRead(trackId: String): Int
}

@Serializable
data class TrackCreateRequest(
    val type: TrackType,
    val target: TrackTarget,
    val categoryCode: String? = null,
    val filters: TrackFilters = TrackFilters(),
    val title: String? = null,
)

@Serializable
data class TrackUpdateRequest(
    val title: String? = null,
    val filters: TrackFilters? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class TrackTargetUpdateRequest(
    val type: TrackType,
    val matchKey: String? = null,
    val categoryCode: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val title: String? = null,
)

sealed interface TrackTargetUpdateRemoteResult {
    data class Updated(val track: Track) : TrackTargetUpdateRemoteResult
    data class AlreadyExists(val track: Track) : TrackTargetUpdateRemoteResult
    data class InvalidInput(val reason: String) : TrackTargetUpdateRemoteResult
    data object NotFound : TrackTargetUpdateRemoteResult
}
