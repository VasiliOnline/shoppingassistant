package com.example.shoppingassistant.domain.catalog

import java.util.Locale

fun resolveCatalogGovernanceScope(
    categoryCode: String,
    lookupBrand: String? = null,
    lookupModel: String? = null,
): CatalogGovernanceScope {
    val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
    val normalizedBrand = lookupBrand?.trim()?.takeIf { value -> value.isNotEmpty() }
    val normalizedModel = lookupModel?.trim()?.takeIf { value -> value.isNotEmpty() }
    val modelMatch = normalizedModel
        ?.let(CatalogCanonicalModelRegistry::matchQuery)
        ?.takeIf { match ->
            match.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true) &&
                match.matchesBrandContext(normalizedBrand)
        }
    val familyMatch = normalizedModel
        ?.takeIf { modelMatch == null }
        ?.let(CatalogCanonicalProductFamilyRegistry::matchQuery)
        ?.takeIf { match ->
            match.defaultCategoryCode.equals(normalizedCategoryCode, ignoreCase = true) &&
                match.matchesBrandContext(normalizedBrand)
        }
    val brandCode = sequenceOf(
        normalizedBrand,
        modelMatch?.brandCanonical,
        familyMatch?.brandCanonical,
    )
        .mapNotNull(CatalogGovernanceCuratedSeed::resolveBrandCode)
        .firstOrNull()

    return CatalogGovernanceScope(
        categoryCode = normalizedCategoryCode,
        brandCode = brandCode,
        familyCode = modelMatch?.familyCode ?: familyMatch?.familyCode,
        modelCode = modelMatch?.modelCode,
    )
}

fun catalogCanonicalBrandMatchesLookup(
    lookupBrand: String?,
    canonicalBrand: String,
): Boolean {
    val normalizedLookupBrand = lookupBrand?.trim()?.takeIf { value -> value.isNotEmpty() } ?: return true
    val lookupBrandCode = CatalogGovernanceCuratedSeed.resolveBrandCode(normalizedLookupBrand)
    val canonicalBrandCode = CatalogGovernanceCuratedSeed.resolveBrandCode(canonicalBrand)
    return when {
        lookupBrandCode != null && canonicalBrandCode != null -> lookupBrandCode == canonicalBrandCode
        else -> canonicalBrand.equals(normalizedLookupBrand, ignoreCase = true)
    }
}

private fun CatalogCanonicalModelMatch.matchesBrandContext(
    lookupBrand: String?,
): Boolean = catalogCanonicalBrandMatchesLookup(
    lookupBrand = lookupBrand,
    canonicalBrand = brandCanonical,
)

private fun CatalogCanonicalProductFamilyMatch.matchesBrandContext(
    lookupBrand: String?,
): Boolean = catalogCanonicalBrandMatchesLookup(
    lookupBrand = lookupBrand,
    canonicalBrand = brandCanonical,
)
