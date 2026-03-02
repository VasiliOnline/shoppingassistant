package com.example.shoppingassistant.server.tracks

import com.example.shoppingassistant.server.db.DatabaseFactory
import java.util.Locale
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

data class TrackTargetPostMigrationGuardReport(
    val scannedTracks: Int,
    val emptyTargetCategoryCode: Int,
    val targetAttributesQualityIssues: Int,
    val reasonCounts: Map<String, Int>,
    val emptyCategorySampleTrackIds: List<Long>,
    val attributesIssueSampleTrackIds: List<Long>,
)

interface TrackTargetPostMigrationGuardService {
    suspend fun runChecks(limit: Int = 20_000): TrackTargetPostMigrationGuardReport
}

class TrackTargetPostMigrationGuardServiceImpl : TrackTargetPostMigrationGuardService {
    private companion object {
        const val MAX_TARGET_ATTRIBUTES: Int = 64
        const val MAX_TARGET_ATTR_KEY_LENGTH: Int = 96
        const val MAX_TARGET_ATTR_VALUE_LENGTH: Int = 256
        const val MAX_SAMPLE_SIZE: Int = 20
    }

    private val logger = LoggerFactory.getLogger(TrackTargetPostMigrationGuardServiceImpl::class.java)

    override suspend fun runChecks(limit: Int): TrackTargetPostMigrationGuardReport = DatabaseFactory.dbQuery {
        val safeLimit = limit.coerceIn(1, 200_000)
        val rows = TracksTable
            .selectAll()
            .limit(safeLimit)
            .toList()

        val reasonCounts = linkedMapOf<String, Int>()
        var emptyCategoryCount = 0
        var qualityIssueCount = 0
        val emptyCategorySample = ArrayList<Long>(MAX_SAMPLE_SIZE)
        val qualityIssueSample = ArrayList<Long>(MAX_SAMPLE_SIZE)

        rows.forEach { row ->
            val trackId = row[TracksTable.id]
            val categoryCode = sanitizeCategoryCode(
                row[TracksTable.targetCategoryCode] ?: row[TracksTable.target].spec?.categoryCode,
            )
            if (categoryCode == null) {
                emptyCategoryCount += 1
                if (emptyCategorySample.size < MAX_SAMPLE_SIZE) {
                    emptyCategorySample += trackId
                }
            }

            val qualityReasons = targetAttributesQualityReasons(row)
            if (qualityReasons.isNotEmpty()) {
                qualityIssueCount += 1
                if (qualityIssueSample.size < MAX_SAMPLE_SIZE) {
                    qualityIssueSample += trackId
                }
                qualityReasons.forEach { reason ->
                    reasonCounts.merge(reason, 1, Int::plus)
                }
            }
        }

        if (emptyCategoryCount > 0) {
            logger.error(
                "tracks.guard.post_migration.empty_target_category_code count={} scanned={} sampleTrackIds={}",
                emptyCategoryCount,
                rows.size,
                emptyCategorySample.joinToString(","),
            )
        } else {
            logger.info(
                "tracks.guard.post_migration.empty_target_category_code count=0 scanned={}",
                rows.size,
            )
        }

        if (qualityIssueCount > 0) {
            val reasonsSerialized = reasonCounts.entries
                .sortedByDescending { it.value }
                .joinToString(",") { (reason, count) -> "$reason=$count" }
            logger.error(
                "tracks.guard.post_migration.target_attributes_quality count={} scanned={} reasons={} sampleTrackIds={}",
                qualityIssueCount,
                rows.size,
                reasonsSerialized,
                qualityIssueSample.joinToString(","),
            )
        } else {
            logger.info(
                "tracks.guard.post_migration.target_attributes_quality count=0 scanned={}",
                rows.size,
            )
        }

        TrackTargetPostMigrationGuardReport(
            scannedTracks = rows.size,
            emptyTargetCategoryCode = emptyCategoryCount,
            targetAttributesQualityIssues = qualityIssueCount,
            reasonCounts = reasonCounts.toMap(),
            emptyCategorySampleTrackIds = emptyCategorySample,
            attributesIssueSampleTrackIds = qualityIssueSample,
        )
    }

    private fun targetAttributesQualityReasons(row: org.jetbrains.exposed.sql.ResultRow): Set<String> {
        val reasons = linkedSetOf<String>()
        val rawAttributes = row[TracksTable.targetAttributes].orEmpty()
        val normalizedAttributes = sanitizeAttributes(rawAttributes)
        val specAttributes = sanitizeAttributes(row[TracksTable.target].spec?.attributes.orEmpty())

        if (rawAttributes.size > MAX_TARGET_ATTRIBUTES) {
            reasons += "too-many-target-attributes"
        }
        if (rawAttributes.any { (key, value) -> key.isBlank() || value.isBlank() }) {
            reasons += "blank-key-or-value"
        }
        if (rawAttributes.any { (key, value) ->
                key.trim().length > MAX_TARGET_ATTR_KEY_LENGTH ||
                    value.trim().length > MAX_TARGET_ATTR_VALUE_LENGTH
            }
        ) {
            reasons += "target-attribute-too-long"
        }
        if (normalizedAttributes != rawAttributes) {
            reasons += "not-normalized"
        }
        if (normalizedAttributes != specAttributes) {
            reasons += "spec-column-mismatch"
        }
        return reasons
    }

    private fun sanitizeCategoryCode(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)

    private fun sanitizeAttributes(values: Map<String, String>): Map<String, String> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
            .toMap(LinkedHashMap())
    }
}
