package com.example.shoppingassistant.feature.pages.trackeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.domain.tracks.GetTop10Task
import com.example.shoppingassistant.domain.tracks.Freshness
import com.example.shoppingassistant.domain.tracks.FreshnessState
import com.example.shoppingassistant.domain.tracks.GetTrackOffersPageTask
import com.example.shoppingassistant.domain.tracks.MarkAllTrackEventsReadTask
import com.example.shoppingassistant.domain.tracks.RefreshTop10Task
import com.example.shoppingassistant.domain.tracks.TrackOfferSort
import com.example.shoppingassistant.domain.tracks.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.math.max

class TrackTop10ViewModel(
    private val trackRepository: TrackRepository,
    private val getTop10: GetTop10Task,
    private val refreshTop10: RefreshTop10Task,
    private val getTrackOffersPage: GetTrackOffersPageTask,
    private val markAllEventsRead: MarkAllTrackEventsReadTask,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private companion object {
        const val REFRESH_COOLDOWN_MS: Long = 15_000L
        const val TICK_INTERVAL_MS: Long = 2_000L
        const val DEFAULT_TTL_SEC: Int = 300
        const val AUTO_REFRESH_COOLDOWN_MS: Long = 60_000L
        const val OFFERS_PAGE_SIZE: Int = 20
    }

    private val _state = MutableStateFlow(TrackTop10State())
    val state: StateFlow<TrackTop10State> = _state.asStateFlow()

    private val lastRefreshAt = HashMap<String, Long>()
    private var tickerJob: Job? = null
    private var activeTrackId: String? = null
    private var lastAutoRefreshAt: Long = 0L

    fun load(trackId: String) {
        activeTrackId = trackId
        viewModelScope.launch {
            val current = _state.value.top10
            val hasContent = current is Top10LoadState.Data || current is Top10LoadState.Empty
            if (hasContent) {
                _state.update {
                    it.copy(
                        top10 = updateRefreshState(current, isRefreshing = true, lastError = null),
                        actionMessage = null,
                        offersError = null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        top10 = Top10LoadState.Loading,
                        actionMessage = null,
                        freshness = null,
                        offers = emptyList(),
                        offersCanLoadMore = false,
                        offersLoading = false,
                        offersError = null,
                    )
                }
            }
            val track = runCatching { trackRepository.getTrack(trackId) }.getOrNull()
            _state.update { it.copy(track = track) }
            runCatching { getTop10(trackId, false) }
                .onSuccess { top10 ->
                    val freshness = computeFreshness(top10, clock())
                    val next = buildContentState(top10, freshness, isRefreshing = false, lastError = null)
                    _state.update { it.copy(top10 = next, freshness = freshness, isOffline = false, offersError = null) }
                    startTicker()
                    loadOffers(trackId = trackId, reset = true)
                    markAllOnOpen(trackId)
                }
                .onFailure { e ->
                    val message = e.message ?: "Не удалось загрузить"
                    val previous = _state.value.top10
                    if (previous is Top10LoadState.Data || previous is Top10LoadState.Empty) {
                        _state.update {
                            it.copy(
                                top10 = updateRefreshState(previous, isRefreshing = false, lastError = message),
                                isOffline = isOfflineError(e),
                                actionMessage = "Не удалось обновить. Показан последний кеш",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                top10 = Top10LoadState.FatalError(message),
                                isOffline = isOfflineError(e),
                            )
                        }
                    }
                }
        }
    }

    fun refresh(trackId: String) {
        activeTrackId = trackId
        viewModelScope.launch {
            val now = clock()
            val last = lastRefreshAt[trackId]
            if (last != null && now - last < REFRESH_COOLDOWN_MS) {
                _state.update { it.copy(actionMessage = "Обновление уже выполняется") }
                return@launch
            }
            lastRefreshAt[trackId] = now
            val current = _state.value.top10
            if (current is Top10LoadState.Data || current is Top10LoadState.Empty) {
                _state.update {
                    it.copy(
                        top10 = updateRefreshState(current, isRefreshing = true, lastError = null),
                        actionMessage = null,
                        offersError = null,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        top10 = Top10LoadState.Loading,
                        actionMessage = null,
                        freshness = null,
                        offersError = null,
                    )
                }
            }
            runCatching { refreshTop10(trackId) }
                .onSuccess { top10 ->
                    val freshness = computeFreshness(top10, clock())
                    val next = buildContentState(top10, freshness, isRefreshing = false, lastError = null)
                    _state.update { it.copy(top10 = next, freshness = freshness, isOffline = false) }
                    startTicker()
                    loadOffers(trackId = trackId, reset = true)
                }
                .onFailure { e ->
                    val message = e.message ?: "Не удалось обновить"
                    val previous = _state.value.top10
                    if (previous is Top10LoadState.Data || previous is Top10LoadState.Empty) {
                        _state.update {
                            it.copy(
                                top10 = updateRefreshState(previous, isRefreshing = false, lastError = message),
                                isOffline = isOfflineError(e),
                                actionMessage = "Не удалось обновить. Показан последний кеш",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                top10 = Top10LoadState.FatalError(message),
                                isOffline = isOfflineError(e),
                            )
                        }
                    }
                }
        }
    }

    fun setOffersSort(trackId: String, sort: TrackOfferSort) {
        if (_state.value.offersSort == sort) return
        _state.update { it.copy(offersSort = sort, offers = emptyList(), offersCanLoadMore = false) }
        loadOffers(trackId = trackId, reset = true)
    }

    fun loadMoreOffers(trackId: String) {
        val state = _state.value
        if (state.offersLoading || !state.offersCanLoadMore) return
        loadOffers(trackId = trackId, reset = false)
    }

    fun consumeActionMessage() {
        _state.update { it.copy(actionMessage = null) }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        tickerJob = null
        super.onCleared()
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(TICK_INTERVAL_MS)
                val current = _state.value
                val snapshot = extractTop10(current.top10) ?: return@launch
                val freshness = computeFreshness(snapshot, clock())
                _state.update { it.copy(freshness = freshness) }

                if (freshness.state == FreshnessState.EXPIRED) {
                    val trackId = activeTrackId
                    val isRefreshing = when (val state = current.top10) {
                        is Top10LoadState.Data -> state.isRefreshing
                        is Top10LoadState.Empty -> state.isRefreshing
                        else -> false
                    }
                    if (trackId != null && !current.isOffline && !isRefreshing) {
                        val now = clock()
                        if (now - lastAutoRefreshAt >= AUTO_REFRESH_COOLDOWN_MS) {
                            lastAutoRefreshAt = now
                            refresh(trackId)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun computeFreshness(top10: com.example.shoppingassistant.domain.tracks.TrackTop10, now: Long): Freshness {
        val ttlSec = top10.sourceStamps.map { it.ttlSec }.maxOrNull() ?: DEFAULT_TTL_SEC
        val ageSec = max(0, ((now - top10.computedAt) / 1000L).toInt())
        val state = when {
            ageSec <= ttlSec -> FreshnessState.FRESH
            ageSec <= ttlSec * 2 -> FreshnessState.STALE
            else -> FreshnessState.EXPIRED
        }
        return Freshness(
            computedAt = top10.computedAt,
            ageSec = ageSec,
            ttlSec = ttlSec,
            state = state,
        )
    }

    private fun isOfflineError(error: Throwable): Boolean =
        error is IOException

    private fun loadOffers(trackId: String, reset: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            val offset = if (reset) 0 else current.offers.size
            _state.update {
                it.copy(
                    offersLoading = true,
                    offersError = null,
                    offers = if (reset) emptyList() else it.offers,
                    offersCanLoadMore = if (reset) false else it.offersCanLoadMore,
                )
            }
            runCatching {
                getTrackOffersPage(
                    trackId = trackId,
                    limit = OFFERS_PAGE_SIZE,
                    offset = offset,
                    sort = _state.value.offersSort,
                )
            }.onSuccess { page ->
                _state.update { state ->
                    val merged = if (reset) page.items else (state.offers + page.items)
                    state.copy(
                        offers = merged,
                        offersLoading = false,
                        offersCanLoadMore = page.canLoadMore,
                        offersError = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        offersLoading = false,
                        offersCanLoadMore = false,
                        offersError = error.message ?: "Не удалось загрузить предложения",
                    )
                }
            }
        }
    }

    private fun markAllOnOpen(trackId: String) {
        viewModelScope.launch {
            runCatching { markAllEventsRead(trackId) }
                .onSuccess { updated ->
                    if (updated > 0) {
                        val refreshed = runCatching { trackRepository.getTrack(trackId) }.getOrNull()
                        if (refreshed != null) {
                            _state.update { it.copy(track = refreshed) }
                        }
                    }
                }
        }
    }

    private fun buildContentState(
        top10: com.example.shoppingassistant.domain.tracks.TrackTop10,
        freshness: Freshness,
        isRefreshing: Boolean,
        lastError: String?,
    ): Top10LoadState {
        val isStale = freshness.state != FreshnessState.FRESH
        return if (top10.items.isEmpty()) {
            Top10LoadState.Empty(
                top10 = top10,
                isStale = isStale,
                isRefreshing = isRefreshing,
                lastError = lastError,
            )
        } else {
            Top10LoadState.Data(
                top10 = top10,
                isStale = isStale,
                isRefreshing = isRefreshing,
                lastError = lastError,
            )
        }
    }

    private fun updateRefreshState(
        current: Top10LoadState,
        isRefreshing: Boolean,
        lastError: String?,
    ): Top10LoadState {
        return when (current) {
            is Top10LoadState.Data -> current.copy(isRefreshing = isRefreshing, lastError = lastError)
            is Top10LoadState.Empty -> current.copy(isRefreshing = isRefreshing, lastError = lastError)
            else -> current
        }
    }

    private fun extractTop10(state: Top10LoadState): com.example.shoppingassistant.domain.tracks.TrackTop10? {
        return when (state) {
            is Top10LoadState.Data -> state.top10
            is Top10LoadState.Empty -> state.top10
            else -> null
        }
    }
}
