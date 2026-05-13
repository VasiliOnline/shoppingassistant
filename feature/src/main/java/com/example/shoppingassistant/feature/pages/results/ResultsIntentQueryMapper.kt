package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.search.InterpretedSearchIntent
import com.example.shoppingassistant.domain.search.SearchIntentMode
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import java.util.Locale

internal fun InterpretedSearchIntent.toResultsQuery(): NormalizedQuery? {
    if (mode == SearchIntentMode.CATEGORY_ONLY) return null

    val normalizedAttrs = Normalization.normalizeAttrs(attrs)
    val initialHeuristicQuery = resolveResultsHeuristicQuery(
        queryText = queryText,
        normalizedAttrs = normalizedAttrs,
    )
    val effectiveAttrs = enrichResultsScopedAttributes(
        queryText = queryText,
        normalizedAttrs = normalizedAttrs,
        heuristicQuery = initialHeuristicQuery,
    )
    val heuristicQuery = resolveResultsHeuristicQuery(
        queryText = queryText,
        normalizedAttrs = effectiveAttrs,
    )
    val brand = effectiveAttrs["brand"].orEmpty().trim()
    val model = effectiveAttrs["model"].orEmpty().trim()
    val nonIdentityAttrs = effectiveAttrs.filterKeys { key ->
        key != "brand" && key != "model"
    }
    val fallbackModel = queryText
        .trim()
        .takeIf { value ->
            value.isNotBlank() &&
                brand.isBlank() &&
                model.isBlank() &&
                nonIdentityAttrs.isEmpty()
        }
        .orEmpty()

    val effectiveBrand = brand.ifBlank { heuristicQuery?.brand.orEmpty().trim() }
    val effectiveModel = model.ifBlank {
        heuristicQuery?.model.orEmpty().trim().ifBlank { fallbackModel }
    }
    if (effectiveBrand.isBlank() && effectiveModel.isBlank() && normalizedAttrs.isEmpty()) return null

    return NormalizedQuery(
        brand = effectiveBrand,
        model = effectiveModel,
        attributes = Normalization.normalizeTypedAttrs(effectiveAttrs),
    )
}

private val resultsMapperIdentityKeys = setOf("brand", "model", "model_line", "product_name")

private fun resolveResultsHeuristicQuery(
    queryText: String,
    normalizedAttrs: Map<String, String>,
): NormalizedQuery? {
    val directMatch = BrandModelRules.fromKnownFamily(
        raw = queryText,
        attrs = normalizedAttrs,
    )
    val hasNonIdentityAttrs = normalizedAttrs.keys.any { key -> key !in resultsMapperIdentityKeys }
    if (!hasNonIdentityAttrs) return canonicalizeResultsHeuristicModel(directMatch)
    val prunedQueryText = stripResultsParsedAttributePhrasesFromQuery(
        queryText = queryText,
        normalizedAttrs = normalizedAttrs,
    )
    if (prunedQueryText.equals(queryText.trim(), ignoreCase = true)) {
        return canonicalizeResultsHeuristicModel(directMatch)
    }
    val resolved = BrandModelRules.fromKnownFamily(
        raw = prunedQueryText,
        attrs = normalizedAttrs,
    ) ?: directMatch
    return canonicalizeResultsHeuristicModel(resolved)
}

private fun stripResultsParsedAttributePhrasesFromQuery(
    queryText: String,
    normalizedAttrs: Map<String, String>,
): String {
    var sanitized = queryText.trim().replace("\\s+".toRegex(), " ")
    if (sanitized.isBlank()) return sanitized
    buildResultsHeuristicRemovalPhrases(normalizedAttrs).forEach { phrase ->
        sanitized = removeLeadingResultsHeuristicPhrase(sanitized, phrase)
        sanitized = removeTrailingResultsHeuristicPhrase(sanitized, phrase)
        sanitized = sanitized.replace(Regex("(?iu)[\\s,;/\\\\-]+${Regex.escape(phrase)}(?=[\\s,;/\\\\-]|$)"), " ")
    }
    return sanitized.trim().replace("\\s+".toRegex(), " ")
}

