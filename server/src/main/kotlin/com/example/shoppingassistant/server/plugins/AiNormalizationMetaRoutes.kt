package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.ai.AiNormalizationObservabilityService
import com.example.shoppingassistant.server.config.parseBooleanEnv
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent

fun Route.aiNormalizationMetaRoutes() {
    route("/api/meta") {
        get("/ai-normalization") {
            val enabled = parseBooleanEnv(
                key = "AI_NORMALIZATION_META_ENABLED",
                defaultValue = true,
            )
            if (!enabled) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val service: AiNormalizationObservabilityService? = try {
                KoinJavaComponent.get(AiNormalizationObservabilityService::class.java)
            } catch (_: Exception) {
                null
            }
            if (service == null) {
                call.respond(HttpStatusCode.ServiceUnavailable)
                return@get
            }
            call.respond(service.snapshot())
        }
    }
}
