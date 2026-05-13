package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal object Stage21HomePackageLoader {
    private val descriptor = Stage21PackageDescriptor(
        l0Code = "HOME",
        basePath = "${CatalogContractPaths.stage21Base}/HOME",
        browseNodesFile = "browse_nodes.home.tsv",
        aliasesFile = "aliases.home.tsv",
        goldenQueriesFile = "queries_golden.home.tsv",
        coverageFile = "coverage.home.json",
        routingRulesFile = "routing_rules.home.yaml",
        browseRootCode = "B.HOME",
        schemaFamily = Stage21SchemaFamily.HOME_FAMILY,
        supportsBlockedGoldenRouteKind = false,
    )

    private val packageData: Stage21HomeFamilyPackageData by lazy {
        GenericStage21PackageLoader.load(descriptor)
    }

    val browseNodes: List<BrowseNode> by lazy { packageData.browseNodes }
    val aliasSeedRows: List<HomeAliasSeedRow> by lazy { packageData.aliasSeedRows }
    val aliasEntries: List<AliasEntry> by lazy { aliasSeedRows.map { it.toAliasEntry() } }
    val goldenQueries: List<GoldenQuery> by lazy { packageData.goldenQueries }
    val coverageGate: Stage21HomeCoverageGate by lazy { packageData.coverageGate }
    val routingRulesYaml: String by lazy { packageData.routingRulesYaml }
}

data class HomeAliasSeedRow(
    val aliasId: String,
    val aliasText: String,
    val locale: String,
    val normalizedAlias: String,
    val routeKind: HomeAliasRouteKind,
    val targetId: String,
    val priority: Int,
    val matchType: HomeAliasMatchType,
    val negativeTokens: List<String>,
    val isActive: Boolean,
    val notes: String?,
)

enum class HomeAliasRouteKind {
    BROWSE_NODE,
    CANONICAL,
    BLOCKED,
}

enum class HomeAliasMatchType {
    CONTAINS,
    TOKEN,
    EXACT,
    REGEX,
}

@Serializable
internal data class Stage21HomeCoverageGate(
    val version: String,
    @SerialName("l0_code") val l0Code: String,
    @SerialName("required_leaf_codes") val requiredLeafCodes: List<String>,
    val requirements: Stage21HomeCoverageRequirements,
    val gates: Stage21HomeCoverageGates,
    @SerialName("report_schema") val reportSchema: Stage21HomeCoverageReportSchema? = null,
)

@Serializable
internal data class Stage21HomeCoverageRequirements(
    @SerialName("require_browse_path_to_each_leaf") val requireBrowsePathToEachLeaf: Boolean,
    @SerialName("min_aliases_per_leaf") val minAliasesPerLeaf: Int,
    @SerialName("min_golden_queries_per_leaf") val minGoldenQueriesPerLeaf: Int,
    @SerialName("max_blocked_share_of_aliases") val maxBlockedShareOfAliases: Double,
)

@Serializable
internal data class Stage21HomeCoverageGates(
    @SerialName("coverage_leaf_reachability") val coverageLeafReachability: String,
    @SerialName("aliases_density") val aliasesDensity: String,
    @SerialName("golden_set_size") val goldenSetSize: String,
    @SerialName("golden_set_pass_rate") val goldenSetPassRate: Double,
)

@Serializable
internal data class Stage21HomeCoverageReportSchema(
    @SerialName("leaf_code") val leafCodeType: String,
    @SerialName("reachable_via_browse") val reachableViaBrowseType: String,
    @SerialName("aliases_count") val aliasesCountType: String,
    @SerialName("golden_queries_count") val goldenQueriesCountType: String,
    @SerialName("status") val statusType: String,
    @SerialName("notes") val notesType: String,
)

internal fun HomeAliasSeedRow.toAliasEntry(): AliasEntry = toStage21AliasEntry(notesPrefix = "stage2.1.home")

internal fun HomeAliasSeedRow.toStage21AliasEntry(notesPrefix: String): AliasEntry {
    val targetCode = targetId.trim()
    return AliasEntry(
        locale = locale,
        term = aliasText,
        normalizedTerm = normalizedAlias,
        kind = when (routeKind) {
            HomeAliasRouteKind.BROWSE_NODE,
            HomeAliasRouteKind.BLOCKED,
            -> AliasKind.BROWSE

            HomeAliasRouteKind.CANONICAL -> AliasKind.CATEGORY
        },
        targetCode = targetCode,
        weight = priority.coerceIn(0, 100),
        matchKind = matchType.toAliasMatchKind(),
        isBlocked = routeKind == HomeAliasRouteKind.BLOCKED || !isActive,
        source = AliasSource.SEED,
        notes = buildString {
            append(notesPrefix)
            append(":")
            append(routeKind.name.lowercase())
            append(":")
            append(aliasId)
            if (notes != null) {
                append(":")
                append(notes)
            }
        },
    )
}

private fun HomeAliasMatchType.toAliasMatchKind(): AliasMatchKind = when (this) {
    HomeAliasMatchType.EXACT -> AliasMatchKind.EXACT
    HomeAliasMatchType.CONTAINS -> AliasMatchKind.PREFIX
    HomeAliasMatchType.TOKEN -> AliasMatchKind.TOKEN
    HomeAliasMatchType.REGEX -> AliasMatchKind.FUZZY
}