private fun buildResultsHeuristicRemovalPhrases(
    normalizedAttrs: Map<String, String>,
): List<String> {
    val phrases = LinkedHashSet<String>()
    normalizedAttrs.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim().lowercase(Locale.ROOT)
        val value = rawValue.trim()
        if (value.isEmpty() || key in resultsMapperIdentityKeys) return@forEach
        phrases += value
        phrases += value.lowercase(Locale.ROOT).replace('_', ' ')
        phrases += value.lowercase(Locale.ROOT).replace("_", "")

        val digitsOnly = value.filter(Char::isDigit)
        when (key) {
            "memory_gb",
            "ram_gb",
            -> {
                if (digitsOnly.isNotBlank()) {
                    phrases += digitsOnly
                    phrases += "$digitsOnly gb"
                    phrases += "${digitsOnly}gb"
                    phrases += "$digitsOnly гб"
                    phrases += "${digitsOnly}гб"
                    if (digitsOnly == "1024") {
                        phrases += "1 tb"
                        phrases += "1tb"
                        phrases += "1 тб"
                        phrases += "1тб"
                    }
                }
            }

            "refresh_rate_hz" -> {
                if (digitsOnly.isNotBlank()) {
                    phrases += digitsOnly
                    phrases += "$digitsOnly hz"
                    phrases += "${digitsOnly}hz"
                    phrases += "$digitsOnly гц"
                    phrases += "${digitsOnly}гц"
                }
            }
        }
    }

    return phrases
        .asSequence()
        .map { phrase -> phrase.trim() }
        .filter { phrase -> phrase.length >= 2 }
        .distinctBy { phrase -> phrase.lowercase(Locale.ROOT) }
        .sortedByDescending { phrase -> phrase.length }
        .toList()
}

private fun canonicalizeResultsHeuristicModel(
    query: NormalizedQuery?,
): NormalizedQuery? {
    query ?: return null
    val normalizedModel = query.model.trim()
    if (normalizedModel.isEmpty()) return query
    val canonicalModel = CatalogCanonicalModelRegistry.matchQuery(normalizedModel)
        ?.takeIf { match ->
            query.brand.isBlank() || match.brandCanonical.equals(query.brand, ignoreCase = true)
        }
        ?.modelText
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: return query
    if (canonicalModel == normalizedModel) return query
    return NormalizedQuery(
        brand = query.brand,
        model = canonicalModel,
        attributes = query.attributes,
    )
}

private fun enrichResultsScopedAttributes(
    queryText: String,
    normalizedAttrs: Map<String, String>,
    heuristicQuery: NormalizedQuery?,
): Map<String, String> {
    if (heuristicQuery == null || normalizedAttrs.containsKey("color")) return normalizedAttrs
    val scopedModelMatch = resolveResultsScopedModelMatch(
        queryText = queryText,
        normalizedAttrs = normalizedAttrs,
        heuristicQuery = heuristicQuery,
    )
        ?: return normalizedAttrs
    val scopedColor = resolveResultsScopedCanonicalValue(
        queryText = queryText,
        attributeCode = "color",
        scope = CatalogGovernanceScope(
            categoryCode = scopedModelMatch.defaultCategoryCode,
            brandCode = CatalogGovernanceCuratedSeed.resolveBrandCode(scopedModelMatch.brandCanonical),
            familyCode = scopedModelMatch.familyCode,
            modelCode = scopedModelMatch.modelCode,
        ),
    ) ?: return normalizedAttrs
    return LinkedHashMap(normalizedAttrs).apply {
        put("color", scopedColor)
    }
}

private fun resolveResultsScopedModelMatch(
    queryText: String,
    normalizedAttrs: Map<String, String>,
    heuristicQuery: NormalizedQuery,
): com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelMatch? {
    val brandStrippedQuery = heuristicQuery.brand
        .trim()
        .takeIf { value -> value.isNotEmpty() }
        ?.let { brand -> removeLeadingResultsHeuristicPhrase(queryText, brand) }
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?: queryText.trim()
    val prunedBrandQuery = stripResultsParsedAttributePhrasesFromQuery(
        queryText = brandStrippedQuery,
        normalizedAttrs = normalizedAttrs,
    )
    return sequenceOf(
        prunedBrandQuery.takeIf { value -> value.isNotEmpty() },
        humanizeResultsRegistryCandidate(prunedBrandQuery).takeIf { value ->
            value.isNotEmpty() && !value.equals(prunedBrandQuery, ignoreCase = true)
        },
        brandStrippedQuery.takeIf { value -> value.isNotEmpty() },
        humanizeResultsRegistryCandidate(brandStrippedQuery).takeIf { value ->
            value.isNotEmpty() && !value.equals(brandStrippedQuery, ignoreCase = true)
        },
        heuristicQuery.model.trim().takeIf { value -> value.isNotEmpty() },
    )
        .filterNotNull()
        .mapNotNull { candidate -> CatalogCanonicalModelRegistry.matchQuery(candidate) }
        .firstOrNull { match ->
            heuristicQuery.brand.isBlank() || match.brandCanonical.equals(heuristicQuery.brand, ignoreCase = true)
        }
}

