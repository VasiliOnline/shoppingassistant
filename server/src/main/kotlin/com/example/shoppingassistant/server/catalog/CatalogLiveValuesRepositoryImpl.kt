package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot
import com.example.shoppingassistant.domain.catalog.catalogCanonicalBrandMatchesLookup
import com.example.shoppingassistant.domain.catalog.resolveCatalogGovernanceScope
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.publicOfferVisibilityOp
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import java.util.Locale

class CatalogLiveValuesRepositoryImpl(
    private val governanceRepository: CatalogGovernanceRepository,
) : CatalogLiveValuesRepository {
    override suspend fun getLiveValues(
        request: CatalogLiveValuesRequest,
    ): CatalogLiveValuesSnapshot {
        val normalizedCategoryCode = request.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedBrand = request.brand?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedModel = request.model?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedLocaleTag = request.localeTag?.trim()?.takeIf { it.isNotEmpty() } ?: "en"
        val requestedAttributeCodes = request.attributeCodes
            .asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toSet()

        val rows = DatabaseFactory.dbQuery {
            val joined = OffersTable.innerJoin(ProductsTable, { productId }, { id })
            val query = joined.selectAll()
            query.andWhere { publicOfferVisibilityOp() }
            normalizedCategoryCode?.let { categoryCode ->
                query.andWhere { ProductsTable.category eq categoryCode }
            }
            normalizedBrand?.let { brand ->
                query.andWhere { ProductsTable.brand eq brand }
            }
            normalizedModel?.let { model ->
                query.andWhere { ProductsTable.model eq model }
            }
            query.toList()
        }

        val valuesByCode = LinkedHashMap<String, LinkedHashSet<String>>()
        val brandOptions = LinkedHashSet<String>()
        val modelOptions = LinkedHashSet<String>()

        rows.forEach { row ->
            row[ProductsTable.brand]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { brand ->
                    brandOptions += brand
                    addLiveValue(valuesByCode, requestedAttributeCodes, "brand", brand)
                }

            row[ProductsTable.model]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { model ->
                    modelOptions += model
                    addLiveValue(valuesByCode, requestedAttributeCodes, "model", model)
                }

            row[OffersTable.currency]
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { currency ->
                    addLiveValue(valuesByCode, requestedAttributeCodes, "currency", currency)
                }

            row[OffersTable.condition]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { condition ->
                    addLiveValue(valuesByCode, requestedAttributeCodes, "condition", condition)
                }

            row[ProductsTable.specs]
                .orEmpty()
                .forEach { (attributeCode, value) ->
                    addLiveValue(
                        valuesByCode = valuesByCode,
                        requestedAttributeCodes = requestedAttributeCodes,
                        attributeCode = attributeCode,
                        value = value.asRawString(),
                    )
                }

            row[OffersTable.attributes]
                .orEmpty()
                .forEach { (attributeCode, value) ->
                    addLiveValue(
                        valuesByCode = valuesByCode,
                        requestedAttributeCodes = requestedAttributeCodes,
                        attributeCode = attributeCode,
                        value = value.asRawString(),
                    )
                }
        }

        val knownValuesByAttributeCode = buildKnownValuesByAttributeCode(
            requestedAttributeCodes = requestedAttributeCodes,
            categoryCode = normalizedCategoryCode,
            brand = normalizedBrand,
            model = normalizedModel,
            localeTag = normalizedLocaleTag,
        )
        val knownValueAliasesByAttributeCode = buildKnownValueAliasesByAttributeCode(
            knownValuesByAttributeCode = knownValuesByAttributeCode,
            requestedAttributeCodes = requestedAttributeCodes,
            categoryCode = normalizedCategoryCode,
            brand = normalizedBrand,
            model = normalizedModel,
            localeTag = normalizedLocaleTag,
        )
        normalizedCategoryCode?.let { categoryCode ->
            brandOptions += resolveKnownBrands(
                categoryCode = categoryCode,
                localeTag = normalizedLocaleTag,
            )
        }
        modelOptions += knownValuesByAttributeCode["model"].orEmpty()

        return CatalogLiveValuesSnapshot(
            valuesByAttributeCode = valuesByCode.mapValues { (_, values) -> values.toList() },
            knownValuesByAttributeCode = knownValuesByAttributeCode,
            knownValueAliasesByAttributeCode = knownValueAliasesByAttributeCode,
            brandOptions = brandOptions.toList(),
            modelOptions = modelOptions.toList(),
        )
    }

    private fun addLiveValue(
        valuesByCode: LinkedHashMap<String, LinkedHashSet<String>>,
        requestedAttributeCodes: Set<String>,
        attributeCode: String,
        value: String,
    ) {
        val normalizedAttributeCode = attributeCode.trim().lowercase(Locale.ROOT)
        val normalizedValue = value.trim()
        if (normalizedAttributeCode.isEmpty() || normalizedValue.isEmpty()) return
        if (requestedAttributeCodes.isNotEmpty() && normalizedAttributeCode !in requestedAttributeCodes) return
        val bucket = valuesByCode.getOrPut(normalizedAttributeCode) { LinkedHashSet() }
        if (bucket.none { existing -> existing.equals(normalizedValue, ignoreCase = true) }) {
            bucket += normalizedValue
        }
    }

    private suspend fun buildKnownValuesByAttributeCode(
        requestedAttributeCodes: Set<String>,
        categoryCode: String?,
        brand: String?,
        model: String?,
        localeTag: String,
    ): Map<String, List<String>> {
        val normalizedCategoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
        val scope = resolveRuntimeGovernanceScope(
            categoryCode = normalizedCategoryCode,
            lookupBrand = brand,
            lookupModel = model,
        )
        val result = LinkedHashMap<String, List<String>>()
        requestedAttributeCodes.forEach { attributeCode ->
            val values = when (attributeCode) {
                "model" -> resolveKnownModels(
                    categoryCode = normalizedCategoryCode,
                    brand = brand,
                    localeTag = localeTag,
                )

                else -> resolveKnownCanonicalValues(
                    attributeCode = attributeCode,
                    scope = scope,
                    localeTag = localeTag,
                )
            }
            if (values.isNotEmpty()) {
                result[attributeCode] = values
            }
        }
        return result
    }

    private suspend fun buildKnownValueAliasesByAttributeCode(
        knownValuesByAttributeCode: Map<String, List<String>>,
        requestedAttributeCodes: Set<String>,
        categoryCode: String?,
        brand: String?,
        model: String?,
        localeTag: String,
    ): Map<String, Map<String, List<String>>> {
        val normalizedCategoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyMap()
        val scope = resolveRuntimeGovernanceScope(
            categoryCode = normalizedCategoryCode,
            lookupBrand = brand,
            lookupModel = model,
        )
        val result = LinkedHashMap<String, Map<String, List<String>>>()
        requestedAttributeCodes.forEach { attributeCode ->
            val aliases = when (attributeCode) {
                "model" -> resolveKnownModelAliases(
                    categoryCode = normalizedCategoryCode,
                    brand = brand,
                    localeTag = localeTag,
                    knownValues = knownValuesByAttributeCode[attributeCode].orEmpty(),
                )

                else -> resolveKnownCanonicalAliases(
                    attributeCode = attributeCode,
                    scope = scope,
                    localeTag = localeTag,
                    knownValues = knownValuesByAttributeCode[attributeCode].orEmpty(),
                )
            }
            if (aliases.isNotEmpty()) {
                result[attributeCode] = aliases
            }
        }
        return result
    }

    private suspend fun resolveKnownModels(
        categoryCode: String,
        brand: String?,
        localeTag: String,
    ): List<String> {
        return listScopedModels(
            categoryCode = categoryCode,
            brand = brand,
            localeTag = localeTag,
        )
            .map { (entry, _) -> entry.displayLabel(localeTag = localeTag).trim() }
            .filter { entry ->
                entry.isNotEmpty()
            }
            .filter { entry -> entry.isNotEmpty() }
            .distinctBy { entry -> entry.lowercase(Locale.ROOT) }
            .sortedBy { entry -> entry.lowercase(Locale.ROOT) }
            .toList()
    }

    private suspend fun resolveKnownModelAliases(
        categoryCode: String,
        brand: String?,
        localeTag: String,
        knownValues: List<String>,
    ): Map<String, List<String>> {
        if (knownValues.isEmpty()) return emptyMap()
        val aliasesByTargetCode = governanceRepository.listAliases(
            locale = null,
            targetKind = CatalogGovernanceAliasTargetKind.MODEL,
        )
            .filter { entry ->
                entry.status == CatalogGovernanceAliasStatus.ACTIVE
            }
            .groupBy { entry -> entry.targetCode }
        val aliasesByModel = listScopedModels(
            categoryCode = categoryCode,
            brand = brand,
            localeTag = localeTag,
        )
            .asSequence()
            .map { (entry, _) ->
                val displayValue = entry.displayLabel(localeTag = localeTag)
                displayValue to (
                    sequenceOf(
                        aliasesByTargetCode[entry.code].orEmpty().map { alias -> alias.aliasText },
                        entry.labels.values,
                        listOf(displayValue),
                    )
                        .flatten()
                        .map { alias -> alias.trim() }
                        .filter { alias -> alias.isNotEmpty() }
                        .distinct()
                        .toList()
                    )
            }
            .associate { it }
        return knownValues.associateWith { displayValue ->
            aliasesByModel[displayValue].orEmpty().ifEmpty { listOf(displayValue) }
        }
    }

    private suspend fun resolveKnownBrands(
        categoryCode: String,
        localeTag: String,
    ): List<String> {
        val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
        val brands = governanceRepository.listBrands()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
        if (brands.isEmpty()) return emptyList()
        val families = governanceRepository.listProductFamilies()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
        val models = governanceRepository.listModels()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
        val brandCodesInCategory = LinkedHashSet<String>()
        brands.forEach { brand ->
            if (brand.primaryCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)) {
                brandCodesInCategory += brand.code
            }
        }
        families.forEach { family ->
            if (family.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)) {
                brandCodesInCategory += family.brandCode
            }
        }
        models.forEach { model ->
            if (model.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)) {
                brandCodesInCategory += model.brandCode
            }
        }
        return brands
            .asSequence()
            .filter { brand -> brand.code in brandCodesInCategory }
            .map { brand -> brand.displayLabel(localeTag = localeTag).trim() }
            .filter { brand -> brand.isNotEmpty() }
            .distinctBy { brand -> brand.lowercase(Locale.ROOT) }
            .sortedBy { brand -> brand.lowercase(Locale.ROOT) }
            .toList()
    }

    private suspend fun listScopedModels(
        categoryCode: String,
        brand: String?,
        localeTag: String,
    ): List<Pair<com.example.shoppingassistant.domain.catalog.CatalogModelCanon, com.example.shoppingassistant.domain.catalog.CatalogBrandCanon>> {
        val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
        val brandsByCode = governanceRepository.listBrands()
            .asSequence()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
            .associateBy { entry -> entry.code }
        val familiesByCode = governanceRepository.listProductFamilies()
            .asSequence()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
            .associateBy { entry -> entry.code }
        return governanceRepository.listModels()
            .asSequence()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
            .mapNotNull { entry ->
                val brandCanon = brandsByCode[entry.brandCode] ?: return@mapNotNull null
                val familyCanon = entry.familyCode?.let(familiesByCode::get)
                val effectiveCategoryCode = entry.defaultCategoryCode
                    ?: familyCanon?.defaultCategoryCode
                    ?: brandCanon.primaryCategoryCode
                    ?: "TECH.PHONES"
                if (!effectiveCategoryCode.equals(normalizedCategoryCode, ignoreCase = true)) {
                    return@mapNotNull null
                }
                if (!catalogCanonicalBrandMatchesLookup(
                        lookupBrand = brand,
                        canonicalBrand = brandCanon.displayLabel(localeTag = localeTag),
                    )
                ) {
                    return@mapNotNull null
                }
                entry to brandCanon
            }
            .toList()
    }

    private suspend fun resolveKnownCanonicalValues(
        attributeCode: String,
        scope: CatalogGovernanceScope,
        localeTag: String,
    ): List<String> {
        val selectedValues = governanceRepository.listCanonicalValuesAnyScope(attributeCode)
            .filter { value -> value.status == CatalogGovernanceEntityStatus.ACTIVE }
            .filter { value -> value.scope.appliesTo(scope) }
            .selectMostSpecific()
        return selectedValues
            .map { value ->
                value.labels.resolve(locale = localeTag, fallback = value.canonicalValue)
                    ?: value.canonicalValue
            }
            .normalizeKnownValues(attributeCode)
    }

    private suspend fun resolveKnownCanonicalAliases(
        attributeCode: String,
        scope: CatalogGovernanceScope,
        localeTag: String,
        knownValues: List<String>,
    ): Map<String, List<String>> {
        if (knownValues.isEmpty()) return emptyMap()
        val selectedValues = governanceRepository.listCanonicalValuesAnyScope(attributeCode)
            .filter { value -> value.status == CatalogGovernanceEntityStatus.ACTIVE }
            .filter { value -> value.scope.appliesTo(scope) }
            .selectMostSpecific()
        if (selectedValues.isEmpty()) return emptyMap()
        val aliasesByCanonicalCode = governanceRepository.listAliases(
            locale = null,
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            attributeCode = attributeCode,
        )
            .filter { alias -> alias.status == CatalogGovernanceAliasStatus.ACTIVE }
            .filter { alias -> alias.scope.appliesTo(scope) }
            .groupBy { alias -> alias.targetCode }

        val selectedByDisplay = selectedValues.associateBy { value ->
            value.labels.resolve(locale = localeTag, fallback = value.canonicalValue)
                ?: value.canonicalValue
        }

        return knownValues.associateWith { displayValue ->
            val canonical = selectedByDisplay[displayValue]
            val aliases = buildList {
                add(displayValue)
                canonical?.labels?.values?.let(::addAll)
                aliasesByCanonicalCode[canonical?.canonicalCode].orEmpty()
                    .mapTo(this) { alias -> alias.aliasText }
            }
            aliases
                .asSequence()
                .map { alias -> alias.trim() }
                .filter { alias -> alias.isNotEmpty() }
                .distinct()
                .toList()
        }
    }

    private suspend fun resolveRuntimeGovernanceScope(
        categoryCode: String,
        lookupBrand: String?,
        lookupModel: String?,
    ): CatalogGovernanceScope {
        val baseScope = resolveCatalogGovernanceScope(
            categoryCode = categoryCode,
            lookupBrand = lookupBrand,
            lookupModel = lookupModel,
        )
        val brandsByCode = governanceRepository.listBrands()
            .asSequence()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
            .associateBy { entry -> entry.code }
        val familiesByCode = governanceRepository.listProductFamilies()
            .asSequence()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
            .associateBy { entry -> entry.code }
        val modelsByCode = governanceRepository.listModels()
            .asSequence()
            .filter { entry -> entry.status == CatalogGovernanceEntityStatus.ACTIVE }
            .associateBy { entry -> entry.code }

        val modelCanon = baseScope.modelCode?.let(modelsByCode::get)
        val familyCanon = sequenceOf(
            modelCanon?.familyCode,
            baseScope.familyCode,
        )
            .mapNotNull(familiesByCode::get)
            .firstOrNull()
        val brandCanon = when {
            modelCanon != null -> brandsByCode[modelCanon.brandCode]
            familyCanon != null -> brandsByCode[familyCanon.brandCode]
            else -> brandsByCode.values.firstOrNull { candidate -> candidate.matchesLookup(lookupBrand) }
        }

        return CatalogGovernanceScope(
            categoryCode = categoryCode.trim().uppercase(Locale.ROOT),
            brandCode = brandCanon?.code ?: baseScope.brandCode,
            familyCode = familyCanon?.code ?: baseScope.familyCode,
            modelCode = modelCanon?.code ?: baseScope.modelCode,
        )
    }

    private fun List<String>.normalizeKnownValues(
        attributeCode: String,
    ): List<String> {
        val normalizedAttributeCode = attributeCode.trim().lowercase(Locale.ROOT)
        val deduped = asSequence()
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .distinctBy { value -> value.lowercase(Locale.ROOT) }
            .toList()
        return if (normalizedAttributeCode in numericOrderedAttributeCodes) {
            deduped.sortedWith(
                compareBy<String> { value ->
                    extractNumericSortKey(value) ?: Double.POSITIVE_INFINITY
                }.thenBy { value ->
                    value.lowercase(Locale.ROOT)
                },
            )
        } else {
            deduped.sortedBy { value -> value.lowercase(Locale.ROOT) }
        }
    }

    private fun CatalogGovernanceScope.appliesTo(
        requestedScope: CatalogGovernanceScope,
    ): Boolean =
        scopeTokenMatches(categoryCode, requestedScope.categoryCode) &&
            scopeTokenMatches(brandCode, requestedScope.brandCode) &&
            scopeTokenMatches(familyCode, requestedScope.familyCode) &&
            scopeTokenMatches(modelCode, requestedScope.modelCode)

    private fun scopeTokenMatches(
        value: String?,
        requestedValue: String?,
    ): Boolean =
        value == null || (requestedValue != null && value.equals(requestedValue, ignoreCase = true))

    private fun List<com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon>.selectMostSpecific():
        List<com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon> {
        val topSpecificity = maxOfOrNull { value -> value.scope.specificity() } ?: return emptyList()
        return filter { value -> value.scope.specificity() == topSpecificity }
            .distinctBy { value -> value.canonicalCode }
    }

    private fun CatalogGovernanceScope.specificity(): Int =
        sequenceOf(categoryCode, brandCode, familyCode, modelCode)
            .count { value -> !value.isNullOrBlank() }

    private fun extractNumericSortKey(text: String): Double? =
        numericTokenRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()

    private companion object {
        private val numericOrderedAttributeCodes = setOf(
            "memory_gb",
            "ram_gb",
            "refresh_rate_hz",
            "release_year",
            "screen_size_inch",
            "battery_mah",
            "wired_charging_w",
        )
        private val numericTokenRegex = Regex("(\\d+(?:[.,]\\d+)?)")
    }
}

private fun com.example.shoppingassistant.domain.catalog.CatalogBrandCanon.displayLabel(
    localeTag: String,
): String = labels.resolve(locale = localeTag, fallback = labels.resolve(locale = "en", fallback = code) ?: code) ?: code

private fun com.example.shoppingassistant.domain.catalog.CatalogModelCanon.displayLabel(
    localeTag: String,
): String = labels.resolve(locale = localeTag, fallback = labels.resolve(locale = "en", fallback = code) ?: code) ?: code

private fun com.example.shoppingassistant.domain.catalog.CatalogBrandCanon.matchesLookup(
    lookupBrand: String?,
): Boolean {
    val normalizedLookup = lookupBrand?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return code.equals(normalizedLookup, ignoreCase = true) ||
        labels.values.any { label -> label.equals(normalizedLookup, ignoreCase = true) }
}
