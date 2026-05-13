package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.i18n.localizedTextOf
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

internal enum class Stage21SchemaFamily {
    HOME_FAMILY,
    APPL,
    TECH,
}

internal data class Stage21PackageDescriptor(
    val l0Code: String,
    val basePath: String,
    val browseNodesFile: String,
    val aliasesFile: String,
    val goldenQueriesFile: String,
    val coverageFile: String,
    val routingRulesFile: String,
    val browseRootCode: String,
    val schemaFamily: Stage21SchemaFamily = Stage21SchemaFamily.HOME_FAMILY,
    val supportsBlockedGoldenRouteKind: Boolean = false,
)

internal data class Stage21HomeFamilyPackageData(
    val browseNodes: List<BrowseNode>,
    val aliasSeedRows: List<HomeAliasSeedRow>,
    val goldenQueries: List<GoldenQuery>,
    val coverageGate: Stage21HomeCoverageGate,
    val routingRulesYaml: String,
)

internal data class Stage21ApplPackageData(
    val browseNodes: List<BrowseNode>,
    val aliasSeedRows: List<ApplAliasSeedRow>,
    val goldenQueries: List<GoldenQuery>,
    val coverageGate: Stage21ApplCoverageGate,
    val routingRulesYaml: String,
)

internal data class Stage21TechPackageData(
    val browseNodes: List<BrowseNode>,
    val aliasEntries: List<AliasEntry>,
    val goldenQueries: List<GoldenQuery>,
    val coverageGate: Stage21CoverageGate,
    val routingRulesYaml: String,
)

internal interface Stage21SchemaAdapter<out T> {
    fun load(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): T
}

internal object GenericStage21PackageLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(descriptor: Stage21PackageDescriptor): Stage21HomeFamilyPackageData {
        require(descriptor.schemaFamily == Stage21SchemaFamily.HOME_FAMILY) {
            "Stage 2.1 HOME_FAMILY descriptor expected, got ${descriptor.schemaFamily}"
        }
        return Stage21HomeFamilySchemaAdapter.load(descriptor, json)
    }

    fun loadAppl(descriptor: Stage21PackageDescriptor): Stage21ApplPackageData {
        require(descriptor.schemaFamily == Stage21SchemaFamily.APPL) {
            "Stage 2.1 APPL descriptor expected, got ${descriptor.schemaFamily}"
        }
        return Stage21ApplSchemaAdapter.load(descriptor, json)
    }

    fun loadTech(descriptor: Stage21PackageDescriptor): Stage21TechPackageData {
        require(descriptor.schemaFamily == Stage21SchemaFamily.TECH) {
            "Stage 2.1 TECH descriptor expected, got ${descriptor.schemaFamily}"
        }
        return Stage21TechSchemaAdapter.load(descriptor, json)
    }
}

internal object Stage21HomeFamilySchemaAdapter : Stage21SchemaAdapter<Stage21HomeFamilyPackageData> {
    override fun load(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): Stage21HomeFamilyPackageData {
        val browseNodes = loadBrowseNodes(descriptor)
        val aliasSeedRows = loadAliasSeedRows(descriptor)
        val goldenQueries = loadGoldenQueries(descriptor)
        val coverageGate = loadCoverageGate(descriptor, json)
        val routingRulesYaml = readResource("${descriptor.basePath}/${descriptor.routingRulesFile}")
        return Stage21HomeFamilyPackageData(
            browseNodes = browseNodes,
            aliasSeedRows = aliasSeedRows,
            goldenQueries = goldenQueries,
            coverageGate = coverageGate,
            routingRulesYaml = routingRulesYaml,
        )
    }

