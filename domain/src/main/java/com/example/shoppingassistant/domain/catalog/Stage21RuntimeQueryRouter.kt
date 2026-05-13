package com.example.shoppingassistant.domain.catalog

class Stage21RuntimeQueryRouter(
    private val segmentRouters: List<QueryRouter> = defaultSegmentRouters(),
    private val fallbackRouter: QueryRouter = SeedRunSearchQueryRouter(),
    private val minConfidenceToAccept: Double = DEFAULT_MIN_CONFIDENCE,
) : QueryRouter {

    override suspend fun route(query: String, locale: String): QueryRoutingResult {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return fallbackRouter.route(query = query, locale = locale)
        }
        CatalogCanonicalModelRegistry.matchQuery(normalizedQuery)?.let { match ->
            return QueryRoutingResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = match.defaultCategoryCode,
                extractedTokens = Stage21QueryTextNormalizer.tokenize(match.normalizedQuery),
                confidence = match.confidence,
            )
        }
        CatalogCanonicalProductFamilyRegistry.matchQuery(normalizedQuery)?.let { match ->
            return QueryRoutingResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = match.defaultCategoryCode,
                extractedTokens = Stage21QueryTextNormalizer.tokenize(match.normalizedQuery),
                confidence = match.confidence,
            )
        }

        val candidateComparator = compareByDescending<QueryRoutingResult> { it.confidence }
            .thenBy { routeTypePriority(it.routeType) }
            .thenBy { it.primaryTargetCode.orEmpty() }
        var best: QueryRoutingResult? = null
        for (router in segmentRouters) {
            val candidate = runCatching { router.route(query = normalizedQuery, locale = locale) }.getOrNull()
                ?: continue
            if (candidate.routeType == QueryRouteType.RUN_SEARCH) continue
            if (candidate.primaryTargetCode?.trim().isNullOrEmpty()) continue
            if (best == null || candidateComparator.compare(candidate, best) < 0) {
                best = candidate
            }
        }

        if (best == null || best.confidence < minConfidenceToAccept) {
            return fallbackRouter.route(query = normalizedQuery, locale = locale)
        }
        return best
    }

    private fun routeTypePriority(routeType: QueryRouteType): Int = when (routeType) {
        QueryRouteType.OPEN_CATEGORY -> 0
        QueryRouteType.OPEN_BROWSE -> 1
        QueryRouteType.RUN_SEARCH -> 2
    }

    private companion object {
        private const val DEFAULT_MIN_CONFIDENCE = 0.55

        private fun defaultSegmentRouters(): List<QueryRouter> = listOf(
            Stage21TechQueryRouter(),
            Stage21ApplQueryRouter(),
            Stage21HomeQueryRouter(),
            Stage21BeautyQueryRouter(),
            Stage21FashQueryRouter(),
            Stage21FoodQueryRouter(),
            Stage21KidsQueryRouter(),
            Stage21PetsQueryRouter(),
            Stage21SportQueryRouter(),
            Stage21AutoQueryRouter(),
        )
    }
}
