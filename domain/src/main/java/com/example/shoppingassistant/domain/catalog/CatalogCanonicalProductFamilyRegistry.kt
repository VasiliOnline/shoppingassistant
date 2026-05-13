package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

@Serializable
data class CatalogCanonicalProductFamilyRegistryDocument(
    val schemaVersion: String,
    val families: List<CatalogCanonicalProductFamilyEntry> = emptyList(),
)

@Serializable
data class CatalogCanonicalProductFamilyEntry(
    val familyCode: String,
    val defaultCategoryCode: String,
    val brandCanonical: String,
    val brandAliases: List<String> = emptyList(),
    val familyCanonical: String,
    val familyAliases: List<String> = emptyList(),
    val prettyModelPrefix: String,
    val variantTokens: List<String> = emptyList(),
    val accessoryBlockers: List<String> = emptyList(),
)

data class CatalogCanonicalProductFamilyMatch(
    val familyCode: String,
    val defaultCategoryCode: String,
    val brandCanonical: String,
    val prettyModelPrefix: String,
    val normalizedQuery: String,
    val modelText: String,
    val matchedAliasLength: Int,
    val confidence: Double,
)

interface CatalogCanonicalProductFamilyRegistryProvider {
    fun families(): List<CatalogCanonicalProductFamilyEntry>
}

object SeedCatalogCanonicalProductFamilyRegistryProvider : CatalogCanonicalProductFamilyRegistryProvider {
    private val resourcePath: String
        get() = "${CatalogContractPaths.stage22RegistryBase}/product_families.json"

    private val familiesCache: List<CatalogCanonicalProductFamilyEntry> by lazy {
        val document = CatalogSeedResourceReader.readJson(
            resourcePath = resourcePath,
            deserializer = CatalogCanonicalProductFamilyRegistryDocument.serializer(),
        )
        mergeFamilies(
            baseFamilies = document.families,
            projectedFamilies = CatalogGovernanceCuratedSeed.projectedProductFamilies(),
        )
    }

    override fun families(): List<CatalogCanonicalProductFamilyEntry> = familiesCache
}

class StaticCatalogCanonicalProductFamilyRegistryProvider(
    families: List<CatalogCanonicalProductFamilyEntry>,
) : CatalogCanonicalProductFamilyRegistryProvider {
    private val normalizedFamilies = normalizeFamilies(families)

    override fun families(): List<CatalogCanonicalProductFamilyEntry> = normalizedFamilies
}

object CatalogCanonicalProductFamilyRegistry {
    private val providerRef = AtomicReference<CatalogCanonicalProductFamilyRegistryProvider>(
        SeedCatalogCanonicalProductFamilyRegistryProvider,
    )

    fun currentProvider(): CatalogCanonicalProductFamilyRegistryProvider = providerRef.get()

    fun installProvider(provider: CatalogCanonicalProductFamilyRegistryProvider) {
        providerRef.set(provider)
    }

    fun resetProvider() {
        providerRef.set(SeedCatalogCanonicalProductFamilyRegistryProvider)
    }

    fun families(): List<CatalogCanonicalProductFamilyEntry> = currentProvider().families()

    fun matchQuery(queryText: String): CatalogCanonicalProductFamilyMatch? {
        val normalizedQuery = Stage21QueryTextNormalizer.normalize(queryText)
        if (normalizedQuery.isBlank()) return null
        val compactQuery = SearchTextNormalizer.normalizeToken(queryText)

        return currentProvider()
            .families()
            .asSequence()
            .mapNotNull { family ->
                matchFamily(
                    family = family,
                    normalizedQuery = normalizedQuery,
                    compactQuery = compactQuery,
                )
            }
            .sortedWith(
                compareByDescending<CatalogCanonicalProductFamilyMatch> { it.confidence }
                    .thenByDescending { it.matchedAliasLength }
                    .thenByDescending { it.modelText.length }
                    .thenBy { it.familyCode },
            )
            .firstOrNull()
    }

