package com.example.shoppingassistant.server.price

import com.example.shoppingassistant.domain.ingest.SourceType

enum class PriceProbeStatus {
    OK,
    UNSUPPORTED,
    TEMP_BLOCKED,
    NETWORK_ERROR,
    PARSE_ERROR,
}

data class PriceProbeResult(
    val status: PriceProbeStatus,
    val priceValue: Double? = null,
    val currency: String? = null,
    val canonicalUrl: String? = null,
    val listingId: String? = null,
    val httpStatus: Int? = null,
    val latencyMs: Long? = null,
    val bytes: Long? = null,
    val message: String? = null,
)

interface PriceProbe {
    val sourceType: SourceType
    val parserVersion: String get() = "1"
    suspend fun probe(url: String): PriceProbeResult
}

class PriceProbeRegistry(
    probes: List<PriceProbe>,
) {
    private val bySource = probes.associateBy { it.sourceType }

    fun get(sourceType: SourceType): PriceProbe? = bySource[sourceType]
}
