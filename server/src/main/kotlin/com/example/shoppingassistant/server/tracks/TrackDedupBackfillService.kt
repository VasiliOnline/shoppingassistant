package com.example.shoppingassistant.server.tracks

import com.example.shoppingassistant.domain.tracks.TrackType
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

data class TrackDedupBackfillReport(
    val totalTracks: Int,
    val processedTracks: Int,
    val skippedTracks: Int,
    val updatedTracks: Int,
    val collisions: Int,
)

interface TrackDedupBackfillService {
    suspend fun runBackfill(): TrackDedupBackfillReport
}

class TrackDedupBackfillServiceImpl : TrackDedupBackfillService {
    private val logger = LoggerFactory.getLogger(TrackDedupBackfillServiceImpl::class.java)

    override suspend fun runBackfill(): TrackDedupBackfillReport = DatabaseFactory.dbQuery {
        val rows = TracksTable
            .selectAll()
            .orderBy(TracksTable.id, SortOrder.ASC)
            .toList()

        val computed = ArrayList<ComputedTrackDedup>(rows.size)
        var skipped = 0

        rows.forEach { row ->
            val type = runCatching { TrackType.valueOf(row[TracksTable.type]) }.getOrNull()
            if (type == null) {
                skipped += 1
                return@forEach
            }
            val storedTarget = row[TracksTable.target]
            val storedTargetSpec = storedTarget.spec
            val matchKey = row[TracksTable.matchKey]
                ?: storedTargetSpec?.matchKey
            val categoryCode = row[TracksTable.targetCategoryCode]
                ?: storedTargetSpec?.categoryCode
            val targetAttributes = when {
                row[TracksTable.targetAttributes]?.isNotEmpty() == true -> row[TracksTable.targetAttributes].orEmpty()
                storedTargetSpec?.attributes?.isNotEmpty() == true -> storedTargetSpec.attributes
                else -> emptyMap()
            }
            val normalizedTarget = TrackDedupKeyFactory.normalizeTarget(
                type = type,
                matchKeyRaw = matchKey,
                categoryCodeRaw = categoryCode,
                attributesRaw = targetAttributes,
            )
            if (normalizedTarget == null) {
                skipped += 1
                return@forEach
            }

            val runtimeFilters = row[TracksTable.filters].let { filters ->
                filters.copy(extra = filters.extra.filterKeys { key -> !targetAttributes.containsKey(key) })
            }
            val nextDedupKey = TrackDedupKeyFactory.buildDedupKey(normalizedTarget, runtimeFilters)
            computed += ComputedTrackDedup(
                id = row[TracksTable.id],
                userId = row[TracksTable.userId],
                currentDedupKey = row[TracksTable.dedupKey],
                nextDedupKey = nextDedupKey,
            )
        }

        val collisions = computed
            .groupBy { item -> item.userId to item.nextDedupKey }
            .values
            .filter { items -> items.size > 1 }

        if (collisions.isNotEmpty()) {
            val sample = collisions
                .take(5)
                .joinToString(separator = ";") { items ->
                    val first = items.first()
                    val ids = items.joinToString(separator = ",") { it.id.toString() }
                    "userId=${first.userId},dedupKey=${first.nextDedupKey},ids=$ids"
                }
            logger.error(
                "tracks.dedup.backfill.collision_detected collisions={} sample={}",
                collisions.size,
                sample,
            )
            error("Track dedup backfill collision detected. collisions=${collisions.size} sample=$sample")
        }

        var updated = 0
        computed.forEach { item ->
            if (item.currentDedupKey == item.nextDedupKey) return@forEach
            val changed = TracksTable.update(
                where = { TracksTable.id eq item.id },
            ) { stmt ->
                stmt[TracksTable.dedupKey] = item.nextDedupKey
            }
            if (changed > 0) {
                updated += 1
            }
        }

        val report = TrackDedupBackfillReport(
            totalTracks = rows.size,
            processedTracks = computed.size,
            skippedTracks = skipped,
            updatedTracks = updated,
            collisions = 0,
        )
        logger.info(
            "tracks.dedup.backfill.completed total={} processed={} skipped={} updated={}",
            report.totalTracks,
            report.processedTracks,
            report.skippedTracks,
            report.updatedTracks,
        )
        report
    }

    private data class ComputedTrackDedup(
        val id: Long,
        val userId: Long,
        val currentDedupKey: String,
        val nextDedupKey: String,
    )
}
