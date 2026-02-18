package com.example.shoppingassistant.server.useroffers

import com.example.shoppingassistant.domain.useroffers.UserOffersPage
import com.example.shoppingassistant.domain.useroffers.UserOffersQuery

interface UserOffersBackendRepository {
    suspend fun listUserOffers(userId: Long, query: UserOffersQuery): UserOffersPage
}