    private fun matchFamily(
        family: CatalogCanonicalProductFamilyEntry,
        normalizedQuery: String,
        compactQuery: String,
    ): CatalogCanonicalProductFamilyMatch? {
        val prefixMatch = resolvePrefixMatch(
            family = family,
            normalizedQuery = normalizedQuery,
            compactQuery = compactQuery,
        ) ?: return null

        if (containsAccessoryBlocker(normalizedQuery, compactQuery, family.accessoryBlockers, prefixMatch.remainder)) {
            return null
        }

        val remainder = prefixMatch.remainder
        if (remainder.isNotBlank() && !hasProductCue(remainder, family.variantTokens)) {
            return null
        }

        val baseConfidence = when {
            remainder.isBlank() -> 0.90
            hasStrongVariantCue(remainder, family.variantTokens) -> 0.97
            else -> 0.92
        }
        val confidence = (
            baseConfidence +
                if (prefixMatch.usedBrandPrefix) 0.01 else 0.0 +
                if (prefixMatch.compact) 0.005 else 0.0
            ).coerceAtMost(0.99)
        val modelText = if (remainder.isBlank()) {
            family.prettyModelPrefix
        } else {
            "${family.prettyModelPrefix} $remainder"
        }
        return CatalogCanonicalProductFamilyMatch(
            familyCode = family.familyCode,
            defaultCategoryCode = family.defaultCategoryCode,
            brandCanonical = family.brandCanonical,
            prettyModelPrefix = family.prettyModelPrefix,
            normalizedQuery = normalizedQuery,
            modelText = modelText,
            matchedAliasLength = prefixMatch.aliasLength,
            confidence = confidence,
        )
    }

    private fun resolvePrefixMatch(
        family: CatalogCanonicalProductFamilyEntry,
        normalizedQuery: String,
        compactQuery: String,
    ): PrefixMatch? {
        val familyAliases = family.familyAliases.sortedByDescending { alias -> alias.length }
        val brandAliases = family.brandAliases.sortedByDescending { alias -> alias.length }

        familyAliases.forEach { familyAlias ->
            if (matchesPrefix(normalizedQuery, familyAlias)) {
                return PrefixMatch(
                    remainder = normalizedQuery.removePrefix(familyAlias).trim(),
                    usedBrandPrefix = false,
                    aliasLength = familyAlias.length,
                    compact = false,
                )
            }
        }

        brandAliases.forEach { brandAlias ->
            familyAliases.forEach { familyAlias ->
                val combined = "$brandAlias $familyAlias"
                if (matchesPrefix(normalizedQuery, combined)) {
                    return PrefixMatch(
                        remainder = normalizedQuery.removePrefix(combined).trim(),
                        usedBrandPrefix = true,
                        aliasLength = combined.length,
                        compact = false,
                    )
                }
            }
        }

        if (compactQuery.isBlank()) return null

        val compactFamilyAliases = familyAliases
            .map { alias -> alias to SearchTextNormalizer.normalizeToken(alias) }
            .filter { (_, compactAlias) -> compactAlias.isNotBlank() }
            .sortedByDescending { (_, compactAlias) -> compactAlias.length }
        compactFamilyAliases.firstOrNull { (_, compactAlias) ->
            matchesCompactRegistryPrefix(
                compactQuery = compactQuery,
                compactAlias = compactAlias,
            )
        }?.let { (_, compactAlias) ->
            return PrefixMatch(
                remainder = humanizeCompactRegistryRemainder(compactQuery.removePrefix(compactAlias).trim()),
                usedBrandPrefix = false,
                aliasLength = compactAlias.length,
                compact = true,
            )
        }

        val compactBrandAliases = brandAliases
            .map { alias -> alias to SearchTextNormalizer.normalizeToken(alias) }
            .filter { (_, compactAlias) -> compactAlias.isNotBlank() }
            .sortedByDescending { (_, compactAlias) -> compactAlias.length }
        compactBrandAliases.forEach { (_, compactBrandAlias) ->
            compactFamilyAliases.forEach { (_, compactFamilyAlias) ->
                val combinedCompact = compactBrandAlias + compactFamilyAlias
                if (matchesCompactRegistryPrefix(compactQuery = compactQuery, compactAlias = combinedCompact)) {
                    return PrefixMatch(
                        remainder = humanizeCompactRegistryRemainder(
                            compactQuery.removePrefix(combinedCompact).trim(),
                        ),
                        usedBrandPrefix = true,
                        aliasLength = combinedCompact.length,
                        compact = true,
                    )
                }
            }
        }

        return null
    }

    private fun containsAccessoryBlocker(
        normalizedQuery: String,
        compactQuery: String,
        blockers: List<String>,
        remainder: String,
    ): Boolean {
        if (blockers.isEmpty()) return false
        val tokens = Stage21QueryTextNormalizer.tokenize(normalizedQuery).toSet()
        val compactRemainder = SearchTextNormalizer.normalizeToken(remainder)
        return blockers.any { blocker ->
            val normalizedBlocker = Stage21QueryTextNormalizer.normalize(blocker)
            val compactBlocker = SearchTextNormalizer.normalizeToken(blocker)
            normalizedBlocker.isNotBlank() && (
                normalizedBlocker in tokens ||
                    containsPhrase(normalizedQuery, normalizedBlocker) ||
                    remainder.startsWith(normalizedBlocker) ||
                    (compactBlocker.isNotBlank() && (
                        compactQuery.endsWith(compactBlocker) ||
                            compactRemainder.startsWith(compactBlocker)
                        ))
                )
        }
    }

