// Last synced: 2025-12-16 17:55:48
package com.example.shoppingassistant.feature.pages.main.context

import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintCheckResult
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.feature.pages.model.AttributeDef
import com.example.shoppingassistant.feature.pages.model.Product
import com.example.shoppingassistant.feature.pages.model.ValueDef
import com.example.shoppingassistant.feature.pages.model.toFeatureAttributeDefs
import java.util.Locale

data class CategoryUiCatalog(
    val categoryCode: String?,
    val defs: List<AttributeDef>,
    val liveValuesByKey: Map<String, List<String>> = emptyMap(),
    val requiredIfRules: List<RequiredIfRule> = emptyList(),
    val constraints: List<CatalogConstraints> = emptyList(),
)

/**
 * Каталог атрибутов для выбранного товара.
 * Канонический read-контракт строится только из effective spec категории.
 * Live/observed values из локальной БД возвращаются отдельно как enrichment и не подменяют сам каталог.
 */
suspend fun attributeCatalogFor(
    product: Product?,
    liveValuesRepository: CatalogLiveValuesRepository,
    catalog: CatalogReadRepository,
    constraintsResolver: CatalogConstraintsResolver,
    categoryCode: String?,
    selectedFilters: Map<String, String> = emptyMap(),
): CategoryUiCatalog {
    val resolvedCategoryCode = categoryCode?.takeIf { it.isNotBlank() }
        ?: product?.categoryCode?.takeIf { it.isNotBlank() }
    if (resolvedCategoryCode == null) {
        return CategoryUiCatalog(
            categoryCode = null,
            defs = emptyList(),
            requiredIfRules = emptyList(),
            constraints = emptyList(),
        )
    }

    val lookupBrand = product?.brand?.takeIf { it.isNotBlank() } ?: selectedFilters["brand"]?.takeIf { it.isNotBlank() }
    val lookupModel = product?.model?.takeIf { it.isNotBlank() } ?: selectedFilters["model"]?.takeIf { it.isNotBlank() }
    val effectiveSpec = runCatching {
        catalog.getCategoryEffectiveSpec(
            categoryCode = resolvedCategoryCode,
            brand = lookupBrand,
            model = lookupModel,
        )
    }.getOrNull()
    if (effectiveSpec == null) {
        return CategoryUiCatalog(
            categoryCode = resolvedCategoryCode,
            defs = emptyList(),
            requiredIfRules = emptyList(),
            constraints = emptyList(),
        )
    }

    val requiredIfRules: List<RequiredIfRule> = effectiveSpec.requiredIfRules
    val profileAttrCodes = effectiveSpec.allAttributes()
        .map { it.code.lowercase() }
        .toSet()
    val allowBrand = "brand" in profileAttrCodes
    val allowModel = "model" in profileAttrCodes

    val constraintAttrs = buildMap<String, String> {
        selectedFilters.forEach { (k, v) -> if (v.isNotBlank()) put(k, v) }
        if (!containsKey("brand")) lookupBrand?.let { put("brand", it) }
        if (!containsKey("model")) lookupModel?.let { put("model", it) }
    }
    val constraints = effectiveSpec.constraints
    val constraintsResult = constraintsResolver.evaluate(constraints, constraintAttrs)

    val liveSnapshot = runCatching {
        liveValuesRepository.getLiveValues(
            CatalogLiveValuesRequest(
                categoryCode = resolvedCategoryCode,
                brand = lookupBrand,
                model = lookupModel,
                localeTag = Locale.getDefault().toLanguageTag(),
                attributeCodes = effectiveSpec.allAttributes().map { attribute -> attribute.code },
            ),
        )
    }.getOrDefault(CatalogLiveValuesSnapshot())
    val rawDbByKey: Map<String, List<String>> = liveSnapshot.valuesByAttributeCode

    val filteredDbByKey = rawDbByKey.filterKeys { key ->
        when (key.lowercase()) {
            "brand" -> allowBrand
            "model" -> allowModel
            else -> true
        }
    }

    val brandOptions: List<String> = if (allowBrand) {
        when {
            product?.brand?.isNotBlank() == true -> listOf(product.brand)
            else -> liveSnapshot.brandOptions
        }
    } else {
        emptyList()
    }
    val modelOptions: List<String> = if (allowModel) {
        when {
            product?.model?.isNotBlank() == true -> listOf(product.model)
            else -> liveSnapshot.modelOptions
        }
    } else {
        emptyList()
    }

    val liveDbByKey: Map<String, List<String>> = buildMap {
        putAll(filteredDbByKey)
        if (brandOptions.isNotEmpty()) put("brand", brandOptions)
        if (modelOptions.isNotEmpty()) put("model", modelOptions)
    }

    val defs = effectiveSpec
        .toFeatureAttributeDefs()
        .map { def -> applyConstraints(def, constraintsResult) }
    val liveValuesByKey = buildLiveValueEnrichment(
        defs = defs,
        dbByKey = liveDbByKey,
        constraintsResult = constraintsResult,
    )

    return CategoryUiCatalog(
        categoryCode = resolvedCategoryCode,
        defs = defs,
        liveValuesByKey = liveValuesByKey,
        requiredIfRules = requiredIfRules,
        constraints = constraints,
    )
}

