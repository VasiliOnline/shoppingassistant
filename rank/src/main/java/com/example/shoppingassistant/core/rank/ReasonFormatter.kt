package com.example.shoppingassistant.core.rank

import kotlin.math.roundToInt

/**
 * Преобразует ScoreBreakdown в короткие "человеческие" причины для UI/чата.
 * Без внешних зависимостей; не меняет доменные модели.
 */
object ReasonFormatter {

    /**
     * Возвращает до трёх коротких причин (по одной на сигнал),
     * ранжированных по вкладу (score component) — от большей к меньшей.
     */
    fun reasons(b: ScoreBreakdown): List<String> {
        // Компоненты уже в [0..1]; переводим в проценты вклада
        val parts = listOf(
            "Цена" to b.price,
            "Доставка" to b.delivery,
            "Рейтинг продавца" to b.rating
        ).sortedByDescending { it.second }

        return parts.map { (name, v) ->
            val pct = (v.coerceIn(0f, 1f) * 100).roundToInt()
            when (name) {
                "Цена" -> "Цена выгоднее ~${pct}%"
                "Доставка" -> "Доставка быстрее ~${pct}%"
                "Рейтинг продавца" -> "Рейтинг выше ~${pct}%"
                else -> "$name ~${pct}%"
            }
        }
    }
}
