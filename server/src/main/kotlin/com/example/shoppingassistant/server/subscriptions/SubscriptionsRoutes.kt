package com.example.shoppingassistant.server.subscriptions

import com.example.shoppingassistant.server.auth.SessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent

fun Route.subscriptionsRoutes() {
    val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)
    val notificationsRepo: SubscriptionNotificationsRepository =
        KoinJavaComponent.get(SubscriptionNotificationsRepository::class.java)

    route("/api/subscriptions") {
        get("/notifications") {
            val userId = requireUserId(call, sessionManager) ?: return@get
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val page = notificationsRepo.listNotificationsPage(
                userId = userId,
                limit = limit,
                offset = offset,
            )
            call.respond(page)
        }

        post("/notifications/{id}/read") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respondText("Bad request", status = HttpStatusCode.BadRequest)
                return@post
            }
            val ok = notificationsRepo.markRead(userId = userId, notificationId = id)
            if (!ok) {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
                return@post
            }
            call.respondText("OK")
        }

        post("/notifications/read-all") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val updated = notificationsRepo.markAllRead(userId)
            call.respond(MarkAllReadResponse(updated = updated))
        }
    }
}

@Serializable
private data class MarkAllReadResponse(val updated: Int)

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
