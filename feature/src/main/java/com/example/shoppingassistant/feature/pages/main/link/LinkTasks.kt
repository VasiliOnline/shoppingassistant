package com.example.shoppingassistant.feature.pages.main.link

import com.example.shoppingassistant.domain.ingest.RawOffer
import com.example.shoppingassistant.domain.ingest.SourceType

/**
 * Контракты UI-флоу «создать и отслеживать по ссылке».
 */
data class LinkInputState(
    val url: String = "",
    val isValid: Boolean = false,
)

data class LinkLoadingStep(
    val title: String,
    val done: Boolean = false,
)

sealed interface LinkSheetState {
    data object Input : LinkSheetState
    data class Loading(val steps: List<LinkLoadingStep>) : LinkSheetState
    data class Error(val message: String) : LinkSheetState
}

data class LinkDraft(
    val url: String,
    val source: SourceType,
    val raw: RawOffer,
    val canTrackPrice: Boolean,
    val brand: String?,
    val model: String?,
    val category: String?,
    val price: Double?,
    val currency: String?,
    val attributes: List<Pair<String, String>>,
    val thumbUrl: String?,
)
