package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object Stage21TechPackageLoader {
    private val descriptor = Stage21PackageDescriptor(
        l0Code = "TECH",
        basePath = "${CatalogContractPaths.stage21Base}/TECH",
        browseNodesFile = "browse_nodes.tech.tsv",
        aliasesFile = "aliases.tech.tsv",
        goldenQueriesFile = "queries_golden.tech.tsv",
        coverageFile = "coverage.tech.json",
        routingRulesFile = "routing_rules.tech.yaml",
        browseRootCode = "B.TECH",
        schemaFamily = Stage21SchemaFamily.TECH,
        supportsBlockedGoldenRouteKind = false,
    )

    private val packageData: Stage21TechPackageData by lazy {
        GenericStage21PackageLoader.loadTech(descriptor)
    }

    val browseNodes: List<BrowseNode> by lazy { packageData.browseNodes }
    val aliasEntries: List<AliasEntry> by lazy { packageData.aliasEntries }
    val goldenQueries: List<GoldenQuery> by lazy { packageData.goldenQueries }
    val coverageGate: Stage21CoverageGate by lazy { packageData.coverageGate }
    val routingRulesYaml: String by lazy { packageData.routingRulesYaml }
}

@Serializable
internal data class Stage21CoverageGate(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("l0_code") val l0Code: String,
    val thresholds: Stage21CoverageThresholds,
    val results: Stage21CoverageResults,
)

@Serializable
internal data class Stage21CoverageThresholds(
    @SerialName("min_alias_per_leaf") val minAliasPerLeaf: Int,
    @SerialName("golden_min_queries") val goldenMinQueries: Int,
    @SerialName("golden_recall_top1") val goldenRecallTop1: Double,
    @SerialName("golden_recall_top3") val goldenRecallTop3: Double,
    @SerialName("browse_leaf_reachable") val browseLeafReachable: Boolean,
    @SerialName("ambiguous_queries_share_warn") val ambiguousQueriesShareWarn: Double,
)

@Serializable
internal data class Stage21CoverageResults(
    @SerialName("computed_at") val computedAt: String,
    @SerialName("golden_total") val goldenTotal: Int,
)

internal data class GoldenQuery(
    val queryId: String,
    val locale: String,
    val queryText: String,
    val expectedTargetKind: GoldenTargetKind,
    val expectedCode: String,
    val mustRankTopN: Int,
    val notes: String?,
)

enum class GoldenTargetKind {
    CATEGORY_LEAF,
    BROWSE_NODE,
}
