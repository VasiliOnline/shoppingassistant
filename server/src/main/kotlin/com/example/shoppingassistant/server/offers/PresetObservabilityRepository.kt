package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityEvent
import com.example.shoppingassistant.domain.model.PresetObservabilityEventType
import com.example.shoppingassistant.server.catalog.CategoriesTable
import com.example.shoppingassistant.server.catalog.FacetCollectionsTable
import com.example.shoppingassistant.server.catalog.FacetPresetsTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.sql.SQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

interface PresetObservabilityRepository {
    suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse
}

class PresetObservabilityRepositoryImpl : PresetObservabilityRepository {

    override suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse = DatabaseFactory.dbQuery {
        if (request.events.isEmpty()) {
            return@dbQuery PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = 1,
                rejectedEventKeys = listOf("BATCH_EMPTY"),
            )
        }

        val now = System.currentTimeMillis()
        val rejectedEventKeys = mutableListOf<String>()
        var rejectedCount = 0

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

        if (normalized.isEmpty()) {
            return@dbQuery PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = 0,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
        }

        val uniqueByKey = LinkedHashMap<String, NormalizedPresetEvent>()
        var dedupedCount = 0
        normalized.forEach { event ->
            val previous = uniqueByKey.putIfAbsent(event.idempotencyKey, event)
            if (previous != null) dedupedCount += 1
        }

        val uniqueEvents = uniqueByKey.values.toList()
        val referentiallyValidEvents = filterReferentiallyValidEvents(
            events = uniqueEvents,
            rejectedEventKeys = rejectedEventKeys,
            onRejected = { rejectedCount += 1 },
        )

        if (referentiallyValidEvents.isEmpty()) {
            return@dbQuery PresetObservabilityBatchResponse(
                acceptedCount = 0,
                dedupedCount = dedupedCount,
                rejectedCount = rejectedCount,
                rejectedEventKeys = rejectedEventKeys,
            )
        }

        val keys = referentiallyValidEvents.map { it.idempotencyKey }
        val existingKeys = CatalogPresetEventsTable
            .selectAll()
            .where { CatalogPresetEventsTable.idempotencyKey inList keys }
            .map { row -> row[CatalogPresetEventsTable.idempotencyKey] }
            .toSet()

        dedupedCount += existingKeys.size
        val toInsert = referentiallyValidEvents.filterNot { event -> event.idempotencyKey in existingKeys }

        var acceptedCount = 0
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
                    stmt[payloadJson] = emptyMap()
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

        PresetObservabilityBatchResponse(
            acceptedCount = acceptedCount,
            dedupedCount = dedupedCount,
            rejectedCount = rejectedCount,
            rejectedEventKeys = rejectedEventKeys,
        )
    }

    private fun normalize(
        raw: PresetObservabilityEvent,
        now: Long,
    ): NormalizationResult {
        val idempotencyKey = raw.idempotencyKey.trim().takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("IDEMPOTENCY_KEY_BLANK")
        val querySessionId = raw.querySessionId.trim().takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("QUERY_SESSION_ID_BLANK:$idempotencyKey")
        val categoryCode = raw.categoryCode.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("CATEGORY_CODE_BLANK:$idempotencyKey")
        val facetPresetCode = raw.facetPresetCode.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: return NormalizationResult.Rejected("FACET_PRESET_CODE_BLANK:$idempotencyKey")
        val facetCollectionCode = raw.facetCollectionCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
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
            ),
        )
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
    )

    private companion object {
        private const val MAX_BATCH_SIZE = 500
        private const val MAX_REJECTED_KEYS = 50
        private const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L
        private const val SQL_STATE_FOREIGN_KEY_VIOLATION = "23503"
    }
}
