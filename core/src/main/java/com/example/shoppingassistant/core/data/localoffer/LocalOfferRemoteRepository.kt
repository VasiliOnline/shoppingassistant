package com.example.shoppingassistant.core.data.localoffer

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftIds
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoSnapshot
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishCommandRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishResult
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferRepository
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
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

class LocalOfferRemoteRepository(
    private val backendClient: BackendClient,
    private val authRepository: AuthRepository,
) : LocalOfferRepository {

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun createOrResumeSession(request: LocalOfferSessionRequest): LocalOfferSession {
        val response = authorizedPost("$baseUrl/api/localoffer/sessions", request)
        return when (response.status) {
            HttpStatusCode.Created,
            HttpStatusCode.OK,
                -> response.body()

            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }

            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid local offer session request")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun confirmGeoSnapshot(request: LocalOfferConfirmGeoSnapshotRequest): LocalOfferGeoSnapshot {
        val response = authorizedPost("$baseUrl/api/localoffer/geo-snapshots", request)
        return when (response.status) {
            HttpStatusCode.Created,
            HttpStatusCode.OK,
                -> response.body()

            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }

            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid geo snapshot payload")
            HttpStatusCode.NotFound -> throw IllegalStateException("Local offer session not found")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun listDrafts(): List<LocalOfferDraftSession> {
        val response = authorizedGet("$baseUrl/api/localoffer/drafts")
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun createDraft(request: LocalOfferCreateDraftRequest): LocalOfferDraftEnvelope {
        val response = authorizedPost("$baseUrl/api/localoffer/drafts", request)
        return when (response.status) {
            HttpStatusCode.Created,
            HttpStatusCode.OK,
                -> response.body()

            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }

            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid local offer draft payload")
            HttpStatusCode.NotFound -> throw IllegalStateException("Required local offer session or geo snapshot not found")
            HttpStatusCode.Conflict -> throw IllegalStateException("Geo snapshot is not valid for draft creation")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun getDraft(draftId: String): LocalOfferDraftEnvelope? {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val response = authorizedGet("$baseUrl/api/localoffer/drafts/${canonicalDraftId.encodeURLPathPart()}")
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
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferDraftEnvelope {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val response = authorizedPost(
            url = "$baseUrl/api/localoffer/drafts/${canonicalDraftId.encodeURLPathPart()}/review",
            body = request,
        )
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw IllegalStateException("Local offer draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid local offer review payload")
            HttpStatusCode.Conflict -> throw IllegalStateException("Local offer revision conflict")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun getPreview(
        draftId: String,
        request: LocalOfferPreviewRequest,
    ): LocalOfferPreviewResponse {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val response = authorizedPost(
            url = "$baseUrl/api/localoffer/drafts/${canonicalDraftId.encodeURLPathPart()}/preview",
            body = request,
        )
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw IllegalStateException("Local offer draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            HttpStatusCode.Conflict -> throw IllegalStateException("Local offer preview conflict")
            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid local offer preview payload")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun getPublishPreflight(
        draftId: String,
        request: LocalOfferPublishPreflightRequest,
    ): LocalOfferPublishPreflightResponse {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val response = authorizedPost(
            url = "$baseUrl/api/localoffer/drafts/${canonicalDraftId.encodeURLPathPart()}/preflight",
            body = request,
        )
        return when (response.status) {
            HttpStatusCode.OK -> response.body()
            HttpStatusCode.NotFound -> throw IllegalStateException("Local offer draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            HttpStatusCode.Conflict -> throw IllegalStateException("Local offer preflight conflict")
            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid local offer preflight payload")
            else -> throw IllegalStateException("HTTP ${response.status.value}")
        }
    }

    override suspend fun publishDraft(
        draftId: String,
        request: LocalOfferPublishCommandRequest,
    ): LocalOfferPublishResult {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val response = authorizedPost(
            url = "$baseUrl/api/localoffer/drafts/${canonicalDraftId.encodeURLPathPart()}/publish",
            body = request,
        )
        return when (response.status) {
            HttpStatusCode.OK,
            HttpStatusCode.Conflict,
                -> response.body()

            HttpStatusCode.NotFound -> throw IllegalStateException("Local offer draft not found")
            HttpStatusCode.Unauthorized -> {
                authRepository.logout()
                throw IllegalStateException("Unauthorized")
            }
            HttpStatusCode.BadRequest -> throw IllegalStateException("Invalid local offer publish payload")
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

    private fun requireCanonicalDraftId(draftId: String): String =
        LocalOfferDraftIds.canonicalOrNull(draftId)
            ?: throw IllegalArgumentException("Local offer draft id must use lodraft-* namespace")
}
