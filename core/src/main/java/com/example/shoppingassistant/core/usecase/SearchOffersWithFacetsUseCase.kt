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
import com.example.shoppingassistant.domain.model.asTextOrNull
import com.example.shoppingassistant.domain.model.ValueFacet
import com.example.shoppingassistant.domain.model.asFloatOrNull
import java.net.URI

data class SearchOffersWithFacetsResult(
    val items: List<ExplainedItem>,
    val total: Int,
    val brandFacets: List<BrandFacet>,
    val conditionFacets: List<ValueFacet>,
    val deliveryChannelFacets: List<ValueFacet>,
    val attributeFacets: Map<String, List<ValueFacet>> = emptyMap(),
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
                conditionFacets = response.facets.conditions,
                deliveryChannelFacets = response.facets.deliveryChannels,
                attributeFacets = response.facets.attributes,
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
            conditionFacets = response.facets.conditions,
            deliveryChannelFacets = response.facets.deliveryChannels,
            attributeFacets = response.facets.attributes,
        )
    }
}

private fun OfferSearchCriteria.toNormalizedQuery(): NormalizedQuery =
    NormalizedQuery(
        brand = brand ?: brands.firstOrNull().orEmpty(),
        model = model ?: "",
        attributes = attributes,
    )

private fun OfferFull.toProductDto(): ProductDto {
    val offerOpenUrls = resolveFacetSearchOfferOpenUrls()
    val externalUrl = offerOpenUrls.externalUrl
    return ProductDto(
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
        source = if (externalUrl == null) OfferSource.EXPRESS else OfferSource.EXTERNAL,
        sourceName = sourceNameFromUrl(externalUrl) ?: "Express",
        externalUrl = externalUrl,
        redirectUrl = offerOpenUrls.redirectUrl,
        deeplinkUrl = offerOpenUrls.deeplinkUrl,
        imageUrls = (imageUrls + product.imageUrls).filter { it.isNotBlank() }.distinct(),
        updatedAt = updatedAt ?: product.updatedAt,
        trustScore = null,
        distanceKm = distanceKmOrNull(),
    )
}

private data class FacetSearchOfferOpenUrls(
    val externalUrl: String?,
    val redirectUrl: String?,
    val deeplinkUrl: String?,
)

private fun OfferFull.resolveFacetSearchOfferOpenUrls(): FacetSearchOfferOpenUrls {
    fun pick(keys: List<String>): String? {
        keys.forEach { key ->
            val direct = attributes[key]?.asTextOrNull()?.trim()?.takeIf { value -> value.isNotEmpty() }
                ?: attributes.entries
                    .firstOrNull { (attrKey, _) -> attrKey.equals(key, ignoreCase = true) }
                    ?.value
                    ?.asTextOrNull()
                    ?.trim()
                    ?.takeIf { value -> value.isNotEmpty() }
            if (direct != null) return direct
        }
        return null
    }

    val redirectUrl = pick(listOf("redirect_url", "redirectUrl", "track_url", "trackUrl"))
    val deeplinkUrl = pick(
        listOf(
            "deeplink_url",
            "deeplinkUrl",
            "deeplink",
            "external_url",
            "externalUrl",
            "source_url",
            "sourceUrl",
            "url",
        ),
    )
    val externalUrl = redirectUrl ?: deeplinkUrl

    return FacetSearchOfferOpenUrls(
        externalUrl = externalUrl,
        redirectUrl = redirectUrl,
        deeplinkUrl = deeplinkUrl,
    )
}

private fun sourceNameFromUrl(url: String?): String? {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { URI(raw).host?.removePrefix("www.") }.getOrNull()
}

private fun OfferFull.distanceKmOrNull(): Float? {
    val raw = attributes["distance_km"]
        ?: attributes["distanceKm"]
        ?: attributes["distance"]
        ?: return null
    return raw.asFloatOrNull()
}
