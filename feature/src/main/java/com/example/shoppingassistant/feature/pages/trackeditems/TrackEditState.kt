package com.example.shoppingassistant.feature.pages.trackeditems

import com.example.shoppingassistant.domain.tracks.TrackFilterKey
import com.example.shoppingassistant.domain.tracks.TrackFilterOption
import com.example.shoppingassistant.domain.tracks.TrackFilters

sealed interface LoadState {
    data object Loading : LoadState
    data object Content : LoadState
    data class Error(val message: String, val canRetry: Boolean) : LoadState
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Error(val message: String) : SaveState
    data class Saved(val at: Long) : SaveState
}

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: UiAction? = null,
)

sealed interface UiAction {
    data object RetrySave : UiAction
    data object RetryLoad : UiAction
}

sealed interface BlockingDialog {
    data object ConfirmDiscard : BlockingDialog
    data class DedupConflict(val existingTrackId: String, val message: String) : BlockingDialog
}

data class SheetOptionsState(
    val key: TrackFilterKey,
    val query: String = "",
    val isLoading: Boolean = false,
    val options: List<TrackFilterOption> = emptyList(),
    val allowCustom: Boolean = false,
    val allowEmpty: Boolean = true,
    val searchEnabled: Boolean = false,
    val errorMessage: String? = null,
    val extraKey: String? = null,
    val selectedId: String? = null,
    val customInput: String = "",
)

data class TrackEditState(
    val trackId: String = "",
    val load: LoadState = LoadState.Loading,
    val save: SaveState = SaveState.Idle,
    val trackTitle: String = "",
    val original: TrackFilters = TrackFilters(),
    val edited: TrackFilters = TrackFilters(),
    val activeCount: Int = 0,
    val hasUnsavedChanges: Boolean = false,
    val sheetState: SheetOptionsState? = null,
    val uiMessage: UiMessage? = null,
    val blockingDialog: BlockingDialog? = null,
    val closeRequested: Boolean = false,
    val navigateToTrackId: String? = null,
)
