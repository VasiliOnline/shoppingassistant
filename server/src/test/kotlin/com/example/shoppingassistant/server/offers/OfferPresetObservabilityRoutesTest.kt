package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityEvent
import com.example.shoppingassistant.domain.model.PresetObservabilityEventType
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class OfferPresetObservabilityRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<OfferRepository> { StubOfferRepository() }
                    single<PresetObservabilityRepository> { StubPresetObservabilityRepository() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun preset_observability_batch_endpoint_accepts_valid_events() = testApplication {
        application {
            configureSerialization()
            routing { offerRoutes() }
        }

        val payload = PresetObservabilityBatchRequest(
            events = listOf(
                PresetObservabilityEvent(
                    idempotencyKey = "imp|qs-1|offer-1|1",
                    eventType = PresetObservabilityEventType.IMPRESSION,
                    querySessionId = "qs-1",
                    categoryCode = "FOOD.READY_MEALS",
                    facetCollectionCode = "B.FOOD.READY",
                    facetPresetCode = "FP.FOOD.READY.DEFAULT",
                    offerId = "offer-1",
                    position = 1,
                    occurredAtMs = 1_738_000_000_000,
                ),
            ),
        )

        val response = client.post("/api/offers/observability/preset-events/batch") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(payload))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString(
            deserializer = PresetObservabilityBatchResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals(1, body.acceptedCount)
        assertEquals(0, body.dedupedCount)
        assertEquals(0, body.rejectedCount)
    }

    @Test
    fun preset_observability_batch_endpoint_rejects_empty_batch() = testApplication {
        application {
            configureSerialization()
            routing { offerRoutes() }
        }

        val payload = PresetObservabilityBatchRequest(events = emptyList())
        val response = client.post("/api/offers/observability/preset-events/batch") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(payload))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString(
            deserializer = PresetObservabilityBatchResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals(0, body.acceptedCount)
        assertEquals(0, body.dedupedCount)
        assertEquals(1, body.rejectedCount)
        assertEquals(listOf("BATCH_EMPTY"), body.rejectedEventKeys)
    }
}

private class StubOfferRepository : OfferRepository {
    override suspend fun searchOffers(criteria: OfferSearchCriteria) = emptyList<com.example.shoppingassistant.domain.model.OfferFull>()

    override suspend fun searchOffersWithFacets(req: OfferSearchWithFacetsRequest): OfferSearchWithFacetsResponse =
        OfferSearchWithFacetsResponse(
            offers = emptyList(),
            total = 0,
        )
}

private class StubPresetObservabilityRepository : PresetObservabilityRepository {
    override suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse {
        if (request.events.isEmpty()) {
            return PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = 1,
                rejectedEventKeys = listOf("BATCH_EMPTY"),
            )
        }
        return PresetObservabilityBatchResponse(
            acceptedCount = request.events.size,
            dedupedCount = 0,
            rejectedCount = 0,
        )
    }
}
