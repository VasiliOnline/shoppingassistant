package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PresetObservabilityEventType {
    IMPRESSION,
    CLICK,
    CONVERSION,
}

@Serializable
data class PresetObservabilityEvent(
    val idempotencyKey: String,
    val eventType: PresetObservabilityEventType,
    val querySessionId: String,
    val categoryCode: String,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String,
    val offerId: String? = null,
    val position: Int? = null,
    val occurredAtMs: Long,
    val dataVersion: String? = null,
)

@Serializable
data class PresetObservabilityBatchRequest(
    val events: List<PresetObservabilityEvent>,
)

@Serializable
data class PresetObservabilityBatchResponse(
    val acceptedCount: Int,
    val dedupedCount: Int,
    val rejectedCount: Int,
    val rejectedEventKeys: List<String> = emptyList(),
    val serverTimeMs: Long = System.currentTimeMillis(),
)
