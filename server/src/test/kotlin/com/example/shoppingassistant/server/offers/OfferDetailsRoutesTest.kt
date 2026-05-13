package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.OfferDetailCapabilities
import com.example.shoppingassistant.domain.model.OfferDetailState
import com.example.shoppingassistant.domain.model.OfferDetailsPage
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferProvenance
import com.example.shoppingassistant.domain.model.OfferRelatedOffer
import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.ProductFull
import com.example.shoppingassistant.domain.model.UserPreferences
import com.example.shoppingassistant.domain.model.UserProfile
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class OfferDetailsRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var offerRepository: StubOfferDetailsRepository

    @Before
    fun setUp() {
        offerRepository = StubOfferDetailsRepository(
            page = samplePage("offer-42"),
        )
        startKoin {
            modules(
                module {
                    single<OfferRepository> { offerRepository }
                    single<PresetObservabilityRepository> { NoopPresetObservabilityRepositoryForDetails() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun details_endpoint_returns_offer_page_payload() = testApplication {
        application {
            configureSerialization()
            routing { offerRoutes() }
        }

        val response = client.get("/api/offers/offer-42")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("offer-42", offerRepository.lastRequestedId)

        val payload = json.decodeFromString(
            deserializer = OfferDetailsPage.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("offer-42", payload.offer.id)
        assertEquals(OfferDetailState.EXTERNAL_CLAIMED, payload.detailState)
        assertEquals("Avito", payload.provenance.sourceName)
        assertNotNull(payload.provenance.sourceUrl)
        assertEquals(1, payload.relatedOffers.size)
    }

    @Test
    fun details_endpoint_returns_404_when_offer_missing() = testApplication {
        offerRepository.page = null

        application {
            configureSerialization()
            routing { offerRoutes() }
        }

        val response = client.get("/api/offers/missing-offer")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("missing-offer", offerRepository.lastRequestedId)
    }
}

private class StubOfferDetailsRepository(
    var page: OfferDetailsPage?,
) : OfferRepository {
    var lastRequestedId: String? = null

    override suspend fun searchOffers(criteria: OfferSearchCriteria): List<OfferFull> = emptyList()

    override suspend fun searchOffersWithFacets(
        req: OfferSearchWithFacetsRequest,
    ): OfferSearchWithFacetsResponse = OfferSearchWithFacetsResponse(
        offers = emptyList(),
        total = 0,
    )

    override suspend fun getOfferDetails(offerId: String): OfferDetailsPage? {
        lastRequestedId = offerId
        return page?.takeIf { it.offer.id == offerId }
    }
}

private class NoopPresetObservabilityRepositoryForDetails : PresetObservabilityRepository {
    override suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse = PresetObservabilityBatchResponse(
        acceptedCount = 0,
        dedupedCount = 0,
        rejectedCount = 0,
    )
}

private fun samplePage(offerId: String): OfferDetailsPage = OfferDetailsPage(
    offer = OfferFull(
        id = offerId,
        product = ProductFull(
            id = "product-$offerId",
            brand = "Apple",
            model = "iPhone 15 Pro",
            title = "Apple iPhone 15 Pro",
        ),
        seller = UserProfile(
            id = "seller-42",
            name = "Seller 42",
            avatarUrl = null,
            countryCode = "RU",
            city = "Moscow",
            preferences = UserPreferences(),
        ),
        price = Money.fromMajor(129_990.0),
        currency = "RUB",
    ),
    categoryCode = "TECH.PHONES",
    detailState = OfferDetailState.EXTERNAL_CLAIMED,
    provenance = OfferProvenance(
        sourceName = "Avito",
        sourceUrl = "https://www.avito.ru/items/$offerId",
        canonicalUrl = "https://www.avito.ru/items/$offerId",
        sourceDomain = "avito.ru",
        sourceType = "AVITO",
    ),
    capabilities = OfferDetailCapabilities(
        canChat = true,
        canOpenSource = true,
        canTrackPrice = true,
    ),
    relatedOffers = listOf(
        OfferRelatedOffer(
            id = "related-$offerId",
            title = "Similar $offerId",
            price = Money.fromMajor(124_990.0),
            currency = "RUB",
            imageUrl = "https://example.com/$offerId.jpg",
            sourceName = "Avito",
        ),
    ),
)
