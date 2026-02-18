package com.example.shoppingassistant.server.useroffers.actions

import com.example.shoppingassistant.domain.useroffers.UserOfferActionRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferActionStatus
import com.example.shoppingassistant.server.auth.SessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent

fun Route.userOffersActionsRoutes() {
    val service: UserOffersActionsService =
        KoinJavaComponent.get(UserOffersActionsService::class.java)
    val sessionManager: SessionManager =
        KoinJavaComponent.get(SessionManager::class.java)

    route("/api/user/offers") {
        post("/action") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val request = call.receive<UserOfferActionRequest>()
            val result = service.performAction(userId, request)
            val status = when (result.status) {
                UserOfferActionStatus.SUCCESS -> HttpStatusCode.OK
                UserOfferActionStatus.INVALID_INPUT -> HttpStatusCode.BadRequest
                UserOfferActionStatus.NOT_FOUND -> HttpStatusCode.NotFound
                UserOfferActionStatus.FORBIDDEN -> HttpStatusCode.Forbidden
                UserOfferActionStatus.FAILED -> HttpStatusCode.InternalServerError
            }
            call.respond(status, result)
        }
    }
}

private suspend fun requireUserId(call: ApplicationCall, sessionManager: SessionManager): Long? {
    val header = call.request.headers["Authorization"]
    val token = extractBearerToken(header)
    if (token == null) {
        call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
        return null
    }
    val userId = sessionManager.getUserId(token)
    if (userId == null) {
        call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
        return null
    }
    return userId
}

private fun extractBearerToken(header: String?): String? {
    if (header == null || !header.startsWith("Bearer ")) return null
    return header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
}
