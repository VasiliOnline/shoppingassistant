package com.example.shoppingassistant.core.rank

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.ProductDto

/**
 * Веса для агрегирования компонент ранжирования.
 */
data class Weights(
    val price: Float,
    val delivery: Float,
    val rating: Float,
    val attrPenalty: Float
)

/**
 * Разложение итогового score по компонентам — удобно для отладки/объяснения.
 */
data class ScoreBreakdown(
    val price: Float,
    val delivery: Float,
    val rating: Float,
    val penalties: Float,
    val score: Float
)

/**
 * Контракт движка скоринга.
 */
interface ScoringEngine {
    /**
     * @param dto       кандидат оффера (ProductDto)
     * @param q         нормализованный запрос пользователя
     * @param avgPrice  средняя цена по пулу кандидатов (для нормировки)
     */
    fun score(dto: ProductDto, q: NormalizedQuery, avgPrice: Double): ScoreBreakdown
}
