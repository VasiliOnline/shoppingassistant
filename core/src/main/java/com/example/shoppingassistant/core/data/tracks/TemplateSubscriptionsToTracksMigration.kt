package com.example.shoppingassistant.core.data.tracks

import android.content.Context
import com.example.shoppingassistant.core.data.templatesubscriptions.db.TemplateSubscriptionsDao
import com.example.shoppingassistant.domain.template.TemplateSnapshot
import com.example.shoppingassistant.domain.tracks.AlertRule
import com.example.shoppingassistant.domain.tracks.Track
import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackRepository
import com.example.shoppingassistant.domain.tracks.TrackState
import com.example.shoppingassistant.domain.tracks.TrackTarget
import com.example.shoppingassistant.domain.tracks.TrackType
import kotlinx.serialization.json.Json
import androidx.core.content.edit

/**
 * One-time migration from legacy template subscriptions to tracks.
 *
 * Idempotency:
 * - guarded by preference flag;
 * - per-item dedup by legacy template id and normalized query.
 */
class TemplateSubscriptionsToTracksMigration(
    context: Context,
    private val templateSubscriptionsDao: TemplateSubscriptionsDao,
    private val trackRepository: TrackRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_DONE, false)) return

        val rows = templateSubscriptionsDao.listWithTriggers()
        if (rows.isEmpty()) {
            markDone()
            return
        }

        val existingTracks = runCatching { trackRepository.listTracks() }.getOrElse { emptyList() }
        val existingLegacyTemplateIds = existingTracks
            .mapNotNull { it.filters.extra[LEGACY_TEMPLATE_ID_KEY] }
            .toMutableSet()
        val existingSearchQueries = existingTracks
            .asSequence()
            .filter { it.type == TrackType.SEARCH }
            .mapNotNull { it.target.query?.trim()?.lowercase().takeIf { q -> !q.isNullOrBlank() } }
            .toMutableSet()

        var hasFailures = false

        rows.forEach { row ->
            val templateId = row.subscription.templateId
            val snapshot = runCatching {
                json.decodeFromString(TemplateSnapshot.serializer(), row.subscription.snapshotJson)
            }.getOrNull()

            if (snapshot == null) {
                templateSubscriptionsDao.deleteById(templateId)
                return@forEach
            }

            val query = buildQuery(snapshot)
            if (query.isBlank()) {
                templateSubscriptionsDao.deleteById(templateId)
                return@forEach
            }

            val normalizedQuery = query.lowercase()
            if (existingLegacyTemplateIds.contains(templateId) || existingSearchQueries.contains(normalizedQuery)) {
                templateSubscriptionsDao.deleteById(templateId)
                return@forEach
            }

            val state = if (row.subscription.isActive) TrackState.ACTIVE else TrackState.PAUSED
            val attrsExtra = snapshot.data.attrs
                .asSequence()
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .associate { attr -> "attr_${attr.key}" to attr.value }

            val track = Track(
                id = "legacy-$templateId",
                title = snapshot.data.anchorId.trim().ifBlank { query }.take(80),
                categoryCode = snapshot.data.categoryCode?.takeIf { it.isNotBlank() },
                type = TrackType.SEARCH,
                target = TrackTarget(query = query),
                filters = TrackFilters(
                    extra = buildMap {
                        put(LEGACY_TEMPLATE_ID_KEY, templateId)
                        putAll(attrsExtra)
                    }
                ),
                alertRules = row.triggers.map { trigger ->
                    AlertRule(
                        type = trigger.type,
                        value = when {
                            trigger.moneyMinor != null -> trigger.moneyMinor.toString()
                            trigger.numberValue != null -> trigger.numberValue.toString()
                            !trigger.currency.isNullOrBlank() -> trigger.currency
                            else -> null
                        },
                    )
                },
                state = state,
                createdAt = row.subscription.createdAtMillis,
                updatedAt = row.subscription.updatedAtMillis,
            )

            val migrated = runCatching { trackRepository.upsertTrack(track) }.isSuccess
            if (migrated) {
                existingLegacyTemplateIds += templateId
                existingSearchQueries += normalizedQuery
                templateSubscriptionsDao.deleteById(templateId)
            } else {
                hasFailures = true
            }
        }

        if (!hasFailures) {
            markDone()
        }
    }

    private fun buildQuery(snapshot: TemplateSnapshot): String {
        val heading = snapshot.data.anchorId.trim()
        val freeText = snapshot.data.freeText?.trim().orEmpty()
        return listOfNotNull(
            heading.takeIf { it.isNotBlank() },
            freeText.takeIf { it.isNotBlank() },
        ).joinToString(" ").trim()
    }

    private fun markDone() {
        prefs.edit { putBoolean(KEY_DONE, true) }
    }

    private companion object {
        private const val PREFS_NAME: String = "tracking_migrations"
        private const val KEY_DONE: String = "template_subscriptions_to_tracks_v1_done"
        private const val LEGACY_TEMPLATE_ID_KEY: String = "legacyTemplateId"
    }
}
