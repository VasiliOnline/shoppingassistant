package com.example.shoppingassistant.core.data.useroffers

import com.example.shoppingassistant.domain.useroffers.UserOffersPage
import com.example.shoppingassistant.domain.useroffers.UserOffersQuery
import com.example.shoppingassistant.domain.useroffers.UserOffersRepository

class UserOffersRepositoryImpl(
    private val remoteDataSource: UserOffersRemoteDataSource,
) : UserOffersRepository {
    override suspend fun list(query: UserOffersQuery): UserOffersPage =
        remoteDataSource.list(query)
}
