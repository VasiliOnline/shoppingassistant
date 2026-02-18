package com.example.shoppingassistant.core.data.useroffers.price

import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkResult
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateResult

interface UserOffersPriceRemoteDataSource {
    suspend fun updatePrice(
        request: UserOfferPriceUpdateRequest,
        bearerToken: String? = null,
    ): UserOfferPriceUpdateResult

    suspend fun updatePrices(
        request: UserOfferPriceBulkRequest,
        bearerToken: String? = null,
    ): UserOfferPriceBulkResult
}