    private fun loadBrowseNodes(descriptor: Stage21PackageDescriptor): List<BrowseNode> =
        parseTsv("${descriptor.basePath}/${descriptor.browseNodesFile}").map { row ->
            val parentCode = row.optional("parent_browse_node_id")?.let { raw -> mapBrowseCode(raw, descriptor) }
            val depth = row.optional("depth")?.toIntOrNull() ?: 0
            val canonicalCode = row.optional("canonical_category_code")
            val nodeKind = when {
                parentCode == null || depth <= 1 -> BrowseNodeKind.ROOT
                canonicalCode.isNullOrBlank() -> BrowseNodeKind.GROUP
                else -> BrowseNodeKind.LEAF_LINK
            }
            val targetCategoryCode = canonicalCode?.trim()?.takeIf { nodeKind == BrowseNodeKind.LEAF_LINK }
            BrowseNode(
                browseCode = mapBrowseCode(row.required("browse_node_id"), descriptor),
                parentBrowseCode = parentCode,
                nodeKind = nodeKind,
                titleKey = row.optional("slug")?.let { "catalog.${descriptor.l0Code.lowercase()}.$it" },
                title = localizedTextOf("ru" to row.required("title_ru")),
                targetCategoryCode = targetCategoryCode,
                targetType = targetCategoryCode?.let { BrowseTargetType.CATEGORY },
                order = row.optional("sort_order")?.toIntOrNull() ?: 0,
                availabilityScope = "ALL",
                iconKey = extractIcon(row.optional("meta_json")),
                analyticsKey = null,
                searchKeywordsRu = emptyList(),
                status = parseBrowseStatus(row.optional("status"), descriptor.browseNodesFile),
                tags = listOf("stage2.1", descriptor.l0Code.uppercase(), "kind:${nodeKind.name.lowercase()}"),
                notes = null,
            )
        }

    private fun loadAliasSeedRows(descriptor: Stage21PackageDescriptor): List<HomeAliasSeedRow> =
        parseTsv("${descriptor.basePath}/${descriptor.aliasesFile}").map { row ->
            val routeKind = parseAliasRouteKind(row.required("route_kind"), descriptor.aliasesFile)
            val aliasText = row.required("alias_text")
            val normalizedAlias = normalize(row.optional("normalized") ?: aliasText)
            val rawTargetId = row.required("target_id")
            val targetId = when (routeKind) {
                HomeAliasRouteKind.BROWSE_NODE -> mapBrowseCode(rawTargetId, descriptor)
                HomeAliasRouteKind.CANONICAL -> rawTargetId
                HomeAliasRouteKind.BLOCKED -> descriptor.browseRootCode
            }
            HomeAliasSeedRow(
                aliasId = row.required("alias_id"),
                aliasText = aliasText,
                locale = row.optional("locale") ?: "ru-RU",
                normalizedAlias = normalizedAlias,
                routeKind = routeKind,
                targetId = targetId,
                priority = row.optional("priority")?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                matchType = parseAliasMatchType(row.optional("match_type"), descriptor.aliasesFile),
                negativeTokens = parseNegativeTokens(row.optional("negative_tokens")),
                isActive = parseAliasStatus(row.optional("status")),
                notes = row.optional("notes"),
            )
        }

    private fun loadGoldenQueries(descriptor: Stage21PackageDescriptor): List<GoldenQuery> =
        parseTsv("${descriptor.basePath}/${descriptor.goldenQueriesFile}").map { row ->
            val rawTargetKind = row.required("expected_route_kind")
            val targetKind = parseGoldenTargetKind(
                raw = rawTargetKind,
                supportsBlocked = descriptor.supportsBlockedGoldenRouteKind,
                sourceName = descriptor.goldenQueriesFile,
            )
            val rawExpectedTarget = row.required("expected_target")
            GoldenQuery(
                queryId = row.required("qid"),
                locale = row.optional("locale") ?: "ru-RU",
                queryText = row.required("query_text"),
                expectedTargetKind = targetKind,
                expectedCode = when (targetKind) {
                    GoldenTargetKind.CATEGORY_LEAF -> rawExpectedTarget
                    GoldenTargetKind.BROWSE_NODE -> {
                        if (descriptor.supportsBlockedGoldenRouteKind && rawTargetKind.trim().equals("blocked", ignoreCase = true)) {
                            descriptor.browseRootCode
                        } else {
                            mapBrowseCode(rawExpectedTarget, descriptor)
                        }
                    }
                },
                mustRankTopN = row.optional("must_rank_top_n")?.toIntOrNull() ?: 1,
                notes = row.optional("notes"),
            )
        }

    private fun loadCoverageGate(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): Stage21HomeCoverageGate {
        val raw = readResource("${descriptor.basePath}/${descriptor.coverageFile}")
        return json.decodeFromString(raw)
    }

    private fun parseAliasRouteKind(
        raw: String,
        sourceName: String,
    ): HomeAliasRouteKind = when (raw.trim().lowercase()) {
        "browse_node" -> HomeAliasRouteKind.BROWSE_NODE
        "canonical" -> HomeAliasRouteKind.CANONICAL
        "blocked" -> HomeAliasRouteKind.BLOCKED
        else -> error("Unsupported route_kind in $sourceName: '$raw'")
    }

