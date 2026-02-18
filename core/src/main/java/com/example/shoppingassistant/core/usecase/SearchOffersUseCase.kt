// Last synced: 2025-12-19 15:12:21
package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.data.Normalizer
import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ReasonFormatter
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSource
import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.ProductDto

/**
 * Use-case удалённого поиска офферов через backend API.
 * Возвращает те же ExplainedItem, что и локальный поиск, чтобы UI не менять.
 */
class SearchOffersUseCase(
    private val remote: OfferRemoteDataSource,
    private val rankService: RankService,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        criteria: OfferSearchCriteria,
        limit: Int = criteria.limit,
    ): List<ExplainedItem> {
        val bearer = authRepository.currentToken()
        val effectiveCriteria = if (limit == criteria.limit) criteria else criteria.copy(limit = limit)

        val offers = runCatching {
            remote.search(effectiveCriteria, bearerToken = bearer)
        }.getOrElse {
            emptyList()
        }

        if (offers.isEmpty()) return emptyList()

        val dtos = offers.map { it.toProductDto() }
        val breakdowns = rankService.breakdownFor(dtos, effectiveCriteria.toNormalizedQuery())

        return offers.indices.map { idx ->
            val (_, breakdown) = breakdowns[idx]
            ExplainedItem(
                dto = dtos[idx],
                breakdown = breakdown,
                reasons = ReasonFormatter.reasons(breakdown),
            )
        }
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
        id = id, // используем id оффера как ключ и для алертов
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
        sellerType = null, // backend пока не отдаёт тип продавца
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
