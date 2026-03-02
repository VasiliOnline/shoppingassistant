package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Критерии поиска офферов/объявлений.
 */
@Serializable
data class OfferSearchCriteria(
    val brand: String?,
    val model: String?,
    val brands: List<String> = emptyList(),
    val categoryCode: String? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val location: String? = null,
    val radiusKm: Int? = null,
    val centerLat: Double? = null,
    val centerLon: Double? = null,
    val geoMode: GeoMode? = null,
    val deliverableOnly: Boolean = false,
    val condition: String? = null,
    val deliveryChannels: List<String> = emptyList(),
    val attributes: Map<String, TypedAttributeValue> = emptyMap(),
    val attributeFilters: Map<String, TypedAttributeFilter> = emptyMap(),
    val userCountry: String? = null,
    val userLanguage: String? = null,
    val limit: Int = 20,
    val sort: OfferSort = OfferSort.RANK,
    val sellerQuery: String? = null,
    val sellerCity: String? = null,
    val sellerCountryCode: String? = null,
    val updatedAfterMs: Long? = null,
    val conditions: List<String> = emptyList(),
    val querySessionId: String? = null,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
)

fun OfferSearchCriteria.rawAttributes(): Map<String, String> =
    attributes.toRawStringAttributes()

@Serializable
data class TypedAttributeFilter(
    val op: TypedAttributeOperator = TypedAttributeOperator.EQ,
    val value: TypedAttributeValue? = null,
    val values: List<TypedAttributeValue> = emptyList(),
    val from: TypedAttributeValue? = null,
    val to: TypedAttributeValue? = null,
)

@Serializable
enum class TypedAttributeOperator {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE,
    BETWEEN,
    IN,
    CONTAINS,
    EXISTS,
    NOT_EXISTS,
}

@Serializable
enum class OfferSort {
    RANK,
    PRICE_ASC,
    PRICE_DESC,
    RATING_DESC,
    DELIVERY_ASC,
    NEWEST,
    DISTANCE_ASC,
}

@Serializable
enum class GeoMode {
    RADIUS,
    CITY_FALLBACK,
}