    private fun parseAliasMatchType(
        raw: String?,
        sourceName: String,
    ): HomeAliasMatchType = when (raw?.trim()?.lowercase()) {
        null, "", "contains" -> HomeAliasMatchType.CONTAINS
        "token" -> HomeAliasMatchType.TOKEN
        "exact" -> HomeAliasMatchType.EXACT
        "regex" -> HomeAliasMatchType.REGEX
        else -> error("Unsupported match_type in $sourceName: '$raw'")
    }
}

internal object Stage21ApplSchemaAdapter : Stage21SchemaAdapter<Stage21ApplPackageData> {
    override fun load(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): Stage21ApplPackageData {
        val browseNodes = loadBrowseNodes(descriptor)
        val aliasSeedRows = loadAliasSeedRows(descriptor)
        val goldenQueries = loadGoldenQueries(descriptor)
        val coverageGate = loadCoverageGate(descriptor, json)
        val routingRulesYaml = readResource("${descriptor.basePath}/${descriptor.routingRulesFile}")
        return Stage21ApplPackageData(
            browseNodes = browseNodes,
            aliasSeedRows = aliasSeedRows,
            goldenQueries = goldenQueries,
            coverageGate = coverageGate,
            routingRulesYaml = routingRulesYaml,
        )
    }

    private fun loadBrowseNodes(descriptor: Stage21PackageDescriptor): List<BrowseNode> =
        parseTsv("${descriptor.basePath}/${descriptor.browseNodesFile}").map { row ->
            val nodeKind = parseBrowseNodeKind(row.required("kind"), descriptor.browseNodesFile)
            val canonicalCode = row.optional("canonical_code")
            val browseCode = mapBrowseCode(row.required("node_id"), descriptor)
            val targetCanonicalCode = if (nodeKind == BrowseNodeKind.LEAF_LINK) {
                canonicalCode?.let(::resolveLeafCanonical)
            } else {
                null
            }
            BrowseNode(
                browseCode = browseCode,
                parentBrowseCode = row.optional("parent_id")?.let { mapBrowseCode(it, descriptor) },
                nodeKind = nodeKind,
                titleKey = null,
                title = localizedTextOf("ru" to row.required("title_ru")),
                targetCategoryCode = targetCanonicalCode,
                targetType = targetCanonicalCode?.let { BrowseTargetType.CATEGORY },
                order = row.optional("rank")?.toIntOrNull() ?: 0,
                availabilityScope = "ALL",
                iconKey = null,
                analyticsKey = null,
                searchKeywordsRu = emptyList(),
                status = parseBrowseStatus(row.optional("status"), descriptor.browseNodesFile),
                tags = listOf("stage2.1", "APPL", "kind:${nodeKind.name.lowercase()}"),
                notes = null,
            )
        }

    private fun loadAliasSeedRows(descriptor: Stage21PackageDescriptor): List<ApplAliasSeedRow> =
        parseTsv("${descriptor.basePath}/${descriptor.aliasesFile}").map { row ->
            val query = row.required("query")
            val flag = parseAliasFlag(row.optional("flags"), descriptor.aliasesFile)
            val targetType = parseAliasTargetType(row.required("target_type"), descriptor.aliasesFile)
            val rawTargetId = row.required("target_id")
            ApplAliasSeedRow(
                aliasId = row.required("alias_id"),
                locale = row.optional("locale") ?: "ru-RU",
                query = query,
                normalizedQuery = normalize(query),
                targetType = targetType,
                targetId = when (targetType) {
                    ApplAliasTargetType.NODE -> mapBrowseCode(rawTargetId, descriptor)
                    ApplAliasTargetType.CANONICAL -> rawTargetId
                },
                flag = flag,
                matchKind = parseSharedAliasMatchKind(row.required("match_kind"), descriptor.aliasesFile),
                negativeTokens = parseNegativeTokens(row.optional("negative_tokens")),
                isBlocked = row.optional("is_blocked")?.toBooleanStrictOrNullCompat() ?: false,
                source = parseSharedAliasSource(row.required("source"), descriptor.aliasesFile),
                weight = row.required("weight").toIntOrNull()
                    ?: error("Unsupported weight in ${descriptor.aliasesFile}: '${row.optional("weight")}'"),
                notes = row.optional("notes"),
            )
        }

