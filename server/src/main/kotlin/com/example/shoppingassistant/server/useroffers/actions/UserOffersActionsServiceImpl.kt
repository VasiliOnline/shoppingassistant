package com.example.shoppingassistant.server.useroffers.actions

import com.example.shoppingassistant.domain.useroffers.UserOfferActionRequest
import com.example.shoppingassistant.domain.useroffers.UserOfferActionResult
import com.example.shoppingassistant.domain.useroffers.UserOfferActionStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferActionType
import com.example.shoppingassistant.domain.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferStatus
import com.example.shoppingassistant.domain.useroffers.UserOfferSummary
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.catalog.Stage4ExecutionMetricSample
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepository
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepositoryImpl
import com.example.shoppingassistant.server.catalog.Stage4ExecutionStream
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OfferPriceHistoryTable
import com.example.shoppingassistant.server.offers.OfferSourcesTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.normalizeOfferCondition
import java.net.URI
import java.util.Locale
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

class UserOffersActionsServiceImpl(
    private val stage4ExecutionLayer: Stage4ExecutionLayer,
    private val stage4ExecutionObservabilityRepository: Stage4ExecutionObservabilityRepository =
        Stage4ExecutionObservabilityRepositoryImpl(),
) : UserOffersActionsService {
    override suspend fun performAction(userId: Long, request: UserOfferActionRequest): UserOfferActionResult =
        DatabaseFactory.dbQuery {
            val offerId = request.offerId.toLongOrNull()
                ?: return@dbQuery UserOfferActionResult(
                    status = UserOfferActionStatus.INVALID_INPUT,
                    message = "offerId must be numeric",
                )

            val baseQuery = OffersTable
                .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
                .selectAll()
                .apply {
                    andWhere { OffersTable.id eq offerId }
                    andWhere { OffersTable.userId eq userId }
                }

            val existing = baseQuery.singleOrNull()
                ?: return@dbQuery UserOfferActionResult(
                    status = UserOfferActionStatus.NOT_FOUND,
                    message = "Offer not found",
                )

            val now = System.currentTimeMillis()
            when (request.action) {
                UserOfferActionType.DUPLICATE -> {
                    val categoryCode = existing[ProductsTable.category]
                    val rawAttributes = stage4ExecutionLayer.toRawStringAttributes(
                        existing[OffersTable.attributes].orEmpty(),
                    )
                    val normalizationOutcome = stage4ExecutionLayer.normalizeAttributesForIngestStrict(
                        categoryCode = categoryCode,
                        attributes = rawAttributes,
                    )
                    val normalizedTypedAttributes = stage4ExecutionLayer.toTypedAttributes(
                        normalizationOutcome.normalizedAttributes,
                    )
                    val normalizedCondition = normalizeOfferCondition(
                        normalizationOutcome.normalizedAttributes["condition"],
                    ) ?: existing[OffersTable.condition]
                    val normalizedDeliveryChannel = normalizeDeliveryChannel(
                        normalizationOutcome.normalizedAttributes["delivery_channel"]
                            ?: normalizationOutcome.normalizedAttributes["delivery"],
                    ) ?: existing[OffersTable.deliveryChannel]

                    val newOfferId = OffersTable.insert { stmt ->
                        stmt[OffersTable.productId] = existing[OffersTable.productId]
                        stmt[OffersTable.userId] = userId
                        stmt[OffersTable.priceCents] = existing[OffersTable.priceCents]
                        stmt[OffersTable.currency] = existing[OffersTable.currency]
                        stmt[OffersTable.attributes] = normalizedTypedAttributes.ifEmpty { null }
                        stmt[OffersTable.description] = existing[OffersTable.description]
                        stmt[OffersTable.imageUrls] = existing[OffersTable.imageUrls]
                        stmt[OffersTable.condition] = normalizedCondition
                        stmt[OffersTable.deliveryChannel] = normalizedDeliveryChannel
                        stmt[OffersTable.lat] = existing[OffersTable.lat]
                        stmt[OffersTable.lon] = existing[OffersTable.lon]
                        stmt[OffersTable.status] = UserOfferStatus.DRAFT.name
                        stmt[OffersTable.updatedAt] = now
                    }.resultedValues?.single()?.get(OffersTable.id)
                        ?: return@dbQuery UserOfferActionResult(
                            status = UserOfferActionStatus.FAILED,
                            message = "Failed to duplicate offer",
                        )

                    stage4ExecutionObservabilityRepository.recordInTransaction(
                        Stage4ExecutionMetricSample(
                            stream = Stage4ExecutionStream.OFFERS_INGEST,
                            normalizedCount = normalizationOutcome.normalizedCount,
                            droppedCount = normalizationOutcome.droppedCount,
                            logicalDedupCount = normalizationOutcome.logicalDedupCount,
                            unknownAttributeCount = normalizationOutcome.unknownAttributeCount,
                            reasonCodes = normalizationOutcome.reasonCodes,
                            metadata = mapOf(
                                "operation" to "useroffers_duplicate",
                                "userId" to userId.toString(),
                                "sourceOfferId" to offerId.toString(),
                                "newOfferId" to newOfferId.toString(),
                                "categoryCode" to categoryCode,
                            ),
                            createdAtMs = now,
                        ),
                    )

                    val newRow = OffersTable
                        .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
                        .selectAll()
                        .apply {
                            andWhere { OffersTable.id eq newOfferId }
                            andWhere { OffersTable.userId eq userId }
                        }
                        .singleOrNull()
                        ?: return@dbQuery UserOfferActionResult(
                            status = UserOfferActionStatus.FAILED,
                            message = "Failed to load duplicated offer",
                        )

                    return@dbQuery UserOfferActionResult(
                        status = UserOfferActionStatus.SUCCESS,
                        newOffer = newRow.toSummary(),
                    )
                }
                else -> {
                    val newStatus = when (request.action) {
                        UserOfferActionType.PAUSE -> UserOfferStatus.PAUSED
                        UserOfferActionType.ACTIVATE -> UserOfferStatus.ACTIVE
                        UserOfferActionType.MARK_FINISHED -> UserOfferStatus.FINISHED
                        UserOfferActionType.ARCHIVE -> UserOfferStatus.ARCHIVED
                        UserOfferActionType.DELETE -> UserOfferStatus.ARCHIVED
                        UserOfferActionType.RENEW -> UserOfferStatus.ACTIVE
                        UserOfferActionType.DUPLICATE -> UserOfferStatus.DRAFT
                    }

                    OffersTable.update({ OffersTable.id eq offerId }) { stmt ->
                        stmt[OffersTable.status] = newStatus.name
                        stmt[OffersTable.updatedAt] = now
                    }
                    stage4ExecutionObservabilityRepository.recordInTransaction(
                        Stage4ExecutionMetricSample(
                            stream = Stage4ExecutionStream.OFFERS_INGEST,
                            normalizedCount = 0,
                            droppedCount = 0,
                            logicalDedupCount = 0,
                            unknownAttributeCount = 0,
                            reasonCodes = listOf("STATUS_ACTION:${request.action.name}"),
                            metadata = mapOf(
                                "operation" to "useroffers_status_action",
                                "action" to request.action.name,
                                "offerId" to offerId.toString(),
                                "userId" to userId.toString(),
                            ),
                            createdAtMs = now,
                        ),
                    )

                    val updatedRow = OffersTable
                        .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
                        .selectAll()
                        .apply {
                            andWhere { OffersTable.id eq offerId }
                            andWhere { OffersTable.userId eq userId }
                        }
                        .orderBy(OffersTable.id to SortOrder.DESC)
                        .singleOrNull()
                        ?: return@dbQuery UserOfferActionResult(
                            status = UserOfferActionStatus.FAILED,
                            message = "Failed to load updated offer",
                        )

                    return@dbQuery UserOfferActionResult(
                        status = UserOfferActionStatus.SUCCESS,
                        offer = updatedRow.toSummary(),
                    )
                }
            }
        }

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
                    (OfferPriceHistoryTable.dataSource neq USER_UPDATE_SOURCE)
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

    private fun normalizeDeliveryChannel(value: String?): String? {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "delivery", "pickup", "meeting" -> normalized
            else -> null
        }
    }

    private fun Long.toMajor(): Double = Money(this).toMajor()

    private fun String.toUserOfferStatus(): UserOfferStatus =
        runCatching { UserOfferStatus.valueOf(this) }.getOrDefault(UserOfferStatus.ACTIVE)

    private data class SourceMeta(
        val sourceName: String?,
        val sourceIconUrl: String?,
    )

    private companion object {
        const val USER_UPDATE_SOURCE = "user_update"
    }
}
