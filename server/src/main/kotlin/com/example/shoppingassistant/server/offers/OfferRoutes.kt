package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.getKoin

fun Route.offerRoutes() {
    val repo: OfferRepository = getKoin().get()

    route("/api/offers") {
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
    }
}
