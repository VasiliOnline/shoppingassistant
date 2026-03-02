package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.ProductFull
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.UserPreferences
import com.example.shoppingassistant.domain.model.UserProfile
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class OfferTypedSearchRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var offerRepository: CapturingTypedOfferRepository

    @Before
    fun setUp() {
        offerRepository = CapturingTypedOfferRepository()
        startKoin {
            modules(
                module {
                    single<OfferRepository> { offerRepository }
                    single<PresetObservabilityRepository> { NoopPresetObservabilityRepository() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun search_endpoint_accepts_typed_attributes_and_returns_typed_payload() = testApplication {
        application {
            configureSerialization()
            routing { offerRoutes() }
        }

        val requestBody = """
            {
              "brand": "Apple",
              "model": "iPhone",
              "attributes": {
                "ram_gb": 8,
                "wireless": true,
                "color": "black"
              }
            }
        """.trimIndent()

        val response = client.post("/api/offers/search") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val captured = offerRepository.lastCriteria
        assertNotNull("Expected repository to receive criteria", captured)
        assertEquals(TypedAttributeValue.Number(8.0), captured?.attributes?.get("ram_gb"))
        assertEquals(TypedAttributeValue.Bool(true), captured?.attributes?.get("wireless"))
        assertEquals(TypedAttributeValue.Text("black"), captured?.attributes?.get("color"))

        val payload = json.decodeFromString(
            deserializer = ListSerializer(OfferFull.serializer()),
            string = response.bodyAsText(),
        )
        assertEquals(1, payload.size)

        val first = payload.first()
        assertTrue(first.attributes["ram_gb"] is TypedAttributeValue.Number)
        assertTrue(first.attributes["wireless"] is TypedAttributeValue.Bool)
        assertTrue(first.product.specs["wireless"] is TypedAttributeValue.Bool)
        assertTrue(first.product.specs["ram_gb"] is TypedAttributeValue.Number)
    }
}

private class CapturingTypedOfferRepository : OfferRepository {
    var lastCriteria: OfferSearchCriteria? = null

    override suspend fun searchOffers(criteria: OfferSearchCriteria): List<OfferFull> {
        lastCriteria = criteria
        return listOf(
            OfferFull(
                id = "offer-typed-1",
                product = ProductFull(
                    id = "product-typed-1",
                    brand = "Apple",
                    model = "iPhone",
                    title = "iPhone typed",
                    specs = mapOf(
                        "ram_gb" to TypedAttributeValue.Number(8.0),
                        "wireless" to TypedAttributeValue.Bool(true),
                    ),
                ),
                seller = UserProfile(
                    id = "seller-1",
                    name = "Seller",
                    avatarUrl = null,
                    countryCode = "RU",
                    city = "Moscow",
                    preferences = UserPreferences(),
                ),
                price = Money(100_000),
                currency = "RUB",
                attributes = mapOf(
                    "ram_gb" to TypedAttributeValue.Number(8.0),
                    "wireless" to TypedAttributeValue.Bool(true),
                    "color" to TypedAttributeValue.Text("black"),
                ),
            ),
        )
    }

    override suspend fun searchOffersWithFacets(req: OfferSearchWithFacetsRequest): OfferSearchWithFacetsResponse =
        OfferSearchWithFacetsResponse(
            offers = searchOffers(req.criteria),
            total = 1,
        )
}

private class NoopPresetObservabilityRepository : PresetObservabilityRepository {
    override suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse = PresetObservabilityBatchResponse(
        acceptedCount = 0,
        dedupedCount = 0,
        rejectedCount = 0,
    )
}
