package com.example.shoppingassistant.feature.pages.trackeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.domain.tracks.GetTrackEventsPageTask
import com.example.shoppingassistant.domain.tracks.MarkAllTrackEventsReadTask
import com.example.shoppingassistant.domain.tracks.MarkTrackEventReadTask
import com.example.shoppingassistant.domain.tracks.TrackEvent
import com.example.shoppingassistant.domain.tracks.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrackEventsViewModel(
    private val trackRepository: TrackRepository,
    private val getEventsPage: GetTrackEventsPageTask,
    private val markEventRead: MarkTrackEventReadTask,
    private val markAllRead: MarkAllTrackEventsReadTask,
) : ViewModel() {

    private companion object {
        const val PAGE_LIMIT: Int = 30
    }

    private val _state = MutableStateFlow(TrackEventsState())
    val state: StateFlow<TrackEventsState> = _state.asStateFlow()

    private var currentOffset: Int = 0
    private var canLoadMore: Boolean = false
    private var activeTrackId: String? = null

    fun load(trackId: String) {
        if (trackId.isBlank()) return
        if (activeTrackId == trackId && _state.value.events is TrackEventsLoadState.Content) return

        activeTrackId = trackId
        currentOffset = 0
        canLoadMore = false

        _state.update { it.copy(trackId = trackId, events = TrackEventsLoadState.Loading, actionMessage = null) }
        viewModelScope.launch {
            val trackTitle = runCatching { trackRepository.getTrack(trackId) }.getOrNull()?.title.orEmpty()
            val page = runCatching { getEventsPage(trackId, PAGE_LIMIT, 0) }
                .getOrElse {
                    _state.update { state ->
                        state.copy(events = TrackEventsLoadState.Error(it.message ?: "Не удалось загрузить события"))
                    }
                    return@launch
                }

            currentOffset = page.offset + page.items.size
            canLoadMore = page.canLoadMore

            _state.update { state ->
                val nextEvents = if (page.items.isEmpty()) {
                    TrackEventsLoadState.Empty
                } else {
                    TrackEventsLoadState.Content(page.items)
                }
                state.copy(
                    trackTitle = trackTitle,
                    events = nextEvents,
                    canLoadMore = canLoadMore,
                    isLoadingMore = false,
                )
            }
        }
    }

    fun loadMore() {
        val trackId = activeTrackId ?: return
        val current = _state.value
        if (!canLoadMore || current.isLoadingMore) return

        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val page = runCatching { getEventsPage(trackId, PAGE_LIMIT, currentOffset) }
                .getOrElse {
                    _state.update { state ->
                        state.copy(isLoadingMore = false, actionMessage = "Не удалось загрузить ещё")
                    }
                    return@launch
                }

            currentOffset = page.offset + page.items.size
            canLoadMore = page.canLoadMore

            _state.update { state ->
                val existing = (state.events as? TrackEventsLoadState.Content)?.items.orEmpty()
                val merged = mergeEvents(existing, page.items)
                state.copy(
                    events = if (merged.isEmpty()) TrackEventsLoadState.Empty else TrackEventsLoadState.Content(merged),
                    canLoadMore = canLoadMore,
                    isLoadingMore = false,
                )
            }
        }
    }

    fun markRead(event: TrackEvent) {
        if (event.isRead) return
        viewModelScope.launch {
            val ok = runCatching { markEventRead(event.id) }.getOrElse { false }
            if (ok) {
                _state.update { state ->
                    val items = (state.events as? TrackEventsLoadState.Content)?.items.orEmpty()
                    state.copy(events = TrackEventsLoadState.Content(markItemRead(items, event.id)))
                }
            }
        }
    }

    fun markAllRead() {
        val trackId = activeTrackId ?: return
        viewModelScope.launch {
            val updated = runCatching { markAllRead(trackId) }.getOrElse { 0 }
            if (updated > 0) {
                _state.update { state ->
                    val items = (state.events as? TrackEventsLoadState.Content)?.items.orEmpty()
                    state.copy(events = TrackEventsLoadState.Content(items.map { it.copy(isRead = true) }))
                }
            }
        }
    }

    fun consumeActionMessage() {
        _state.update { it.copy(actionMessage = null) }
    }

    private fun mergeEvents(existing: List<TrackEvent>, incoming: List<TrackEvent>): List<TrackEvent> {
        if (incoming.isEmpty()) return existing
        val map = LinkedHashMap<String, TrackEvent>()
        existing.forEach { map[it.id] = it }
        incoming.forEach { map[it.id] = it }
        return map.values.toList()
    }

    private fun markItemRead(items: List<TrackEvent>, eventId: String): List<TrackEvent> =
        items.map { if (it.id == eventId) it.copy(isRead = true) else it }
}
