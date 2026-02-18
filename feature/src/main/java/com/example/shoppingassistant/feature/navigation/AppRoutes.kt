package com.example.shoppingassistant.feature.navigation

import com.example.shoppingassistant.feature.pages.useroffers.UserOffersTab

object AppRoutes {
    const val Main = "main"
    const val ArgOpenSearchHub = "openSearchHub"
    const val MainRoute = "$Main?$ArgOpenSearchHub={$ArgOpenSearchHub}"
    const val Ingest = "ingest"
    const val Profile = "profile"
    const val FeedCategories = "feedCategories"

    const val TrackedItems = "tracked_items"
    const val ArgTrackId = "trackId"
    const val TrackedItemsTop10 = "tracked_items/{$ArgTrackId}/top10"
    const val TrackedItemsEdit = "tracked_items/{$ArgTrackId}/edit"
    const val TrackedItemsEvents = "tracked_items/{$ArgTrackId}/events"

    const val MyItemsRoute = "myItems"
    const val ArgMyItemsTab = "tab"

    fun myItems(tab: UserOffersTab? = null): String {
        return if (tab == null) {
            MyItemsRoute
        } else {
            "$MyItemsRoute?$ArgMyItemsTab=${tab.name}"
        }
    }

    fun mainWithSearchHub(expanded: Boolean = true): String {
        val value = if (expanded) "1" else "0"
        return "$Main?$ArgOpenSearchHub=$value"
    }

    fun trackedItemsTop10(trackId: String): String = "tracked_items/$trackId/top10"
    fun trackedItemsEdit(trackId: String): String = "tracked_items/$trackId/edit"
    fun trackedItemsEvents(trackId: String): String = "tracked_items/$trackId/events"
}
