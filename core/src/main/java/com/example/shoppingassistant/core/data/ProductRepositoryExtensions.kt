// FILE: core_data_ProductRepositoryExtensions.kt
// Last synced: 2025-11-01
package com.example.shoppingassistant.core.data

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ReasonFormatter
import com.example.shoppingassistant.core.rank.ScoreBreakdown

/**
 * Удобная обёртка для чата: берём уже существующий searchTop(..),
 * затем для этих DTO считаем разложение и причины (без изменения интерфейсов).
 */
data class ExplainedItem(
    val dto: ProductDto,
    val breakdown: ScoreBreakdown,
    val reasons: List<String>
)

/**
 * Получить Top-N с "причинами" для чата.
 * Важно: используем RankService.breakdownFor(...) — порядок DTO сохраняется, пересортировки нет.
 */
suspend fun ProductRepository.searchTopExplained(
    q: NormalizedQuery,
    limit: Int,
    rankService: RankService
): List<ExplainedItem> {
    // Уже отсортированный Top-N из репозитория
    val topDtos: List<ProductDto> = this.searchTop(q, limit)

    // Считаем разложение по тем же DTO, сохраняя порядок
    val withBreakdown = rankService.breakdownFor(topDtos, q)

    return withBreakdown.map { (dto, breakdown) ->
        ExplainedItem(
            dto = dto,
            breakdown = breakdown,
            reasons = ReasonFormatter.reasons(breakdown)
        )
    }
}
