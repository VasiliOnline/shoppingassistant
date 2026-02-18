package com.example.shoppingassistant.core.data.useroffers

import com.example.shoppingassistant.domain.useroffers.UserOffersPage
import com.example.shoppingassistant.domain.useroffers.UserOffersQuery

interface UserOffersRemoteDataSource {
    suspend fun list(query: UserOffersQuery, bearerToken: String? = null): UserOffersPage
}
