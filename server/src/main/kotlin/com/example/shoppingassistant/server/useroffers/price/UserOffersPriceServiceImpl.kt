package com.example.shoppingassistant.server.useroffers.price

import com.example.shoppingassistant.domain.useroffers.UserOfferActionStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceBulkResult
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferPriceUpdateResult
import com.example.shoppingassistant.domain.useroffers.UserOfferStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferSummary
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OfferPriceHistoryTable
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import java.net.URI
import java.util.Locale
import kotlin.math.roundToLong
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq

class UserOffersPriceServiceImpl : UserOffersPriceService {
    override suspend fun updatePrice(
        userId: Long,
        request: UserOfferPriceUpdateRequest,
    ): UserOfferPriceUpdateResult = DatabaseFactory.dbQuery {
        val result = updatePricesInternal(userId, listOf(request))
        result.results.firstOrNull()
            ?: UserOfferPriceUpdateResult(
                offerId = request.offerId,
                status = UserOfferActionStatus.INVALID_INPUT,
                message = "Empty request",
            )
    }

    override suspend fun updatePrices(
        userId: Long,
        request: UserOfferPriceBulkRequest,
    ): UserOfferPriceBulkResult = DatabaseFactory.dbQuery {
        updatePricesInternal(userId, request.items)
    }

    private fun updatePricesInternal(
        userId: Long,
        items: List<UserOfferPriceUpdateRequest>,
    ): UserOfferPriceBulkResult {
        if (items.isEmpty()) {
            return UserOfferPriceBulkResult(
                results = emptyList(),
                succeeded = 0,
                failed = 0,
            )
        }

        val results = items.map { updateSingle(userId, it) }
        val succeeded = results.count { it.status == UserOfferActionStatus.SUCCESS }
        val failed = results.size - succeeded
        return UserOfferPriceBulkResult(
            results = results,
            succeeded = succeeded,
            failed = failed,
        )
    }

    private fun updateSingle(
        userId: Long,
        request: UserOfferPriceUpdateRequest,
    ): UserOfferPriceUpdateResult {
        val offerId = request.offerId.toLongOrNull()
            ?: return invalid(request.offerId, "offerId must be numeric")

        val priceCents = priceMajorToCents(request.priceMajor)
            ?: return invalid(request.offerId, "Invalid price")

        val baseQuery = OffersTable
            .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
            .selectAll()
            .apply {
                andWhere { OffersTable.id eq offerId }
                andWhere { OffersTable.userId eq userId }
            }

        val existing = baseQuery.singleOrNull()
            ?: return UserOfferPriceUpdateResult(
                offerId = request.offerId,
                status = UserOfferActionStatus.NOT_FOUND,
                message = "Offer not found",
            )

        val currency = normalizeCurrency(request.currency) ?: existing[OffersTable.currency]
        if (currency.length != CURRENCY_CODE_LENGTH) {
            return invalid(request.offerId, "Currency code must be ISO-4217")
        }

        val now = System.currentTimeMillis()

        OffersTable.update({ OffersTable.id eq offerId }) { stmt ->
            stmt[OffersTable.priceCents] = priceCents
            stmt[OffersTable.currency] = currency
            stmt[OffersTable.updatedAt] = now
        }

        OfferPriceHistoryTable.insert { stmt ->
            stmt[OfferPriceHistoryTable.offerId] = offerId
            stmt[OfferPriceHistoryTable.priceMinor] = priceCents
            stmt[OfferPriceHistoryTable.currency] = currency
            stmt[OfferPriceHistoryTable.collectedAt] = now
            stmt[OfferPriceHistoryTable.dataSource] = PRICE_HISTORY_SOURCE
        }

        val updatedRow = OffersTable
            .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
            .selectAll()
            .apply {
                andWhere { OffersTable.id eq offerId }
                andWhere { OffersTable.userId eq userId }
            }
            .orderBy(OffersTable.id to SortOrder.DESC)
            .singleOrNull()
            ?: return UserOfferPriceUpdateResult(
                offerId = request.offerId,
                status = UserOfferActionStatus.FAILED,
                message = "Failed to load updated offer",
            )

        return UserOfferPriceUpdateResult(
            offerId = request.offerId,
            status = UserOfferActionStatus.SUCCESS,
            offer = updatedRow.toSummary(),
        )
    }

    private fun invalid(offerId: String, message: String): UserOfferPriceUpdateResult =
        UserOfferPriceUpdateResult(
            offerId = offerId,
            status = UserOfferActionStatus.INVALID_INPUT,
            message = message,
        )

    private fun priceMajorToCents(priceMajor: Double): Long? {
        if (!priceMajor.isFinite()) return null
        if (priceMajor <= 0.0) return null
        if (priceMajor > MAX_PRICE_MAJOR) return null
        val cents = (priceMajor * PRICE_SCALE).roundToLong()
        return cents.takeIf { it >= MIN_PRICE_CENTS }
    }

