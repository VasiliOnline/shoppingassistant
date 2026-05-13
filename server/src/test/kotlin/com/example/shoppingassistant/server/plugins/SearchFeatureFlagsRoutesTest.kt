package com.example.shoppingassistant.server.plugins

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchFeatureFlagsRoutesTest {

    @Test
    fun exposes_default_search_feature_flags_contract() = testApplication {
        application {
            configureSerialization()
            routing {
                searchFeatureFlagsRoutes()
            }
        }

        val response = client.get("/api/config/search-feature-flags?reason=results_open")
        assertEquals(HttpStatusCode.OK, response.status)

        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<SearchFeatureFlagsPayload>(response.bodyAsText())

        assertTrue(payload.values.containsKey("SEARCH_MAP_ENABLED"))
        assertTrue(payload.values.containsKey("SEARCH_SORT_SHEET_EXPLICIT_APPLY"))
        assertTrue(payload.values.containsKey("FILTERS_GEO_RADIUS_SLIDER"))
        assertTrue(payload.values.containsKey("RESULTS_LOADING_PROGRESSIVE"))
    }
}

@Serializable
private data class SearchFeatureFlagsPayload(
    val fetchedAtMs: Long,
    val source: String,
    val values: Map<String, Boolean>,
)
