package com.example.shoppingassistant.core.data.shortlisting

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftEnvelope
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftSession
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishPreflight
import com.example.shoppingassistant.domain.shortlisting.ShortListingRepository
import com.example.shoppingassistant.domain.shortlisting.ShortListingReviewUpdateRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart

class ShortListingRemoteRepository(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : ShortListingRepository {

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun listDrafts(): List<ShortListingDraftSession> {
        val response = authorizedGet("$baseUrl/api/shortlisting/drafts")
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun createDraft(request: ShortListingCreateDraftRequest): ShortListingDraftEnvelope {
        val response = authorizedPost(
            url = "$baseUrl/api/shortlisting/drafts",
            body = request,
        )
        return when (response.status) {
            HttpStatusCode.Created,
            HttpStatusCode.OK,
                -> response.body()

            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }

            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid short listing draft input")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun getDraft(draftId: String): ShortListingDraftEnvelope? {
        val response = authorizedGet("$baseUrl/api/shortlisting/drafts/${draftId.encodeURLPathPart()}")
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> null
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                null
            }
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun updateDraftReview(
        draftId: String,
        request: ShortListingReviewUpdateRequest,
    ): ShortListingDraftEnvelope {
        val response = authorizedPost(
            url = "$baseUrl/api/shortlisting/drafts/${draftId.encodeURLPathPart()}/review",
            body = request,
        )
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw IllegalStateException("Draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid review payload")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun getPublishPreflight(draftId: String): ShortListingPublishPreflight {
        val response = authorizedPost(
            url = "$baseUrl/api/shortlisting/drafts/${draftId.encodeURLPathPart()}/preflight",
        )
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw IllegalStateException("Draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun publishDraft(draftId: String): ShortListingPublishAttemptResult {
        val response = authorizedPost(
            url = "$baseUrl/api/shortlisting/drafts/${draftId.encodeURLPathPart()}/publish",
        )
        return when (response.status) {
            HttpStatusCode.OK,
            HttpStatusCode.Conflict,
                -> response.body()

            HttpStatusCode.NotFound -> throw IllegalStateException("Draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    private suspend fun authorizedGet(url: String): HttpResponse {
        val token = authRepository.currentToken()
        return backendClient.client.get(url) {
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
        }
    }

    private suspend fun authorizedPost(
        url: String,
        body: Any? = null,
    ): HttpResponse {
        val token = authRepository.currentToken()
        return backendClient.client.post(url) {
            contentType(ContentType.Application.Json)
            if (!token.isNullOrBlank()) {
                header("Authorization", ensureBearer(token))
            }
            if (body != null) {
                setBody(body)
            }
        }
    }

    private fun ensureBearer(token: String): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"
}
