package com.example.shoppingassistant.core.data.useroffers.price

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkResult
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateResult
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class UserOffersPriceRemoteDataSourceImpl(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : UserOffersPriceRemoteDataSource {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun updatePrice(
        request: UserOfferPriceUpdateRequest,
        bearerToken: String?,
    ): UserOfferPriceUpdateResult {
        val token = bearerToken ?: authRepository.currentToken()
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/user/offers/price") {
            contentType(ContentType.Application.Json)
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
            setBody(request)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> response.body()
        }
    }

    override suspend fun updatePrices(
        request: UserOfferPriceBulkRequest,
        bearerToken: String?,
    ): UserOfferPriceBulkResult {
        val token = bearerToken ?: authRepository.currentToken()
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/user/offers/price/bulk") {
            contentType(ContentType.Application.Json)
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
            setBody(request)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> response.body()
        }
    }

    private fun ensureBearer(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"
}
