package com.example.shoppingassistant.domain.catalog

internal object Stage21FoodPackageLoader {
    private val descriptor = Stage21PackageDescriptor(
        l0Code = "FOOD",
        basePath = "taxonomy/stage2/2.1/FOOD",
        browseNodesFile = "browse_nodes.food.tsv",
        aliasesFile = "aliases.food.tsv",
        goldenQueriesFile = "queries_golden.food.tsv",
        coverageFile = "coverage.food.json",
        routingRulesFile = "routing_rules.food.yaml",
        browseRootCode = "B.FOOD",
        schemaFamily = Stage21SchemaFamily.HOME_FAMILY,
        supportsBlockedGoldenRouteKind = true,
    )

    private val packageData: Stage21HomeFamilyPackageData by lazy {
        GenericStage21PackageLoader.load(descriptor)
    }

    val browseNodes: List<BrowseNode> by lazy { packageData.browseNodes }
    val aliasSeedRows: List<HomeAliasSeedRow> by lazy { packageData.aliasSeedRows }
    val aliasEntries: List<AliasEntry> by lazy { aliasSeedRows.map { it.toStage21AliasEntry(notesPrefix = "stage2.1.food") } }
    val goldenQueries: List<GoldenQuery> by lazy { packageData.goldenQueries }
    val coverageGate: Stage21HomeCoverageGate by lazy { packageData.coverageGate }
    val routingRulesYaml: String by lazy { packageData.routingRulesYaml }
}
