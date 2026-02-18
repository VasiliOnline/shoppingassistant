package com.example.shoppingassistant.core.usecase

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ScoreBreakdown

/**
 * UseCase без предположений о репозитории.
 * Принимает уже полученных кандидатов и нормализованный запрос, возвращает Top-3.
 */
class GetTop3FromCandidatesUseCase(
    private val rankService: RankService
) {
    /**
     * @param candidates список кандидатов (например, из твоего репозитория)
     * @param query нормализованный запрос
     * @return Top-3 пар (ProductDto, ScoreBreakdown) в детерминированном порядке
     */
    operator fun invoke(
        candidates: List<ProductDto>,
        query: NormalizedQuery
    ): List<Pair<ProductDto, ScoreBreakdown>> {
        return rankService.topN(candidates, query, n = 3)
    }
}
