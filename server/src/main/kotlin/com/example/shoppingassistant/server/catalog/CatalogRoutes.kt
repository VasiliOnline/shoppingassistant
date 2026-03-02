package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.getKoin

fun Route.catalogRoutes() {
    val repository: CatalogRepository = getKoin().get()

    route("/api/catalog") {
        get("/categories") {
            call.respond(repository.listCategories())
        }

        get("/profiles/{categoryCode}") {
            val categoryCode = call.parameters["categoryCode"]?.trim().orEmpty()
            if (categoryCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "categoryCode is required")
                return@get
            }
            val profile = repository.getCategoryProfile(categoryCode)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(profile)
            }
        }

        get("/dictionaries/{attributeCode}") {
            val attributeCode = call.parameters["attributeCode"]?.trim().orEmpty()
            if (attributeCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "attributeCode is required")
                return@get
            }
            val dictionary = repository.listAttributeValueDict(attributeCode)
            if (dictionary == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(dictionary)
            }
        }

        get("/constraints") {
            val categoryCode = call.request.queryParameters["categoryCode"]?.trim().orEmpty()
            if (categoryCode.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "categoryCode is required")
                return@get
            }
            val brand = call.request.queryParameters["brand"]?.trim()?.ifBlank { null }
            val model = call.request.queryParameters["model"]?.trim()?.ifBlank { null }
            val constraints = repository.listConstraints(
                categoryCode = categoryCode,
                brand = brand,
                model = model,
            )
            call.respond(constraints)
        }
    }
}
