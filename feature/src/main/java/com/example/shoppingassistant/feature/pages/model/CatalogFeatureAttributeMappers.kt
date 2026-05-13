package com.example.shoppingassistant.feature.pages.model

import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogValueOption
import com.example.shoppingassistant.domain.catalog.Stage22ValueSetType
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.i18n.displayLabel
import java.util.Locale

fun CatalogCategoryEffectiveSpec.toFeatureAttributeDefs(): List<AttributeDef> =
    allAttributes()
        .sortedBy { it.uiOrder }
        .map { attribute -> attribute.toFeatureAttributeDef() }

fun CatalogCategoryEffectiveSpec.toFeatureParseableAttributeDefs(): List<AttributeDef> =
    allAttributes()
        .sortedBy { it.uiOrder }
        .mapNotNull { attribute -> attribute.toFeatureParseableAttributeDef(categoryCode = category.code) }

private fun CatalogAttributeSpec.toFeatureAttributeDef(): AttributeDef {
    val allowedValues = toFeatureValueDefs()
    val enumLike = dataType == AttributeDataType.ENUM || dataType == AttributeDataType.BOOL
    val selectionOnly =
        enumLike ||
            valueSetType != Stage22ValueSetType.OPEN ||
            code == "brand" ||
            code == "model"

    return AttributeDef(
        key = code,
        title = title,
        options = if (enumLike) allowedValues.map(ValueDef::label) else emptyList(),
        allowedValues = allowedValues,
        requiredForSearch = requiredForSearch,
        requiredForOffer = requiredForOffer,
        requiredForExpress = requiredForExpress,
        facetEnabled = facetEnabled,
        multiValued = multiValued,
        selectionOnly = selectionOnly,
        isDictionaryComplete = allowedValues.isNotEmpty() || valueSetType == Stage22ValueSetType.OPEN,
    )
}

private fun CatalogAttributeSpec.toFeatureParseableAttributeDef(categoryCode: String): AttributeDef? {
    if (code == "model_line") return null
    val allowedValues = toFeatureParseableValueDefs(categoryCode = categoryCode)
    val canParseFromText = when (dataType) {
        AttributeDataType.ENUM,
        AttributeDataType.BOOL,
            -> true

        else -> facetEnabled && allowedValues.isNotEmpty()
    }
    if (!canParseFromText || allowedValues.isEmpty()) return null

    return AttributeDef(
        key = code,
        title = title,
        options = allowedValues.map(ValueDef::label),
        allowedValues = allowedValues,
        requiredForSearch = requiredForSearch,
        requiredForOffer = requiredForOffer,
        requiredForExpress = requiredForExpress,
        facetEnabled = facetEnabled,
        multiValued = multiValued,
        selectionOnly = true,
        isDictionaryComplete = true,
    )
}

private fun CatalogAttributeSpec.toFeatureParseableValueDefs(categoryCode: String): List<ValueDef> {
    val localeTag = Locale.getDefault().toLanguageTag()
    val baseValues = toFeatureValueDefs()
    val scopedFallback = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
        attributeCode = code,
        scope = CatalogGovernanceScope(categoryCode = categoryCode),
        locale = localeTag,
        includeLessSpecificFallback = true,
    )
        .map { value ->
            ValueDef(
                code = value.canonicalCode,
                label = value.displayValue,
                synonyms = value.aliases,
                rank = 0,
            )
        }
    return mergeFeatureValueDefs(baseValues, scopedFallback)
}

private fun mergeFeatureValueDefs(
    base: List<ValueDef>,
    overlay: List<ValueDef>,
): List<ValueDef> {
    if (overlay.isEmpty()) return base
    val mergedByCode = LinkedHashMap<String, ValueDef>()
    base.forEach { value ->
        mergedByCode[value.code] = value
    }
    overlay.forEach { value ->
        val existing = mergedByCode[value.code]
        mergedByCode[value.code] = if (existing == null) {
            value
        } else {
            existing.copy(
                label = value.label.ifBlank { existing.label },
                synonyms = (existing.synonyms + value.synonyms)
                    .map { synonym -> synonym.trim() }
                    .filter { synonym -> synonym.isNotEmpty() }
                    .distinctBy { synonym -> synonym.lowercase() },
                rank = maxOf(existing.rank, value.rank),
            )
        }
    }
    return mergedByCode.values.toList()
}

private fun CatalogAttributeSpec.toFeatureValueDefs(): List<ValueDef> =
    options
        .sortedWith(
            compareByDescending<CatalogValueOption> { it.rank }
                .thenBy { option ->
                    option.displayLabel(locale = Locale.getDefault().toLanguageTag()).lowercase()
                },
        )
        .map { option ->
            val label = option.displayLabel(locale = Locale.getDefault().toLanguageTag())
            ValueDef(
                code = option.valueCode,
                label = label,
                synonyms = (option.labels.values + option.aliases)
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals(label, ignoreCase = true) }
                    .distinctBy { it.lowercase() },
                rank = option.rank,
            )
        }
