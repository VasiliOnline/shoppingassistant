package com.example.shoppingassistant.core.data.link

import com.example.shoppingassistant.domain.ingest.IngestStatus
import com.example.shoppingassistant.domain.ingest.SourceType
import com.example.shoppingassistant.domain.offers.TrackedOfferInput

/**
 * Метаданные источника для шаблона ссылки.
 */
data class LinkSourceMeta(
    val sourceType: SourceType,
    val sourceId: String? = null,
    val url: String,
    val canonicalUrl: String? = null,
    val listingId: String? = null,
    val domainName: String? = null,
    val sourceIconUrl: String? = null,
)

/**
 * Сырый шаблон, который отдаём UI после ingest+mirror.
 */
data class LinkTemplateRaw(
    val title: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val categoryCode: String,
    val categoryConfidence: Double,
    val parserVersion: String,
    val price: Double? = null,
    val currency: String? = null,
    val imageUrls: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
    val ingestStatus: IngestStatus = IngestStatus.OK,
    val ingestMessage: String? = null,
    val sourceMeta: LinkSourceMeta,
)

/**
 * Строит LinkTemplateRaw: ходит в ingest и mirror, объединяет атрибуты.
 */
interface LinkTemplateBuilderTask {
    suspend fun build(source: SourceType, url: String): LinkTemplateRaw
    suspend fun build(url: String): LinkTemplateRaw
}

/**
 * Чистый маппер из LinkTemplateRaw + selectedFilters + userId → TrackedOfferInput.
 */
interface LinkTemplateMapperTask {
    fun map(
        template: LinkTemplateRaw,
        selectedFilters: Map<String, String>,
        userId: String,
        photoUrls: List<String> = emptyList(),
    ): TrackedOfferInput
}
