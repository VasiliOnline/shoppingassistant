package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.Stage40DedupEntity
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityEvent
import com.example.shoppingassistant.domain.model.PresetObservabilityEventType
import com.example.shoppingassistant.server.catalog.CategoriesTable
import com.example.shoppingassistant.server.catalog.FacetCollectionsTable
import com.example.shoppingassistant.server.catalog.FacetPresetsTable
import com.example.shoppingassistant.server.catalog.Stage4ExecutionMetricSample
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepository
import com.example.shoppingassistant.server.catalog.Stage4ExecutionObservabilityRepositoryImpl
import com.example.shoppingassistant.server.catalog.Stage4ExecutionStream
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayerImpl
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.sql.SQLException
import java.util.Locale
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

interface PresetObservabilityRepository {
    suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse
}

class PresetObservabilityRepositoryImpl(
    private val stage4ExecutionLayer: Stage4ExecutionLayer = Stage4ExecutionLayerImpl(),
    private val stage4ExecutionObservabilityRepository: Stage4ExecutionObservabilityRepository =
        Stage4ExecutionObservabilityRepositoryImpl(),
) : PresetObservabilityRepository {

    override suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        val rejectedEventKeys = mutableListOf<String>()
        var acceptedCount = 0
        var dedupedCount = 0
        var rejectedCount = 0
        var normalizedCount = 0

        if (request.events.isEmpty()) {
            rejectedCount = 1
            rejectedEventKeys += "BATCH_EMPTY"
            val response = PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = 1,
                rejectedEventKeys = listOf("BATCH_EMPTY"),
            )
            recordExecutionMetrics(
                now = now,
                normalizedCount = normalizedCount,
                dedupedCount = dedupedCount,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
            return@dbQuery response
        }

        val candidateEvents = if (request.events.size > MAX_BATCH_SIZE) {
            rejectedCount += (request.events.size - MAX_BATCH_SIZE)
            rejectedEventKeys += "BATCH_LIMIT_EXCEEDED:${request.events.size - MAX_BATCH_SIZE}"
            request.events.take(MAX_BATCH_SIZE)
        } else {
            request.events
        }

        val normalized = mutableListOf<NormalizedPresetEvent>()
        candidateEvents.forEach { raw ->
            when (val result = normalize(raw, now)) {
                is NormalizationResult.Accepted -> normalized += result.value
                is NormalizationResult.Rejected -> {
                    rejectedCount += 1
                    if (rejectedEventKeys.size < MAX_REJECTED_KEYS) {
                        rejectedEventKeys += result.key
                    }
                }
            }
        }
        normalizedCount = normalized.size

        if (normalized.isEmpty()) {
            val response = PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
            recordExecutionMetrics(
                now = now,
                normalizedCount = normalizedCount,
                dedupedCount = dedupedCount,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
            return@dbQuery response
        }

        val uniqueByKey = LinkedHashMap<String, NormalizedPresetEvent>()
        normalized.forEach { event ->
            val previous = uniqueByKey.putIfAbsent(event.idempotencyKey, event)
            if (previous != null) dedupedCount += 1
        }

        val uniqueByLogicalKey = LinkedHashMap<String, NormalizedPresetEvent>()
        uniqueByKey.values.forEach { event ->
            val previous = uniqueByLogicalKey.putIfAbsent(event.logicalDedupKey, event)
            if (previous != null) dedupedCount += 1
        }

        val uniqueEvents = uniqueByLogicalKey.values.toList()
        val referentiallyValidEvents = filterReferentiallyValidEvents(
            events = uniqueEvents,
            rejectedEventKeys = rejectedEventKeys,
            onRejected = { rejectedCount += 1 },
        )

        if (referentiallyValidEvents.isEmpty()) {
            val response = PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = dedupedCount,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
            recordExecutionMetrics(
                now = now,
                normalizedCount = normalizedCount,
                dedupedCount = dedupedCount,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
            return@dbQuery response
        }

        val keys = referentiallyValidEvents.map { it.idempotencyKey }
        val existingKeys = CatalogPresetEventsTable
            .selectAll()
            .where { CatalogPresetEventsTable.idempotencyKey inList keys }
            .map { row -> row[CatalogPresetEventsTable.idempotencyKey] }
            .toSet()

        dedupedCount += existingKeys.size
        val candidateToInsert = referentiallyValidEvents.filterNot { event -> event.idempotencyKey in existingKeys }

        val existingLogicalKeys = loadExistingLogicalDedupKeys(candidateToInsert)
        dedupedCount += candidateToInsert.count { event -> event.logicalDedupKey in existingLogicalKeys }
        val toInsert = candidateToInsert.filterNot { event -> event.logicalDedupKey in existingLogicalKeys }

        toInsert.forEach { event ->
            var rejectedByConstraint = false
            val inserted = try {
                CatalogPresetEventsTable.insertIgnore { stmt ->
                    stmt[idempotencyKey] = event.idempotencyKey
                    stmt[eventType] = event.eventType.name
                    stmt[querySessionId] = event.querySessionId
                    stmt[categoryCode] = event.categoryCode
                    stmt[facetCollectionCode] = event.facetCollectionCode
                    stmt[facetPresetCode] = event.facetPresetCode
                    stmt[offerId] = event.offerId
                    stmt[position] = event.position
                    stmt[occurredAt] = event.occurredAtMs
                    stmt[receivedAt] = now
                    stmt[eventDate] = event.eventDate
                    stmt[dataVersion] = event.dataVersion
                    stmt[payloadJson] = mapOf(
                        "stage4LogicalDedupKey" to event.logicalDedupKey,
                        "stage4PresetKey" to event.stage4PresetKey,
                        "stage4CollectionKey" to event.stage4CollectionKey.orEmpty(),
                    )
                }.insertedCount > 0
            } catch (exception: Throwable) {
                if (isForeignKeyViolation(exception)) {
                    rejectedCount += 1
                    rejectedByConstraint = true
                    appendRejectedKey(
                        rejectedEventKeys,
                        "REFERENTIAL_INTEGRITY_VIOLATION:${event.idempotencyKey}",
                    )
                    false
                } else {
                    throw exception
                }
            }

            when {
                inserted -> acceptedCount += 1
                rejectedByConstraint -> Unit
                else -> dedupedCount += 1
            }
        }

        val response = PresetObservabilityBatchResponse(
            acceptedCount = acceptedCount,
            dedupedCount = dedupedCount,
            rejectedCount = rejectedCount,
            rejectedEventKeys = rejectedEventKeys,
        )
        recordExecutionMetrics(
            now = now,
            normalizedCount = normalizedCount,
            dedupedCount = dedupedCount,
            rejectedCount = rejectedCount,
            rejectedEventKeys = rejectedEventKeys,
        )
        response
    }

    private fun normalize(
        raw: PresetObservabilityEvent,
        now: Long,
    ): NormalizationResult {
        val idempotencyKey = raw.idempotencyKey.trim().takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("IDEMPOTENCY_KEY_BLANK")
        val querySessionId = raw.querySessionId.trim().takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("QUERY_SESSION_ID_BLANK:$idempotencyKey")
        val categoryCode = stage4ExecutionLayer.normalizeCatalogCode(raw.categoryCode)
            ?: return NormalizationResult.Rejected("CATEGORY_CODE_BLANK:$idempotencyKey")
        val facetPresetCode = stage4ExecutionLayer.normalizeCatalogCode(raw.facetPresetCode)
            ?: return NormalizationResult.Rejected("FACET_PRESET_CODE_BLANK:$idempotencyKey")
        val facetCollectionCode = stage4ExecutionLayer.normalizeCatalogCode(raw.facetCollectionCode)
        val offerId = raw.offerId?.trim()?.takeIf { it.isNotEmpty() }
        val position = raw.position
        if (position != null && position <= 0) {
            return NormalizationResult.Rejected("POSITION_INVALID:$idempotencyKey")
        }
        if (raw.occurredAtMs <= 0L || raw.occurredAtMs > now + MAX_FUTURE_SKEW_MS) {
            return NormalizationResult.Rejected("OCCURRED_AT_INVALID:$idempotencyKey")
        }
        if ((raw.eventType == PresetObservabilityEventType.IMPRESSION || raw.eventType == PresetObservabilityEventType.CLICK) && offerId == null) {
            return NormalizationResult.Rejected("OFFER_ID_REQUIRED:$idempotencyKey")
        }
        if (raw.eventType == PresetObservabilityEventType.IMPRESSION && position == null) {
            return NormalizationResult.Rejected("POSITION_REQUIRED:$idempotencyKey")
        }

        val eventDate = Instant.fromEpochMilliseconds(raw.occurredAtMs)
            .toLocalDateTime(TimeZone.UTC)
            .date
        val dataVersion = raw.dataVersion?.trim()?.takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("DATA_VERSION_BLANK:$idempotencyKey")
        if (!isCompatibleDataVersion(dataVersion)) {
            return NormalizationResult.Rejected("DATA_VERSION_INCOMPATIBLE:$idempotencyKey")
        }

        val stage4PresetKey = stage4ExecutionLayer.renderDedupKey(
            entity = Stage40DedupEntity.FACET_PRESET,
            fields = mapOf("presetCode" to facetPresetCode),
            fallback = facetPresetCode,
        )
        val stage4CollectionKey = facetCollectionCode?.let { collectionCode ->
            stage4ExecutionLayer.renderDedupKey(
                entity = Stage40DedupEntity.FACET_COLLECTION,
                fields = mapOf("collectionCode" to collectionCode),
                fallback = collectionCode,
            )
        }
        val logicalDedupKey = buildLogicalDedupKey(
            eventDate = eventDate,
            eventType = raw.eventType.name,
            querySessionId = querySessionId,
            categoryCode = categoryCode,
            stage4PresetKey = stage4PresetKey,
            stage4CollectionKey = stage4CollectionKey,
            offerId = offerId,
            position = position,
            occurredAtMs = raw.occurredAtMs,
        )

        return NormalizationResult.Accepted(
            NormalizedPresetEvent(
                idempotencyKey = idempotencyKey,
                eventType = raw.eventType,
                querySessionId = querySessionId,
                categoryCode = categoryCode,
                facetCollectionCode = facetCollectionCode,
                facetPresetCode = facetPresetCode,
                offerId = offerId,
                position = position,
                occurredAtMs = raw.occurredAtMs,
                eventDate = eventDate,
                dataVersion = dataVersion,
                stage4PresetKey = stage4PresetKey,
                stage4CollectionKey = stage4CollectionKey,
                logicalDedupKey = logicalDedupKey,
            ),
        )
    }

    private fun loadExistingLogicalDedupKeys(
        candidateEvents: List<NormalizedPresetEvent>,
    ): Set<String> {
        if (candidateEvents.isEmpty()) return emptySet()

        val dates = candidateEvents.map { it.eventDate }.toSet()
        val eventTypes = candidateEvents.map { it.eventType.name }.toSet()
        val querySessionIds = candidateEvents.map { it.querySessionId }.toSet()

        val query = CatalogPresetEventsTable.selectAll()
        query.andWhere { CatalogPresetEventsTable.eventDate inList dates.toList() }
        query.andWhere { CatalogPresetEventsTable.eventType inList eventTypes.toList() }
        query.andWhere { CatalogPresetEventsTable.querySessionId inList querySessionIds.toList() }

        return query.map { row ->
            val categoryCode = stage4ExecutionLayer.normalizeCatalogCode(row[CatalogPresetEventsTable.categoryCode])
                ?: row[CatalogPresetEventsTable.categoryCode]
            val presetCode = stage4ExecutionLayer.normalizeCatalogCode(row[CatalogPresetEventsTable.facetPresetCode])
                ?: row[CatalogPresetEventsTable.facetPresetCode]
            val collectionCode = stage4ExecutionLayer.normalizeCatalogCode(row[CatalogPresetEventsTable.facetCollectionCode])

            val stage4PresetKey = stage4ExecutionLayer.renderDedupKey(
                entity = Stage40DedupEntity.FACET_PRESET,
                fields = mapOf("presetCode" to presetCode),
                fallback = presetCode,
            )
            val stage4CollectionKey = collectionCode?.let { normalizedCollectionCode ->
                stage4ExecutionLayer.renderDedupKey(
                    entity = Stage40DedupEntity.FACET_COLLECTION,
                    fields = mapOf("collectionCode" to normalizedCollectionCode),
                    fallback = normalizedCollectionCode,
                )
            }

            buildLogicalDedupKey(
                eventDate = row[CatalogPresetEventsTable.eventDate],
                eventType = row[CatalogPresetEventsTable.eventType],
                querySessionId = row[CatalogPresetEventsTable.querySessionId],
                categoryCode = categoryCode,
                stage4PresetKey = stage4PresetKey,
                stage4CollectionKey = stage4CollectionKey,
                offerId = row[CatalogPresetEventsTable.offerId],
                position = row[CatalogPresetEventsTable.position],
                occurredAtMs = row[CatalogPresetEventsTable.occurredAt],
            )
        }.toSet()
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

    private fun filterReferentiallyValidEvents(
        events: List<NormalizedPresetEvent>,
        rejectedEventKeys: MutableList<String>,
        onRejected: () -> Unit,
    ): List<NormalizedPresetEvent> {
        if (events.isEmpty()) return emptyList()

        val categoryCodes = events.map { it.categoryCode }.toSet()
        val presetCodes = events.map { it.facetPresetCode }.toSet()
        val collectionCodes = events.mapNotNull { it.facetCollectionCode }.toSet()

        val existingCategoryCodes = CategoriesTable
            .selectAll()
            .where { CategoriesTable.code inList categoryCodes.toList() }
            .map { row -> row[CategoriesTable.code] }
            .toSet()
        val existingPresetCodes = FacetPresetsTable
            .selectAll()
            .where { FacetPresetsTable.presetCode inList presetCodes.toList() }
            .map { row -> row[FacetPresetsTable.presetCode] }
            .toSet()
        val existingCollectionCodes = if (collectionCodes.isEmpty()) {
            emptySet()
        } else {
            FacetCollectionsTable
                .selectAll()
                .where { FacetCollectionsTable.collectionCode inList collectionCodes.toList() }
                .map { row -> row[FacetCollectionsTable.collectionCode] }
                .toSet()
        }

        val validEvents = mutableListOf<NormalizedPresetEvent>()
        events.forEach { event ->
            when {
                event.categoryCode !in existingCategoryCodes -> {
                    onRejected()
                    appendRejectedKey(rejectedEventKeys, "CATEGORY_CODE_UNKNOWN:${event.idempotencyKey}")
                }

                event.facetPresetCode !in existingPresetCodes -> {
                    onRejected()
                    appendRejectedKey(rejectedEventKeys, "FACET_PRESET_CODE_UNKNOWN:${event.idempotencyKey}")
                }

                event.facetCollectionCode != null && event.facetCollectionCode !in existingCollectionCodes -> {
                    onRejected()
                    appendRejectedKey(rejectedEventKeys, "FACET_COLLECTION_CODE_UNKNOWN:${event.idempotencyKey}")
                }

                else -> validEvents += event
            }
        }

        return validEvents
    }

    private fun isCompatibleDataVersion(dataVersion: String): Boolean {
        if (REQUIRED_DATA_VERSION == "unknown") return true
        return dataVersion == REQUIRED_DATA_VERSION
    }

    private fun recordExecutionMetrics(
        now: Long,
        normalizedCount: Int,
        dedupedCount: Int,
        rejectedCount: Int,
        rejectedEventKeys: List<String>,
    ) {
        stage4ExecutionObservabilityRepository.recordInTransaction(
            Stage4ExecutionMetricSample(
                stream = Stage4ExecutionStream.PRESET_EVENTS_INGEST,
                normalizedCount = normalizedCount,
                droppedCount = rejectedCount,
                logicalDedupCount = dedupedCount,
                unknownAttributeCount = 0,
                reasonCodes = rejectedEventKeys
                    .map { key -> key.substringBefore(':').trim() }
                    .filter { key -> key.isNotEmpty() }
                    .distinct(),
                metadata = mapOf(
                    "source" to "PresetObservabilityRepositoryImpl",
                ),
                createdAtMs = now,
            ),
        )
    }

    private fun appendRejectedKey(
        rejectedEventKeys: MutableList<String>,
        key: String,
    ) {
        if (rejectedEventKeys.size < MAX_REJECTED_KEYS) {
            rejectedEventKeys += key
        }
    }

    private fun isForeignKeyViolation(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (current is SQLException && current.sqlState == SQL_STATE_FOREIGN_KEY_VIOLATION) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private sealed class NormalizationResult {
        data class Accepted(val value: NormalizedPresetEvent) : NormalizationResult()
        data class Rejected(val key: String) : NormalizationResult()
    }

    private data class NormalizedPresetEvent(
        val idempotencyKey: String,
        val eventType: PresetObservabilityEventType,
        val querySessionId: String,
        val categoryCode: String,
        val facetCollectionCode: String?,
        val facetPresetCode: String,
        val offerId: String?,
        val position: Int?,
        val occurredAtMs: Long,
        val eventDate: LocalDate,
        val dataVersion: String?,
        val stage4PresetKey: String,
        val stage4CollectionKey: String?,
        val logicalDedupKey: String,
    )

    private companion object {
        private const val MAX_BATCH_SIZE = 500
        private const val MAX_REJECTED_KEYS = 50
        private const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L
        private const val SQL_STATE_FOREIGN_KEY_VIOLATION = "23503"
        private val REQUIRED_DATA_VERSION: String =
            CatalogDataVersion.current.trim().ifEmpty { "unknown" }
    }
}