    private fun normalizeCurrency(raw: String?): String? =
        raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    private fun ResultRow.toSummary(): UserOfferSummary {
        val offerIdLong = this[OffersTable.id]
        val offerId = offerIdLong.toString()
        val title = this[ProductsTable.titleNorm]
        val category = this[ProductsTable.category]
        val priceMajor = this[OffersTable.priceCents].toMajor()
        val currency = this[OffersTable.currency]
        val imageUrls = this[OffersTable.imageUrls] ?: this[ProductsTable.imageUrls] ?: emptyList()
        val updatedAt = this[OffersTable.updatedAt]
        val status = this[OffersTable.status].toUserOfferStatus()
        val sourceUpdatedAtMillis = loadSourceUpdatedAt(offerIdLong)
        val sourceMeta = loadSourceMeta(offerIdLong)

        return UserOfferSummary(
            id = offerId,
            title = title,
            category = category,
            priceMajor = priceMajor,
            currency = currency,
            status = status,
            publicationStatus = UserOfferPublicationStatus.PUBLISHED,
            coverUrl = imageUrls.firstOrNull(),
            publishedAtMillis = updatedAt,
            updatedAtMillis = updatedAt,
            sourceUpdatedAtMillis = sourceUpdatedAtMillis,
            sourceName = sourceMeta?.sourceName,
            sourceIconUrl = sourceMeta?.sourceIconUrl,
        )
    }

    private fun loadSourceUpdatedAt(offerId: Long): Long? {
        val tracked = OfferSourcesTable
            .selectAll()
            .apply {
                andWhere { OfferSourcesTable.offerId eq offerId }
                andWhere { OfferSourcesTable.canTrackPrice eq true }
            }
            .limit(1)
            .singleOrNull()
            ?: return null

        val maxCollectedAt = OfferPriceHistoryTable.collectedAt.max()
        return OfferPriceHistoryTable
            .select(maxCollectedAt)
            .where { OfferPriceHistoryTable.offerId eq tracked[OfferSourcesTable.offerId] }
            .andWhere {
                OfferPriceHistoryTable.dataSource.isNull() or
                    (OfferPriceHistoryTable.dataSource neq PRICE_HISTORY_SOURCE)
            }
            .limit(1)
            .singleOrNull()
            ?.get(maxCollectedAt)
    }

    private fun loadSourceMeta(offerId: Long): SourceMeta? {
        val row = OfferSourcesTable
            .selectAll()
            .apply { andWhere { OfferSourcesTable.offerId eq offerId } }
            .limit(1)
            .singleOrNull()
            ?: return null

        val domainName = row[OfferSourcesTable.domainName]
        val sourceUrl = row[OfferSourcesTable.sourceUrl]
        val sourceType = row[OfferSourcesTable.sourceType]
        val iconUrl = row[OfferSourcesTable.sourceIconUrl]
        return SourceMeta(
            sourceName = resolveSourceName(domainName, sourceUrl, sourceType),
            sourceIconUrl = resolveSourceIconUrl(iconUrl, domainName, sourceUrl),
        )
    }

    private fun resolveSourceName(domainName: String?, sourceUrl: String?, sourceType: String?): String? {
        val fromDomain = domainName?.trim()?.ifBlank { null }
        val fromUrl = sourceUrl?.let { runCatching { URI(it).host?.removePrefix("www.") }.getOrNull() }
        val fromType = sourceType?.lowercase()?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return fromDomain ?: fromUrl ?: fromType
    }

    private fun resolveSourceIconUrl(
        iconUrl: String?,
        domainName: String?,
        sourceUrl: String?,
    ): String? {
        val cleaned = iconUrl?.trim()?.ifBlank { null }
        if (cleaned != null) return cleaned
        val host = domainName?.trim()?.ifBlank { null }
            ?: sourceUrl?.let { runCatching { URI(it).host }.getOrNull() }
            ?: return null
        val scheme = sourceUrl?.let { runCatching { URI(it).scheme }.getOrNull() } ?: "https"
        return "$scheme://${host.removePrefix("www.")}/favicon.ico"
    }

    private fun Long.toMajor(): Double = this.toDouble() / PRICE_SCALE

    private fun String.toUserOfferStatus(): UserOfferStatus =
        runCatching { UserOfferStatus.valueOf(this) }.getOrDefault(UserOfferStatus.ACTIVE)

    private data class SourceMeta(
        val sourceName: String?,
        val sourceIconUrl: String?,
    )

    private companion object {
        const val PRICE_SCALE = 100.0
        const val CURRENCY_CODE_LENGTH = 3
        const val MIN_PRICE_CENTS = 1L
        const val PRICE_HISTORY_SOURCE = "user_update"
        const val MAX_PRICE_MAJOR = Long.MAX_VALUE / PRICE_SCALE
    }
}
