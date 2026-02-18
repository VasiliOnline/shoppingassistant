package com.example.shoppingassistant.domain.catalog

internal object Stage21KidsPackageLoader {
    private val descriptor = Stage21PackageDescriptor(
        l0Code = "KIDS",
        basePath = "taxonomy/stage2/2.1/KIDS",
        browseNodesFile = "browse_nodes.kids.tsv",
        aliasesFile = "aliases.kids.tsv",
        goldenQueriesFile = "queries_golden.kids.tsv",
        coverageFile = "coverage.kids.json",
        routingRulesFile = "routing_rules.kids.yaml",
        browseRootCode = "B.KIDS",
        schemaFamily = Stage21SchemaFamily.HOME_FAMILY,
        supportsBlockedGoldenRouteKind = true,
    )

    private val packageData: Stage21HomeFamilyPackageData by lazy {
        GenericStage21PackageLoader.load(descriptor)
    }

    val browseNodes: List<BrowseNode> by lazy { packageData.browseNodes }
    val aliasSeedRows: List<HomeAliasSeedRow> by lazy { packageData.aliasSeedRows }
    val aliasEntries: List<AliasEntry> by lazy { aliasSeedRows.map { it.toStage21AliasEntry(notesPrefix = "stage2.1.kids") } }
    val goldenQueries: List<GoldenQuery> by lazy { packageData.goldenQueries }
    val coverageGate: Stage21HomeCoverageGate by lazy { packageData.coverageGate }
    val routingRulesYaml: String by lazy { packageData.routingRulesYaml }
}
