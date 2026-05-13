package com.example.shoppingassistant.feature.navigation

import android.net.Uri
import com.example.shoppingassistant.feature.pages.useroffers.UserOffersTab

object AppRoutes {
    const val Main = "main"
    const val ArgOpenSearchHub = "openSearchHub"
    const val MainRoute = "$Main?$ArgOpenSearchHub={$ArgOpenSearchHub}"
    const val Ingest = "ingest"
    const val Profile = "profile"
    const val ArgProfileUserId = "userId"
    const val ProfileRoute = "$Profile?$ArgProfileUserId={$ArgProfileUserId}"
    const val FeedCategories = "feedCategories"

    const val TrackedItems = "tracked_items"
    const val ArgTrackId = "trackId"
    const val TrackedItemsTop10 = "tracked_items/{$ArgTrackId}/top10"
    const val TrackedItemsEdit = "tracked_items/{$ArgTrackId}/edit"
    const val TrackedItemsEvents = "tracked_items/{$ArgTrackId}/events"

    const val Chat = "chat"
    const val Offer = "offer"
    const val ArgOfferId = "offerId"
    const val ArgSellerName = "sellerName"
    const val ArgOfferTitle = "offerTitle"
    const val ArgPrice = "price"
    const val ArgStatus = "status"
    const val ArgExternalUrl = "externalUrl"
    const val ArgRedirectUrl = "redirectUrl"
    const val ArgDeeplinkUrl = "deeplinkUrl"
    const val ArgSourceName = "sourceName"
    const val ArgQuerySessionId = "querySessionId"
    const val ArgPosition = "position"
    const val OfferRoute =
        "$Offer?" +
            "$ArgOfferId={$ArgOfferId}&" +
            "$ArgQuerySessionId={$ArgQuerySessionId}&" +
            "$ArgPosition={$ArgPosition}"
    const val ChatRoute =
        "$Chat?" +
            "$ArgOfferId={$ArgOfferId}&" +
            "$ArgSellerName={$ArgSellerName}&" +
            "$ArgOfferTitle={$ArgOfferTitle}&" +
            "$ArgPrice={$ArgPrice}&" +
            "$ArgStatus={$ArgStatus}&" +
            "$ArgExternalUrl={$ArgExternalUrl}&" +
            "$ArgRedirectUrl={$ArgRedirectUrl}&" +
            "$ArgDeeplinkUrl={$ArgDeeplinkUrl}&" +
            "$ArgSourceName={$ArgSourceName}&" +
            "$ArgQuerySessionId={$ArgQuerySessionId}&" +
            "$ArgPosition={$ArgPosition}"

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
    fun profile(userId: Long? = null): String =
        if (userId == null) {
            Profile
        } else {
            "$Profile?$ArgProfileUserId=$userId"
        }

    fun offer(
        offerId: String,
        querySessionId: String? = null,
        position: Int? = null,
    ): String = buildString {
        append("$Offer?")
        append("$ArgOfferId=${Uri.encode(offerId)}")
        append("&$ArgQuerySessionId=${Uri.encode(querySessionId.orEmpty())}")
        append("&$ArgPosition=${position ?: ""}")
    }

    fun chat(
        offerId: String,
        sellerName: String,
        offerTitle: String,
        price: String?,
        status: String?,
        externalUrl: String?,
        redirectUrl: String?,
        deeplinkUrl: String?,
        sourceName: String?,
        querySessionId: String?,
        position: Int?,
    ): String = buildString {
        append("$Chat?")
        append("$ArgOfferId=${Uri.encode(offerId)}")
        append("&$ArgSellerName=${Uri.encode(sellerName)}")
        append("&$ArgOfferTitle=${Uri.encode(offerTitle)}")
        append("&$ArgPrice=${Uri.encode(price.orEmpty())}")
        append("&$ArgStatus=${Uri.encode(status.orEmpty())}")
        append("&$ArgExternalUrl=${Uri.encode(externalUrl.orEmpty())}")
        append("&$ArgRedirectUrl=${Uri.encode(redirectUrl.orEmpty())}")
        append("&$ArgDeeplinkUrl=${Uri.encode(deeplinkUrl.orEmpty())}")
        append("&$ArgSourceName=${Uri.encode(sourceName.orEmpty())}")
        append("&$ArgQuerySessionId=${Uri.encode(querySessionId.orEmpty())}")
        append("&$ArgPosition=${position ?: ""}")
    }
}