private fun humanizeResultsRegistryCandidate(raw: String): String =
    raw
        .replace("(?<=\\p{L})(?=\\d)".toRegex(), " ")
        .replace("(?<=\\d)(?=\\p{L})".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

private fun resolveResultsScopedCanonicalValue(
    queryText: String,
    attributeCode: String,
    scope: CatalogGovernanceScope,
): String? {
    val normalizedQuery = normalizeResultsScopedQueryText(queryText)
    if (normalizedQuery.isBlank()) return null
    val compactQuery = SearchTextNormalizer.normalizeToken(queryText)
    val matches = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
        attributeCode = attributeCode,
        scope = scope,
    ).mapNotNull { candidate ->
        val matchedPhrase = sequenceOf(candidate.displayValue)
            .plus(candidate.aliases.asSequence())
            .mapNotNull { phrase ->
                phrase.trim().takeIf { value -> value.isNotEmpty() }
            }
            .distinctBy { phrase -> phrase.lowercase(Locale.ROOT) }
            .filter { phrase ->
                val normalizedPhrase = normalizeResultsScopedQueryText(phrase)
                if (normalizedPhrase.isBlank()) return@filter false
                val compactPhrase = SearchTextNormalizer.normalizeToken(phrase)
                containsResultsScopedQueryPhrase(normalizedQuery, normalizedPhrase) ||
                    (compactPhrase.length >= 2 && compactQuery.contains(compactPhrase))
            }
            .maxByOrNull { phrase -> phrase.length }
            ?: return@mapNotNull null
        ResultsScopedAttributeMatch(
            value = candidate,
            matchedPhrase = matchedPhrase,
        )
    }
    return matches
        .sortedWith(
            compareByDescending<ResultsScopedAttributeMatch> { match ->
                match.value.scope.resultsScopedSpecificityLevel()
            }.thenByDescending { match ->
                match.matchedPhrase.length
            },
        )
        .map { match -> normalizeResultsScopedAttributeValue(match.matchedPhrase) }
        .distinctBy { phrase -> phrase.lowercase(Locale.ROOT) }
        .singleOrNull()
}

private fun normalizeResultsScopedQueryText(raw: String): String =
    SearchTextNormalizer.normalize(raw)
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')

private fun containsResultsScopedQueryPhrase(
    normalizedQuery: String,
    normalizedPhrase: String,
): Boolean {
    if (normalizedQuery == normalizedPhrase) return true
    return normalizedQuery.startsWith("$normalizedPhrase ") ||
        normalizedQuery.endsWith(" $normalizedPhrase") ||
        normalizedQuery.contains(" $normalizedPhrase ")
}

private fun CatalogGovernanceScope.resultsScopedSpecificityLevel(): Int = when {
    !modelCode.isNullOrBlank() -> 4
    !familyCode.isNullOrBlank() -> 3
    !brandCode.isNullOrBlank() -> 2
    !categoryCode.isNullOrBlank() -> 1
    else -> 0
}

private data class ResultsScopedAttributeMatch(
    val value: com.example.shoppingassistant.domain.catalog.CatalogGovernanceScopedCanonicalValue,
    val matchedPhrase: String,
)

private fun normalizeResultsScopedAttributeValue(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return if (SearchTextNormalizer.tokens(trimmed).size == 1) {
        trimmed.uppercase(Locale.ROOT)
    } else {
        trimmed
    }
}

private fun removeLeadingResultsHeuristicPhrase(
    source: String,
    phrase: String,
): String {
    val trimmedPhrase = phrase.trim()
    if (trimmedPhrase.isEmpty()) return source
    val regex = Regex("^\\s*(?iu:${Regex.escape(trimmedPhrase)})[\\s,;/\\\\-]*")
    return regex.replace(source, "").trim()
}

private fun removeTrailingResultsHeuristicPhrase(
    source: String,
    phrase: String,
): String {
    val trimmedPhrase = phrase.trim()
    if (trimmedPhrase.isEmpty()) return source
    val regex = Regex("[\\s,;/\\\\-]*(?iu:${Regex.escape(trimmedPhrase)})\\s*$")
    return regex.replace(source, "").trim()
}
