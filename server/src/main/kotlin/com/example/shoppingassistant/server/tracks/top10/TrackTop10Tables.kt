package com.example.shoppingassistant.server.tracks.top10

import com.example.shoppingassistant.domain.tracks.RankExplanation
import com.example.shoppingassistant.domain.tracks.RankedOffer
import com.example.shoppingassistant.domain.tracks.SourceStamp
import com.example.shoppingassistant.server.tracks.TracksTable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

private val json = Json { ignoreUnknownKeys = true }

object TrackTop10SnapshotsTable : Table("track_top10_snapshots") {
    val trackId = long("track_id").references(TracksTable.id, onDelete = ReferenceOption.CASCADE)
    val computedAt = long("computed_at")
    val items = jsonb("items_json", json, ListSerializer(RankedOffer.serializer()))
    val explanation = jsonb("explanation_json", json, RankExplanation.serializer())
    val sourceStamps = jsonb("source_stamps_json", json, ListSerializer(SourceStamp.serializer()))
    val freshnessSec = integer("freshness_sec")

    val lockedBy = varchar("locked_by", 64).nullable()
    val lockUntil = long("lock_until").nullable()
    val lastAttemptAt = long("last_attempt_at").nullable()
    val lastSuccessAt = long("last_success_at").nullable()
    val failCount = integer("fail_count").default(0)
    val nextRetryAt = long("next_retry_at").nullable()
    val lastError = varchar("last_error", 512).nullable()

    override val primaryKey = PrimaryKey(trackId)

    init {
        index(false, nextRetryAt)
        index(false, lockUntil)
        index(false, computedAt)
    }
}
