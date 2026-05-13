package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.ai.AiAgentRegistryAdminService
import com.example.shoppingassistant.server.ai.AiAgentRegistryOverrideUpdateRequest
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.config.envValue
import com.example.shoppingassistant.server.config.parseBooleanEnv
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent

data class AiNormalizationAdminApiConfig(
    val enabled: Boolean,
    val adminToken: String?,
) {
    companion object {
        fun fromEnv(): AiNormalizationAdminApiConfig = AiNormalizationAdminApiConfig(
            enabled = parseBooleanEnv(
                key = "AI_NORMALIZATION_ADMIN_API_ENABLED",
                defaultValue = false,
            ),
            adminToken = envValue("AI_NORMALIZATION_ADMIN_TOKEN")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
        )
    }
}

@Serializable
private data class AiNormalizationAdminErrorResponse(
    val error: String,
)

fun Route.aiNormalizationAdminRoutes(
    config: AiNormalizationAdminApiConfig = AiNormalizationAdminApiConfig.fromEnv(),
) {
    route("/api/admin/ai-normalization") {
        get("/registry") {
            if (!call.authorizeAiNormalizationAdmin(config)) return@get
            val service = call.resolveAdminService() ?: return@get
            call.respond(service.snapshot())
        }

        put("/registry/{flow}") {
            if (!call.authorizeAiNormalizationAdmin(config)) return@put
            val flow = parseFlow(call.parameters["flow"]) ?: run {
                call.respond(HttpStatusCode.NotFound, AiNormalizationAdminErrorResponse("unsupported_flow"))
                return@put
            }
            val service = call.resolveAdminService() ?: return@put
            val request = runCatching { call.receive<AiAgentRegistryOverrideUpdateRequest>() }
                .getOrElse {
                    call.respond(HttpStatusCode.BadRequest, AiNormalizationAdminErrorResponse("invalid_request_body"))
                    return@put
                }
            runCatching { service.upsert(flow, request) }
                .getOrElse { error ->
                    when (error) {
                        is NoSuchElementException -> call.respond(
                            HttpStatusCode.NotFound,
                            AiNormalizationAdminErrorResponse("unsupported_flow"),
                        )
                        is IllegalArgumentException -> call.respond(
                            HttpStatusCode.BadRequest,
                            AiNormalizationAdminErrorResponse(error.message ?: "invalid_override"),
                        )
                        else -> call.respond(
                            HttpStatusCode.InternalServerError,
                            AiNormalizationAdminErrorResponse("override_write_failed"),
                        )
                    }
                    return@put
                }
            call.respond(service.snapshot())
        }

        delete("/registry/{flow}") {
            if (!call.authorizeAiNormalizationAdmin(config)) return@delete
            val flow = parseFlow(call.parameters["flow"]) ?: run {
                call.respond(HttpStatusCode.NotFound, AiNormalizationAdminErrorResponse("unsupported_flow"))
                return@delete
            }
            val service = call.resolveAdminService() ?: return@delete
            val deleted = service.delete(flow)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound, AiNormalizationAdminErrorResponse("override_not_found"))
                return@delete
            }
            call.respond(service.snapshot())
        }
    }
}

private suspend fun ApplicationCall.authorizeAiNormalizationAdmin(
    config: AiNormalizationAdminApiConfig,
): Boolean {
    if (!config.enabled) {
        respond(HttpStatusCode.NotFound)
        return false
    }
    val expectedToken = config.adminToken
    if (expectedToken.isNullOrBlank()) {
        respond(
            HttpStatusCode.ServiceUnavailable,
            AiNormalizationAdminErrorResponse("admin_token_not_configured"),
        )
        return false
    }
    val providedToken = request.headers["X-Admin-Token"]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: extractBearerToken(request.headers[HttpHeaders.Authorization])
    if (providedToken != expectedToken) {
        respond(HttpStatusCode.Unauthorized, AiNormalizationAdminErrorResponse("invalid_admin_token"))
        return false
    }
    return true
}

private suspend fun ApplicationCall.resolveAdminService(): AiAgentRegistryAdminService? {
    val service: AiAgentRegistryAdminService? = try {
        KoinJavaComponent.get(AiAgentRegistryAdminService::class.java)
    } catch (_: Exception) {
        null
    }
    if (service == null) {
        respond(
            HttpStatusCode.ServiceUnavailable,
            AiNormalizationAdminErrorResponse("admin_service_unavailable"),
        )
        return null
    }
    return service
}

private fun parseFlow(raw: String?): AiNormalizationFlow? = raw
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.uppercase()
    ?.let { normalized ->
        AiNormalizationFlow.entries.firstOrNull { it.name == normalized }
    }

private fun extractBearerToken(header: String?): String? {
    if (header == null || !header.startsWith("Bearer ")) return null
    return header.removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
}
