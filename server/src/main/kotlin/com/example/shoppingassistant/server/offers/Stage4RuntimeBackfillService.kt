package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.catalog.Stage40DedupEntity
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.catalog.Stage4ExecutionMetricSample
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepository
import com.example.shoppingassistant.server.catalog.Stage4ExecutionStream
import com.example.shoppingassistant.server.db.DatabaseFactory
import java.util.Locale
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

data class Stage4RuntimeBackfillReport(
    val offersProcessed: Int,
    val offersUpdated: Int,
    val presetEventsProcessed: Int,
    val presetEventsUpdated: Int,
    val presetEventsDropped: Int,
    val normalizedCount: Int,
    val droppedCount: Int,
    val logicalDedupCount: Int,
    val unknownAttributeCount: Int,
    val reasonCodes: List<String>,
)

interface Stage4RuntimeBackfillService {
    suspend fun runHistoricalBackfill(batchSize: Int = 500): Stage4RuntimeBackfillReport
}

class Stage4RuntimeBackfillServiceImpl(
    private val stage4ExecutionLayer: Stage4ExecutionLayer,
    private val stage4ExecutionObservabilityRepository: Stage4ExecutionObservabilityRepository,
) : Stage4RuntimeBackfillService {

    override suspend fun runHistoricalBackfill(batchSize: Int): Stage4RuntimeBackfillReport {
        val safeBatchSize = batchSize.coerceIn(50, 5000)
        val offers = backfillOffersAttributes(safeBatchSize)
        val presetEvents = backfillPresetEvents(safeBatchSize)

        val report = Stage4RuntimeBackfillReport(
            offersProcessed = offers.processed,
            offersUpdated = offers.updated,
            presetEventsProcessed = presetEvents.processed,
            presetEventsUpdated = presetEvents.updated,
            presetEventsDropped = presetEvents.dropped,
            normalizedCount = offers.normalizedCount + presetEvents.normalizedCount,
            droppedCount = offers.droppedCount + presetEvents.droppedCount,
            logicalDedupCount = offers.logicalDedupCount + presetEvents.logicalDedupCount,
            unknownAttributeCount = offers.unknownAttributeCount + presetEvents.unknownAttributeCount,
            reasonCodes = (offers.reasonCodes + presetEvents.reasonCodes).distinct(),
        )

        logger.info(
            "stage4.backfill.completed offersProcessed={} offersUpdated={} presetProcessed={} presetUpdated={} presetDropped={} normalized={} dropped={} logicalDedup={} unknownAttribute={}",
            report.offersProcessed,
            report.offersUpdated,
            report.presetEventsProcessed,
            report.presetEventsUpdated,
            report.presetEventsDropped,
            report.normalizedCount,
            report.droppedCount,
            report.logicalDedupCount,
            report.unknownAttributeCount,
        )
        return report
    }

    private suspend fun backfillOffersAttributes(
        batchSize: Int,
    ): BackfillCounters {
        var lastSeenOfferId = 0L
        var processed = 0
        var updated = 0
        var normalizedCount = 0
        var droppedCount = 0
        var logicalDedupCount = 0
        var unknownAttributeCount = 0
        val reasonCodes = linkedSetOf<String>()

        while (true) {
            val batch = DatabaseFactory.dbQuery {
                val query = OffersTable
                    .innerJoin(ProductsTable, { OffersTable.productId }, { ProductsTable.id })
                    .selectAll()
                query.andWhere { OffersTable.id greater lastSeenOfferId }
                query.andWhere { OffersTable.attributes.isNotNull() }
                query.orderBy(OffersTable.id to SortOrder.ASC)

                val rows = query.limit(batchSize).map { row ->
                    OfferBackfillRow(
                        id = row[OffersTable.id],
                        categoryCode = row[ProductsTable.category],
                        attributes = row[OffersTable.attributes].orEmpty(),
                        condition = row[OffersTable.condition],
                        deliveryChannel = row[OffersTable.deliveryChannel],
                    )
                }
                if (rows.isEmpty()) {
                    return@dbQuery BatchResult(
                        lastSeenId = lastSeenOfferId,
                        processed = 0,
                        updated = 0,
                        normalizedCount = 0,
                        droppedCount = 0,
                        logicalDedupCount = 0,
                        unknownAttributeCount = 0,
                        reasonCodes = emptySet(),
                    )
                }

                var batchUpdated = 0
                var batchNormalizedCount = 0
                var batchDroppedCount = 0
                var batchLogicalDedupCount = 0
                var batchUnknownAttributeCount = 0
                val batchReasons = linkedSetOf<String>()

                rows.forEach { row ->
                    val rawAttributes = stage4ExecutionLayer.toRawStringAttributes(row.attributes)
                    val outcome = stage4ExecutionLayer.normalizeAttributesForIngestStrict(
                        categoryCode = row.categoryCode,
                        attributes = rawAttributes,
                    )
                    val normalizedAttributes = outcome.normalizedAttributes
                    val normalizedTypedAttributes = stage4ExecutionLayer.toTypedAttributes(normalizedAttributes)
                    val normalizedCondition = normalizeOfferCondition(normalizedAttributes["condition"])
                    val normalizedDeliveryChannel = normalizeDeliveryChannel(
                        normalizedAttributes["delivery_channel"] ?: normalizedAttributes["delivery"],
                    )
                    val hasChanges = normalizedTypedAttributes != row.attributes ||
                        normalizedCondition != row.condition ||
                        normalizedDeliveryChannel != row.deliveryChannel

                    if (hasChanges) {
                        batchUpdated += OffersTable.update({ OffersTable.id eq row.id }) { stmt ->
                            stmt[OffersTable.attributes] = normalizedTypedAttributes.ifEmpty { null }
                            stmt[OffersTable.condition] = normalizedCondition
                            stmt[OffersTable.deliveryChannel] = normalizedDeliveryChannel
                        }
                    }

                    batchNormalizedCount += outcome.normalizedCount
                    batchDroppedCount += outcome.droppedCount
                    batchLogicalDedupCount += outcome.logicalDedupCount
                    batchUnknownAttributeCount += outcome.unknownAttributeCount
                    batchReasons += outcome.reasonCodes
                }

                BatchResult(
                    lastSeenId = rows.last().id,
                    processed = rows.size,
                    updated = batchUpdated,
                    normalizedCount = batchNormalizedCount,
                    droppedCount = batchDroppedCount,
                    logicalDedupCount = batchLogicalDedupCount,
                    unknownAttributeCount = batchUnknownAttributeCount,
                    reasonCodes = batchReasons,
                )
            }

            if (batch.processed == 0) break
            lastSeenOfferId = batch.lastSeenId
            processed += batch.processed
            updated += batch.updated
            normalizedCount += batch.normalizedCount
            droppedCount += batch.droppedCount
            logicalDedupCount += batch.logicalDedupCount
            unknownAttributeCount += batch.unknownAttributeCount
            reasonCodes += batch.reasonCodes
        }

        DatabaseFactory.dbQuery {
            stage4ExecutionObservabilityRepository.recordInTransaction(
                Stage4ExecutionMetricSample(
                    stream = Stage4ExecutionStream.OFFERS_BACKFILL,
                    normalizedCount = normalizedCount,
                    droppedCount = droppedCount,
                    logicalDedupCount = logicalDedupCount,
                    unknownAttributeCount = unknownAttributeCount,
                    reasonCodes = reasonCodes.toList(),
                    metadata = mapOf(
                        "processedRows" to processed.toString(),
                        "updatedRows" to updated.toString(),
                    ),
                ),
            )
        }

        return BackfillCounters(
            processed = processed,
            updated = updated,
            dropped = 0,
            normalizedCount = normalizedCount,
            droppedCount = droppedCount,
            logicalDedupCount = logicalDedupCount,
            unknownAttributeCount = unknownAttributeCount,
            reasonCodes = reasonCodes.toList(),
        )
    }

    private suspend fun backfillPresetEvents(
        batchSize: Int,
    ): BackfillCounters {
        var lastSeenEventId = 0L
        var processed = 0
        var updated = 0
        var dropped = 0
        var normalizedCount = 0
        var droppedCount = 0
        var logicalDedupCount = 0
        val reasonCodes = linkedSetOf<String>()
        val seenLogicalKeys = linkedSetOf<String>()

        while (true) {
            val batch = DatabaseFactory.dbQuery {
                val query = CatalogPresetEventsTable.selectAll()
                query.andWhere { CatalogPresetEventsTable.id greater lastSeenEventId }
                query.orderBy(CatalogPresetEventsTable.id to SortOrder.ASC)

                val rows = query.limit(batchSize).map { row ->
                    PresetBackfillRow(
                        id = row[CatalogPresetEventsTable.id],
                        eventDate = row[CatalogPresetEventsTable.eventDate],
                        eventType = row[CatalogPresetEventsTable.eventType],
                        querySessionId = row[CatalogPresetEventsTable.querySessionId],
                        categoryCode = row[CatalogPresetEventsTable.categoryCode],
                        facetCollectionCode = row[CatalogPresetEventsTable.facetCollectionCode],
                        facetPresetCode = row[CatalogPresetEventsTable.facetPresetCode],
                        offerId = row[CatalogPresetEventsTable.offerId],
                        position = row[CatalogPresetEventsTable.position],
                        occurredAtMs = row[CatalogPresetEventsTable.occurredAt],
                        payload = row[CatalogPresetEventsTable.payloadJson],
                    )
                }

                if (rows.isEmpty()) {
                    return@dbQuery BatchResult(
                        lastSeenId = lastSeenEventId,
                        processed = 0,
                        updated = 0,
                        normalizedCount = 0,
                        droppedCount = 0,
                        logicalDedupCount = 0,
                        unknownAttributeCount = 0,
                        reasonCodes = emptySet(),
                        physicallyDropped = 0,
                    )
                }

                var batchUpdated = 0
                var batchDropped = 0
                var batchNormalizedCount = 0
                var batchDroppedCount = 0
                var batchLogicalDedupCount = 0
                val batchReasons = linkedSetOf<String>()

                rows.forEach { row ->
                    val normalizedCategoryCode = stage4ExecutionLayer.normalizeCatalogCode(row.categoryCode)
                    val normalizedPresetCode = stage4ExecutionLayer.normalizeCatalogCode(row.facetPresetCode)
                    if (normalizedCategoryCode == null || normalizedPresetCode == null) {
                        CatalogPresetEventsTable.deleteWhere {
                            (CatalogPresetEventsTable.id eq row.id) and
                                (CatalogPresetEventsTable.eventDate eq row.eventDate)
                        }
                        batchDropped += 1
                        batchDroppedCount += 1
                        if (normalizedCategoryCode == null) {
                            batchReasons += "CATEGORY_CODE_BLANK"
                        }
                        if (normalizedPresetCode == null) {
                            batchReasons += "FACET_PRESET_CODE_BLANK"
                        }
                        return@forEach
                    }

                    val normalizedCollectionCode = stage4ExecutionLayer.normalizeCatalogCode(row.facetCollectionCode)
                    val stage4PresetKey = stage4ExecutionLayer.renderDedupKey(
                        entity = Stage40DedupEntity.FACET_PRESET,
                        fields = mapOf("presetCode" to normalizedPresetCode),
                        fallback = normalizedPresetCode,
                    )
                    val stage4CollectionKey = normalizedCollectionCode?.let { collectionCode ->
                        stage4ExecutionLayer.renderDedupKey(
                            entity = Stage40DedupEntity.FACET_COLLECTION,
                            fields = mapOf("collectionCode" to collectionCode),
                            fallback = collectionCode,
                        )
                    }

                    val logicalDedupKey = buildLogicalDedupKey(
                        eventDate = row.eventDate,
                        eventType = row.eventType,
                        querySessionId = row.querySessionId,
                        categoryCode = normalizedCategoryCode,
                        stage4PresetKey = stage4PresetKey,
                        stage4CollectionKey = stage4CollectionKey,
                        offerId = row.offerId,
                        position = row.position,
                        occurredAtMs = row.occurredAtMs,
                    )

                    if (!seenLogicalKeys.add(logicalDedupKey)) {
                        CatalogPresetEventsTable.deleteWhere {
                            (CatalogPresetEventsTable.id eq row.id) and
                                (CatalogPresetEventsTable.eventDate eq row.eventDate)
                        }
                        batchDropped += 1
                        batchLogicalDedupCount += 1
                        batchReasons += "LOGICAL_DEDUP_EVENT"
                        return@forEach
                    }

                    val mergedPayload = row.payload + mapOf(
                        "stage4LogicalDedupKey" to logicalDedupKey,
                        "stage4PresetKey" to stage4PresetKey,
                        "stage4CollectionKey" to stage4CollectionKey.orEmpty(),
                    )
                    val hasChanges = normalizedCategoryCode != row.categoryCode ||
                        normalizedPresetCode != row.facetPresetCode ||
                        normalizedCollectionCode != row.facetCollectionCode ||
                        mergedPayload != row.payload

                    if (hasChanges) {
                        batchUpdated += CatalogPresetEventsTable.update({
                            (CatalogPresetEventsTable.id eq row.id) and
                                (CatalogPresetEventsTable.eventDate eq row.eventDate)
                        }) { stmt ->
                            stmt[CatalogPresetEventsTable.categoryCode] = normalizedCategoryCode
                            stmt[CatalogPresetEventsTable.facetPresetCode] = normalizedPresetCode
                            stmt[CatalogPresetEventsTable.facetCollectionCode] = normalizedCollectionCode
                            stmt[CatalogPresetEventsTable.payloadJson] = mergedPayload
                        }
                    }
                    batchNormalizedCount += 1
                }

                BatchResult(
                    lastSeenId = rows.last().id,
                    processed = rows.size,
                    updated = batchUpdated,
                    normalizedCount = batchNormalizedCount,
                    droppedCount = batchDroppedCount,
                    logicalDedupCount = batchLogicalDedupCount,
                    unknownAttributeCount = 0,
                    reasonCodes = batchReasons,
                    physicallyDropped = batchDropped,
                )
            }

            if (batch.processed == 0) break
            lastSeenEventId = batch.lastSeenId
            processed += batch.processed
            updated += batch.updated
            dropped += batch.physicallyDropped
            normalizedCount += batch.normalizedCount
            droppedCount += batch.droppedCount
            logicalDedupCount += batch.logicalDedupCount
            reasonCodes += batch.reasonCodes
        }

        DatabaseFactory.dbQuery {
            stage4ExecutionObservabilityRepository.recordInTransaction(
                Stage4ExecutionMetricSample(
                    stream = Stage4ExecutionStream.PRESET_EVENTS_BACKFILL,
                    normalizedCount = normalizedCount,
                    droppedCount = droppedCount,
                    logicalDedupCount = logicalDedupCount,
                    unknownAttributeCount = 0,
                    reasonCodes = reasonCodes.toList(),
                    metadata = mapOf(
                        "processedRows" to processed.toString(),
                        "updatedRows" to updated.toString(),
                        "droppedRows" to dropped.toString(),
                    ),
                ),
            )
        }

        return BackfillCounters(
            processed = processed,
            updated = updated,
            dropped = dropped,
            normalizedCount = normalizedCount,
            droppedCount = droppedCount,
            logicalDedupCount = logicalDedupCount,
            unknownAttributeCount = 0,
            reasonCodes = reasonCodes.toList(),
        )
    }

    private fun buildLogicalDedupKey(
        eventDate: LocalDate,
        eventType: String,
        querySessionId: String,
        categoryCode: String,
        stage4PresetKey: String,
        stage4CollectionKey: String?,
        offerId: String?,
        position: Int?,
        occurredAtMs: Long,
    ): String = buildString {
        append(eventDate.toString())
        append("|")
        append(eventType.trim().uppercase(Locale.ROOT))
        append("|")
        append(querySessionId.trim())
        append("|")
        append(categoryCode.trim())
        append("|")
        append(stage4PresetKey.trim())
        append("|")
        append(stage4CollectionKey?.trim().orEmpty())
        append("|")
        append(offerId?.trim().orEmpty())
        append("|")
        append(position ?: 0)
        append("|")
        append(occurredAtMs)
    }

    private fun normalizeDeliveryChannel(value: String?): String? {
        val trimmed = value?.trim()?.lowercase().orEmpty()
        return when (trimmed) {
            "delivery", "pickup", "meeting" -> trimmed
            else -> null
        }
    }

    private data class OfferBackfillRow(
        val id: Long,
        val categoryCode: String,
        val attributes: Map<String, TypedAttributeValue>,
        val condition: String?,
        val deliveryChannel: String?,
    )

    private data class PresetBackfillRow(
        val id: Long,
        val eventDate: LocalDate,
        val eventType: String,
        val querySessionId: String,
        val categoryCode: String,
        val facetCollectionCode: String?,
        val facetPresetCode: String,
        val offerId: String?,
        val position: Int?,
        val occurredAtMs: Long,
        val payload: Map<String, String>,
    )

    private data class BatchResult(
        val lastSeenId: Long,
        val processed: Int,
        val updated: Int,
        val normalizedCount: Int,
        val droppedCount: Int,
        val logicalDedupCount: Int,
        val unknownAttributeCount: Int,
        val reasonCodes: Set<String>,
        val physicallyDropped: Int = 0,
    )

    private data class BackfillCounters(
        val processed: Int,
        val updated: Int,
        val dropped: Int,
        val normalizedCount: Int,
        val droppedCount: Int,
        val logicalDedupCount: Int,
        val unknownAttributeCount: Int,
        val reasonCodes: List<String>,
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(Stage4RuntimeBackfillServiceImpl::class.java)
    }
}
