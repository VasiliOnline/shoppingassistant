package com.example.shoppingassistant.core.data.useroffers

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.useroffers.UserOffersPage
import com.example.shoppingassistant.domain.useroffers.UserOffersQuery
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class UserOffersRemoteDataSourceImpl(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : UserOffersRemoteDataSource {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun list(query: UserOffersQuery, bearerToken: String?): UserOffersPage {
        val token = bearerToken ?: authRepository.currentToken()
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/user/offers/list") {
            contentType(ContentType.Application.Json)
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
            setBody(query)
        }

        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    private fun ensureBearer(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"
}
