package com.example.shoppingassistant.core.data.useroffers.actions

import com.example.shoppingassistant.domain.useroffers.UserOfferActionRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferActionResult

interface UserOffersActionsRemoteDataSource {
    suspend fun performAction(
        request: UserOfferActionRequest,
        bearerToken: String? = null,
    ): UserOfferActionResult
}
