package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import java.util.concurrent.atomic.AtomicReference

data class CatalogCanonicalModelEntry(
    val modelCode: String,
    val defaultCategoryCode: String,
    val brandCanonical: String,
    val familyCode: String? = null,
    val canonicalModel: String,
    val modelAliases: List<String> = emptyList(),
    val accessoryBlockers: List<String> = emptyList(),
    val searchWeight: Int = 0,
)

data class CatalogCanonicalModelMatch(
    val modelCode: String,
    val defaultCategoryCode: String,
    val brandCanonical: String,
    val familyCode: String? = null,
    val normalizedQuery: String,
    val modelText: String,
    val matchedAliasLength: Int,
    val confidence: Double,
    val searchWeight: Int = 0,
)

interface CatalogCanonicalModelRegistryProvider {
    fun models(): List<CatalogCanonicalModelEntry>
}

object SeedCatalogCanonicalModelRegistryProvider : CatalogCanonicalModelRegistryProvider {
    private val modelsCache: List<CatalogCanonicalModelEntry> by lazy {
        normalizeModels(CatalogGovernanceCuratedSeed.projectedModels())
    }

    override fun models(): List<CatalogCanonicalModelEntry> = modelsCache
}

class StaticCatalogCanonicalModelRegistryProvider(
    models: List<CatalogCanonicalModelEntry>,
) : CatalogCanonicalModelRegistryProvider {
    private val normalizedModels = normalizeModels(models)

    override fun models(): List<CatalogCanonicalModelEntry> = normalizedModels
}

object CatalogCanonicalModelRegistry {
    private val providerRef = AtomicReference<CatalogCanonicalModelRegistryProvider>(
        SeedCatalogCanonicalModelRegistryProvider,
    )

    fun currentProvider(): CatalogCanonicalModelRegistryProvider = providerRef.get()

    fun installProvider(provider: CatalogCanonicalModelRegistryProvider) {
        providerRef.set(provider)
    }

    fun resetProvider() {
        providerRef.set(SeedCatalogCanonicalModelRegistryProvider)
    }

    fun models(): List<CatalogCanonicalModelEntry> = currentProvider().models()

    fun matchQuery(queryText: String): CatalogCanonicalModelMatch? {
        val normalizedQuery = Stage21QueryTextNormalizer.normalize(queryText)
        if (normalizedQuery.isBlank()) return null
        val compactQuery = SearchTextNormalizer.normalizeToken(queryText)

        return currentProvider()
            .models()
            .asSequence()
            .mapNotNull { model ->
                matchModel(
                    model = model,
                    normalizedQuery = normalizedQuery,
                    compactQuery = compactQuery,
                )
            }
            .sortedWith(
                compareByDescending<CatalogCanonicalModelMatch> { it.confidence }
                    .thenByDescending { it.matchedAliasLength }
                    .thenByDescending { it.searchWeight }
                    .thenByDescending { it.modelText.length }
                    .thenBy { it.modelCode },
            )
            .firstOrNull()
    }

