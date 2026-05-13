package com.example.shoppingassistant.domain.catalog

internal object Stage21BeautyPackageLoader {
    private val descriptor = Stage21PackageDescriptor(
        l0Code = "BEAUTY",
        basePath = "${CatalogContractPaths.stage21Base}/BEAUTY",
        browseNodesFile = "browse_nodes.beauty.tsv",
        aliasesFile = "aliases.beauty.tsv",
        goldenQueriesFile = "queries_golden.beauty.tsv",
        coverageFile = "coverage.beauty.json",
        routingRulesFile = "routing_rules.beauty.yaml",
        browseRootCode = "B.BEAUTY",
        schemaFamily = Stage21SchemaFamily.HOME_FAMILY,
        supportsBlockedGoldenRouteKind = true,
    )

    private val packageData: Stage21HomeFamilyPackageData by lazy {
        GenericStage21PackageLoader.load(descriptor)
    }

    val browseNodes: List<BrowseNode> by lazy { packageData.browseNodes }
    val aliasSeedRows: List<HomeAliasSeedRow> by lazy { packageData.aliasSeedRows }
    val aliasEntries: List<AliasEntry> by lazy { aliasSeedRows.map { it.toStage21AliasEntry(notesPrefix = "stage2.1.beauty") } }
    val goldenQueries: List<GoldenQuery> by lazy { packageData.goldenQueries }
    val coverageGate: Stage21HomeCoverageGate by lazy { packageData.coverageGate }
    val routingRulesYaml: String by lazy { packageData.routingRulesYaml }
}
