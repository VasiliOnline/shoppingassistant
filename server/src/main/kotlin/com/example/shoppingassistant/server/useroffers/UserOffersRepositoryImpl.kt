package com.example.shoppingassistant.server.useroffers

import com.example.shoppingassistant.domain.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferStatus
import com.example.shoppingassistant.domain.useroffers.UserOffersPage
import com.example.shoppingassistant.domain.useroffers.UserOffersQuery
import com.example.shoppingassistant.domain.useroffers.UserOfferSummary
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OfferPriceHistoryTable
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import java.net.URI
import java.util.Locale
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.or

class UserOffersRepositoryImpl : UserOffersBackendRepository {
    override suspend fun listUserOffers(userId: Long, query: UserOffersQuery): UserOffersPage =
        DatabaseFactory.dbQuery {
            val limit = query.limit.coerceIn(1, MAX_LIMIT)
            val cursorId = query.cursor?.toLongOrNull()
            val statuses = query.statuses.map { it.name }

            val q = OffersTable
                .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
                .selectAll()
                .apply {
                    andWhere { OffersTable.userId eq userId }
                    if (statuses.isNotEmpty()) {
                        andWhere { OffersTable.status inList statuses }
                    }
                    if (cursorId != null) {
                        andWhere { OffersTable.id less cursorId }
                    }
                    val updatedSince = query.updatedSinceMillis
                    if (updatedSince != null) {
                        andWhere { OffersTable.updatedAt greaterEq updatedSince }
                    }
                }
                .orderBy(OffersTable.id to SortOrder.DESC)
                .limit(limit + 1)

            val rows = q.toList()
            val hasMore = rows.size > limit
            val pageRows = rows.take(limit)
            val offerIds = pageRows.map { it[OffersTable.id] }
            val sourceMetaByOfferId = if (offerIds.isNotEmpty()) {
                OfferSourcesTable
                    .selectAll()
                    .apply { andWhere { OfferSourcesTable.offerId inList offerIds } }
                    .associate { row -> row[OfferSourcesTable.offerId] to row.toSourceMeta() }
            } else {
                emptyMap()
            }
            val trackedOfferIds = if (offerIds.isNotEmpty()) {
                OfferSourcesTable
                    .selectAll()
                    .apply {
                        andWhere { OfferSourcesTable.offerId inList offerIds }
                        andWhere { OfferSourcesTable.canTrackPrice eq true }
                    }
                    .map { it[OfferSourcesTable.offerId] }
                    .toSet()
            } else {
                emptySet()
            }
            val sourceUpdatedByOfferId = if (trackedOfferIds.isNotEmpty()) {
                val maxCollectedAt = OfferPriceHistoryTable.collectedAt.max()
                OfferPriceHistoryTable
                    .select(OfferPriceHistoryTable.offerId, maxCollectedAt)
                    .apply {
                        andWhere { OfferPriceHistoryTable.offerId inList trackedOfferIds }
                        andWhere {
                            OfferPriceHistoryTable.dataSource.isNull() or
                                (OfferPriceHistoryTable.dataSource neq USER_UPDATE_SOURCE)
                        }
                    }
                    .groupBy(OfferPriceHistoryTable.offerId)
                    .associate { row ->
                        row[OfferPriceHistoryTable.offerId] to row[maxCollectedAt]
                    }
            } else {
                emptyMap()
            }

            val items = pageRows.map { row ->
                val offerId = row[OffersTable.id]
                row.toSummary(
                    sourceUpdatedByOfferId[offerId],
                    sourceMetaByOfferId[offerId],
                )
            }
            val nextCursor = if (hasMore) items.lastOrNull()?.id else null

            UserOffersPage(
                items = items,
                nextCursor = nextCursor,
                hasMore = hasMore,
            )
        }

    private fun ResultRow.toSummary(
        sourceUpdatedAtMillis: Long?,
        sourceMeta: SourceMeta?,
    ): UserOfferSummary {
        val offerId = this[OffersTable.id].toString()
        val title = this[ProductsTable.titleNorm]
        val category = this[ProductsTable.category]
        val priceMajor = this[OffersTable.priceCents].toMajor()
        val currency = this[OffersTable.currency]
        val imageUrls = this[OffersTable.imageUrls] ?: this[ProductsTable.imageUrls] ?: emptyList()
        val updatedAt = this[OffersTable.updatedAt]
        val status = this[OffersTable.status].toUserOfferStatus()

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

    private fun Long.toMajor(): Double = Money(this).toMajor()

    private fun String.toUserOfferStatus(): UserOfferStatus =
        runCatching { UserOfferStatus.valueOf(this) }.getOrDefault(UserOfferStatus.ACTIVE)

    private fun ResultRow.toSourceMeta(): SourceMeta {
        val domainName = this[OfferSourcesTable.domainName]
        val sourceUrl = this[OfferSourcesTable.sourceUrl]
        val sourceType = this[OfferSourcesTable.sourceType]
        val iconUrl = this[OfferSourcesTable.sourceIconUrl]
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

    private data class SourceMeta(
        val sourceName: String?,
        val sourceIconUrl: String?,
    )

    private companion object {
        const val MAX_LIMIT = 50
        const val USER_UPDATE_SOURCE = "user_update"
    }
}
