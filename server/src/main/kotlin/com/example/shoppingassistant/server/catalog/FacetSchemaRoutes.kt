package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.getKoin

fun Route.facetSchemaRoutes() {
    val definitionRepository: FacetDefinitionRepository = getKoin().get()
    val presetRepository: FacetPresetRepository = getKoin().get()
    val collectionRepository: FacetCollectionRepository = getKoin().get()

    route("/api/catalog/facets") {
        get("/definitions") {
            val categoryCode = call.request.queryParameters["categoryCode"]?.trim().orEmpty()
            val result = if (categoryCode.isBlank()) {
                definitionRepository.listFacetDefinitions()
            } else {
                definitionRepository.listFacetDefinitions(categoryCode)
            }
            call.respond(result)
        }

        get("/definitions/{facetKey}") {
            val facetKey = call.parameters["facetKey"]?.trim().orEmpty()
            if (facetKey.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "facetKey is required")
                return@get
            }
            val result = definitionRepository.getFacetDefinition(facetKey)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(result)
            }
        }

        get("/presets") {
            val categoryCode = call.request.queryParameters["categoryCode"]?.trim().orEmpty()
            val result = if (categoryCode.isBlank()) {
                presetRepository.listFacetPresets()
            } else {
                presetRepository.listFacetPresets(categoryCode)
            }
            call.respond(result)
        }

        get("/presets/{presetCode}") {
            val presetCode = call.parameters["presetCode"]?.trim().orEmpty()
            if (presetCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "presetCode is required")
                return@get
            }
            val result = presetRepository.getFacetPreset(presetCode)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(result)
            }
        }

        get("/collections") {
            val categoryCode = call.request.queryParameters["categoryCode"]?.trim().orEmpty()
            val result = if (categoryCode.isBlank()) {
                collectionRepository.listFacetCollections()
            } else {
                collectionRepository.listFacetCollections(categoryCode)
            }
            call.respond(result)
        }

        get("/collections/{collectionCode}") {
            val collectionCode = call.parameters["collectionCode"]?.trim().orEmpty()
            if (collectionCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "collectionCode is required")
                return@get
            }
            val result = collectionRepository.getFacetCollection(collectionCode)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(result)
            }
        }

        get("/collections/by-browse/{browseCode}") {
            val browseCode = call.parameters["browseCode"]?.trim().orEmpty()
            if (browseCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "browseCode is required")
                return@get
            }
            val result = collectionRepository.getFacetCollectionByBrowseCode(browseCode)
            if (result == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(result)
            }
        }
    }
}
