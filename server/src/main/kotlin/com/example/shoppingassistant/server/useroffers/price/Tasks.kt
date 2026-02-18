package com.example.shoppingassistant.server.useroffers.price

import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkResult
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateResult

interface UserOffersPriceService {
    suspend fun updatePrice(
        userId: Long,
        request: UserOfferPriceUpdateRequest,
    ): UserOfferPriceUpdateResult

    suspend fun updatePrices(
        userId: Long,
        request: UserOfferPriceBulkRequest,
    ): UserOfferPriceBulkResult
}
