package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.OfferDetailsPage

data class OfferDetailsScreenData(
    val page: OfferDetailsPage,
    val viewerOwnsOffer: Boolean,
)

class GetOfferDetailsUseCase(
    private val remote: OfferRemoteDataSource,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(offerId: String): OfferDetailsScreenData? {
        val bearer = authRepository.currentToken()
        val page = remote.getOfferDetails(offerId, bearerToken = bearer) ?: return null
        val currentUserId = runCatching { authRepository.getCurrentUser()?.id?.toString() }.getOrNull()
        return OfferDetailsScreenData(
            page = page,
            viewerOwnsOffer = currentUserId != null && currentUserId == page.offer.seller.id,
        )
    }
}
