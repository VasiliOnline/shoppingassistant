package com.example.shoppingassistant.server.useroffers

import com.example.shoppingassistant.domain.useroffers.UserOffersQuery
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

fun Route.userOffersRoutes() {
    val repository: UserOffersBackendRepository =
        KoinJavaComponent.get(UserOffersBackendRepository::class.java)
    val sessionManager: SessionManager =
        KoinJavaComponent.get(SessionManager::class.java)

    route("/api/user/offers") {
        post("/list") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val query = call.receive<UserOffersQuery>()
            val page = repository.listUserOffers(userId, query)
            call.respond(page)
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