    private fun loadGoldenQueries(descriptor: Stage21PackageDescriptor): List<GoldenQuery> =
        parseTsv("${descriptor.basePath}/${descriptor.goldenQueriesFile}").mapIndexed { index, row ->
            val targetKind = parseGoldenTargetKind(
                raw = row.required("expected_type"),
                supportsBlocked = false,
                sourceName = descriptor.goldenQueriesFile,
            )
            val rawExpectedCode = row.required("expected_id")
            GoldenQuery(
                queryId = "APPL-${(index + 1).toString().padStart(4, '0')}",
                locale = row.optional("locale") ?: "ru-RU",
                queryText = row.required("query"),
                expectedTargetKind = targetKind,
                expectedCode = when (targetKind) {
                    GoldenTargetKind.BROWSE_NODE -> mapBrowseCode(rawExpectedCode, descriptor)
                    GoldenTargetKind.CATEGORY_LEAF -> rawExpectedCode
                },
                mustRankTopN = row.optional("must_rank_top_n")?.toIntOrNull() ?: 1,
                notes = row.optional("notes"),
            )
        }

    private fun loadCoverageGate(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): Stage21ApplCoverageGate {
        val raw = readResource("${descriptor.basePath}/${descriptor.coverageFile}")
        val parsed = json.decodeFromString<Stage21ApplCoverageGate>(raw)
        return parsed.copy(expectedLeafNodes = parsed.expectedLeafNodes.map { mapBrowseCode(it, descriptor) })
    }

    private fun resolveLeafCanonical(rawCanonicalCode: String): String {
        val canonicalCode = rawCanonicalCode.trim().uppercase()
        return if (canonicalCode == "APPL") "APPL.SMALL" else canonicalCode
    }

    private fun parseAliasTargetType(
        raw: String,
        sourceName: String,
    ): ApplAliasTargetType = when (raw.trim().uppercase()) {
        "NODE" -> ApplAliasTargetType.NODE
        "CANONICAL" -> ApplAliasTargetType.CANONICAL
        else -> error("Unsupported target_type in $sourceName: '$raw'")
    }

    private fun parseAliasFlag(
        raw: String?,
        sourceName: String,
    ): ApplAliasFlag = when (raw?.trim()?.uppercase()) {
        null, "", "POSITIVE" -> ApplAliasFlag.POSITIVE
        "DISAMBIGUATE" -> ApplAliasFlag.DISAMBIGUATE
        "NEGATIVE_FOR_APPL" -> ApplAliasFlag.NEGATIVE_FOR_APPL
        else -> error("Unsupported flags in $sourceName: '$raw'")
    }
}

internal object Stage21TechSchemaAdapter : Stage21SchemaAdapter<Stage21TechPackageData> {
    override fun load(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): Stage21TechPackageData {
        val browseNodes = loadBrowseNodes(descriptor)
        val aliasEntries = loadAliasEntries(descriptor)
        val goldenQueries = loadGoldenQueries(descriptor)
        val coverageGate = loadCoverageGate(descriptor, json)
        val routingRulesYaml = readResource("${descriptor.basePath}/${descriptor.routingRulesFile}")
        return Stage21TechPackageData(
            browseNodes = browseNodes,
            aliasEntries = aliasEntries,
            goldenQueries = goldenQueries,
            coverageGate = coverageGate,
            routingRulesYaml = routingRulesYaml,
        )
    }

    private fun loadBrowseNodes(descriptor: Stage21PackageDescriptor): List<BrowseNode> =
        parseTsv("${descriptor.basePath}/${descriptor.browseNodesFile}").map { row ->
            val browseCode = mapBrowseCode(row.required("browse_node_code"), descriptor)
            val nodeKind = parseBrowseNodeKind(row.required("node_kind"), descriptor.browseNodesFile)
            val targetCategoryCode = row.optional("target_category_code")
            BrowseNode(
                browseCode = browseCode,
                parentBrowseCode = row.optional("parent_browse_node_code")?.let { mapBrowseCode(it, descriptor) },
                nodeKind = nodeKind,
                titleKey = row.optional("analytics_key")?.let { "catalog.$it" },
                title = localizedTextOf(
                    "ru" to row.required("title_ru"),
                    "en" to row.optional("title_en"),
                ),
                targetCategoryCode = targetCategoryCode,
                targetType = if (targetCategoryCode == null) null else BrowseTargetType.CATEGORY,
                order = row.required("sort_order").toIntOrNull() ?: 0,
                availabilityScope = row.optional("availability_scope") ?: "ALL",
                iconKey = row.optional("icon_key"),
                analyticsKey = row.optional("analytics_key"),
                searchKeywordsRu = row.optional("search_keywords_ru")
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
                status = if (row.required("is_visible").toBooleanStrictOrNullCompat() != false) {
                    BrowseNodeStatus.ACTIVE
                } else {
                    BrowseNodeStatus.HIDDEN
                },
                tags = listOf("stage2.1", "TECH", "kind:${nodeKind.name.lowercase()}"),
                notes = row.optional("notes"),
            )
        }

