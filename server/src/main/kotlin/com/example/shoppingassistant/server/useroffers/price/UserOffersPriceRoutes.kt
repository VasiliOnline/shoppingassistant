package com.example.shoppingassistant.server.useroffers.price

import com.example.shoppingassistant.domain.useroffers.UserOfferActionStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateRequest
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

fun Route.userOffersPriceRoutes() {
    val service: UserOffersPriceService =
        KoinJavaComponent.get(UserOffersPriceService::class.java)
    val sessionManager: SessionManager =
        KoinJavaComponent.get(SessionManager::class.java)

    route("/api/user/offers") {
        post("/price") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val request = call.receive<UserOfferPriceUpdateRequest>()
            val result = service.updatePrice(userId, request)
            val status = when (result.status) {
                UserOfferActionStatus.SUCCESS -> HttpStatusCode.OK
                UserOfferActionStatus.INVALID_INPUT -> HttpStatusCode.BadRequest
                UserOfferActionStatus.NOT_FOUND -> HttpStatusCode.NotFound
                UserOfferActionStatus.FORBIDDEN -> HttpStatusCode.Forbidden
                UserOfferActionStatus.FAILED -> HttpStatusCode.InternalServerError
            }
            call.respond(status, result)
        }

        post("/price/bulk") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val request = call.receive<UserOfferPriceBulkRequest>()
            val result = service.updatePrices(userId, request)
            call.respond(HttpStatusCode.OK, result)
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
