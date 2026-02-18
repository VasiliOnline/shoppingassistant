package com.example.shoppingassistant.server.useroffers.actions

import com.example.shoppingassistant.domain.useroffers.UserOfferActionRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferActionResult

interface UserOffersActionsService {
    suspend fun performAction(userId: Long, request: UserOfferActionRequest): UserOfferActionResult
}
