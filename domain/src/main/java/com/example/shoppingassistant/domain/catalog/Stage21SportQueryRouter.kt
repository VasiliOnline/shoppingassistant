package com.example.shoppingassistant.domain.catalog

class Stage21SportQueryRouter(
    aliases: List<HomeAliasSeedRow> = Stage21SportPackageLoader.aliasSeedRows,
    browseNodes: List<BrowseNode> = Stage21SportPackageLoader.browseNodes,
    routingRulesYaml: String = Stage21SportPackageLoader.routingRulesYaml,
) : QueryRouter {

    private val delegate = Stage21HomeQueryRouter(
        aliases = aliases,
        browseNodes = browseNodes,
        routingRulesYaml = routingRulesYaml,
    )

    override suspend fun route(query: String, locale: String): QueryRoutingResult =
        delegate.route(query = query, locale = locale)

    fun routeWithCandidates(
        query: String,
        locale: String = "ru-RU",
        topN: Int = 3,
    ): QueryRoutingDebugResult = delegate.routeWithCandidates(
        query = query,
        locale = locale,
        topN = topN,
    )
}

