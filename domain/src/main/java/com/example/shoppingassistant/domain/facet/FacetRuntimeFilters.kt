package com.example.shoppingassistant.domain.facet

import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.toRawStringAttributes

enum class FacetPurchaseFormat {
    PICKUP,
    DELIVERY,
}

data class FacetRuntimeFilters(
    val categoryCode: String? = null,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val attributes: Map<String, TypedAttributeValue> = emptyMap(),
    val brands: Set<String> = emptySet(),
    val priceMin: Int? = null,
    val priceMax: Int? = null,
    val conditions: Set<String> = emptySet(),
    val purchaseFormat: FacetPurchaseFormat? = null,
)

fun FacetRuntimeFilters.rawAttributes(): Map<String, String> =
    attributes.toRawStringAttributes()

object FacetRuntimeFiltersApplier {

    fun apply(
        base: FacetRuntimeFilters,
        collection: FacetCollection?,
        preset: FacetPreset?,
    ): FacetRuntimeFilters {
        if (collection == null && preset == null) return base

        val attributes = LinkedHashMap(base.attributes)
        val brands = base.brands.toMutableSet()
        val conditions = base.conditions.toMutableSet()
        var priceMin = base.priceMin
        var priceMax = base.priceMax
        var purchaseFormat = base.purchaseFormat

        preset?.rules?.forEach { rule ->
            val facetKey = rule.facetKey.trim().lowercase()
            if (facetKey.isBlank()) return@forEach

            when (facetKey) {
                "brand" -> {
                    rule.includeValues
                        .mapNotNull { value -> value.trim().takeIf { it.isNotBlank() } }
                        .forEach { value -> brands += value }
                }

                "condition" -> {
                    rule.includeValues
                        .mapNotNull { value -> normalizeCondition(value) }
                        .forEach { value -> conditions += value }
                }

                "price",
                "price_rub",
                -> {
                    rule.minValue?.toInt()?.let { min ->
                        priceMin = if (priceMin == null) min else maxOf(priceMin ?: min, min)
                    }
                    rule.maxValue?.toInt()?.let { max ->
                        priceMax = if (priceMax == null) max else minOf(priceMax ?: max, max)
                    }
                }

                "purchase_format",
                "delivery_channel",
                "delivery",
                -> {
                    val value = rule.includeValues.firstOrNull()?.trim()?.lowercase().orEmpty()
                    purchaseFormat = when (value) {
                        "pickup" -> FacetPurchaseFormat.PICKUP
                        "delivery" -> FacetPurchaseFormat.DELIVERY
                        else -> purchaseFormat
                    }
                }

                else -> {
                    val includeValue = rule.includeValues.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    if (includeValue != null) {
                        attributes[facetKey] = TypedAttributeValue.Text(includeValue)
                    } else if (rule.boolValue != null) {
                        attributes[facetKey] = TypedAttributeValue.Bool(rule.boolValue)
                    }
                }
            }
        }

        val categoryCode = collection?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: preset?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: base.categoryCode

        return base.copy(
            categoryCode = categoryCode,
            facetCollectionCode = collection?.collectionCode ?: base.facetCollectionCode,
            facetPresetCode = preset?.presetCode ?: base.facetPresetCode,
            attributes = attributes,
            brands = brands,
            priceMin = priceMin,
            priceMax = priceMax,
            conditions = conditions,
            purchaseFormat = purchaseFormat,
        )
    }

    private fun normalizeCondition(raw: String): String? {
        val normalized = raw.trim().lowercase()
        if (normalized.isBlank()) return null
        return when (normalized) {
            "new",
            "новый",
            -> "new"

            "like_new",
            "likenew",
            "как новый",
            -> "like_new"

            "used",
            "б/у",
            "бу",
            -> "used"

            else -> normalized
        }
    }
}