    private fun loadAliasEntries(descriptor: Stage21PackageDescriptor): List<AliasEntry> =
        parseTsv("${descriptor.basePath}/${descriptor.aliasesFile}").map { row ->
            AliasEntry(
                locale = row.required("locale"),
                term = row.required("alias_text"),
                normalizedTerm = row.required("normalized_alias"),
                kind = parseAliasKind(row.required("route_kind"), descriptor.aliasesFile),
                targetCode = row.required("target_code"),
                weight = row.required("priority").toIntOrNull() ?: 0,
                matchKind = parseAliasMatchKind(row.required("match_kind"), descriptor.aliasesFile),
                isBlocked = row.required("is_blocked").toBooleanStrictOrNullCompat() ?: false,
                source = parseAliasSource(row.required("source"), descriptor.aliasesFile),
                notes = row.optional("comment"),
            )
        }

    private fun loadGoldenQueries(descriptor: Stage21PackageDescriptor): List<GoldenQuery> =
        parseTsv("${descriptor.basePath}/${descriptor.goldenQueriesFile}").map { row ->
            GoldenQuery(
                queryId = row.required("query_id"),
                locale = row.required("locale"),
                queryText = row.required("query_text"),
                expectedTargetKind = parseGoldenTargetKind(
                    raw = row.required("expected_target_kind"),
                    supportsBlocked = false,
                    sourceName = descriptor.goldenQueriesFile,
                ),
                expectedCode = row.required("expected_code"),
                mustRankTopN = row.required("must_rank_top_n").toIntOrNull() ?: 1,
                notes = row.optional("notes"),
            )
        }

    private fun loadCoverageGate(
        descriptor: Stage21PackageDescriptor,
        json: Json,
    ): Stage21CoverageGate {
        val raw = readResource("${descriptor.basePath}/${descriptor.coverageFile}")
        return json.decodeFromString(raw)
    }

    private fun parseAliasKind(
        raw: String,
        sourceName: String,
    ): AliasKind = when (raw.trim().uppercase()) {
        "CATEGORY_LEAF", "CANONICAL" -> AliasKind.CATEGORY
        "BROWSE_NODE", "NODE" -> AliasKind.BROWSE
        else -> error("Unsupported route_kind in $sourceName: '$raw'")
    }

    private fun parseAliasMatchKind(
        raw: String,
        sourceName: String,
    ): AliasMatchKind = when (raw.trim().uppercase()) {
        "EXACT" -> AliasMatchKind.EXACT
        "PREFIX", "CONTAINS" -> AliasMatchKind.PREFIX
        "TOKEN" -> AliasMatchKind.TOKEN
        "FUZZY", "REGEX" -> AliasMatchKind.FUZZY
        else -> error("Unsupported match_kind in $sourceName: '$raw'")
    }

    private fun parseAliasSource(
        raw: String,
        sourceName: String,
    ): AliasSource = when (raw.trim().uppercase()) {
        "SEED" -> AliasSource.SEED
        "ANALYTICS" -> AliasSource.ANALYTICS
        "MANUAL" -> AliasSource.MANUAL
        "LEARNED" -> AliasSource.LEARNED
        else -> error("Unsupported source in $sourceName: '$raw'")
    }
}

private fun parseGoldenTargetKind(
    raw: String,
    supportsBlocked: Boolean,
    sourceName: String,
): GoldenTargetKind = when (raw.trim().lowercase()) {
    "canonical", "category_leaf" -> GoldenTargetKind.CATEGORY_LEAF
    "browse_node", "node" -> GoldenTargetKind.BROWSE_NODE
    "blocked" -> {
        if (supportsBlocked) {
            GoldenTargetKind.BROWSE_NODE
        } else {
            error("Unsupported expected target kind in $sourceName: '$raw'")
        }
    }

    else -> error("Unsupported expected target kind in $sourceName: '$raw'")
}

private fun parseBrowseNodeKind(
    raw: String,
    sourceName: String,
): BrowseNodeKind = when (raw.trim().lowercase()) {
    "root" -> BrowseNodeKind.ROOT
    "group" -> BrowseNodeKind.GROUP
    "leaf", "leaf_link" -> BrowseNodeKind.LEAF_LINK
    else -> error("Unsupported node_kind in $sourceName: '$raw'")
}

