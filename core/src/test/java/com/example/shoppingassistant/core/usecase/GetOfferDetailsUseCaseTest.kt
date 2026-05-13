package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.AuthError
import com.example.shoppingassistant.domain.model.AuthResult
import com.example.shoppingassistant.domain.model.AuthUser
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.OfferDetailCapabilities
import com.example.shoppingassistant.domain.model.OfferDetailState
import com.example.shoppingassistant.domain.model.OfferDetailsPage
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferProvenance
import com.example.shoppingassistant.domain.model.OfferRelatedOffer
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.ProductFull
import com.example.shoppingassistant.domain.model.UserPreferences
import com.example.shoppingassistant.domain.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetOfferDetailsUseCaseTest {

    @Test
    fun returns_owner_view_and_forwards_auth_token() = runBlocking {
        val remote = FakeOfferRemoteDataSource(
            page = samplePage(
                offerId = "offer-42",
                sellerId = "42",
            ),
        )
        val useCase = GetOfferDetailsUseCase(
            remote = remote,
            authRepository = FakeAuthRepository(
                token = "token-123",
                currentUser = sampleAuthUser(id = 42L),
            ),
        )

        val result = useCase("offer-42")

        requireNotNull(result)
        assertTrue(result.viewerOwnsOffer)
        assertEquals("offer-42", result.page.offer.id)
        assertEquals("offer-42", remote.lastOfferId)
        assertEquals("token-123", remote.lastBearer)
    }

    @Test
    fun returns_null_when_remote_has_no_page() = runBlocking {
        val remote = FakeOfferRemoteDataSource(page = null)
        val useCase = GetOfferDetailsUseCase(
            remote = remote,
            authRepository = FakeAuthRepository(
                token = "token-456",
                currentUser = sampleAuthUser(id = 77L),
            ),
        )

        val result = useCase("missing-offer")

        assertNull(result)
        assertEquals("missing-offer", remote.lastOfferId)
        assertEquals("token-456", remote.lastBearer)
        assertFalse(remote.logoutCalled)
    }

    private class FakeOfferRemoteDataSource(
        private val page: OfferDetailsPage?,
    ) : OfferRemoteDataSource {
        var lastOfferId: String? = null
        var lastBearer: String? = null
        var logoutCalled: Boolean = false

        override suspend fun search(
            criteria: OfferSearchCriteria,
            bearerToken: String?,
        ): List<OfferFull> = emptyList()

        override suspend fun getOfferDetails(
            offerId: String,
            bearerToken: String?,
        ): OfferDetailsPage? {
            lastOfferId = offerId
            lastBearer = bearerToken
            return page?.takeIf { it.offer.id == offerId }
        }

        override suspend fun searchWithFacets(
            req: OfferSearchWithFacetsRequest,
            bearerToken: String?,
        ): OfferSearchWithFacetsResponse = OfferSearchWithFacetsResponse(
            offers = emptyList(),
            total = 0,
        )

        override suspend fun submitPresetObservabilityBatch(
            req: PresetObservabilityBatchRequest,
            bearerToken: String?,
        ): PresetObservabilityBatchResponse = PresetObservabilityBatchResponse(
            acceptedCount = 0,
            dedupedCount = 0,
            rejectedCount = 0,
        )
    }

    private class FakeAuthRepository(
        private val token: String?,
        private val currentUser: AuthUser?,
    ) : AuthRepository {
        override suspend fun register(
            email: String,
            password: String,
            displayName: String?,
        ): AuthResult = AuthResult.Error(AuthError.UNKNOWN)

        override suspend fun login(
            email: String,
            password: String,
        ): AuthResult = AuthResult.Error(AuthError.UNKNOWN)

        override suspend fun getCurrentUser(): AuthUser? = currentUser

        override suspend fun getUserById(id: Long): AuthUser? = null

        override suspend fun logout() = Unit

        override suspend fun currentToken(): String? = token

        override suspend fun changePassword(
            oldPassword: String,
            newPassword: String,
        ): AuthResult = AuthResult.Error(AuthError.UNKNOWN)
    }

    private fun samplePage(
        offerId: String,
        sellerId: String,
    ): OfferDetailsPage = OfferDetailsPage(
        offer = OfferFull(
            id = offerId,
            product = ProductFull(
                id = "product-$offerId",
                brand = "Apple",
                model = "iPhone 15",
                title = "Apple iPhone 15",
            ),
            seller = UserProfile(
                id = sellerId,
                name = "Seller $sellerId",
                avatarUrl = null,
                countryCode = "RU",
                city = "Moscow",
                preferences = UserPreferences(),
            ),
            price = Money.fromMajor(119_990.0),
            currency = "RUB",
        ),
        categoryCode = "TECH.PHONES",
        detailState = OfferDetailState.EXTERNAL_CLAIMED,
        provenance = OfferProvenance(
            sourceName = "Avito",
            sourceUrl = "https://www.avito.ru/items/$offerId",
            sourceDomain = "avito.ru",
        ),
        capabilities = OfferDetailCapabilities(
            canChat = true,
            canOpenSource = true,
            canTrackPrice = true,
        ),
        relatedOffers = listOf(
            OfferRelatedOffer(
                id = "related-$offerId",
                title = "Related $offerId",
                price = Money.fromMajor(109_990.0),
                currency = "RUB",
            ),
        ),
    )

    private fun sampleAuthUser(id: Long): AuthUser = AuthUser(
        id = id,
        email = "user$id@example.com",
        displayName = "User $id",
        phone = null,
        avatarUrl = null,
        city = "Moscow",
    )
}
