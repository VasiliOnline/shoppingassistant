package com.example.shoppingassistant.feature.pages.trackeditems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.domain.tracks.GetTrackFilterOptionsTask
import com.example.shoppingassistant.domain.tracks.TrackFilterKey
import com.example.shoppingassistant.domain.tracks.TrackFilterOption
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.UpdateTrackFiltersTask
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrackEditViewModel(
    private val trackRepository: TrackRepository,
    private val updateTrackFilters: UpdateTrackFiltersTask,
    private val getTrackFilterOptions: GetTrackFilterOptionsTask,
) : ViewModel() {

    private companion object {
        const val MAX_EXTRA: Int = 5
    }

    private val _state = MutableStateFlow(TrackEditState())
    val state: StateFlow<TrackEditState> = _state.asStateFlow()

    private var optionsJob: Job? = null

    fun load(trackId: String) {
        if (trackId.isBlank()) return
        val current = _state.value
        if (current.trackId == trackId && current.load is LoadState.Content) return

        _state.update {
            it.copy(
                trackId = trackId,
                load = LoadState.Loading,
                uiMessage = null,
                blockingDialog = null,
                closeRequested = false,
                navigateToTrackId = null,
            )
        }
        viewModelScope.launch {
            runCatching { trackRepository.getTrack(trackId) }
                .onSuccess { track ->
                    val normalized = normalizeFilters(track.filters)
                    _state.update {
                        it.copy(
                            load = LoadState.Content,
                            trackTitle = track.title,
                            original = normalized,
                            edited = normalized,
                            activeCount = activeCount(normalized),
                            hasUnsavedChanges = false,
                            save = SaveState.Idle,
                        )
                    }
                }
                .onFailure { error ->
                    val mapped = TrackEditErrorMapper.fromThrowable(error)
                    val message = TrackEditErrorMapper.message(mapped, "Не удалось загрузить фильтры")
                    _state.update {
                        it.copy(
                            load = LoadState.Error(message = message, canRetry = true),
                        )
                    }
                }
        }
    }

    fun retryLoad() {
        val trackId = _state.value.trackId
        if (trackId.isBlank()) return
        load(trackId)
    }

    fun openSheet(key: TrackFilterKey, extraKey: String? = null) {
        if (key == TrackFilterKey.EXTRA_VALUE && extraKey.isNullOrBlank()) return
        val selected = when (key) {
            TrackFilterKey.REGION -> _state.value.edited.region
            TrackFilterKey.DELIVERY -> _state.value.edited.delivery
            TrackFilterKey.CONDITION -> _state.value.edited.condition
            TrackFilterKey.SELLER -> _state.value.edited.seller
            TrackFilterKey.EXTRA_KEY -> null
            TrackFilterKey.EXTRA_VALUE -> extraKey?.let { _state.value.edited.extra[it] }
        }
        val sheet = SheetOptionsState(
            key = key,
            isLoading = true,
            extraKey = extraKey,
            selectedId = selected,
            customInput = selected.orEmpty(),
        )
        _state.update { it.copy(sheetState = sheet) }
        loadSheetOptions(key, query = null, extraKey = extraKey)
    }

    fun closeSheet() {
        optionsJob?.cancel()
        optionsJob = null
        _state.update { it.copy(sheetState = null) }
    }

    fun onSheetQueryChanged(query: String) {
        val sheet = _state.value.sheetState ?: return
        _state.update { it.copy(sheetState = sheet.copy(query = query, errorMessage = null)) }
        optionsJob?.cancel()
        optionsJob = viewModelScope.launch {
            delay(200)
            loadSheetOptions(sheet.key, query, sheet.extraKey)
        }
    }

    fun retryOptions() {
        val sheet = _state.value.sheetState ?: return
        loadSheetOptions(sheet.key, sheet.query, sheet.extraKey)
    }

    fun onOptionSelected(option: TrackFilterOption) {
        val sheet = _state.value.sheetState ?: return
        val value = option.payload ?: option.label
        when (sheet.key) {
            TrackFilterKey.REGION -> {
                updateRegion(value)
                closeSheet()
            }
            TrackFilterKey.DELIVERY -> {
                updateDelivery(value)
                closeSheet()
            }
            TrackFilterKey.CONDITION -> {
                updateCondition(value)
                closeSheet()
            }
            TrackFilterKey.SELLER -> {
                updateSeller(value)
                closeSheet()
            }
            TrackFilterKey.EXTRA_KEY -> {
                val trimmedKey = value.trim()
                if (trimmedKey.isBlank()) return
                val current = _state.value.edited.extra
                val exists = current.containsKey(trimmedKey)
                if (!exists && current.size >= MAX_EXTRA) {
                    _state.update {
                        it.copy(uiMessage = UiMessage(text = "Можно добавить не более $MAX_EXTRA параметров"))
                    }
                    return
                }
                openSheet(TrackFilterKey.EXTRA_VALUE, extraKey = trimmedKey)
            }
            TrackFilterKey.EXTRA_VALUE -> {
                val extra = sheet.extraKey ?: return
                updateExtra(extra, value)
                closeSheet()
            }
        }
    }

    fun onCustomInputChanged(value: String) {
        val sheet = _state.value.sheetState ?: return
        _state.update { it.copy(sheetState = sheet.copy(customInput = value)) }
    }

    fun applyCustomInput() {
        val sheet = _state.value.sheetState ?: return
        val value = sheet.customInput.trim()
        if (value.isBlank()) return
        when (sheet.key) {
            TrackFilterKey.REGION -> updateRegion(value)
            TrackFilterKey.DELIVERY -> updateDelivery(value)
            TrackFilterKey.CONDITION -> updateCondition(value)
            TrackFilterKey.SELLER -> updateSeller(value)
            TrackFilterKey.EXTRA_KEY -> {
                val current = _state.value.edited.extra
                val exists = current.containsKey(value)
                if (!exists && current.size >= MAX_EXTRA) {
                    _state.update {
                        it.copy(uiMessage = UiMessage(text = "Можно добавить не более $MAX_EXTRA параметров"))
                    }
                    return
                }
                openSheet(TrackFilterKey.EXTRA_VALUE, extraKey = value)
                return
            }
            TrackFilterKey.EXTRA_VALUE -> {
                val extra = sheet.extraKey ?: return
                updateExtra(extra, value)
            }
        }
        closeSheet()
    }

    fun clearSelection() {
        val sheet = _state.value.sheetState ?: return
        when (sheet.key) {
            TrackFilterKey.REGION -> updateRegion(null)
            TrackFilterKey.DELIVERY -> updateDelivery(null)
            TrackFilterKey.CONDITION -> updateCondition(null)
            TrackFilterKey.SELLER -> updateSeller(null)
            TrackFilterKey.EXTRA_KEY -> Unit
            TrackFilterKey.EXTRA_VALUE -> {
                val extra = sheet.extraKey ?: return
                removeExtra(extra)
            }
        }
        closeSheet()
    }

    fun updateRegion(value: String?) {
        updateEdited(_state.value.edited.copy(region = value))
    }

    fun updateDelivery(value: String?) {
        updateEdited(_state.value.edited.copy(delivery = value))
    }

    fun updateCondition(value: String?) {
        updateEdited(_state.value.edited.copy(condition = value))
    }

    fun updateSeller(value: String?) {
        updateEdited(_state.value.edited.copy(seller = value))
    }

    fun updateExtra(key: String, value: String) {
        val trimmedKey = key.trim()
        val trimmedValue = value.trim()
        if (trimmedKey.isBlank()) return

        val current = _state.value.edited.extra.toMutableMap()
        val exists = current.containsKey(trimmedKey)
        if (!exists && current.size >= MAX_EXTRA) {
            _state.update { it.copy(uiMessage = UiMessage(text = "Можно добавить не более $MAX_EXTRA параметров")) }
            return
        }
        if (trimmedValue.isBlank()) {
            current.remove(trimmedKey)
        } else {
            current[trimmedKey] = trimmedValue
        }
        updateEdited(_state.value.edited.copy(extra = current))
    }

    fun removeExtra(key: String) {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return
        val current = _state.value.edited.extra.toMutableMap()
        current.remove(trimmedKey)
        updateEdited(_state.value.edited.copy(extra = current))
    }

    fun resetAll() {
        updateEdited(TrackFilters())
    }

    fun apply() {
        val current = _state.value
        if (current.save is SaveState.Saving || !current.hasUnsavedChanges) return
        val trackId = current.trackId
        if (trackId.isBlank()) return

        val normalized = normalizeFilters(current.edited)
        _state.update { it.copy(save = SaveState.Saving, uiMessage = null) }

        viewModelScope.launch {
            runCatching { updateTrackFilters(trackId, normalized) }
                .onSuccess { updated ->
                    val savedFilters = normalizeFilters(updated.filters)
                    val nextActiveCount = activeCount(savedFilters)
                    if (updated.id != trackId) {
                        _state.update {
                            it.copy(
                                save = SaveState.Idle,
                                original = savedFilters,
                                edited = savedFilters,
                                activeCount = nextActiveCount,
                                hasUnsavedChanges = false,
                                blockingDialog = BlockingDialog.DedupConflict(
                                    existingTrackId = updated.id,
                                    message = "Такой трек уже существует",
                                ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                save = SaveState.Saved(System.currentTimeMillis()),
                                original = savedFilters,
                                edited = savedFilters,
                                activeCount = nextActiveCount,
                                hasUnsavedChanges = false,
                                closeRequested = true,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    val mapped = TrackEditErrorMapper.fromThrowable(error)
                    val message = TrackEditErrorMapper.message(mapped, "Не удалось сохранить фильтры")
                    _state.update {
                        it.copy(
                            save = SaveState.Error(message),
                            uiMessage = UiMessage(text = message, actionLabel = "Повторить", action = UiAction.RetrySave),
                        )
                    }
                }
        }
    }

    fun onBackPressed() {
        val current = _state.value
        if (current.save is SaveState.Saving) {
            _state.update { it.copy(uiMessage = UiMessage(text = "Сохраняем… подождите")) }
            return
        }
        if (current.sheetState != null) {
            closeSheet()
            return
        }
        if (current.hasUnsavedChanges) {
            _state.update { it.copy(blockingDialog = BlockingDialog.ConfirmDiscard) }
        } else {
            _state.update { it.copy(closeRequested = true) }
        }
    }

    fun confirmDiscard() {
        _state.update { it.copy(blockingDialog = null, closeRequested = true) }
    }

    fun cancelDiscard() {
        _state.update { it.copy(blockingDialog = null) }
    }

    fun consumeUiMessage() {
        _state.update { it.copy(uiMessage = null) }
    }

    fun consumeCloseRequest() {
        _state.update { it.copy(closeRequested = false) }
    }

    fun navigateToTrack(trackId: String) {
        _state.update { it.copy(navigateToTrackId = trackId, blockingDialog = null) }
    }

    fun consumeNavigateToTrack() {
        _state.update { it.copy(navigateToTrackId = null) }
    }

    private fun updateEdited(filters: TrackFilters) {
        val normalized = normalizeFilters(filters)
        val original = _state.value.original
        _state.update {
            it.copy(
                edited = normalized,
                activeCount = activeCount(normalized),
                hasUnsavedChanges = normalized != original,
            )
        }
    }

    private fun normalizeFilters(filters: TrackFilters): TrackFilters {
        val region = normalizeValue(filters.region)
        val delivery = normalizeValue(filters.delivery)
        val condition = normalizeValue(filters.condition)
        val seller = normalizeValue(filters.seller)
        val extra = normalizeExtra(filters.extra)
        return TrackFilters(
            region = region,
            delivery = delivery,
            condition = condition,
            seller = seller,
            extra = extra,
        )
    }

    private fun normalizeValue(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        return trimmed.takeIf { it.isNotBlank() }
    }

    private fun normalizeExtra(extra: Map<String, String>): Map<String, String> {
        if (extra.isEmpty()) return emptyMap()
        val cleaned = extra.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .sortedBy { it.first.lowercase() }
            .take(MAX_EXTRA)
        if (cleaned.isEmpty()) return emptyMap()
        return LinkedHashMap<String, String>().apply {
            cleaned.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun activeCount(filters: TrackFilters): Int {
        val base = listOf(
            filters.region,
            filters.delivery,
            filters.condition,
            filters.seller,
        ).count { !it.isNullOrBlank() }
        return base + filters.extra.size
    }

    private fun loadSheetOptions(
        key: TrackFilterKey,
        query: String?,
        extraKey: String?,
    ) {
        val trackId = _state.value.trackId
        if (trackId.isBlank()) return
        _state.update { state ->
            val sheet = state.sheetState ?: return@update state
            if (sheet.key != key || sheet.extraKey != extraKey) return@update state
            state.copy(sheetState = sheet.copy(isLoading = true, errorMessage = null))
        }
        viewModelScope.launch {
            runCatching { getTrackFilterOptions(trackId, key, query, extraKey) }
                .onSuccess { bundle ->
                    _state.update { state ->
                        val sheet = state.sheetState ?: return@update state
                        if (sheet.key != key || sheet.extraKey != extraKey) return@update state
                        state.copy(
                            sheetState = sheet.copy(
                                isLoading = false,
                                options = bundle.options,
                                allowCustom = bundle.allowCustom,
                                allowEmpty = bundle.allowEmpty,
                                searchEnabled = bundle.searchEnabled,
                                errorMessage = null,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    val mapped = TrackEditErrorMapper.fromThrowable(error)
                    val message = TrackEditErrorMapper.message(mapped, "Не удалось загрузить варианты")
                    _state.update { state ->
                        val sheet = state.sheetState ?: return@update state
                        if (sheet.key != key || sheet.extraKey != extraKey) return@update state
                        state.copy(
                            sheetState = sheet.copy(
                                isLoading = false,
                                errorMessage = message,
                            ),
                        )
                    }
                }
        }
    }
}
