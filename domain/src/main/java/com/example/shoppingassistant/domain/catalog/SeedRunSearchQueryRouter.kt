package com.example.shoppingassistant.domain.catalog

class SeedRunSearchQueryRouter : QueryRouter {
    override suspend fun route(query: String, locale: String): QueryRoutingResult {
        val normalizedQuery = Stage21QueryTextNormalizer.normalize(query)
        val tokens = Stage21QueryTextNormalizer.tokenize(normalizedQuery)
        return QueryRoutingResult(
            routeType = QueryRouteType.RUN_SEARCH,
            extractedTokens = tokens,
            confidence = if (tokens.isEmpty()) 0.0 else 0.35,
        )
    }
}
