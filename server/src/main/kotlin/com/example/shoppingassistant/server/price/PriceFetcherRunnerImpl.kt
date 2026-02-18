package com.example.shoppingassistant.server.price

import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.SourceType
import com.example.shoppingassistant.domain.ingest.UrlNormalizer
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferInput
import com.example.shoppingassistant.domain.offers.RefreshTrackedOfferStatus
import com.example.shoppingassistant.domain.offers.TrackedOfferRepository
import com.example.shoppingassistant.server.config.PriceFetcherConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OfferPriceHistoryTable
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import kotlin.math.ceil

class PriceFetcherRunnerImpl(
    private val config: PriceFetcherConfig,
    private val probeRegistry: PriceProbeRegistry,
    private val rateLimiter: SourceRateLimiter,
    private val trackedOfferRepository: TrackedOfferRepository,
    private val sourceRegistry: SourceRegistry,
    private val urlNormalizer: UrlNormalizer,
) : PriceFetcherRunner {

    private val logger = LoggerFactory.getLogger("PriceFetcherRunner")

    override suspend fun runOnce(): PriceFetcherRunResult {
        if (!config.enabled) {
            return PriceFetcherRunResult(
                attempted = 0,
                updated = 0,
                skipped = 0,
                blocked = 0,
                failed = 0,
            )
        }

        val candidates = loadCandidates()
        if (candidates.isEmpty()) {
            return PriceFetcherRunResult(
                attempted = 0,
                updated = 0,
                skipped = 0,
                blocked = 0,
                failed = 0,
            )
        }

        var attempted = 0
        var updated = 0
        var skipped = 0
        var blocked = 0
        var failed = 0
        val metrics = mutableMapOf<String, SourceProbeStats>()

        for (candidate in candidates.take(config.maxPerRun)) {
            val sourceId = candidate.sourceId ?: candidate.sourceType.name.lowercase()
            val probe = probeRegistry.get(candidate.sourceType)
            if (probe == null) {
                failed++
                metricsFor(metrics, sourceId).record(PriceProbeStatus.UNSUPPORTED, null, null)
                continue
            }

            if (rateLimiter.isCoolingDown(sourceId)) {
                skipped++
                continue
            }

            attempted++
            val result = withContext(Dispatchers.IO) {
                probe.probe(candidate.sourceUrl)
            }

            metricsFor(metrics, sourceId).record(result.status, result.latencyMs, result.httpStatus)

            when (result.status) {
                PriceProbeStatus.OK -> {
                    val price = result.priceValue
                    val currency = result.currency
                    if (price == null || currency.isNullOrBlank()) {
                        failed++
                        rateLimiter.onError(sourceId)
                        continue
                    }
                    val refresh = trackedOfferRepository.refreshTrackedOffer(
                        RefreshTrackedOfferInput(
                            offerId = candidate.offerId.toString(),
                            priceValue = price,
                            currency = currency,
                            attributes = emptyMap(),
                            dataSource = sourceId,
                        ),
                    )
                    if (refresh.status == RefreshTrackedOfferStatus.UPDATED) {
                        updated++
                        rateLimiter.onSuccess(sourceId)
                    } else {
                        failed++
                        rateLimiter.onError(sourceId)
                    }
                    updateSourceMetaIfNeeded(candidate, result)
                }
                PriceProbeStatus.TEMP_BLOCKED -> {
                    blocked++
                    rateLimiter.onBlocked(sourceId)
                }
                PriceProbeStatus.NETWORK_ERROR -> {
                    failed++
                    rateLimiter.onError(sourceId)
                }
                PriceProbeStatus.PARSE_ERROR -> {
                    failed++
                    rateLimiter.onError(sourceId)
                }
                PriceProbeStatus.UNSUPPORTED -> {
                    failed++
                    rateLimiter.onError(sourceId)
                }
            }
        }

        logMetrics(metrics)

        return PriceFetcherRunResult(
            attempted = attempted,
            updated = updated,
            skipped = skipped,
            blocked = blocked,
            failed = failed,
        )
    }

    private suspend fun loadCandidates(): List<SourceCandidate> {
        val now = System.currentTimeMillis()
        val lastByOffer = DatabaseFactory.dbQuery {
            val maxCollectedAt = OfferPriceHistoryTable.collectedAt.max()
            OfferPriceHistoryTable
                .select(OfferPriceHistoryTable.offerId, maxCollectedAt)
                .groupBy(OfferPriceHistoryTable.offerId)
                .associate { row ->
                    row[OfferPriceHistoryTable.offerId] to row[maxCollectedAt]
                }
        }

        return DatabaseFactory.dbQuery {
            OfferSourcesTable
                .selectAll()
                .apply { andWhere { OfferSourcesTable.canTrackPrice eq true } }
                .orderBy(OfferSourcesTable.createdAt to SortOrder.ASC)
                .limit(config.batchSize)
                .mapNotNull { row ->
                    val offerId = row[OfferSourcesTable.offerId]
                    val lastCollected = lastByOffer[offerId]
                    if (lastCollected != null && now - lastCollected < config.minIntervalMillis) {
                        return@mapNotNull null
                    }
                    val sourceType = runCatching { SourceType.valueOf(row[OfferSourcesTable.sourceType]) }
                        .getOrDefault(SourceType.UNKNOWN)
                    val sourceId = sourceRegistry.findBySourceType(sourceType)?.id
                    SourceCandidate(
                        id = row[OfferSourcesTable.id],
                        offerId = offerId,
                        sourceType = sourceType,
                        sourceId = sourceId,
                        sourceUrl = row[OfferSourcesTable.sourceUrl],
                        canonicalUrl = row[OfferSourcesTable.canonicalUrl],
                        listingId = row[OfferSourcesTable.listingId],
                    )
                }
        }
    }

    private suspend fun updateSourceMetaIfNeeded(candidate: SourceCandidate, result: PriceProbeResult) {
        val canonical = result.canonicalUrl
            ?.let { urlNormalizer.normalize(it).normalized }
            ?.trim()
            ?.ifBlank { null }
        val listingId = result.listingId?.trim()?.ifBlank { null }
        if (canonical == null && listingId == null) return

        DatabaseFactory.dbQuery {
            OfferSourcesTable.update({ OfferSourcesTable.id eq candidate.id }) { stmt ->
                if (canonical != null && canonical != candidate.canonicalUrl) {
                    stmt[OfferSourcesTable.canonicalUrl] = canonical
                }
                if (listingId != null && listingId != candidate.listingId) {
                    stmt[OfferSourcesTable.listingId] = listingId
                }
            }
        }
    }

    private fun metricsFor(
        metrics: MutableMap<String, SourceProbeStats>,
        sourceId: String,
    ): SourceProbeStats = metrics.getOrPut(sourceId) { SourceProbeStats() }

    private fun logMetrics(metrics: Map<String, SourceProbeStats>) {
        if (metrics.isEmpty()) return
        metrics.forEach { (sourceId, stats) ->
            logger.info(
                "Source {}: total={} ok={} blocked={} errors={} p95={}ms http={}",
                sourceId,
                stats.total,
                stats.ok,
                stats.blocked,
                stats.errors,
                stats.p95(),
                stats.httpSummary(),
            )
        }
    }

    private data class SourceCandidate(
        val id: Long,
        val offerId: Long,
        val sourceType: SourceType,
        val sourceId: String?,
        val sourceUrl: String,
        val canonicalUrl: String?,
        val listingId: String?,
    )

    private class SourceProbeStats {
        var total: Int = 0
        var ok: Int = 0
        var blocked: Int = 0
        var errors: Int = 0
        private val latencies = mutableListOf<Long>()
        private val httpStatuses = mutableMapOf<Int, Int>()

        fun record(status: PriceProbeStatus, latencyMs: Long?, httpStatus: Int?) {
            total++
            when (status) {
                PriceProbeStatus.OK -> ok++
                PriceProbeStatus.TEMP_BLOCKED -> blocked++
                PriceProbeStatus.NETWORK_ERROR,
                PriceProbeStatus.PARSE_ERROR,
                PriceProbeStatus.UNSUPPORTED,
                -> errors++
            }
            if (latencyMs != null) latencies.add(latencyMs)
            if (httpStatus != null) {
                httpStatuses[httpStatus] = (httpStatuses[httpStatus] ?: 0) + 1
            }
        }

        fun p95(): Long? {
            if (latencies.isEmpty()) return null
            val sorted = latencies.sorted()
            val idx = ceil(sorted.size * 0.95).toInt().coerceIn(1, sorted.size) - 1
            return sorted[idx]
        }

        fun httpSummary(): String {
            if (httpStatuses.isEmpty()) return "-"
            return httpStatuses.entries
                .sortedBy { it.key }
                .joinToString(",") { (code, count) -> "$code:$count" }
        }
    }
}
