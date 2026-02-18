package com.example.shoppingassistant.feature.pages.useroffers.price

import com.example.shoppingassistant.domain.useroffers.UserOfferActionStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi

data class UserOfferPriceUpdateResult(
    val offerId: String,
    val status: UserOfferActionStatus,
    val updatedOffer: UserOfferCardUi? = null,
    val message: String? = null,
)

data class UserOfferPriceBulkResult(
    val results: List<UserOfferPriceUpdateResult>,
    val succeeded: Int,
    val failed: Int,
)

interface UserOffersPriceTask {
    suspend fun updatePrice(offer: UserOfferCardUi, priceMajor: Double): UserOfferPriceUpdateResult
    suspend fun updatePrices(offers: List<UserOfferCardUi>, priceMajor: Double): UserOfferPriceBulkResult
}
