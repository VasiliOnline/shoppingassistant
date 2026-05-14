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
        explicitCrossSegmentBoundaryRoute(normalizedQuery)?.let { return it }
        if (!hasAccessoryOrContainerRouteIntent(normalizedQuery)) {
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

    private fun hasAccessoryOrContainerRouteIntent(query: String): Boolean {
        val normalized = query
            .lowercase()
            .replace('ё', 'е')
            .replace("[-‐‑‒–—]+".toRegex(), " ")
            .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (normalized.isBlank()) return false

        val accessoryPhrases = listOf(
            "hard shell",
            "keyboard cover",
            "privacy filter",
            "monitor arm",
            "docking station",
            "thunderbolt dock",
            "usb c dock",
            "usb c hub",
            "usb hub",
            "type c hub",
            "laptop stand",
            "mouse pad",
            "cleaning kit",
            "laptop bag",
            "laptop backpack",
            "laptop sleeve",
            "сумка для ноутбука",
            "рюкзак для ноутбука",
            "чехол для ноутбука",
            "чехол для macbook",
            "накладка на macbook",
            "накладка на клавиатуру",
            "фильтр приватности",
            "антишпион для экрана",
            "подставка для ноутбука",
            "кронштейн для монитора",
            "рука для монитора",
            "коврик для мыши",
            "набор для чистки",
            "переходник usb c",
        )
        if (accessoryPhrases.any { phrase -> containsPhrase(normalized, phrase) }) return true

        val tokens = normalized.split(" ").filter { it.isNotBlank() }.toSet()
        return tokens.any { token ->
            token in setOf(
                "чехол",
                "чехлы",
                "сумка",
                "сумки",
                "рюкзак",
                "рюкзаки",
                "sleeve",
                "bag",
                "backpack",
                "dock",
                "hub",
                "хаб",
                "донгл",
                "dongle",
                "adapter",
                "переходник",
                "клавиатура",
                "keyboard",
                "мышь",
                "mouse",
            )
        }
    }

    private fun explicitCrossSegmentBoundaryRoute(query: String): QueryRoutingResult? {
        val normalized = normalizeRouteIntentText(query)
        if (normalized.isBlank()) return null

        val isLaptopBag = listOf(
            "сумка для ноутбука",
            "рюкзак для ноутбука",
            "чехол для ноутбука",
            "сумка для macbook",
            "рюкзак для macbook",
            "laptop bag",
            "laptop backpack",
            "laptop sleeve",
            "soft laptop sleeve",
        ).any { phrase -> containsPhrase(normalized, phrase) }
        if (isLaptopBag) {
            return QueryRoutingResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = "FASH.BAGS",
                extractedTokens = Stage21QueryTextNormalizer.tokenize(normalized),
                confidence = 0.96,
            )
        }

        return null
    }

    private fun normalizeRouteIntentText(query: String): String = query
        .lowercase()
        .replace('ё', 'е')
        .replace("[-‐‑‒–—]+".toRegex(), " ")
        .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    private fun containsPhrase(query: String, phrase: String): Boolean {
        val escaped = Regex.escape(phrase)
        return Regex("(^|\\s)$escaped(\\s|$)").containsMatchIn(query)
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
