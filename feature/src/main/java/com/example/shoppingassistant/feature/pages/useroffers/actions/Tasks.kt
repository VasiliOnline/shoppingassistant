package com.example.shoppingassistant.feature.pages.useroffers.actions

import com.example.shoppingassistant.domain.useroffers.UserOfferActionStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferAction
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi

data class UserOffersActionResult(
    val status: UserOfferActionStatus,
    val updatedOffer: UserOfferCardUi? = null,
    val newOffer: UserOfferCardUi? = null,
    val message: String? = null,
)

interface UserOffersActionsTask {
    suspend fun perform(action: UserOfferAction, offer: UserOfferCardUi): UserOffersActionResult
}
