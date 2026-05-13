package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

/**
 * Отдельный enrichment-контракт для live/observed values.
 * Он не является частью канонического effective spec и может приходить
 * из локального индекса, server-side enrichment или любого другого runtime-источника.
 */
@Serializable
data class CatalogLiveValuesRequest(
    val categoryCode: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val localeTag: String? = null,
    val attributeCodes: List<String> = emptyList(),
)

@Serializable
data class CatalogLiveValuesSnapshot(
    val valuesByAttributeCode: Map<String, List<String>> = emptyMap(),
    val knownValuesByAttributeCode: Map<String, List<String>> = emptyMap(),
    val knownValueAliasesByAttributeCode: Map<String, Map<String, List<String>>> = emptyMap(),
    val brandOptions: List<String> = emptyList(),
    val modelOptions: List<String> = emptyList(),
)

interface CatalogLiveValuesRepository {
    suspend fun getLiveValues(
        request: CatalogLiveValuesRequest,
    ): CatalogLiveValuesSnapshot
}
