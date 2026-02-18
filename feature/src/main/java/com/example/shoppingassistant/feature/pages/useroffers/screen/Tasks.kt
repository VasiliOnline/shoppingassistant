package com.example.shoppingassistant.feature.pages.useroffers.screen

import com.example.shoppingassistant.feature.pages.useroffers.UserOffersLoadState

enum class UserOffersScreenMode {
    LOADING,
    CONTENT,
    EMPTY,
    UNAUTHORIZED,
}

enum class UserOffersEmptyReason {
    NO_ITEMS,
    FILTERED,
}

sealed interface UserOffersScreenIssue {
    val message: String

    data class Offline(override val message: String) : UserOffersScreenIssue
    data class Error(override val message: String) : UserOffersScreenIssue
}

data class UserOffersScreenState(
    val mode: UserOffersScreenMode,
    val emptyReason: UserOffersEmptyReason? = null,
    val issue: UserOffersScreenIssue? = null,
)

interface UserOffersScreenStateResolverTask {
    fun resolve(
        loadState: UserOffersLoadState,
        filteredCount: Int,
        totalCount: Int,
        hasFilters: Boolean,
    ): UserOffersScreenState
}
