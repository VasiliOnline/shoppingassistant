package com.example.shoppingassistant.feature.pages.useroffers.screen

import com.example.shoppingassistant.feature.pages.useroffers.UserOffersLoadState

internal class UserOffersScreenStateResolverTaskImpl : UserOffersScreenStateResolverTask {
    override fun resolve(
        loadState: UserOffersLoadState,
        filteredCount: Int,
        totalCount: Int,
        hasFilters: Boolean,
    ): UserOffersScreenState {
        val issue = when {
            loadState.isOffline -> UserOffersScreenIssue.Offline(
                loadState.errorMessage ?: "Нет подключения к сети",
            )
            loadState.errorMessage != null -> UserOffersScreenIssue.Error(loadState.errorMessage)
            else -> null
        }

        if (loadState.isLoading && totalCount == 0) {
            return UserOffersScreenState(
                mode = UserOffersScreenMode.LOADING,
                issue = issue,
            )
        }

        if (!loadState.isAuthorized) {
            return UserOffersScreenState(
                mode = UserOffersScreenMode.UNAUTHORIZED,
                issue = issue,
            )
        }

        val mode = if (filteredCount > 0) UserOffersScreenMode.CONTENT else UserOffersScreenMode.EMPTY
        val emptyReason = if (mode == UserOffersScreenMode.EMPTY) {
            if (totalCount == 0) {
                UserOffersEmptyReason.NO_ITEMS
            } else if (hasFilters) {
                UserOffersEmptyReason.FILTERED
            } else {
                UserOffersEmptyReason.NO_ITEMS
            }
        } else {
            null
        }

        return UserOffersScreenState(
            mode = mode,
            emptyReason = emptyReason,
            issue = issue,
        )
    }
}

fun userOffersScreenStateResolverTask(): UserOffersScreenStateResolverTask =
    UserOffersScreenStateResolverTaskImpl()
