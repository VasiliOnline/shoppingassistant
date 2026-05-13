// Last synced: 2026-02-05 12:10:00
package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.core.data.offers.toNormalizedQuery
import com.example.shoppingassistant.core.data.offers.toProductDto
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ReasonFormatter
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.BrandFacet
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.ValueFacet

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
