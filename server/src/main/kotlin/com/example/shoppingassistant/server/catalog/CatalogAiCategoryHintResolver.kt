package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.QueryRouteType
import com.example.shoppingassistant.domain.catalog.Stage21RuntimeQueryRouter
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import java.util.Locale

internal class CatalogAiCategoryHintResolver(
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val runtimeQueryRouter: Stage21RuntimeQueryRouter = Stage21RuntimeQueryRouter(),
) {
    suspend fun resolveCategoryCode(
        rawHint: String?,
        locale: String?,
        allowedCodes: Set<String> = emptySet(),
    ): String? {
        val directCode = resolveCanonicalCode(rawHint)
        if (directCode != null && isAllowed(directCode, allowedCodes)) return directCode

        val normalizedHint = normalizeHint(rawHint)
        if (normalizedHint.isEmpty()) return null

        val routedCode = runCatching {
            runtimeQueryRouter.route(
                query = rawHint!!.trim(),
                locale = locale?.trim()?.ifEmpty { "ru-RU" } ?: "ru-RU",
            )
        }.getOrNull()
            ?.takeIf { result -> result.routeType == QueryRouteType.OPEN_CATEGORY }
            ?.primaryTargetCode
            ?.let { primaryTargetCode ->
                resolveCanonicalCode(primaryTargetCode)
            }
        if (routedCode != null && isAllowed(routedCode, allowedCodes)) return routedCode

        val categories = activeCategories()
            .let { candidates ->
                if (allowedCodes.isEmpty()) {
                    candidates
                } else {
                    candidates.filter { category -> category.code.uppercase(Locale.ROOT) in allowedCodes }
                }
            }
        return scoreByTitle(
            categories = categories,
            seedText = normalizedHint,
            locale = locale,
        ).firstOrNull()?.code?.uppercase(Locale.ROOT)
    }

    private suspend fun resolveCanonicalCode(categoryCode: String?): String? {
        val normalized = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT) ?: return null
        val resolution = runCatching {
            catalogTaxonomyRepository.resolveCategoryCode(normalized)
        }.getOrNull()
        return if (resolution != null &&
            !resolution.cycleDetected &&
            resolution.unresolvedTarget == null
        ) {
            resolution.resolvedCode.uppercase(Locale.ROOT)
        } else {
            null
        }
    }

    private suspend fun activeCategories(): List<Category> =
        catalogTaxonomyRepository.listCategories()
            .filter { category -> category.status == CategoryStatus.ACTIVE }

    private fun scoreByTitle(
        categories: List<Category>,
        seedText: String,
        locale: String?,
    ): List<Category> {
        val normalizedSeed = normalizeHint(seedText)
        if (normalizedSeed.isEmpty()) return emptyList()
        val hintTokens = SearchTextNormalizer.tokens(normalizedSeed)
            .map { token -> token.lowercase(Locale.ROOT) }
            .toSet()
        return categories
            .map { category ->
                val normalizedTitle = normalizeHint(category.displayTitle(locale))
                val titleTokens = SearchTextNormalizer.tokens(normalizedTitle)
                    .map { token -> token.lowercase(Locale.ROOT) }
                    .toSet()
                val overlap = titleTokens.intersect(hintTokens).size
                val exactBoost = when {
                    normalizedTitle == normalizedSeed -> 100
                    normalizedTitle.contains(normalizedSeed) || normalizedSeed.contains(normalizedTitle) -> 24
                    else -> 0
                }
                category to (exactBoost + overlap * 12)
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<Category, Int>> { it.second }.thenBy { it.first.code })
            .map { (category, _) -> category }
    }

    private fun normalizeHint(raw: String?): String =
        raw?.let(SearchTextNormalizer::normalizeKey).orEmpty()

    private fun isAllowed(
        categoryCode: String,
        allowedCodes: Set<String>,
    ): Boolean = allowedCodes.isEmpty() || categoryCode.uppercase(Locale.ROOT) in allowedCodes
}