private fun parseBrowseStatus(
    raw: String?,
    sourceName: String,
): BrowseNodeStatus = when (raw?.trim()?.lowercase()) {
    null, "", "active" -> BrowseNodeStatus.ACTIVE
    "hidden", "inactive" -> BrowseNodeStatus.HIDDEN
    else -> error("Unsupported status in $sourceName: '$raw'")
}

private fun parseAliasStatus(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
    null, "", "active" -> true
    "inactive", "hidden", "blocked" -> false
    else -> true
}

private fun parseSharedAliasMatchKind(
    raw: String,
    sourceName: String,
): AliasMatchKind = when (raw.trim().uppercase()) {
    "EXACT" -> AliasMatchKind.EXACT
    "PREFIX", "CONTAINS" -> AliasMatchKind.PREFIX
    "TOKEN" -> AliasMatchKind.TOKEN
    "FUZZY", "REGEX" -> AliasMatchKind.FUZZY
    else -> error("Unsupported match_kind in $sourceName: '$raw'")
}

private fun parseSharedAliasSource(
    raw: String,
    sourceName: String,
): AliasSource = when (raw.trim().uppercase()) {
    "SEED" -> AliasSource.SEED
    "ANALYTICS" -> AliasSource.ANALYTICS
    "MANUAL" -> AliasSource.MANUAL
    "LEARNED" -> AliasSource.LEARNED
    else -> error("Unsupported source in $sourceName: '$raw'")
}

private fun parseNegativeTokens(raw: String?): List<String> = raw
    ?.split("|", ",", ";")
    ?.map(::normalize)
    ?.filter { it.isNotBlank() }
    .orEmpty()

private fun parseTsv(resourcePath: String): List<Map<String, String>> {
    val content = readResource(resourcePath)
    val lines = content
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return emptyList()

    val header = lines.first().split('\t').map { it.trim() }
    if (header.isEmpty()) return emptyList()
    val normalizedHeader = header.toMutableList()
    normalizedHeader[0] = normalizedHeader[0].removePrefix("\uFEFF")

    return lines.drop(1).map { line ->
        val cells = line.split('\t')
        normalizedHeader.indices.associate { index ->
            normalizedHeader[index] to cells.getOrElse(index) { "" }
        }
    }
}

private fun readResource(resourcePath: String): String {
    val classLoader = GenericStage21PackageLoader::class.java.classLoader
    val stream = classLoader.getResourceAsStream(resourcePath)
        ?: error("Stage 2.1 resource not found: $resourcePath")
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}

private fun mapBrowseCode(
    rawNodeId: String,
    descriptor: Stage21PackageDescriptor,
): String {
    val normalized = rawNodeId.trim()
    if (normalized.isBlank()) return normalized

    val upper = normalized.uppercase()
    val l0 = descriptor.l0Code.uppercase()
    if (upper.startsWith("B.$l0")) return normalized
    if (upper.startsWith("BN.$l0")) return "B.${normalized.substring(3)}"

    val lower = normalized.lowercase()
    val l0Lower = descriptor.l0Code.lowercase()
    val expectedPrefix = "bn_${l0Lower}_"
    if (!lower.startsWith(expectedPrefix)) return normalized

    val suffix = lower.removePrefix(expectedPrefix)
    if (suffix == "root") return descriptor.browseRootCode

    val segments = suffix
        .split('_')
        .filter { it.isNotBlank() }
        .map { it.uppercase() }
    return (listOf("B", l0) + segments).joinToString(".")
}

private fun normalize(input: String): String = input
    .trim()
    .lowercase()
    .replace('ё', 'е')
    .replace("[-‐‑‒–—]+".toRegex(), " ")
    .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
    .replace("\\s+".toRegex(), " ")
    .trim()

private fun extractIcon(metaJson: String?): String? {
    val raw = metaJson?.trim().orEmpty()
    if (raw.isBlank()) return null
    val match = Regex(""""icon"\\s*:\\s*"([^"]+)"""").find(raw)
    return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun String.toBooleanStrictOrNullCompat(): Boolean? = when (trim().lowercase()) {
    "true", "1", "yes" -> true
    "false", "0", "no" -> false
    else -> null
}

private fun Map<String, String>.required(column: String): String =
    this[column]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Column '$column' is required in Stage 2.1 package")

private fun Map<String, String>.optional(column: String): String? =
    this[column]?.trim()?.takeIf { it.isNotEmpty() }
