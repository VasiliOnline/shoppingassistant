package com.example.shoppingassistant.core.data.useroffers.price

import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkResult
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateResult
import com.example.shoppingassistant.domain.useroffers.UserOffersPriceRepository

class UserOffersPriceRepositoryImpl(
    private val remoteDataSource: UserOffersPriceRemoteDataSource,
) : UserOffersPriceRepository {
    override suspend fun updatePrice(request: UserOfferPriceUpdateRequest): UserOfferPriceUpdateResult =
        remoteDataSource.updatePrice(request)

    override suspend fun updatePrices(request: UserOfferPriceBulkRequest): UserOfferPriceBulkResult =
        remoteDataSource.updatePrices(request)
}
