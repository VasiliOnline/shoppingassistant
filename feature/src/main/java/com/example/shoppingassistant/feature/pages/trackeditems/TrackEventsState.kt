package com.example.shoppingassistant.feature.pages.trackeditems

import com.example.shoppingassistant.domain.tracks.TrackEvent

sealed interface TrackEventsLoadState {
    data object Loading : TrackEventsLoadState
    data class Content(val items: List<TrackEvent>) : TrackEventsLoadState
    data object Empty : TrackEventsLoadState
    data class Error(val message: String) : TrackEventsLoadState
}

data class TrackEventsState(
    val trackId: String = "",
    val trackTitle: String = "",
    val events: TrackEventsLoadState = TrackEventsLoadState.Loading,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val actionMessage: String? = null,
)
