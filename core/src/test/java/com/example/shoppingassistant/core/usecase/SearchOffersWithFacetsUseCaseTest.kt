package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ScoreBreakdown
import com.example.shoppingassistant.core.rank.ScoringEngine
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchFacets
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.OfferSource
import com.example.shoppingassistant.domain.model.ProductFull
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.BrandFacet
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.UserPreferences
import com.example.shoppingassistant.domain.model.UserProfile
import com.example.shoppingassistant.domain.model.ValueFacet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchOffersWithFacetsUseCaseTest {

    @Test
    fun returns_all_runtime_facets_for_ui_contract() = runBlocking {
        val remote = FakeRemoteDataSource(
            response = OfferSearchWithFacetsResponse(
                offers = emptyList(),
                total = 37,
                facets = OfferSearchFacets(
                    brands = listOf(BrandFacet(id = "apple", name = "Apple", count = 11)),
                    conditions = listOf(ValueFacet(id = "new", name = "Новый", count = 19)),
                    deliveryChannels = listOf(ValueFacet(id = "pickup", name = "Самовывоз", count = 7)),
                    attributes = mapOf(
                        "color" to listOf(
                            ValueFacet(id = "black", name = "black", count = 5),
                        ),
                    ),
                ),
            ),
        )
        val useCase = SearchOffersWithFacetsUseCase(
            remote = remote,
            rankService = RankService(FakeScoringEngine),
            authRepository = FakeAuthRepository(token = "token-123"),
        )

        val result = useCase(
            OfferSearchWithFacetsRequest(
                criteria = OfferSearchCriteria(
                    brand = null,
                    model = null,
                ),
            ),
        )

        assertEquals(37, result.total)
        assertEquals(1, result.brandFacets.size)
        assertEquals(1, result.conditionFacets.size)
        assertEquals(1, result.deliveryChannelFacets.size)
        assertEquals(1, result.attributeFacets.size)
        assertEquals("black", result.attributeFacets["color"]?.firstOrNull()?.name)
        assertEquals("token-123", remote.lastBearer)
    }

    @Test
    fun maps_redirect_and_deeplink_urls_with_redirect_priority() = runBlocking {
        val redirectUrl = "https://redirect.example/offer/1"
        val deeplinkUrl = "https://deeplink.example/offer/1"
        val remote = FakeRemoteDataSource(
            response = OfferSearchWithFacetsResponse(
                offers = listOf(
                    offerWithAttributes(
                        id = "offer-1",
                        attributes = mapOf(
                            "redirect_url" to TypedAttributeValue.Text(redirectUrl),
                            "deeplink_url" to TypedAttributeValue.Text(deeplinkUrl),
                        ),
                    ),
                    offerWithAttributes(
                        id = "offer-2",
                        attributes = mapOf(
                            "DeepLink_Url" to TypedAttributeValue.Text(deeplinkUrl),
                        ),
                    ),
                ),
                total = 2,
                facets = OfferSearchFacets(),
            ),
        )
        val useCase = SearchOffersWithFacetsUseCase(
            remote = remote,
            rankService = RankService(FakeScoringEngine),
            authRepository = FakeAuthRepository(token = "token-456"),
        )

        val result = useCase(
            OfferSearchWithFacetsRequest(
                criteria = OfferSearchCriteria(
                    brand = "Apple",
                    model = "iPhone",
                ),
            ),
        )

        val first = result.items.first().dto
        val second = result.items[1].dto
        assertEquals(redirectUrl, first.redirectUrl)
        assertEquals(deeplinkUrl, first.deeplinkUrl)
        assertEquals(redirectUrl, first.externalUrl)
        assertEquals(OfferSource.EXTERNAL, first.source)

        assertEquals(null, second.redirectUrl)
        assertEquals(deeplinkUrl, second.deeplinkUrl)
        assertEquals(deeplinkUrl, second.externalUrl)
        assertEquals(OfferSource.EXTERNAL, second.source)
        assertEquals("token-456", remote.lastBearer)
    }

    private class FakeRemoteDataSource(
        private val response: OfferSearchWithFacetsResponse,
    ) : OfferRemoteDataSource {
        var lastBearer: String? = null

        override suspend fun search(
            criteria: OfferSearchCriteria,
            bearerToken: String?,
        ) = emptyList<com.example.shoppingassistant.domain.model.OfferFull>()

        override suspend fun searchWithFacets(
            req: OfferSearchWithFacetsRequest,
            bearerToken: String?,
        ): OfferSearchWithFacetsResponse {
            lastBearer = bearerToken
            return response
        }

        override suspend fun submitPresetObservabilityBatch(
            req: PresetObservabilityBatchRequest,
            bearerToken: String?,
        ): PresetObservabilityBatchResponse {
            return PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = 0,
                rejectedEventKeys = emptyList(),
            )
        }
    }

    private class FakeAuthRepository(
        private val token: String?,
    ) : AuthRepository {
        override suspend fun register(email: String, password: String, displayName: String?): AuthResult =
            AuthResult.Error(AuthError.UNKNOWN)

        override suspend fun login(email: String, password: String): AuthResult =
            AuthResult.Error(AuthError.UNKNOWN)

        override suspend fun getCurrentUser(): AuthUser? = null

        override suspend fun getUserById(id: Long): AuthUser? = null

        override suspend fun logout() = Unit

        override suspend fun currentToken(): String? = token

        override suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult =
            AuthResult.Error(AuthError.UNKNOWN)
    }

    private object FakeScoringEngine : ScoringEngine {
        override fun score(
            dto: ProductDto,
            q: NormalizedQuery,
            avgPrice: Double,
        ): ScoreBreakdown = ScoreBreakdown(
            price = 0f,
            delivery = 0f,
            rating = 0f,
            penalties = 0f,
            score = 0f,
        )
    }

    private fun offerWithAttributes(
        id: String,
        attributes: Map<String, TypedAttributeValue>,
    ): OfferFull = OfferFull(
        id = id,
        product = ProductFull(
            id = "product-$id",
            brand = "Apple",
            model = "iPhone",
            title = "Apple iPhone",
        ),
        seller = UserProfile(
            id = "seller-$id",
            name = "Seller",
            avatarUrl = null,
            countryCode = "RU",
            city = "Moscow",
            preferences = UserPreferences(),
        ),
        price = Money.fromMajor(99_990.0),
        currency = "RUB",
        attributes = attributes,
    )
}
