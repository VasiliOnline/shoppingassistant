package com.example.shoppingassistant.feature.pages.main.state

import com.example.shoppingassistant.domain.visualsearch.VisualSearchBinderStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryActionType
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.feature.pages.results.ResultsPayload

internal const val VISUAL_SEARCH_MAX_CAPTURE_ASSETS: Int = 3

enum class VisualSearchSessionStep {
    Hidden,
    Source,
    Review,
    Recovery,
}

data class VisualSearchAssetUi(
    val fingerprint: String,
    val localUri: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val byteSize: Long? = null,
    val inlineBase64: String? = null,
)

data class VisualSearchRecoveryActionUi(
    val type: VisualSearchRecoveryActionType,
    val label: String,
)

data class VisualSearchRegionUi(
    val label: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class VisualSearchPreflightUi(
    val signals: VisualSearchPreflightSignals,
    val summary: String,
    val hintLabels: List<String> = emptyList(),
)

data class VisualSearchInsightUi(
    val title: String? = null,
    val subtitle: String? = null,
    val suggestedCategoryCode: String? = null,
    val suggestedCategoryTitle: String? = null,
    val promoteSuggestedCategory: Boolean = false,
    val suggestedRegion: VisualSearchRegionUi? = null,
    val barcodeValue: String? = null,
    val recognizedText: String? = null,
    val imageLabelHints: List<String> = emptyList(),
    val objectLabel: String? = null,
    val objectConfidence: Float? = null,
    val hintLabels: List<String> = emptyList(),
)

data class VisualSearchSessionState(
    val visible: Boolean = false,
    val sessionId: String? = null,
    val step: VisualSearchSessionStep = VisualSearchSessionStep.Hidden,
    val source: VisualSearchSource? = null,
    val asset: VisualSearchAssetUi? = null,
    val capturedAssets: List<VisualSearchAssetUi> = emptyList(),
    val captureMode: VisualSearchCaptureMode = VisualSearchCaptureMode.IMAGE,
    val intent: VisualSearchIntent = VisualSearchIntent.IDENTIFY_FIRST,
    val selectionMode: VisualSearchSelectionMode = VisualSearchSelectionMode.AUTO_TARGET,
    val selectedRegion: VisualSearchRegionUi? = null,
    val selectedCategoryCode: String? = null,
    val selectedCategoryTitle: String? = null,
    val insight: VisualSearchInsightUi? = null,
    val preflight: VisualSearchPreflightUi? = null,
    val binderStatus: VisualSearchBinderStatus? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val recoveryMessage: String? = null,
    val recoveryActions: List<VisualSearchRecoveryActionUi> = emptyList(),
    val pendingResultsPayload: ResultsPayload? = null,
) {
    val canSubmit: Boolean
        get() = (asset != null || capturedAssets.isNotEmpty()) && !isSubmitting
}
