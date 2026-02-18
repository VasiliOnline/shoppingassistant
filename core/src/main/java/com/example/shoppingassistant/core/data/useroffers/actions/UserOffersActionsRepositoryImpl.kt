package com.example.shoppingassistant.core.data.useroffers.actions

import com.example.shoppingassistant.domain.useroffers.UserOfferActionRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferActionResult
import com.example.shoppingassistant.domain.useroffers.UserOffersActionsRepository

class UserOffersActionsRepositoryImpl(
    private val remoteDataSource: UserOffersActionsRemoteDataSource,
) : UserOffersActionsRepository {
    override suspend fun performAction(request: UserOfferActionRequest): UserOfferActionResult =
        remoteDataSource.performAction(request)
}
