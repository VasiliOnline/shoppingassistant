package com.example.shoppingassistant.server.tracks.top10.refresh

import com.example.shoppingassistant.domain.model.OfferFull
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.tracks.Badge
import com.example.shoppingassistant.domain.tracks.RankExplanation
import com.example.shoppingassistant.domain.tracks.RankedOffer
import com.example.shoppingassistant.domain.tracks.SourceStamp
import com.example.shoppingassistant.domain.tracks.TrackMatchKeyFactory
import com.example.shoppingassistant.domain.tracks.TrackTop10
import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.domain.tracks.top10.DedupAndMerge
import kotlin.math.max
import kotlin.math.min

class TrackTop10SnapshotBuilder(
    private val offerRepository: OfferRepository,
    private val rateLimiter: RateLimiter,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlSec: Int = 300,
) {
    private companion object {
        const val SOURCE_KEY = "db/offers"
        const val NEW_BADGE_WINDOW_MS: Long = 3L * 24 * 60 * 60 * 1000
    }

    suspend fun buildSnapshot(candidate: TrackRefreshCandidate): TrackTop10 {
        val now = clock()
        val categoryCode = candidate.categoryCode?.trim()?.takeIf { it.isNotBlank() }
        val brandModel = if (candidate.type == TrackType.PRODUCT) {
            TrackMatchKeyFactory.parse(candidate.matchKey)
        } else {
            null
        }

        if (candidate.type == TrackType.CATEGORY && categoryCode == null) {
            return emptySnapshot(candidate.trackId, now, "missing-category")
        }
        if (candidate.type == TrackType.PRODUCT && brandModel == null) {
            return emptySnapshot(candidate.trackId, now, "missing-match-key")
        }
        if (candidate.type != TrackType.CATEGORY && candidate.type != TrackType.PRODUCT) {
            return emptySnapshot(candidate.trackId, now, "legacy-type")
        }

        if (!rateLimiter.acquire(SOURCE_KEY)) {
            return emptySnapshot(candidate.trackId, now, "rate-limit")
        }

        val criteria = OfferSearchCriteria(
            brand = brandModel?.first,
            model = brandModel?.second,
            categoryCode = categoryCode,
            attributes = candidate.filters.extra
                .mapKeys { it.key.trim() }
                .mapValues { it.value.trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() },
            userCountry = candidate.userCountry?.trim()?.takeIf { it.isNotBlank() },
            sellerCountryCode = candidate.userCountry?.trim()?.takeIf { it.isNotBlank() },
            limit = 50,
            sort = OfferSort.RANK,
        )

        val offers = offerRepository.searchOffers(criteria)
        val ranked = rankAndBadge(offers, candidate.type == TrackType.CATEGORY, now)
        val deduped = DedupAndMerge.mergeRanked(ranked)
        val topItems = deduped.take(10)

        val explanation = RankExplanation(
            summary = "Top-10 по цели трека и атрибутам",
            details = buildList {
                add("sort: OfferSort.RANK")
                add("ttlSec: $ttlSec")
                if (!categoryCode.isNullOrBlank()) add("categoryCode: $categoryCode")
                if (brandModel != null) add("matchKey: ${candidate.matchKey}")
                if (criteria.attributes.isNotEmpty()) add("attributes: ${criteria.attributes.size}")
                if (!criteria.userCountry.isNullOrBlank()) add("country: ${criteria.userCountry}")
            },
        )

        return TrackTop10(
            trackId = candidate.trackId.toString(),
            computedAt = now,
            freshnessSec = 0,
            items = topItems,
            explanation = explanation,
            sourceStamps = listOf(
                SourceStamp(
                    sourceType = "db",
                    sourceName = "offers",
                    fetchedAt = now,
                    ttlSec = ttlSec,
                ),
            ),
        )
    }

    private fun rankAndBadge(
        offers: List<OfferFull>,
        isCategoryTrack: Boolean,
        now: Long,
    ): List<RankedOffer> {
        if (offers.isEmpty()) return emptyList()
        val prices = offers.map { it.price.minor }.sorted()
        val meanPrice = prices.average()
        val medianPrice = prices[prices.size / 2].toDouble()

        return offers.map { offer ->
            val rating = offer.seller.rating?.value
            val badges = buildList {
                val updatedAt = offer.updatedAt ?: 0L
                if (updatedAt > 0L && now - updatedAt <= NEW_BADGE_WINDOW_MS) {
                    add(Badge(label = "Новинка", priority = 3))
                }
                if (offer.price.minor <= meanPrice * 0.95) {
                    add(Badge(label = "Ниже средней цены", priority = 2))
                }
                if ((rating ?: 0.0) >= 4.5) {
                    add(Badge(label = "Рейтинг продавца", priority = 1))
                }
                if (isCategoryTrack && offer.price.minor <= medianPrice) {
                    add(Badge(label = "Выгодно в категории", priority = 0))
                }
            }
            RankedOffer(
                offerId = offer.id,
                sellerId = offer.seller.id,
                price = offer.price,
                delivery = null,
                trustScore = rating?.toFloat(),
                distanceKm = offer.attributes["distance_km"]?.toFloatOrNull(),
                badges = badges,
                deeplink = "https://example.com/offers/${offer.id}",
                dedupKey = DedupAndMerge.computeDedupKey(offer),
            )
        }
    }

    private fun emptySnapshot(trackId: Long, now: Long, reason: String): TrackTop10 {
        return TrackTop10(
            trackId = trackId.toString(),
            computedAt = 0L,
            freshnessSec = Int.MAX_VALUE,
            items = emptyList(),
            explanation = RankExplanation(
                summary = "Нет данных",
                details = listOf("reason: $reason"),
            ),
            sourceStamps = listOf(
                SourceStamp(
                    sourceType = "empty",
                    sourceName = reason,
                    fetchedAt = now,
                    ttlSec = min(60, ttlSec),
                ),
            ),
        )
    }
}