    private fun hasProductCue(
        remainder: String,
        variantTokens: List<String>,
    ): Boolean = remainder.isBlank() || hasStrongVariantCue(remainder, variantTokens)

    private fun hasStrongVariantCue(
        remainder: String,
        variantTokens: List<String>,
    ): Boolean {
        val tokens = Stage21QueryTextNormalizer.tokenize(remainder)
        if (tokens.isEmpty()) return false
        if (tokens.any { token -> token.any(Char::isDigit) }) return true
        if (tokens.any { token -> token in variantTokens }) return true
        if (storageRegex.containsMatchIn(remainder)) return true
        return false
    }

    private fun matchesPrefix(
        query: String,
        prefix: String,
    ): Boolean {
        if (!query.startsWith(prefix)) return false
        if (query.length == prefix.length) return true
        return query[prefix.length].isWhitespace()
    }

    private fun containsPhrase(
        query: String,
        phrase: String,
    ): Boolean {
        if (query == phrase) return true
        return query.startsWith("$phrase ") ||
            query.endsWith(" $phrase") ||
            query.contains(" $phrase ")
    }

    private data class PrefixMatch(
        val remainder: String,
        val usedBrandPrefix: Boolean,
        val aliasLength: Int,
        val compact: Boolean,
    )

    private val storageRegex = Regex("""\b\d+\s?(gb|гб|tb|тб)\b""")
}

private fun mergeFamilies(
    baseFamilies: List<CatalogCanonicalProductFamilyEntry>,
    projectedFamilies: List<CatalogCanonicalProductFamilyEntry>,
): List<CatalogCanonicalProductFamilyEntry> {
    val merged = LinkedHashMap<String, CatalogCanonicalProductFamilyEntry>()
    normalizeFamilies(baseFamilies).forEach { family ->
        merged[family.familyCode] = family
    }
    normalizeFamilies(projectedFamilies).forEach { family ->
        val existing = merged[family.familyCode]
        merged[family.familyCode] = if (existing == null) {
            family
        } else {
            CatalogCanonicalProductFamilyEntry(
                familyCode = family.familyCode,
                defaultCategoryCode = family.defaultCategoryCode.ifBlank { existing.defaultCategoryCode },
                brandCanonical = family.brandCanonical.ifBlank { existing.brandCanonical },
                brandAliases = (existing.brandAliases + family.brandAliases).distinct(),
                familyCanonical = family.familyCanonical.ifBlank { existing.familyCanonical },
                familyAliases = (existing.familyAliases + family.familyAliases).distinct(),
                prettyModelPrefix = family.prettyModelPrefix.ifBlank { existing.prettyModelPrefix },
                variantTokens = (existing.variantTokens + family.variantTokens).distinct(),
                accessoryBlockers = (existing.accessoryBlockers + family.accessoryBlockers).distinct(),
            )
        }
    }
    return normalizeFamilies(merged.values.toList())
}

private fun normalizeFamilies(
    families: List<CatalogCanonicalProductFamilyEntry>,
): List<CatalogCanonicalProductFamilyEntry> =
    families.map { family ->
        CatalogCanonicalProductFamilyEntry(
            familyCode = family.familyCode.trim(),
            defaultCategoryCode = family.defaultCategoryCode.trim().uppercase(),
            brandCanonical = family.brandCanonical.trim(),
            brandAliases = family.brandAliases
                .map(Stage21QueryTextNormalizer::normalize)
                .filter { alias -> alias.isNotBlank() }
                .distinct(),
            familyCanonical = family.familyCanonical.trim(),
            familyAliases = family.familyAliases
                .map(Stage21QueryTextNormalizer::normalize)
                .filter { alias -> alias.isNotBlank() }
                .distinct(),
            prettyModelPrefix = family.prettyModelPrefix.trim(),
            variantTokens = family.variantTokens
                .map(Stage21QueryTextNormalizer::normalize)
                .filter { token -> token.isNotBlank() }
                .distinct(),
            accessoryBlockers = family.accessoryBlockers
                .map(Stage21QueryTextNormalizer::normalize)
                .filter { token -> token.isNotBlank() }
                .distinct(),
        )
    }