    private fun matchModel(
        model: CatalogCanonicalModelEntry,
        normalizedQuery: String,
        compactQuery: String,
    ): CatalogCanonicalModelMatch? {
        val normalizedAliases = model.modelAliases.sortedByDescending { alias -> alias.length }
        val normalAlias = normalizedAliases.firstOrNull { alias -> matchesPrefix(normalizedQuery, alias) }
        val compactAlias = if (compactQuery.isNotBlank()) {
            normalizedAliases
                .map { alias -> alias to SearchTextNormalizer.normalizeToken(alias) }
                .filter { (_, compactAliasValue) -> compactAliasValue.isNotBlank() }
                .sortedByDescending { (_, compactAliasValue) -> compactAliasValue.length }
                .firstOrNull { (_, compactAliasValue) ->
                    matchesCompactRegistryPrefix(
                        compactQuery = compactQuery,
                        compactAlias = compactAliasValue,
                    )
                }
        } else {
            null
        }

        val match = when {
            normalAlias != null -> MatchedAlias(
                aliasText = normalAlias,
                aliasLength = normalAlias.length,
                remainder = normalizedQuery.removePrefix(normalAlias).trim(),
                compact = false,
            )
            compactAlias != null -> {
                val remainderCompact = compactQuery.removePrefix(compactAlias.second).trim()
                MatchedAlias(
                    aliasText = compactAlias.first,
                    aliasLength = compactAlias.second.length,
                    remainder = humanizeCompactRegistryRemainder(remainderCompact),
                    compact = true,
                )
            }
            else -> null
        } ?: return null

        if (containsAccessoryBlocker(
                normalizedQuery = normalizedQuery,
                compactQuery = compactQuery,
                blockers = model.accessoryBlockers,
                remainder = match.remainder,
            )
        ) {
            return null
        }

        val remainder = match.remainder
        val canonicalAlias = Stage21QueryTextNormalizer.normalize(model.canonicalModel)
        val confidence = when {
            remainder.isBlank() -> 0.99
            match.compact && match.aliasText != canonicalAlias -> 0.985
            match.aliasText == canonicalAlias -> 0.98
            match.compact -> 0.975
            else -> 0.97
        }
        val modelText = if (remainder.isBlank()) {
            model.canonicalModel
        } else {
            "${model.canonicalModel} $remainder"
        }
        return CatalogCanonicalModelMatch(
            modelCode = model.modelCode,
            defaultCategoryCode = model.defaultCategoryCode,
            brandCanonical = model.brandCanonical,
            familyCode = model.familyCode,
            normalizedQuery = normalizedQuery,
            modelText = modelText,
            matchedAliasLength = match.aliasLength,
            confidence = confidence,
            searchWeight = model.searchWeight,
        )
    }

    private fun matchesPrefix(
        query: String,
        prefix: String,
    ): Boolean {
        if (!query.startsWith(prefix)) return false
        if (query.length == prefix.length) return true
        return query[prefix.length].isWhitespace()
    }

    private fun containsAccessoryBlocker(
        normalizedQuery: String,
        compactQuery: String,
        blockers: List<String>,
        remainder: String,
    ): Boolean {
        if (blockers.isEmpty()) return false
        val tokens = Stage21QueryTextNormalizer.tokenize(normalizedQuery).toSet()
        return blockers.any { blocker ->
            val normalizedBlocker = Stage21QueryTextNormalizer.normalize(blocker)
            val compactBlocker = SearchTextNormalizer.normalizeToken(blocker)
            normalizedBlocker.isNotBlank() && (
                normalizedBlocker in tokens ||
                    normalizedQuery.contains(" $normalizedBlocker ") ||
                    normalizedQuery.startsWith("$normalizedBlocker ") ||
                    normalizedQuery.endsWith(" $normalizedBlocker") ||
                    remainder.startsWith(normalizedBlocker) ||
                    (compactBlocker.isNotBlank() && (
                        compactQuery.endsWith(compactBlocker) ||
                            SearchTextNormalizer.normalizeToken(remainder).startsWith(compactBlocker)
                        ))
                )
        }
    }

    private data class MatchedAlias(
        val aliasText: String,
        val aliasLength: Int,
        val remainder: String,
        val compact: Boolean,
    )
}

private fun normalizeModels(
    models: List<CatalogCanonicalModelEntry>,
): List<CatalogCanonicalModelEntry> =
    models.map { model ->
        model.copy(
            modelCode = model.modelCode.trim().uppercase(),
            defaultCategoryCode = model.defaultCategoryCode.trim().uppercase(),
            familyCode = model.familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            brandCanonical = model.brandCanonical.trim(),
            canonicalModel = model.canonicalModel.trim(),
            modelAliases = model.modelAliases
                .map { Stage21QueryTextNormalizer.normalize(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            accessoryBlockers = model.accessoryBlockers
                .map { Stage21QueryTextNormalizer.normalize(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted(),
            searchWeight = model.searchWeight.coerceIn(0, 100),
        )
    }.sortedWith(
        compareBy<CatalogCanonicalModelEntry> { it.defaultCategoryCode }
            .thenBy { it.brandCanonical }
            .thenBy { it.canonicalModel },
    )
