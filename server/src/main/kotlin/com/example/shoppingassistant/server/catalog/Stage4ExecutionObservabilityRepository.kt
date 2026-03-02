package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.server.db.DatabaseFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.insert
import org.slf4j.LoggerFactory

enum class Stage4ExecutionStream(val code: String) {
    OFFERS_INGEST("OFFERS_INGEST"),
    OFFERS_SEARCH("OFFERS_SEARCH"),
    PRESET_EVENTS_INGEST("PRESET_EVENTS_INGEST"),
    OFFERS_BACKFILL("OFFERS_BACKFILL"),
    PRESET_EVENTS_BACKFILL("PRESET_EVENTS_BACKFILL"),
}

data class Stage4ExecutionMetricSample(
    val stream: Stage4ExecutionStream,
    val normalizedCount: Int,
    val droppedCount: Int,
    val logicalDedupCount: Int,
    val unknownAttributeCount: Int,
    val reasonCodes: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    val metricDate: LocalDate = Instant.fromEpochMilliseconds(createdAtMs)
        .toLocalDateTime(TimeZone.UTC)
        .date
}

interface Stage4ExecutionObservabilityRepository {
    fun recordInTransaction(sample: Stage4ExecutionMetricSample)

    suspend fun record(sample: Stage4ExecutionMetricSample) {
        DatabaseFactory.dbQuery {
            recordInTransaction(sample)
        }
    }
}

class Stage4ExecutionObservabilityRepositoryImpl : Stage4ExecutionObservabilityRepository {
    private val logger = LoggerFactory.getLogger(Stage4ExecutionObservabilityRepositoryImpl::class.java)
    private val warned = AtomicBoolean(false)

    override fun recordInTransaction(sample: Stage4ExecutionMetricSample) {
        runCatching {
            CatalogStage4ExecutionMetricsTable.insert { stmt ->
                stmt[metricDate] = sample.metricDate
                stmt[stream] = sample.stream.code
                stmt[normalizedCount] = sample.normalizedCount.coerceAtLeast(0)
                stmt[droppedCount] = sample.droppedCount.coerceAtLeast(0)
                stmt[logicalDedupCount] = sample.logicalDedupCount.coerceAtLeast(0)
                stmt[unknownAttributeCount] = sample.unknownAttributeCount.coerceAtLeast(0)
                stmt[reasonCodes] = sample.reasonCodes
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_REASON_CODES)
                stmt[metadata] = sample.metadata
                    .mapNotNull { (key, value) ->
                        key.trim().takeIf { it.isNotEmpty() }?.let { trimmedKey ->
                            trimmedKey to value.trim()
                        }
                    }
                    .toMap()
                stmt[createdAt] = sample.createdAtMs
            }
        }.onFailure { error ->
            if (warned.compareAndSet(false, true)) {
                logger.warn("stage4.execution.metrics.write_failed reason={}", error.message)
            }
        }
    }

    private companion object {
        private const val MAX_REASON_CODES = 50
    }
}
