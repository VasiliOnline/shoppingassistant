package com.example.shoppingassistant.feature.pages.main.link

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
