package com.example.shoppingassistant.domain.facet

import com.example.shoppingassistant.domain.model.TypedAttributeFilter
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.toRawStringAttributes
import java.util.Locale

enum class FacetPurchaseFormat {
    PICKUP,
    DELIVERY,
}

data class FacetRuntimeFilters(
    val categoryCode: String? = null,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val attributes: Map<String, TypedAttributeValue> = emptyMap(),
    val attributeFilters: Map<String, TypedAttributeFilter> = emptyMap(),
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
        definitions: Collection<FacetDefinition> = emptyList(),
    ): FacetRuntimeFilters {
        if (collection == null && preset == null) return base

        val definitionsByKey = definitions.associateBy { definition ->
            definition.facetKey.trim().lowercase(Locale.ROOT)
        }
        val attributes = LinkedHashMap(base.attributes)
        val attributeFilters = LinkedHashMap(base.attributeFilters)
        val brands = base.brands.toMutableSet()
        val conditions = base.conditions.toMutableSet()
        var priceMin = base.priceMin
        var priceMax = base.priceMax
        var purchaseFormat = base.purchaseFormat

        preset?.rules?.forEach { rule ->
            val facetKey = rule.facetKey.trim().lowercase()
            if (facetKey.isBlank()) return@forEach
            val definition = definitionsByKey[facetKey]
            val runtimeKey = definition?.runtimeFilterKey() ?: facetKey

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
                    val typedFilter = buildGenericAttributeFilter(
                        rule = rule,
                        valueType = definition?.valueType,
                    )
                    if (typedFilter != null) {
                        attributeFilters[runtimeKey] = typedFilter
                        if (definition == null && typedFilter.op == TypedAttributeOperator.EQ) {
                            typedFilter.value?.let { value ->
                                attributes[runtimeKey] = value
                            }
                        }
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
            attributeFilters = attributeFilters,
            brands = brands,
            priceMin = priceMin,
            priceMax = priceMax,
            conditions = conditions,
            purchaseFormat = purchaseFormat,
        )
    }

    private fun buildGenericAttributeFilter(
        rule: FacetPresetRule,
        valueType: FacetDataType?,
    ): TypedAttributeFilter? {
        rule.boolValue?.let { boolValue ->
            return TypedAttributeFilter(
                op = TypedAttributeOperator.EQ,
                value = TypedAttributeValue.Bool(boolValue),
            )
        }

        val minValue = rule.minValue
        val maxValue = rule.maxValue
        if (minValue != null || maxValue != null) {
            return when {
                minValue != null && maxValue != null -> TypedAttributeFilter(
                    op = TypedAttributeOperator.BETWEEN,
                    from = TypedAttributeValue.Number(minValue),
                    to = TypedAttributeValue.Number(maxValue),
                )

                minValue != null -> TypedAttributeFilter(
                    op = TypedAttributeOperator.GTE,
                    value = TypedAttributeValue.Number(minValue),
                )

                else -> TypedAttributeFilter(
                    op = TypedAttributeOperator.LTE,
                    value = TypedAttributeValue.Number(maxValue ?: return null),
                )
            }
        }

        val includeValues = rule.includeValues
            .mapNotNull { rawValue -> parseFilterValue(rawValue, valueType) }
            .distinct()
        if (includeValues.isNotEmpty()) {
            return if (includeValues.size == 1) {
                TypedAttributeFilter(
                    op = TypedAttributeOperator.EQ,
                    value = includeValues.first(),
                )
            } else {
                TypedAttributeFilter(
                    op = TypedAttributeOperator.IN,
                    values = includeValues,
                )
            }
        }

        val excludeValues = rule.excludeValues
            .mapNotNull { rawValue -> parseFilterValue(rawValue, valueType) }
            .distinct()
        if (excludeValues.size == 1) {
            return TypedAttributeFilter(
                op = TypedAttributeOperator.NEQ,
                value = excludeValues.first(),
            )
        }

        return null
    }

    private fun parseFilterValue(
        rawValue: String,
        valueType: FacetDataType?,
    ): TypedAttributeValue? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null
        return when (valueType) {
            FacetDataType.BOOL -> parseBooleanValue(trimmed)
            FacetDataType.RANGE -> parseNumericValue(trimmed)
            FacetDataType.ENUM,
            FacetDataType.TEXT,
            null,
            -> TypedAttributeValue.Text(trimmed)
        }
    }

    private fun parseNumericValue(rawValue: String): TypedAttributeValue.Number? {
        val normalized = rawValue.replace(',', '.')
        val number = normalized.toDoubleOrNull() ?: return null
        return TypedAttributeValue.Number(number)
    }

    private fun parseBooleanValue(rawValue: String): TypedAttributeValue.Bool? {
        val normalized = rawValue.trim().lowercase(Locale.ROOT)
        return when (normalized) {
            "true",
            "1",
            "yes",
            "y",
            "да",
            -> TypedAttributeValue.Bool(true)

            "false",
            "0",
            "no",
            "n",
            "нет",
            -> TypedAttributeValue.Bool(false)

            else -> null
        }
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