private fun buildLiveValueEnrichment(
    defs: List<AttributeDef>,
    dbByKey: Map<String, List<String>>,
    constraintsResult: ConstraintCheckResult,
): Map<String, List<String>> =
    defs.mapNotNull { def ->
        if (def.allowedValues.isNotEmpty()) return@mapNotNull null
        val dbKey = pickDbKey(def.key, dbByKey)
        val allowedConstraint = constraintsResult.allowedValuesByAttribute[def.key].orEmpty()
        val forbiddenConstraint = constraintsResult.forbiddenValuesByAttribute[def.key].orEmpty()
        val liveValues = filterValues(
            values = dbByKey[dbKey].orEmpty(),
            allowed = allowedConstraint,
            forbidden = forbiddenConstraint,
        )
        liveValues
            .takeIf { it.isNotEmpty() }
            ?.let { values -> def.key to values }
    }.toMap(LinkedHashMap())

private fun applyConstraints(
    def: AttributeDef,
    constraintsResult: ConstraintCheckResult,
): AttributeDef {
    val allowedConstraint = constraintsResult.allowedValuesByAttribute[def.key].orEmpty()
    val forbiddenConstraint = constraintsResult.forbiddenValuesByAttribute[def.key].orEmpty()

    val filteredAllowedValues = when {
        allowedConstraint.isNotEmpty() && def.allowedValues.isNotEmpty() ->
            def.allowedValues.filter { v -> allowedConstraint.any { it.equals(v.code, ignoreCase = true) } }
        allowedConstraint.isNotEmpty() -> allowedConstraint.map { valueCode ->
            ValueDef(code = valueCode, label = valueCode)
        }
        else -> def.allowedValues
    }

    val orderedAllowedValues = if (filteredAllowedValues.any { it.rank != 0 }) {
        filteredAllowedValues.sortedWith(
            compareByDescending<ValueDef> { it.rank }.thenBy { it.label.lowercase() }
        )
    } else {
        filteredAllowedValues
    }

    val filteredOptions = filterValues(
        values = def.options,
        allowed = allowedConstraint,
        forbidden = forbiddenConstraint,
    )

    val options = if (orderedAllowedValues.isNotEmpty()) {
        orderedAllowedValues.map(ValueDef::label)
    } else {
        filteredOptions
    }

    return def.copy(
        options = options,
        allowedValues = orderedAllowedValues,
        isDictionaryComplete = when {
            allowedConstraint.isNotEmpty() -> orderedAllowedValues.isNotEmpty()
            else -> def.isDictionaryComplete
        },
        forbiddenValues = forbiddenConstraint,
    )
}

private fun filterValues(
    values: List<String>,
    allowed: List<String>,
    forbidden: List<String>,
): List<String> {
    val filteredAllowed = if (allowed.isNotEmpty()) {
        values.filter { v -> allowed.any { it.equals(v, ignoreCase = true) } }
    } else {
        values
    }
    return filteredAllowed
        .filterNot { value -> forbidden.any { it.equals(value, ignoreCase = true) } }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

private fun pickDbKey(profileKey: String, dbByKey: Map<String, List<String>>): String {
    val canonical = canonicalKey(profileKey)
    if (dbByKey.isEmpty()) return canonical

    fun find(match: String): String? =
        dbByKey.keys.firstOrNull { it.equals(match, ignoreCase = true) }

    find(canonical)?.let { return it }
    find(profileKey)?.let { return it }
    for (alt in keyAliases(profileKey) + keyAliases(canonical)) {
        find(alt)?.let { return it }
    }
    return canonical
}

private fun canonicalKey(key: String): String = when (key.lowercase()) {
    "memory_gb" -> "memory"
    "ram_gb" -> "ram"
    else -> key
}

private fun keyAliases(key: String): List<String> = when (key.lowercase()) {
    "storage" -> listOf("memory")
    "memory" -> listOf("storage")
    "memory_gb" -> listOf("memory", "storage")
    "ram_gb" -> listOf("ram")
    "ram" -> listOf("ram_gb")
    "state" -> listOf("condition")
    "condition" -> listOf("state")
    else -> emptyList()
}

