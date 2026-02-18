package com.example.shoppingassistant.server.push

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
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent

@Serializable
data class RegisterPushTokenRequest(
    val platform: String,
    val token: String,
    val deviceId: String? = null,
)

@Serializable
data class RegisterPushTokenResponse(
    val registered: Boolean,
    val id: Long,
)

fun Route.pushRoutes() {
    val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)
    val repo: PushTokensRepository = KoinJavaComponent.get(PushTokensRepository::class.java)

    route("/api/push") {
        post("/tokens") {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val req = call.receive<RegisterPushTokenRequest>()
            val token = req.token.trim()
            val platform = req.platform.trim().uppercase()
            if (token.isEmpty() || platform.isEmpty()) {
                call.respondText("Bad request", status = HttpStatusCode.BadRequest)
                return@post
            }
            val id = repo.upsertToken(
                userId = userId,
                platform = platform,
                token = token,
                deviceId = req.deviceId?.trim()?.ifBlank { null },
            )
            call.respond(RegisterPushTokenResponse(registered = true, id = id))
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

