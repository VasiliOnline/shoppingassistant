package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferHttpContractPaths
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishCommandRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import com.example.shoppingassistant.server.auth.SessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent

fun Route.localOfferRoutes() {
    val service: LocalOfferBackendService =
        KoinJavaComponent.get(LocalOfferBackendService::class.java)

    route(LocalOfferHttpContractPaths.base) {
        post("/sessions") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val request = runCatching { call.receive<LocalOfferSessionRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val session = runCatching { service.createOrResumeSession(userId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            call.respond(HttpStatusCode.Created, session)
        }

        post("/geo-snapshots") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val request = runCatching { call.receive<LocalOfferConfirmGeoSnapshotRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val snapshot = runCatching { service.confirmGeoSnapshot(userId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            call.respond(HttpStatusCode.Created, snapshot)
        }

        get("/drafts") {
            val userId = call.requireAuthorizedUserId() ?: return@get
            call.respond(service.listDrafts(userId))
        }

        post("/drafts") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val request = runCatching { call.receive<LocalOfferCreateDraftRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val envelope = runCatching { service.createDraft(userId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            call.respond(HttpStatusCode.Created, envelope)
        }

        get("/drafts/{draftId}") {
            val userId = call.requireAuthorizedUserId() ?: return@get
            val draftId = call.parameters["draftId"]?.trim().orEmpty()
            if (draftId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val envelope = service.getDraft(userId, draftId)
            if (envelope == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(envelope)
            }
        }

        post("/drafts/{draftId}/review") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val draftId = call.parameters["draftId"]?.trim().orEmpty()
            if (draftId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val request = runCatching { call.receive<LocalOfferReviewUpdateRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val envelope = runCatching { service.updateDraftReview(userId, draftId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            call.respond(envelope)
        }

        post("/drafts/{draftId}/preview") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val draftId = call.parameters["draftId"]?.trim().orEmpty()
            if (draftId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val request = runCatching { call.receive<LocalOfferPreviewRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val preview = runCatching { service.getPreview(userId, draftId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            call.respond(preview)
        }

        post("/drafts/{draftId}/preflight") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val draftId = call.parameters["draftId"]?.trim().orEmpty()
            if (draftId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val request = runCatching { call.receive<LocalOfferPublishPreflightRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val preflight = runCatching { service.getPublishPreflight(userId, draftId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            call.respond(preflight)
        }

        post("/drafts/{draftId}/publish") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val draftId = call.parameters["draftId"]?.trim().orEmpty()
            if (draftId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val request = runCatching { call.receive<LocalOfferPublishCommandRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val result = runCatching { service.publishDraft(userId, draftId, request) }
                .getOrElse { throwable ->
                    call.handleLocalOfferFailure(throwable)
                    return@post
                }
            val status = if (result.outcome == com.example.shoppingassistant.domain.localoffer.LocalOfferPublishOutcome.PUBLISHED) {
                HttpStatusCode.OK
            } else {
                HttpStatusCode.Conflict
            }
            call.respond(status, result)
        }
    }
}

private suspend fun ApplicationCall.handleLocalOfferFailure(
    throwable: Throwable,
) {
    when (throwable) {
        is LocalOfferValidationException,
        is IllegalArgumentException,
            -> respond(HttpStatusCode.BadRequest, localOfferError(throwable.message ?: "INVALID_INPUT"))

        is LocalOfferNotFoundException,
            -> respond(HttpStatusCode.NotFound, localOfferError(throwable.message ?: "NOT_FOUND"))

        is LocalOfferConflictException,
            -> respond(HttpStatusCode.Conflict, localOfferError(throwable.message ?: "CONFLICT"))

        else -> respond(HttpStatusCode.InternalServerError, localOfferError("INTERNAL_ERROR"))
    }
}

private suspend fun ApplicationCall.requireAuthorizedUserId(): Long? {
    val userId = resolveAuthorizedUserIdOrNull()
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return userId
}

private fun ApplicationCall.resolveAuthorizedUserIdOrNull(): Long? {
    val token = extractBearerToken(request.headers["Authorization"]) ?: return null
    val sessionManager: SessionManager =
        KoinJavaComponent.get(SessionManager::class.java)
    return sessionManager.getUserId(token)
}

private fun extractBearerToken(header: String?): String? {
    if (header == null || !header.startsWith("Bearer ")) return null
    val token = header.removePrefix("Bearer ").trim()
    return token.takeIf { it.isNotEmpty() }
}

private fun localOfferError(code: String): Map<String, String> =
    mapOf("code" to code)
