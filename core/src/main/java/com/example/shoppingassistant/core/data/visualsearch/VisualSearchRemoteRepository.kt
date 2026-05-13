package com.example.shoppingassistant.core.data.visualsearch

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEnvelopeStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchErrorCode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchErrorEnvelope
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchHttpContractPaths
import com.example.shoppingassistant.domain.visualsearch.VisualSearchHttpHeaders
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRepository
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class VisualSearchRemoteRepository(
    private val backendClient: BackendClient,
) : VisualSearchRepository {

    private val baseUrl: String
        get() = BackendConfig.BASE_URL

    override suspend fun reuseContext(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchContextReuseRequest,
    ): VisualSearchContextReuseResponse {
        val response = backendClient.client.post("$baseUrl${VisualSearchHttpContractPaths.contextReuse}") {
            applyMetadata(metadata, requireIdempotency = false)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return when {
            response.status.isSuccess() -> response.body()
            response.status == HttpStatusCode.NotFound -> {
                runCatching { response.body<VisualSearchContextReuseResponse>() }.getOrElse {
                    VisualSearchContextReuseResponse(
                        status = VisualSearchContextReuseStatus.MISS,
                        error = null,
                    )
                }
            }

            else -> VisualSearchContextReuseResponse(
                status = VisualSearchContextReuseStatus.ERROR,
                traceId = response.headers[VisualSearchHttpHeaders.serverTraceId],
                error = parseError(response.status, metadata, responseBody = runCatching { response.body<VisualSearchErrorEnvelope>() }.getOrNull()),
            )
        }
    }

    override suspend fun normalizeDraft(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
    ): VisualSearchNormalizeDraftResponse {
        val response = backendClient.client.post("$baseUrl${VisualSearchHttpContractPaths.normalizeDraft}") {
            applyMetadata(metadata, requireIdempotency = true)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return if (response.status.isSuccess()) {
            response.body()
        } else {
            VisualSearchNormalizeDraftResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                traceId = response.headers[VisualSearchHttpHeaders.serverTraceId],
                error = parseError(response.status, metadata, runCatching { response.body<VisualSearchErrorEnvelope>() }.getOrNull()),
            )
        }
    }

    override suspend fun bindQuery(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchBindQueryRequest,
    ): VisualSearchBindQueryResponse {
        val response = backendClient.client.post("$baseUrl${VisualSearchHttpContractPaths.bindQuery}") {
            applyMetadata(metadata, requireIdempotency = true)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return if (response.status.isSuccess()) {
            response.body()
        } else {
            VisualSearchBindQueryResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                traceId = response.headers[VisualSearchHttpHeaders.serverTraceId],
                error = parseError(response.status, metadata, runCatching { response.body<VisualSearchErrorEnvelope>() }.getOrNull()),
            )
        }
    }

    override suspend fun recoveryPlan(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchRecoveryPlanRequest,
    ): VisualSearchRecoveryPlanResponse {
        val response = backendClient.client.post("$baseUrl${VisualSearchHttpContractPaths.recoveryPlan}") {
            applyMetadata(metadata, requireIdempotency = true)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return if (response.status.isSuccess()) {
            response.body()
        } else {
            VisualSearchRecoveryPlanResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                traceId = response.headers[VisualSearchHttpHeaders.serverTraceId],
                error = parseError(response.status, metadata, runCatching { response.body<VisualSearchErrorEnvelope>() }.getOrNull()),
            )
        }
    }

    override suspend fun ingestEvents(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchEventBatchRequest,
    ): VisualSearchEventBatchResponse {
        val response = backendClient.client.post("$baseUrl${VisualSearchHttpContractPaths.eventsBatch}") {
            applyMetadata(metadata, requireIdempotency = false)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return if (response.status.isSuccess()) {
            response.body()
        } else {
            VisualSearchEventBatchResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                acceptedCount = 0,
                traceId = response.headers[VisualSearchHttpHeaders.serverTraceId],
                error = parseError(response.status, metadata, runCatching { response.body<VisualSearchErrorEnvelope>() }.getOrNull()),
            )
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyMetadata(
        metadata: VisualSearchTransportMetadata,
        requireIdempotency: Boolean,
    ) {
        header(VisualSearchHttpHeaders.visualSessionId, metadata.visualSessionId)
        header(VisualSearchHttpHeaders.clientSchemaVersion, metadata.clientSchemaVersion)
        header(VisualSearchHttpHeaders.catalogDataVersion, metadata.catalogDataVersion)
        if (requireIdempotency) {
            header(
                VisualSearchHttpHeaders.idempotencyKey,
                metadata.idempotencyKey.orEmpty(),
            )
        }
    }

    private fun parseError(
        status: HttpStatusCode,
        metadata: VisualSearchTransportMetadata,
        responseBody: VisualSearchErrorEnvelope?,
    ): VisualSearchErrorEnvelope {
        if (responseBody != null) return responseBody
        val code = when (status) {
            HttpStatusCode.PreconditionFailed -> VisualSearchErrorCode.CATALOG_VERSION_MISMATCH
            HttpStatusCode.Conflict -> VisualSearchErrorCode.IDEMPOTENCY_CONFLICT
            HttpStatusCode.TooManyRequests -> VisualSearchErrorCode.RATE_LIMITED
            HttpStatusCode.BadRequest -> VisualSearchErrorCode.BAD_REQUEST
            HttpStatusCode.ServiceUnavailable -> VisualSearchErrorCode.FEATURE_DISABLED
            HttpStatusCode.UnprocessableEntity -> VisualSearchErrorCode.BINDER_REJECTED
            else -> VisualSearchErrorCode.UPSTREAM_FAILURE
        }
        return VisualSearchErrorEnvelope(
            code = code,
            messageKey = "visual_search.error.server",
            retryable = status == HttpStatusCode.TooManyRequests || status == HttpStatusCode.ServiceUnavailable,
            details = mapOf(
                "httpStatus" to status.value.toString(),
                "visualSessionId" to metadata.visualSessionId,
            ),
        )
    }
}
