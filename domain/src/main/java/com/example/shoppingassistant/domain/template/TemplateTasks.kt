package com.example.shoppingassistant.domain.template

import com.example.shoppingassistant.domain.catalog.AttributeDataType
import kotlinx.serialization.Serializable

@Serializable
enum class TemplateAnchorType { PRODUCT, CATEGORY }

@Serializable
enum class TemplateSnapshotMode { SEARCH, OFFER, EXPRESS }

@Serializable
data class TemplateSnapshotAttr(
    val key: String,
    val value: String,
    val type: AttributeDataType = AttributeDataType.STRING,
)

@Serializable
data class TemplateSnapshotRange(
    val min: String? = null,
    val max: String? = null,
    val unit: String? = null,
)

/**
 * Канонические данные шаблона, из которых выводится стабильный `templateId`.
 */
@Serializable
data class TemplateSnapshotData(
    val anchorType: TemplateAnchorType,
    val anchorId: String,
    val categoryCode: String? = null,
    val attrs: List<TemplateSnapshotAttr> = emptyList(),
    val attrsMulti: Map<String, List<TemplateSnapshotAttr>> = emptyMap(),
    val attrsRange: Map<String, TemplateSnapshotRange> = emptyMap(),
    val freeText: String? = null,
    val mode: TemplateSnapshotMode = TemplateSnapshotMode.SEARCH,
    val schemaVersion: Int = 1,
    val taxonomyVersion: String? = null,
    val locale: String? = null,
    val unboundTokens: List<String> = emptyList(),
)

/**
 * Снимок шаблона для истории/подписок: `templateId` — стабильный hash(snapshotData).
 */
@Serializable
data class TemplateSnapshot(
    val data: TemplateSnapshotData,
    val templateId: String,
)

/**
 * Стабильный ID шаблона для дедупа истории/подписок.
 */
interface TemplateIdTask {
    fun computeId(data: TemplateSnapshotData): String
}

@Serializable
data class TemplateHistoryEntry(
    val snapshot: TemplateSnapshot,
    val usedAtMillis: Long,
)

/**
 * История последних выбранных/использованных шаблонов (локальное хранилище).
 */
interface TemplateHistoryRepository {
    suspend fun upsert(entry: TemplateHistoryEntry)
    suspend fun listRecent(limit: Int = 20): List<TemplateHistoryEntry>
    suspend fun deleteByIds(ids: List<String>)
}
