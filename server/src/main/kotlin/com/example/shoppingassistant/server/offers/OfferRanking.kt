package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.asDoubleOrNull
import java.time.LocalDate

/**
 * Заглушка ранжирования: пока просто пробрасываем список.
 * TODO: интегрировать RankService с учетом userCountry/userLanguage и веса цен/рейтинга.
 */
object OfferRanking {
    fun rank(offers: List<OfferFull>, sort: OfferSort): List<OfferFull> = when (sort) {
        OfferSort.RANK -> offers // plug RankService here
        OfferSort.PRICE_ASC -> offers.sortedBy { it.price.minor }
        OfferSort.PRICE_DESC -> offers.sortedByDescending { it.price.minor }
        OfferSort.RATING_DESC -> offers.sortedByDescending { it.seller.rating?.value ?: 0.0 }
        OfferSort.DELIVERY_ASC, OfferSort.DISTANCE_ASC -> offers.sortedBy { it.distanceKmOrNull() ?: Double.MAX_VALUE }
        OfferSort.NEWEST -> offers.sortedByDescending { it.updatedAt ?: 0L }
        OfferSort.MODEL_FRESHNESS_DESC -> offers.sortedWith(
            compareByDescending<OfferFull> { it.modelFreshnessKey() }
                .thenByDescending { it.updatedAt ?: 0L },
        )
    }

    private fun OfferFull.modelFreshnessKey(): Long =
        product.specs["release_date"]
            ?.asRawString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?.let { date -> date.year * 10_000L + date.monthValue * 100L + date.dayOfMonth }
            ?: product.specs["release_year"]
                ?.asDoubleOrNull()
                ?.toLong()
                ?.let { year -> year * 10_000L }
            ?: Long.MIN_VALUE

    private fun OfferFull.distanceKmOrNull(): Double? {
        val raw = attributes["distance_km"]
            ?: attributes["distanceKm"]
            ?: attributes["distance"]
            ?: return null
        return raw.asDoubleOrNull()
    }
}
