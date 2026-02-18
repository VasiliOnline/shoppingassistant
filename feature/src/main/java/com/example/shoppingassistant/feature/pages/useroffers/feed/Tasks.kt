package com.example.shoppingassistant.feature.pages.useroffers.feed

import com.example.shoppingassistant.domain.useroffers.UserOffersQuery
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi

data class UserOffersFeedPage(
    val items: List<UserOfferCardUi>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

interface UserOffersFeedTask {
    suspend fun loadPage(query: UserOffersQuery): UserOffersFeedPage
}
