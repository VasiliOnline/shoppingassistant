// Last synced: 2026-02-05 12:10:00
package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.data.Normalizer
import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ReasonFormatter
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.BrandFacet
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSource
import com.example.shoppingassistant.domain.model.ProductDto

data class SearchOffersWithFacetsResult(
    val items: List<ExplainedItem>,
    val total: Int,
    val brandFacets: List<BrandFacet>,
)

class SearchOffersWithFacetsUseCase(
    private val remote: OfferRemoteDataSource,
    private val rankService: RankService,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        req: OfferSearchWithFacetsRequest,
    ): SearchOffersWithFacetsResult {
        val bearer = authRepository.currentToken()
        val response = remote.searchWithFacets(req, bearerToken = bearer)
        val offers = response.offers

        if (offers.isEmpty()) {
            return SearchOffersWithFacetsResult(
                items = emptyList(),
                total = response.total,
                brandFacets = response.facets.brands,
            )
        }

        val dtos = offers.map { it.toProductDto() }
        val breakdowns = rankService.breakdownFor(dtos, req.criteria.toNormalizedQuery())

        val items = offers.indices.map { idx ->
            val (_, breakdown) = breakdowns[idx]
            ExplainedItem(
                dto = dtos[idx],
                breakdown = breakdown,
                reasons = ReasonFormatter.reasons(breakdown),
            )
        }

        return SearchOffersWithFacetsResult(
            items = items,
            total = response.total,
            brandFacets = response.facets.brands,
        )
    }
}

private fun OfferSearchCriteria.toNormalizedQuery(): NormalizedQuery =
    NormalizedQuery(
        brand = brand ?: brands.firstOrNull().orEmpty(),
        model = model ?: "",
        attributes = attributes,
    )

private fun OfferFull.toProductDto(): ProductDto =
    ProductDto(
        id = id,
        title = product.title,
        brand = product.brand,
        brandId = product.brand?.let { Normalizer.key(it) }?.takeIf { it.isNotBlank() },
        model = product.model,
        price = price.toMajor(),
        deliveryTime = null,
        sellerRating = seller.rating?.value,
        sellerRatingCount = seller.rating?.count,
        sellerCountry = seller.countryCode,
        sellerCity = seller.city,
        sellerBadges = seller.preferences.badges.map { it.name },
        sellerShippingCountries = seller.preferences.shippingCountries,
        sellerName = seller.name,
        sellerAvatarUrl = seller.avatarUrl,
        sellerType = null,
        source = OfferSource.EXPRESS,
        sourceName = "Express",
        externalUrl = null,
        imageUrls = (imageUrls + product.imageUrls).filter { it.isNotBlank() }.distinct(),
        updatedAt = updatedAt ?: product.updatedAt,
        trustScore = null,
        distanceKm = distanceKmOrNull(),
    )

private fun OfferFull.distanceKmOrNull(): Float? {
    val raw = attributes["distance_km"]
        ?: attributes["distanceKm"]
        ?: attributes["distance"]
        ?: return null
    return raw.toFloatOrNull()
}
