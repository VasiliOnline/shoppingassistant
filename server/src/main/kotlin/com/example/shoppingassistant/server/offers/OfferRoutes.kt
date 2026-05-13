package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.getKoin

fun Route.offerRoutes() {
    val repo: OfferRepository = getKoin().get()
    val presetObservabilityRepository: PresetObservabilityRepository = getKoin().get()

    route("/api/offers") {
        get("/{id}") {
            val offerId = call.parameters["id"].orEmpty()
            val result = repo.getOfferDetails(offerId)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(result)
            }
        }
        post("/search") {
            val criteria = call.receive<OfferSearchCriteria>()
            val result = repo.searchOffers(criteria)
            call.respond(result)
        }
        post("/searchWithFacets") {
            val req = call.receive<OfferSearchWithFacetsRequest>()
            val result = repo.searchOffersWithFacets(req)
            call.respond(result)
        }

        post("/observability/preset-events/batch") {
            val req = call.receive<PresetObservabilityBatchRequest>()
            if (req.events.isEmpty()) {
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = PresetObservabilityBatchResponse(
                        acceptedCount = 0,
                        dedupedCount = 0,
                        rejectedCount = 1,
                        rejectedEventKeys = listOf("BATCH_EMPTY"),
                    ),
                )
                return@post
            }
            val result = presetObservabilityRepository.ingestBatch(req)
            call.respond(result)
        }
    }
}
