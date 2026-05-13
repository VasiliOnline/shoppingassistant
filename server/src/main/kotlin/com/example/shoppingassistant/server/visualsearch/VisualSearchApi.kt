package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchErrorCode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchErrorEnvelope
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchHttpContractPaths
import com.example.shoppingassistant.domain.visualsearch.VisualSearchHttpHeaders
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSchemaVersion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import java.util.UUID
import org.koin.java.KoinJavaComponent

fun Route.visualSearchRoutes() {
    post(VisualSearchHttpContractPaths.contextReuse) {
        val traceId = UUID.randomUUID().toString()
        val metadata = call.validateVisualMetadata(traceId, requireIdempotency = false) ?: return@post
        val request = runCatching { call.receive<VisualSearchContextReuseRequest>() }.getOrElse {
            call.respondBadRequest(traceId)
            return@post
        }
        val service: VisualSearchService = KoinJavaComponent.get(VisualSearchService::class.java)
        val response = service.reuseContext(metadata, request).copy(traceId = traceId)
        val status = when (response.status) {
            com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseStatus.HIT -> HttpStatusCode.OK
            com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseStatus.MISS -> HttpStatusCode.NotFound
            com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseStatus.ERROR -> HttpStatusCode.ServiceUnavailable
        }
        call.respondWithTrace(status, traceId, response)
    }

    post(VisualSearchHttpContractPaths.normalizeDraft) {
        val traceId = UUID.randomUUID().toString()
        val metadata = call.validateVisualMetadata(traceId, requireIdempotency = true) ?: return@post
        val request = runCatching { call.receive<VisualSearchNormalizeDraftRequest>() }.getOrElse {
            call.respondBadRequest(traceId)
            return@post
        }
        val service: VisualSearchService = KoinJavaComponent.get(VisualSearchService::class.java)
        val response = service.normalizeDraft(metadata, request).copy(traceId = traceId)
        val status = if (response.error == null) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respondWithTrace(status, traceId, response)
    }

    post(VisualSearchHttpContractPaths.bindQuery) {
        val traceId = UUID.randomUUID().toString()
        val metadata = call.validateVisualMetadata(traceId, requireIdempotency = true) ?: return@post
        val request = runCatching { call.receive<VisualSearchBindQueryRequest>() }.getOrElse {
            call.respondBadRequest(traceId)
            return@post
        }
        val service: VisualSearchService = KoinJavaComponent.get(VisualSearchService::class.java)
        val response = service.bindQuery(metadata, request).copy(traceId = traceId)
        val status = if (response.error == null) HttpStatusCode.OK else HttpStatusCode.UnprocessableEntity
        call.respondWithTrace(status, traceId, response)
    }

    post(VisualSearchHttpContractPaths.recoveryPlan) {
        val traceId = UUID.randomUUID().toString()
        val metadata = call.validateVisualMetadata(traceId, requireIdempotency = true) ?: return@post
        val request = runCatching { call.receive<VisualSearchRecoveryPlanRequest>() }.getOrElse {
            call.respondBadRequest(traceId)
            return@post
        }
        val service: VisualSearchService = KoinJavaComponent.get(VisualSearchService::class.java)
        val response = service.recoveryPlan(metadata, request).copy(traceId = traceId)
        val status = if (response.error == null) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respondWithTrace(status, traceId, response)
    }

    post(VisualSearchHttpContractPaths.eventsBatch) {
        val traceId = UUID.randomUUID().toString()
        val metadata = call.validateVisualMetadata(traceId, requireIdempotency = false) ?: return@post
        val request = runCatching { call.receive<VisualSearchEventBatchRequest>() }.getOrElse {
            call.respondBadRequest(traceId)
            return@post
        }
        val service: VisualSearchService = KoinJavaComponent.get(VisualSearchService::class.java)
        val response = service.ingestEvents(metadata, request).copy(traceId = traceId)
        val status = if (response.error == null) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respondWithTrace(status, traceId, response)
    }
}

private suspend fun ApplicationCall.validateVisualMetadata(
    traceId: String,
    requireIdempotency: Boolean,
): VisualSearchTransportMetadata? {
    val visualSessionId = request.headers[VisualSearchHttpHeaders.visualSessionId]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            respondBadRequest(traceId)
            return null
        }
    val clientSchemaVersion = request.headers[VisualSearchHttpHeaders.clientSchemaVersion]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            respondWithTrace(
                status = HttpStatusCode.BadRequest,
                traceId = traceId,
                body = unsupportedSchemaError(clientSchemaVersion = null),
            )
            return null
        }
    if (clientSchemaVersion != VisualSearchSchemaVersion.current) {
        respondWithTrace(
            status = HttpStatusCode.BadRequest,
            traceId = traceId,
            body = unsupportedSchemaError(clientSchemaVersion),
        )
        return null
    }
    val catalogDataVersion = request.headers[VisualSearchHttpHeaders.catalogDataVersion]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: run {
            respondBadRequest(traceId)
            return null
        }
    if (catalogDataVersion != CatalogDataVersion.current) {
        respondWithTrace(
            status = HttpStatusCode.PreconditionFailed,
            traceId = traceId,
            body = VisualSearchErrorEnvelope(
                code = VisualSearchErrorCode.CATALOG_VERSION_MISMATCH,
                messageKey = "visual_search.error.server",
                retryable = false,
                details = mapOf(
                    "clientCatalogDataVersion" to catalogDataVersion,
                    "serverCatalogDataVersion" to CatalogDataVersion.current,
                ),
            ),
        )
        return null
    }
    val idempotencyKey = request.headers[VisualSearchHttpHeaders.idempotencyKey]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (requireIdempotency && (idempotencyKey == null || idempotencyKey.length < 8)) {
        respondBadRequest(traceId)
        return null
    }
    return VisualSearchTransportMetadata(
        visualSessionId = visualSessionId,
        clientSchemaVersion = clientSchemaVersion,
        catalogDataVersion = catalogDataVersion,
        idempotencyKey = idempotencyKey,
    )
}

private fun unsupportedSchemaError(clientSchemaVersion: String?): VisualSearchErrorEnvelope =
    VisualSearchErrorEnvelope(
        code = VisualSearchErrorCode.UNSUPPORTED_SCHEMA,
        messageKey = "visual_search.error.server",
        retryable = false,
        details = buildMap {
            clientSchemaVersion?.let { put("clientSchemaVersion", it) }
            put(
                "minSupportedClientSchemaVersion",
                VisualSearchSchemaVersion.minSupportedClientSchemaVersion,
            )
        },
    )

private suspend fun ApplicationCall.respondBadRequest(traceId: String) {
    respondWithTrace(
        status = HttpStatusCode.BadRequest,
        traceId = traceId,
        body = VisualSearchErrorEnvelope(
            code = VisualSearchErrorCode.BAD_REQUEST,
            messageKey = "visual_search.error.server",
            retryable = false,
        ),
    )
}

private suspend fun ApplicationCall.respondWithTrace(
    status: HttpStatusCode,
    traceId: String,
    body: Any,
) {
    response.headers.append(VisualSearchHttpHeaders.serverTraceId, traceId)
    response.headers.append(VisualSearchHttpHeaders.catalogDataVersion, CatalogDataVersion.current)
    respond(status = status, message = body)
}
