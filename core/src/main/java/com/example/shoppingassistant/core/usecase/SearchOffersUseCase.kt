// Last synced: 2025-12-19 15:12:21
package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.data.offers.OfferRemoteDataSource
import com.example.shoppingassistant.core.data.offers.toNormalizedQuery
import com.example.shoppingassistant.core.data.offers.toProductDto
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ReasonFormatter
import com.example.shoppingassistant.domain.auth.AuthRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria

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
