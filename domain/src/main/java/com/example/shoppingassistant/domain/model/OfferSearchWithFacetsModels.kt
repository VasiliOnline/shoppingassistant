package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class OfferSearchWithFacetsRequest(
    val criteria: OfferSearchCriteria,
    val facets: Set<OfferFacetType> = emptySet(),
    val excludeFacetFilters: Set<OfferFacetType> = emptySet(),
    val attributeFacetKeys: Set<String> = emptySet(),
)

@Serializable
data class OfferSearchWithFacetsResponse(
    val offers: List<OfferFull>,
    val total: Int,
    val facets: OfferSearchFacets = OfferSearchFacets(),
    val generatedAtMs: Long = System.currentTimeMillis(),
    val meta: OfferSearchMeta = OfferSearchMeta(),
)

@Serializable
data class OfferSearchFacets(
    val brands: List<BrandFacet> = emptyList(),
    val conditions: List<ValueFacet> = emptyList(),
    val deliveryChannels: List<ValueFacet> = emptyList(),
    val attributes: Map<String, List<ValueFacet>> = emptyMap(),
)

@Serializable
data class OfferSearchMeta(
    val computedAtMs: Long = System.currentTimeMillis(),
    val totalCount: Int = 0,
    val filtersHash: String? = null,
    val geoMode: GeoMode? = null,
    val radiusKmApplied: Int? = null,
)

@Serializable
data class BrandFacet(
    val id: String,
    val name: String,
    val count: Int,
)

@Serializable
data class ValueFacet(
    val id: String,
    val name: String,
    val count: Int,
)

@Serializable
enum class OfferFacetType {
    BRAND,
    CONDITION,
    DELIVERY_CHANNEL,
}
