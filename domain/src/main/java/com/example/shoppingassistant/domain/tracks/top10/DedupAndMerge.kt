package com.example.shoppingassistant.domain.tracks.top10

import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.tracks.Badge
import com.example.shoppingassistant.domain.tracks.RankedOffer
import kotlin.math.max

object DedupAndMerge {

    fun computeDedupKey(offer: OfferFull): String {
        val offerId = offer.id.trim()
        if (offerId.isNotBlank()) return offerId

        val brand = normalizeKey(offer.product.brand)
        val model = normalizeKey(offer.product.model)
        val sellerId = normalizeKey(offer.seller.id)
        val price = max(0L, offer.price.minor)
        return listOf(brand, model, sellerId, price.toString()).joinToString("|")
    }

    fun mergeRanked(offers: List<RankedOffer>): List<RankedOffer> {
        if (offers.isEmpty()) return offers

        val firstIndex = HashMap<String, Int>(offers.size)
        val groups = LinkedHashMap<String, MutableList<RankedOffer>>()
        offers.forEachIndexed { index, offer ->
            val key = offer.dedupKey.takeIf { it.isNotBlank() } ?: offer.offerId
            val list = groups.getOrPut(key) { mutableListOf() }
            list += offer
            firstIndex.putIfAbsent(key, index)
        }

        val merged = groups.map { (key, group) ->
            val winner = pickWinner(group)
            val enriched = if (group.size > 1) {
                val badge = Badge(label = "Объединено: ${group.size} источников", priority = 0)
                winner.copy(badges = winner.badges + badge)
            } else {
                winner
            }
            key to enriched
        }

        return merged
            .sortedBy { (key, _) -> firstIndex[key] ?: Int.MAX_VALUE }
            .map { it.second }
    }

    private fun pickWinner(group: List<RankedOffer>): RankedOffer {
        return group.minWith(
            compareBy<RankedOffer> { it.price.minor }
                .thenByDescending { it.trustScore ?: 0f }
                .thenBy { it.offerId }
        )
    }

    private fun normalizeKey(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isBlank()) return ""
        return cleaned.lowercase().replace(Regex("\\s+"), " ")
    }
}
