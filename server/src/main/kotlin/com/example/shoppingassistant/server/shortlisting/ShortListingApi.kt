package com.example.shoppingassistant.server.shortlisting

import com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingHttpContractPaths
import com.example.shoppingassistant.domain.shortlisting.ShortListingReviewUpdateRequest
import com.example.shoppingassistant.server.auth.SessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent

fun Route.shortListingRoutes() {
    val service: ShortListingBackendService =
        KoinJavaComponent.get(ShortListingBackendService::class.java)

    route(ShortListingHttpContractPaths.base) {
        get("/drafts") {
            val userId = call.requireAuthorizedUserId() ?: return@get
            call.respond(service.listDrafts(userId))
        }

        post("/drafts") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val request = runCatching { call.receive<ShortListingCreateDraftRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val envelope = runCatching { service.createDraft(userId, request) }
                .getOrElse { throwable ->
                    call.handleShortListingFailure(throwable)
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
            val request = runCatching { call.receive<ShortListingReviewUpdateRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            val envelope = runCatching { service.updateDraftReview(userId, draftId, request) }
                .getOrElse { throwable ->
                    call.handleShortListingFailure(throwable)
                    return@post
                }
            call.respond(envelope)
        }

        post("/drafts/{draftId}/preflight") {
            val userId = call.requireAuthorizedUserId() ?: return@post
            val draftId = call.parameters["draftId"]?.trim().orEmpty()
            if (draftId.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val preflight = runCatching { service.getPublishPreflight(userId, draftId) }
                .getOrElse { throwable ->
                    call.handleShortListingFailure(throwable)
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
            val result = runCatching { service.publishDraft(userId, draftId) }
                .getOrElse { throwable ->
                    call.handleShortListingFailure(throwable)
                    return@post
                }
            when (result) {
                is com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult.Published ->
                    call.respond(HttpStatusCode.OK, result)

                is com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult.Blocked ->
                    call.respond(HttpStatusCode.Conflict, result)
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.handleShortListingFailure(
    throwable: Throwable,
) {
    when (throwable) {
        is ShortListingValidationException,
        is IllegalArgumentException,
            -> respond(HttpStatusCode.BadRequest, shortListingError(throwable.message ?: "INVALID_INPUT"))

        is ShortListingNotFoundException,
            -> respond(HttpStatusCode.NotFound, shortListingError(throwable.message ?: "NOT_FOUND"))

        else -> respond(HttpStatusCode.InternalServerError, shortListingError("INTERNAL_ERROR"))
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireAuthorizedUserId(): Long? {
    val userId = resolveAuthorizedUserIdOrNull()
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return userId
}

private fun io.ktor.server.application.ApplicationCall.resolveAuthorizedUserIdOrNull(): Long? {
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

private fun shortListingError(code: String): Map<String, String> =
    mapOf("code" to code)
