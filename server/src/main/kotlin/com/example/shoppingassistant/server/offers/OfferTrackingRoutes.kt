package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.offers.CreateTrackedOfferStatus
import com.example.shoppingassistant.domain.offers.TrackedOfferInput
import com.example.shoppingassistant.domain.offers.TrackedOfferRepository
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferInput
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferStatus
import com.example.shoppingassistant.server.auth.SessionManager
import com.example.shoppingassistant.server.db.DatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.java.KoinJavaComponent
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.andWhere

/**
 * Endpoint создания отслеживаемого оффера по нормализованной ссылке.
 */
fun Route.offerTrackingRoutes() {
    val repo: TrackedOfferRepository = getKoin().get()
    val sessionManager: SessionManager = KoinJavaComponent.get(SessionManager::class.java)

    route("/api/offers/tracked") {
        post {
            val userId = requireUserId(call, sessionManager) ?: return@post
            val payload = call.receive<TrackedOfferInput>()
            val normalized = payload.copy(userId = userId.toString())
            val result = repo.createTrackedOffer(normalized)
            val status = when (result.status) {
                CreateTrackedOfferStatus.CREATED -> HttpStatusCode.OK
                CreateTrackedOfferStatus.ALREADY_EXISTS -> HttpStatusCode.OK
                CreateTrackedOfferStatus.INVALID_INPUT -> HttpStatusCode.BadRequest
            }
            call.respond(status, result)
        }

        put("/refresh") {
            val userId = requireUserId(call, sessionManager) ?: return@put
            val payload = call.receive<RefreshTrackedOfferInput>()
            val offerId = payload.offerId.toLongOrNull()
            if (offerId == null) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = com.example.shoppingassistant.domain.offers.RefreshTrackedOfferResult(
                        status = RefreshTrackedOfferStatus.INVALID_INPUT,
                        message = "offerId must be numeric",
                    ),
                )
                return@put
            }

            val owned = DatabaseFactory.dbQuery {
                val q = OffersTable.selectAll()
                q.andWhere { OffersTable.id eq offerId }
                q.andWhere { OffersTable.userId eq userId }
                q.limit(1).singleOrNull() != null
            }
            if (!owned) {
                call.respond(
                    status = HttpStatusCode.NotFound,
                    message = com.example.shoppingassistant.domain.offers.RefreshTrackedOfferResult(
                        status = RefreshTrackedOfferStatus.NOT_FOUND,
                        message = "Offer not found",
                    ),
                )
                return@put
            }

            val result = repo.refreshTrackedOffer(payload)
            val status = when (result.status) {
                RefreshTrackedOfferStatus.UPDATED -> HttpStatusCode.OK
                RefreshTrackedOfferStatus.NOT_FOUND -> HttpStatusCode.NotFound
                RefreshTrackedOfferStatus.INVALID_INPUT -> HttpStatusCode.BadRequest
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
