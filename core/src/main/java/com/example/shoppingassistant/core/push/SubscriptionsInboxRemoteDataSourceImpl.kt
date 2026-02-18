package com.example.shoppingassistant.core.push

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class TrackingInboxRemoteDataSourceImpl(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : TrackingInboxRemoteDataSource {

    private val baseUrl get() = BackendConfig.BASE_URL

    override suspend fun listNotificationsPage(limit: Int, offset: Int): TrackingInboxPage {
        val token = authRepository.currentToken() ?: return TrackingInboxPage(
            items = emptyList(),
            limit = limit,
            offset = offset,
            canLoadMore = false,
        )

        val response: HttpResponse = backendClient.client.get("$baseUrl/api/subscriptions/notifications") {
            header("Authorization", ensureBearer(token))
            parameter("limit", limit)
            parameter("offset", offset)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return TrackingInboxPage(
                items = emptyList(),
                limit = limit,
                offset = offset,
                canLoadMore = false,
            )
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to load subscriptions inbox: HTTP ${response.status.value}")
        }

        return response.body()
    }

    override suspend fun markRead(id: Long): Boolean {
        val token = authRepository.currentToken() ?: return false
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/subscriptions/notifications/$id/read") {
            header("Authorization", ensureBearer(token))
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return false
        }

        return response.status.isSuccess()
    }

    override suspend fun markAllRead(): Int {
        val token = authRepository.currentToken() ?: return 0
        val response: HttpResponse = backendClient.client.post("$baseUrl/api/subscriptions/notifications/read-all") {
            header("Authorization", ensureBearer(token))
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            authRepository.logout()
            return 0
        }

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Failed to mark all inbox notifications: HTTP ${response.status.value}")
        }

        return response.body<MarkAllReadResponse>().updated
    }

    private fun ensureBearer(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"
}

@Serializable
private data class MarkAllReadResponse(val updated: Int)

@Deprecated("Use TrackingInboxRemoteDataSourceImpl")
typealias SubscriptionsInboxRemoteDataSourceImpl = TrackingInboxRemoteDataSourceImpl
