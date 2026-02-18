package com.example.shoppingassistant.domain.catalog

class SeedAliasFirstQueryRouter(
    private val aliasEntryRepository: AliasEntryRepository,
    private val fallbackRouter: QueryRouter,
) : QueryRouter {

    override suspend fun route(query: String, locale: String): QueryRoutingResult {
        val normalizedQuery = Stage21QueryTextNormalizer.normalize(query)
        if (normalizedQuery.isBlank()) {
            return fallbackRouter.route(query = query, locale = locale)
        }

        val entries = loadEntries(locale)
        val bestMatch = entries
            .asSequence()
            .filterNot { it.isBlocked }
            .filter { it.matchKind == AliasMatchKind.EXACT }
            .filter { Stage21QueryTextNormalizer.normalize(it.normalizedTerm) == normalizedQuery }
            .sortedWith(
                compareByDescending<AliasEntry> { it.weight }
                    .thenBy { kindPriority(it.kind) }
                    .thenByDescending { it.term.length },
            )
            .firstOrNull()

        val routed = bestMatch?.toRoutingResult(normalizedQuery)
        return routed ?: fallbackRouter.route(query = query, locale = locale)
    }

    private suspend fun loadEntries(locale: String): List<AliasEntry> {
        val localized = aliasEntryRepository.listAliasEntries(locale = locale)
        if (localized.isNotEmpty() || locale.equals(DEFAULT_LOCALE, ignoreCase = true)) {
            return localized
        }
        return aliasEntryRepository.listAliasEntries(locale = DEFAULT_LOCALE)
    }

    private fun AliasEntry.toRoutingResult(normalizedQuery: String): QueryRoutingResult? {
        val routeType = when (kind) {
            AliasKind.BROWSE -> QueryRouteType.OPEN_BROWSE
            AliasKind.CATEGORY -> QueryRouteType.OPEN_CATEGORY
            AliasKind.BRAND,
            AliasKind.ATTRIBUTE_HINT,
            -> return null
        }
        val normalizedTargetCode = targetCode.trim()
        return QueryRoutingResult(
            routeType = routeType,
            primaryTargetCode = normalizedTargetCode,
            facetCollectionCode = if (routeType == QueryRouteType.OPEN_BROWSE) normalizedTargetCode else null,
            extractedTokens = Stage21QueryTextNormalizer.tokenize(normalizedQuery),
            confidence = aliasWeightToConfidence(weight),
        )
    }

    private fun kindPriority(kind: AliasKind): Int = when (kind) {
        AliasKind.BROWSE -> 0
        AliasKind.CATEGORY -> 1
        AliasKind.BRAND -> 2
        AliasKind.ATTRIBUTE_HINT -> 3
    }

    private fun aliasWeightToConfidence(weight: Int): Double {
        val normalizedWeight = weight.coerceIn(0, 100) / 100.0
        return 0.65 + (normalizedWeight * 0.3)
    }

    private companion object {
        private const val DEFAULT_LOCALE = "ru-RU"
    }
}
