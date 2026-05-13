package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CatalogCategoryWriteSpec
import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasKind
import com.example.shoppingassistant.domain.catalog.AliasMatchKind
import com.example.shoppingassistant.domain.catalog.AliasSource
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeKind
import com.example.shoppingassistant.domain.catalog.BrowseNodeStatus
import com.example.shoppingassistant.domain.catalog.BrowseTargetType
import com.example.shoppingassistant.domain.catalog.CategoryAlias
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingType
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import java.util.Locale

enum class CatalogSeedSyncMode(
    val allowsDestructiveOps: Boolean,
) {
    UPSERT_ONLY(allowsDestructiveOps = false),
    FULL_SYNC(allowsDestructiveOps = true),
    ;

    companion object {
        fun fromEnv(raw: String?): CatalogSeedSyncMode {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            if (normalized.isEmpty()) return UPSERT_ONLY
            return when (normalized) {
                "UPSERT_ONLY", "UPSERT", "SAFE" -> UPSERT_ONLY
                "FULL_SYNC", "FULL", "DESTRUCTIVE" -> FULL_SYNC
                else -> error(
                    "Unsupported CATALOG_SEED_SYNC_MODE='$raw'. " +
                        "Supported values: UPSERT_ONLY, FULL_SYNC.",
                )
            }
        }
    }
}

data class CatalogStage20BackfillReport(
    val syncMode: CatalogSeedSyncMode,
    val ensureReferencedCategories: Boolean,
    val categoriesEnsured: Int,
    val categoryAliasesTotal: Long,
    val browseNodesTotal: Long,
    val aliasEntriesTotal: Long,
    val googleMappingsTotal: Long,
)

/**
 * Инициализация справочников категорий/атрибутов из domain-сидов.
 */
object CatalogSeeder {
    fun seedIfEmpty(
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
    ) {
        syncCategorySpecs(CatalogSeed.categoryWriteSpecs, syncMode = syncMode)
        syncCategoryAliases(CatalogSeed.categoryAliases, syncMode = syncMode)
        syncBrowseNodes(CatalogSeed.browseNodes, syncMode = syncMode)
        syncAliasEntries(CatalogSeed.aliasEntries, syncMode = syncMode)
        syncGoogleMappings(CatalogSeed.googleMappings, syncMode = syncMode)
        syncAttributeValueDict(CatalogSeed.valueDictionaries, syncMode = syncMode)
        syncConstraints(CatalogSeed.constraints, syncMode = syncMode)
        syncFacetDefinitions(CatalogSeed.facetDefinitions, syncMode = syncMode)
        syncFacetPresets(CatalogSeed.facetPresets, syncMode = syncMode)
        syncFacetCollections(CatalogSeed.facetCollections, syncMode = syncMode)
        syncStage40Contract(syncMode = syncMode)
    }

    fun syncRuntimeContracts(
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
    ) {
        syncCategorySpecs(CatalogSeed.categoryWriteSpecs, syncMode = syncMode)
        syncAttributeValueDict(CatalogSeed.valueDictionaries, syncMode = syncMode)
        syncStage40Contract(syncMode = syncMode)
    }

    fun seedStage20Taxonomy(
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
        ensureReferencedCategories: Boolean = false,
    ): CatalogStage20BackfillReport {
        val categoriesEnsured = if (ensureReferencedCategories) {
            ensureStage20ReferencedCategories()
        } else {
            0
        }

        syncCategoryAliases(CatalogSeed.categoryAliases, syncMode = syncMode)
        syncBrowseNodes(CatalogSeed.browseNodes, syncMode = syncMode)
        syncAliasEntries(CatalogSeed.aliasEntries, syncMode = syncMode)
        syncGoogleMappings(CatalogSeed.googleMappings, syncMode = syncMode)

        return CatalogStage20BackfillReport(
            syncMode = syncMode,
            ensureReferencedCategories = ensureReferencedCategories,
            categoriesEnsured = categoriesEnsured,
            categoryAliasesTotal = CategoryAliasesTable.selectAll().count(),
            browseNodesTotal = BrowseNodesTable.selectAll().count(),
            aliasEntriesTotal = AliasEntriesTable.selectAll().count(),
            googleMappingsTotal = GoogleTaxonomyMappingsTable.selectAll().count(),
        )
    }

    fun syncServingAliasEntries(
        aliasEntries: List<AliasEntry>,
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
    ) {
        syncAliasEntries(aliasEntries, syncMode = syncMode)
    }

    fun syncServingAttributeValueDict(
        dictionaries: List<AttributeValueDict>,
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
    ) {
        syncAttributeValueDict(dictionaries, syncMode = syncMode)
    }

    private fun syncCategorySpecs(
        specs: List<CatalogCategoryWriteSpec>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val normalizedProfiles = specs
            .map(::normalizeWriteSpec)
            .filter { it.category.code.isNotEmpty() }
            .distinctBy { it.category.code }
            .sortedBy { it.category.code }

        syncCategories(normalizedProfiles.map { it.category }, syncMode = syncMode)
        syncAttributeDefs(normalizedProfiles.flatMap { it.attributes }, syncMode = syncMode)
        syncCategoryAttributes(
            normalizedProfiles.flatMap { it.categoryAttributes },
            syncMode = syncMode,
        )
    }

    private fun ensureStage20ReferencedCategories(): Int {
        val categoriesByCode = CatalogSeed.categories
            .map(::normalizeSeedCategory)
            .filter { it.code.isNotEmpty() }
            .associateBy { it.code }
        if (categoriesByCode.isEmpty()) return 0

        val referencedCodes = collectStage20ReferencedCategoryCodes()
        if (referencedCodes.isEmpty()) return 0

        val requiredCodes = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        referencedCodes.forEach { code ->
            if (requiredCodes.add(code)) {
                queue.addLast(code)
            }
        }

        while (queue.isNotEmpty()) {
            val currentCode = queue.removeFirst()
            val parentCode = categoriesByCode[currentCode]?.parentCode ?: continue
            if (requiredCodes.add(parentCode)) {
                queue.addLast(parentCode)
            }
        }

        val existingCodes = CategoriesTable
            .selectAll()
            .map { row -> row[CategoriesTable.code] }
            .toSet()

        val missingCodes = (requiredCodes - existingCodes).sorted()
        var inserted = 0
        missingCodes.forEach { code ->
            val category = categoriesByCode[code] ?: return@forEach
            CategoriesTable.insert { stmt ->
                stmt[CategoriesTable.code] = category.code
                stmt[CategoriesTable.segment] = category.segment.name
                stmt[CategoriesTable.status] = category.status.name
                stmt[CategoriesTable.titleLocalized] = category.title
                stmt[CategoriesTable.titleRu] = category.title.storageRu(category.code)
                stmt[CategoriesTable.titleEn] = category.title.storageEn()
                stmt[CategoriesTable.parentCode] = category.parentCode
                stmt[CategoriesTable.description] = category.description
                stmt[CategoriesTable.replacementCode] = category.replacementCode
            }
            inserted += 1
        }
        return inserted
    }

    private fun collectStage20ReferencedCategoryCodes(): Set<String> {
        val aliasCategoryCodes = CatalogSeed.categoryAliases
            .asSequence()
            .map { alias -> alias.categoryCode.trim() }
            .filter { code -> code.isNotEmpty() }

        val browseCategoryCodes = CatalogSeed.browseNodes
            .asSequence()
            .mapNotNull { node -> node.targetCategoryCode?.trim()?.takeIf { it.isNotEmpty() } }

        val aliasEntryCategoryCodes = CatalogSeed.aliasEntries
            .asSequence()
            .filter { entry -> entry.kind == AliasKind.CATEGORY }
            .map { entry -> entry.targetCode.trim() }
            .filter { code -> code.isNotEmpty() }

        val googleCategoryCodes = CatalogSeed.googleMappings
            .asSequence()
            .map { mapping -> mapping.canonicalCode.trim() }
            .filter { code -> code.isNotEmpty() }

        return sequenceOf(
            aliasCategoryCodes,
            browseCategoryCodes,
            aliasEntryCategoryCodes,
            googleCategoryCodes,
        )
            .flatten()
            .toSet()
    }

    private fun syncCategories(
        categories: List<com.example.shoppingassistant.domain.catalog.Category>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = categories
            .map(::normalizeSeedCategory)
            .filter { it.code.isNotEmpty() }
            .distinctBy { it.code }
            .sortedBy { it.code }

        val seedCodes = seedRows.map { it.code }.toSet()
        val existingCodes = CategoriesTable
            .selectAll()
            .map { row -> row[CategoriesTable.code] }
            .toSet()

        val staleCodes = existingCodes - seedCodes
        if (staleCodes.isNotEmpty()) {
            if (syncMode.allowsDestructiveOps) {
                CategoriesTable.deleteWhere { CategoriesTable.code inList staleCodes.toList() }
            } else {
                CategoriesTable.update({ CategoriesTable.code inList staleCodes.toList() }) { stmt ->
                    stmt[status] = CategoryStatus.DEPRECATED.name
                }
            }
        }

        seedRows.forEach { category ->
            val updated = CategoriesTable.update({ CategoriesTable.code eq category.code }) { stmt ->
                stmt[segment] = category.segment.name
                stmt[status] = category.status.name
                stmt[titleLocalized] = category.title
                stmt[titleRu] = category.title.storageRu(category.code)
                stmt[titleEn] = category.title.storageEn()
                stmt[parentCode] = category.parentCode
                stmt[description] = category.description
                stmt[replacementCode] = category.replacementCode
            }
            if (updated == 0) {
                CategoriesTable.insert { stmt ->
                    stmt[code] = category.code
                    stmt[segment] = category.segment.name
                    stmt[status] = category.status.name
                    stmt[titleLocalized] = category.title
                    stmt[titleRu] = category.title.storageRu(category.code)
                    stmt[titleEn] = category.title.storageEn()
                    stmt[parentCode] = category.parentCode
                    stmt[description] = category.description
                    stmt[replacementCode] = category.replacementCode
                }
            }
        }
    }

    private fun syncCategoryAliases(
        aliases: List<CategoryAlias>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val knownCategoryCodes = CategoriesTable
            .selectAll()
            .map { row -> row[CategoriesTable.code] }
            .toSet()

        val seedRows = aliases
            .mapNotNull { alias ->
                val normalizedAlias = alias.alias.trim()
                val normalizedCategoryCode = alias.categoryCode.trim()
                if (normalizedAlias.isEmpty() || normalizedCategoryCode.isEmpty()) return@mapNotNull null
                if (normalizedCategoryCode !in knownCategoryCodes) return@mapNotNull null
                CategoryAliasPayload(
                    alias = normalizedAlias,
                    categoryCode = normalizedCategoryCode,
                )
            }
            .distinctBy { "${it.alias}|${it.categoryCode}" }
            .sortedWith(compareBy<CategoryAliasPayload> { it.alias.lowercase(Locale.ROOT) }.thenBy { it.categoryCode })

        if (syncMode.allowsDestructiveOps) {
            CategoryAliasesTable.deleteAll()
            if (seedRows.isNotEmpty()) {
                CategoryAliasesTable.batchInsert(seedRows) { row ->
                    this[CategoryAliasesTable.alias] = row.alias
                    this[CategoryAliasesTable.categoryCode] = row.categoryCode
                }
            }
            return
        }

        val existingKeys = CategoryAliasesTable
            .selectAll()
            .map { row -> row[CategoryAliasesTable.alias] to row[CategoryAliasesTable.categoryCode] }
            .toSet()

        seedRows.forEach { row ->
            val key = row.alias to row.categoryCode
            if (key !in existingKeys) {
                CategoryAliasesTable.insert { stmt ->
                    stmt[CategoryAliasesTable.alias] = row.alias
                    stmt[CategoryAliasesTable.categoryCode] = row.categoryCode
                }
            }
        }
    }

    private fun syncBrowseNodes(
        browseNodes: List<BrowseNode>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = browseNodes
            .mapNotNull { node ->
                val browseCode = node.browseCode.trim()
                if (browseCode.isEmpty()) return@mapNotNull null
                BrowseNodePayload(
                    browseCode = browseCode,
                    parentBrowseCode = node.parentBrowseCode?.trim()?.takeIf { it.isNotEmpty() },
                    nodeKind = node.nodeKind.name,
                    titleKey = node.titleKey?.trim()?.takeIf { it.isNotEmpty() },
                    title = node.title,
                    titleRu = node.title.storageRu(browseCode),
                    titleEn = node.title.storageEn(),
                    targetCategoryCode = node.targetCategoryCode?.trim()?.takeIf { it.isNotEmpty() },
                    targetType = node.targetType?.name,
                    order = node.order,
                    availabilityScope = node.availabilityScope.trim().ifEmpty { "ALL" },
                    iconKey = node.iconKey?.trim()?.takeIf { it.isNotEmpty() },
                    analyticsKey = node.analyticsKey?.trim()?.takeIf { it.isNotEmpty() },
                    searchKeywordsRu = node.searchKeywordsRu
                        .map { keyword -> keyword.trim() }
                        .filter { keyword -> keyword.isNotEmpty() }
                        .distinct(),
                    status = node.status.name,
                    tags = node.tags
                        .map { tag -> tag.trim() }
                        .filter { tag -> tag.isNotEmpty() }
                        .distinct(),
                    notes = node.notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .distinctBy { it.browseCode }
            .sortedBy { it.browseCode }

        if (syncMode.allowsDestructiveOps) {
            BrowseNodesTable.deleteAll()
            if (seedRows.isNotEmpty()) {
                BrowseNodesTable.batchInsert(seedRows) { row ->
                    this[BrowseNodesTable.browseCode] = row.browseCode
                    this[BrowseNodesTable.parentBrowseCode] = row.parentBrowseCode
                    this[BrowseNodesTable.nodeKind] = row.nodeKind
                    this[BrowseNodesTable.titleKey] = row.titleKey
                    this[BrowseNodesTable.titleLocalized] = row.title
                    this[BrowseNodesTable.titleRu] = row.titleRu
                    this[BrowseNodesTable.titleEn] = row.titleEn
                    this[BrowseNodesTable.targetCategoryCode] = row.targetCategoryCode
                    this[BrowseNodesTable.targetType] = row.targetType
                    this[BrowseNodesTable.order] = row.order
                    this[BrowseNodesTable.availabilityScope] = row.availabilityScope
                    this[BrowseNodesTable.iconKey] = row.iconKey
                    this[BrowseNodesTable.analyticsKey] = row.analyticsKey
                    this[BrowseNodesTable.searchKeywordsRu] = row.searchKeywordsRu
                    this[BrowseNodesTable.status] = row.status
                    this[BrowseNodesTable.tags] = row.tags
                    this[BrowseNodesTable.notes] = row.notes
                }
            }
            return
        }

        seedRows.forEach { row ->
            val updated = BrowseNodesTable.update({ BrowseNodesTable.browseCode eq row.browseCode }) { stmt ->
                stmt[BrowseNodesTable.parentBrowseCode] = row.parentBrowseCode
                stmt[BrowseNodesTable.nodeKind] = row.nodeKind
                stmt[BrowseNodesTable.titleKey] = row.titleKey
                stmt[BrowseNodesTable.titleLocalized] = row.title
                stmt[BrowseNodesTable.titleRu] = row.titleRu
                stmt[BrowseNodesTable.titleEn] = row.titleEn
                stmt[BrowseNodesTable.targetCategoryCode] = row.targetCategoryCode
                stmt[BrowseNodesTable.targetType] = row.targetType
                stmt[BrowseNodesTable.order] = row.order
                stmt[BrowseNodesTable.availabilityScope] = row.availabilityScope
                stmt[BrowseNodesTable.iconKey] = row.iconKey
                stmt[BrowseNodesTable.analyticsKey] = row.analyticsKey
                stmt[BrowseNodesTable.searchKeywordsRu] = row.searchKeywordsRu
                stmt[BrowseNodesTable.status] = row.status
                stmt[BrowseNodesTable.tags] = row.tags
                stmt[BrowseNodesTable.notes] = row.notes
            }
            if (updated == 0) {
                BrowseNodesTable.insert { stmt ->
                    stmt[BrowseNodesTable.browseCode] = row.browseCode
                    stmt[BrowseNodesTable.parentBrowseCode] = row.parentBrowseCode
                    stmt[BrowseNodesTable.nodeKind] = row.nodeKind
                    stmt[BrowseNodesTable.titleKey] = row.titleKey
                    stmt[BrowseNodesTable.titleLocalized] = row.title
                    stmt[BrowseNodesTable.titleRu] = row.titleRu
                    stmt[BrowseNodesTable.titleEn] = row.titleEn
                    stmt[BrowseNodesTable.targetCategoryCode] = row.targetCategoryCode
                    stmt[BrowseNodesTable.targetType] = row.targetType
                    stmt[BrowseNodesTable.order] = row.order
                    stmt[BrowseNodesTable.availabilityScope] = row.availabilityScope
                    stmt[BrowseNodesTable.iconKey] = row.iconKey
                    stmt[BrowseNodesTable.analyticsKey] = row.analyticsKey
                    stmt[BrowseNodesTable.searchKeywordsRu] = row.searchKeywordsRu
                    stmt[BrowseNodesTable.status] = row.status
                    stmt[BrowseNodesTable.tags] = row.tags
                    stmt[BrowseNodesTable.notes] = row.notes
                }
            }
        }
    }

    private fun syncAliasEntries(
        aliasEntries: List<AliasEntry>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = aliasEntries
            .mapNotNull { entry ->
                val locale = entry.locale.trim()
                val term = entry.term.trim()
                val normalizedTerm = normalizeAliasTerm(entry.normalizedTerm.ifBlank { term })
                val targetCode = entry.targetCode.trim()
                if (locale.isEmpty() || term.isEmpty() || normalizedTerm.isEmpty() || targetCode.isEmpty()) {
                    return@mapNotNull null
                }
                AliasEntryPayload(
                    locale = locale,
                    term = term,
                    normalizedTerm = normalizedTerm,
                    kind = entry.kind.name,
                    targetCode = targetCode,
                    weight = entry.weight.coerceIn(0, 100),
                    matchKind = entry.matchKind.name,
                    isBlocked = entry.isBlocked,
                    source = entry.source.name,
                    notes = entry.notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .distinctBy { "${it.locale}|${it.normalizedTerm}|${it.kind}|${it.targetCode}" }
            .sortedWith(
                compareBy<AliasEntryPayload> { it.locale.lowercase(Locale.ROOT) }
                    .thenBy { it.normalizedTerm }
                    .thenBy { it.kind }
                    .thenBy { it.targetCode },
            )

        if (syncMode.allowsDestructiveOps) {
            AliasEntriesTable.deleteAll()
            if (seedRows.isNotEmpty()) {
                AliasEntriesTable.batchInsert(seedRows) { row ->
                    this[AliasEntriesTable.locale] = row.locale
                    this[AliasEntriesTable.term] = row.term
                    this[AliasEntriesTable.normalizedTerm] = row.normalizedTerm
                    this[AliasEntriesTable.kind] = row.kind
                    this[AliasEntriesTable.targetCode] = row.targetCode
                    this[AliasEntriesTable.weight] = row.weight
                    this[AliasEntriesTable.matchKind] = row.matchKind
                    this[AliasEntriesTable.isBlocked] = row.isBlocked
                    this[AliasEntriesTable.aliasSource] = row.source
                    this[AliasEntriesTable.notes] = row.notes
                }
            }
            return
        }

        val normalizedByLogicalKey = seedRows.associate { row ->
            aliasEntryLogicalKey(
                locale = row.locale,
                term = row.term,
                kind = row.kind,
                targetCode = row.targetCode,
            ) to row.normalizedTerm
        }
        if (normalizedByLogicalKey.isNotEmpty()) {
            val staleRows = AliasEntriesTable
                .selectAll()
                .mapNotNull { existingRow ->
                    val logicalKey = aliasEntryLogicalKey(
                        locale = existingRow[AliasEntriesTable.locale],
                        term = existingRow[AliasEntriesTable.term],
                        kind = existingRow[AliasEntriesTable.kind],
                        targetCode = existingRow[AliasEntriesTable.targetCode],
                    )
                    val expectedNormalizedTerm = normalizedByLogicalKey[logicalKey] ?: return@mapNotNull null
                    val currentNormalizedTerm = existingRow[AliasEntriesTable.normalizedTerm]
                    if (currentNormalizedTerm == expectedNormalizedTerm) {
                        return@mapNotNull null
                    }
                    AliasEntryPrimaryKey(
                        locale = existingRow[AliasEntriesTable.locale],
                        normalizedTerm = currentNormalizedTerm,
                        kind = existingRow[AliasEntriesTable.kind],
                        targetCode = existingRow[AliasEntriesTable.targetCode],
                    )
                }

            staleRows.forEach { row ->
                AliasEntriesTable.deleteWhere {
                    (AliasEntriesTable.locale eq row.locale) and
                        (AliasEntriesTable.normalizedTerm eq row.normalizedTerm) and
                        (AliasEntriesTable.kind eq row.kind) and
                        (AliasEntriesTable.targetCode eq row.targetCode)
                }
            }
        }

        seedRows.forEach { row ->
            val updated = AliasEntriesTable.update({
                (AliasEntriesTable.locale eq row.locale) and
                    (AliasEntriesTable.normalizedTerm eq row.normalizedTerm) and
                    (AliasEntriesTable.kind eq row.kind) and
                    (AliasEntriesTable.targetCode eq row.targetCode)
            }) { stmt ->
                stmt[AliasEntriesTable.term] = row.term
                stmt[AliasEntriesTable.weight] = row.weight
                stmt[AliasEntriesTable.matchKind] = row.matchKind
                stmt[AliasEntriesTable.isBlocked] = row.isBlocked
                stmt[AliasEntriesTable.aliasSource] = row.source
                stmt[AliasEntriesTable.notes] = row.notes
            }
            if (updated == 0) {
                AliasEntriesTable.insert { stmt ->
                    stmt[AliasEntriesTable.locale] = row.locale
                    stmt[AliasEntriesTable.term] = row.term
                    stmt[AliasEntriesTable.normalizedTerm] = row.normalizedTerm
                    stmt[AliasEntriesTable.kind] = row.kind
                    stmt[AliasEntriesTable.targetCode] = row.targetCode
                    stmt[AliasEntriesTable.weight] = row.weight
                    stmt[AliasEntriesTable.matchKind] = row.matchKind
                    stmt[AliasEntriesTable.isBlocked] = row.isBlocked
                    stmt[AliasEntriesTable.aliasSource] = row.source
                    stmt[AliasEntriesTable.notes] = row.notes
                }
            }
        }
    }

    private fun syncGoogleMappings(
        mappings: List<GoogleTaxonomyMapping>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val knownCategoryCodes = CategoriesTable
            .selectAll()
            .map { row -> row[CategoriesTable.code] }
            .toSet()

        val seedRows = mappings
            .mapNotNull { mapping ->
                val canonicalCode = mapping.canonicalCode.trim()
                if (canonicalCode.isEmpty() || canonicalCode !in knownCategoryCodes) return@mapNotNull null
                GoogleMappingPayload(
                    canonicalCode = canonicalCode,
                    mappingType = mapping.mappingType.name,
                    googleIds = mapping.googleIds.filter { id -> id > 0L }.distinct(),
                    googlePaths = mapping.googlePaths
                        .map { path -> path.trim() }
                        .filter { path -> path.isNotEmpty() }
                        .distinct(),
                    notes = mapping.notes?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .distinctBy { it.canonicalCode }
            .sortedBy { it.canonicalCode }

        if (syncMode.allowsDestructiveOps) {
            GoogleTaxonomyMappingsTable.deleteAll()
            if (seedRows.isNotEmpty()) {
                GoogleTaxonomyMappingsTable.batchInsert(seedRows) { row ->
                    this[GoogleTaxonomyMappingsTable.canonicalCode] = row.canonicalCode
                    this[GoogleTaxonomyMappingsTable.mappingType] = row.mappingType
                    this[GoogleTaxonomyMappingsTable.googleIds] = row.googleIds
                    this[GoogleTaxonomyMappingsTable.googlePaths] = row.googlePaths
                    this[GoogleTaxonomyMappingsTable.notes] = row.notes
                }
            }
            return
        }

        seedRows.forEach { row ->
            val updated = GoogleTaxonomyMappingsTable.update({
                GoogleTaxonomyMappingsTable.canonicalCode eq row.canonicalCode
            }) { stmt ->
                stmt[GoogleTaxonomyMappingsTable.mappingType] = row.mappingType
                stmt[GoogleTaxonomyMappingsTable.googleIds] = row.googleIds
                stmt[GoogleTaxonomyMappingsTable.googlePaths] = row.googlePaths
                stmt[GoogleTaxonomyMappingsTable.notes] = row.notes
            }
            if (updated == 0) {
                GoogleTaxonomyMappingsTable.insert { stmt ->
                    stmt[GoogleTaxonomyMappingsTable.canonicalCode] = row.canonicalCode
                    stmt[GoogleTaxonomyMappingsTable.mappingType] = row.mappingType
                    stmt[GoogleTaxonomyMappingsTable.googleIds] = row.googleIds
                    stmt[GoogleTaxonomyMappingsTable.googlePaths] = row.googlePaths
                    stmt[GoogleTaxonomyMappingsTable.notes] = row.notes
                }
            }
        }
    }

    private fun syncAttributeDefs(
        attributes: List<com.example.shoppingassistant.domain.catalog.AttributeDef>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = attributes
            .map { def ->
                def.copy(
                    code = def.code.trim(),
                    title = def.title.trim(),
                    requiredBy = def.requiredBy?.trim()?.takeIf { it.isNotEmpty() },
                    valueDictCode = def.valueDictCode?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .filter { it.code.isNotEmpty() }
            .distinctBy { it.code }
            .sortedBy { it.code }

        val seedCodes = seedRows.map { it.code }.toSet()
        val existingCodes = AttributeDefsTable
            .selectAll()
            .map { row -> row[AttributeDefsTable.code] }
            .toSet()
        val staleCodes = existingCodes - seedCodes
        if (syncMode.allowsDestructiveOps && staleCodes.isNotEmpty()) {
            AttributeDefsTable.deleteWhere { AttributeDefsTable.code inList staleCodes.toList() }
        }

        seedRows.forEach { def ->
            val updated = AttributeDefsTable.update({ AttributeDefsTable.code eq def.code }) { stmt ->
                stmt[title] = def.title
                stmt[dataType] = def.dataType.name
                stmt[requiredForSearch] = def.requiredForSearch
                stmt[requiredForOffer] = def.requiredForOffer
                stmt[requiredForExpress] = def.requiredForExpress
                stmt[requiredBy] = def.requiredBy
                stmt[facetEnabled] = def.facetEnabled
                stmt[multiValued] = def.multiValued
                stmt[valueDictCode] = def.valueDictCode
            }
            if (updated == 0) {
                AttributeDefsTable.insert { stmt ->
                    stmt[code] = def.code
                    stmt[title] = def.title
                    stmt[dataType] = def.dataType.name
                    stmt[requiredForSearch] = def.requiredForSearch
                    stmt[requiredForOffer] = def.requiredForOffer
                    stmt[requiredForExpress] = def.requiredForExpress
                    stmt[requiredBy] = def.requiredBy
                    stmt[facetEnabled] = def.facetEnabled
                    stmt[multiValued] = def.multiValued
                    stmt[valueDictCode] = def.valueDictCode
                }
            }
        }
    }

    private fun syncCategoryAttributes(
        categoryAttributes: List<com.example.shoppingassistant.domain.catalog.CategoryAttribute>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val knownCategoryCodes = CategoriesTable
            .selectAll()
            .map { row -> row[CategoriesTable.code] }
            .toSet()
        val knownAttributeCodes = AttributeDefsTable
            .selectAll()
            .map { row -> row[AttributeDefsTable.code] }
            .toSet()

        val seedRows = categoryAttributes
            .map { attr ->
                attr.copy(
                    categoryCode = attr.categoryCode.trim(),
                    attributeCode = attr.attributeCode.trim(),
                )
            }
            .filter { it.categoryCode in knownCategoryCodes && it.attributeCode in knownAttributeCodes }
            .distinctBy { "${it.categoryCode}|${it.attributeCode}" }
            .sortedWith(compareBy<com.example.shoppingassistant.domain.catalog.CategoryAttribute> { it.categoryCode }.thenBy { it.uiOrder }.thenBy { it.attributeCode })

        if (syncMode.allowsDestructiveOps) {
            CategoryAttributesTable.deleteAll()
            if (seedRows.isNotEmpty()) {
                CategoryAttributesTable.batchInsert(seedRows) { attr ->
                    this[CategoryAttributesTable.categoryCode] = attr.categoryCode
                    this[CategoryAttributesTable.attributeCode] = attr.attributeCode
                    this[CategoryAttributesTable.uiOrder] = attr.uiOrder
                    this[CategoryAttributesTable.isRequired] = attr.isRequiredForCategory
                }
            }
            return
        }

        seedRows.forEach { attr ->
            val updated = CategoryAttributesTable.update({
                (CategoryAttributesTable.categoryCode eq attr.categoryCode) and
                    (CategoryAttributesTable.attributeCode eq attr.attributeCode)
            }) { stmt ->
                stmt[CategoryAttributesTable.uiOrder] = attr.uiOrder
                stmt[CategoryAttributesTable.isRequired] = attr.isRequiredForCategory
            }
            if (updated == 0) {
                CategoryAttributesTable.insert { stmt ->
                    stmt[CategoryAttributesTable.categoryCode] = attr.categoryCode
                    stmt[CategoryAttributesTable.attributeCode] = attr.attributeCode
                    stmt[CategoryAttributesTable.uiOrder] = attr.uiOrder
                    stmt[CategoryAttributesTable.isRequired] = attr.isRequiredForCategory
                }
            }
        }
    }

    private fun syncAttributeValueDict(
        valueDictionaries: List<AttributeValueDict>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val knownAttributeCodes = AttributeDefsTable
            .selectAll()
            .map { row -> row[AttributeDefsTable.code] }
            .toSet()

        val seedRows = valueDictionaries
            .flatMap { dict ->
                dict.entries.map { entry -> dict.attributeCode to entry }
            }
            .mapNotNull { (attributeCode, entry) ->
                val normalizedAttributeCode = attributeCode.trim()
                if (normalizedAttributeCode !in knownAttributeCodes) return@mapNotNull null
                val canonicalCode = entry.canonicalCode.trim()
                val canonicalValue = entry.canonicalValue.trim()
                if (canonicalCode.isEmpty() || canonicalValue.isEmpty()) return@mapNotNull null
                AttributeDictRow(
                    attributeCode = normalizedAttributeCode,
                    canonicalCode = canonicalCode,
                    canonicalValue = canonicalValue,
                    synonyms = entry.synonyms
                        .map { synonym -> synonym.trim() }
                        .filter { synonym -> synonym.isNotEmpty() }
                        .distinct(),
                )
            }
            .distinctBy { row -> "${row.attributeCode}|${row.canonicalCode}" }
            .sortedWith(compareBy<AttributeDictRow> { it.attributeCode }.thenBy { it.canonicalCode })

        if (syncMode.allowsDestructiveOps) {
            AttributeValueDictTable.deleteAll()
            if (seedRows.isNotEmpty()) {
                AttributeValueDictTable.batchInsert(seedRows) { row ->
                    this[AttributeValueDictTable.attributeCode] = row.attributeCode
                    this[AttributeValueDictTable.canonicalCode] = row.canonicalCode
                    this[AttributeValueDictTable.canonicalValue] = row.canonicalValue
                    this[AttributeValueDictTable.synonyms] = row.synonyms
                }
            }
            return
        }

        seedRows.forEach { row ->
            val updated = AttributeValueDictTable.update({
                (AttributeValueDictTable.attributeCode eq row.attributeCode) and
                    (AttributeValueDictTable.canonicalCode eq row.canonicalCode)
            }) { stmt ->
                stmt[AttributeValueDictTable.canonicalValue] = row.canonicalValue
                stmt[AttributeValueDictTable.synonyms] = row.synonyms
            }
            if (updated == 0) {
                AttributeValueDictTable.insert { stmt ->
                    stmt[AttributeValueDictTable.attributeCode] = row.attributeCode
                    stmt[AttributeValueDictTable.canonicalCode] = row.canonicalCode
                    stmt[AttributeValueDictTable.canonicalValue] = row.canonicalValue
                    stmt[AttributeValueDictTable.synonyms] = row.synonyms
                }
            }
        }
    }

    private fun syncConstraints(
        constraints: List<CatalogConstraints>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = constraints
            .map(::normalizeConstraintRow)
            .fold(LinkedHashMap<String, ConstraintPayload>()) { acc, row ->
                val existing = acc[row.identityKey]
                acc[row.identityKey] = if (existing == null) row else existing.mergeWith(row)
                acc
            }
            .values
            .sortedBy { it.identityKey }

        if (seedRows.isEmpty() && syncMode.allowsDestructiveOps) {
            CatalogConstraintsTable.deleteAll()
            return
        }

        val existingByKey = CatalogConstraintsTable
            .selectAll()
            .map { row ->
                ExistingConstraintRow(
                    id = row[CatalogConstraintsTable.id],
                    payload = ConstraintPayload(
                        scope = row[CatalogConstraintsTable.scope],
                        categoryCode = row[CatalogConstraintsTable.categoryCode],
                        brand = row[CatalogConstraintsTable.brand],
                        model = row[CatalogConstraintsTable.model],
                        effectiveFrom = row[CatalogConstraintsTable.effectiveFrom],
                        effectiveTo = row[CatalogConstraintsTable.effectiveTo],
                        attributeConstraints = row[CatalogConstraintsTable.attributeConstraints],
                        compatibilityRules = row[CatalogConstraintsTable.compatibilityRules],
                    ),
                )
            }
            .groupBy { it.payload.identityKey }

        val currentByKey = LinkedHashMap<String, ExistingConstraintRow>()
        val duplicateIdsToDelete = mutableListOf<Long>()
        existingByKey.forEach { (key, rows) ->
            val ordered = rows.sortedBy { it.id }
            currentByKey[key] = ordered.first()
            duplicateIdsToDelete += ordered.drop(1).map { it.id }
        }

        if (syncMode.allowsDestructiveOps && duplicateIdsToDelete.isNotEmpty()) {
            CatalogConstraintsTable.deleteWhere { CatalogConstraintsTable.id inList duplicateIdsToDelete }
        }

        val seedKeys = seedRows.map { it.identityKey }.toSet()
        val staleIdsToDelete = currentByKey
            .filterKeys { it !in seedKeys }
            .values
            .map { it.id }
        if (syncMode.allowsDestructiveOps && staleIdsToDelete.isNotEmpty()) {
            CatalogConstraintsTable.deleteWhere { CatalogConstraintsTable.id inList staleIdsToDelete }
        }

        val toInsert = mutableListOf<ConstraintPayload>()
        seedRows.forEach { seedRow ->
            val current = currentByKey[seedRow.identityKey]?.payload
            if (current == null) {
                toInsert += seedRow
                return@forEach
            }
            if (current != seedRow) {
                val id = currentByKey[seedRow.identityKey]?.id ?: return@forEach
                CatalogConstraintsTable.update({ CatalogConstraintsTable.id eq id }) { stmt ->
                    stmt[CatalogConstraintsTable.scope] = seedRow.scope
                    stmt[CatalogConstraintsTable.categoryCode] = seedRow.categoryCode
                    stmt[CatalogConstraintsTable.brand] = seedRow.brand
                    stmt[CatalogConstraintsTable.model] = seedRow.model
                    stmt[CatalogConstraintsTable.effectiveFrom] = seedRow.effectiveFrom
                    stmt[CatalogConstraintsTable.effectiveTo] = seedRow.effectiveTo
                    stmt[CatalogConstraintsTable.attributeConstraints] = seedRow.attributeConstraints
                    stmt[CatalogConstraintsTable.compatibilityRules] = seedRow.compatibilityRules
                }
            }
        }

        if (toInsert.isNotEmpty()) {
            CatalogConstraintsTable.batchInsert(toInsert) { row ->
                this[CatalogConstraintsTable.scope] = row.scope
                this[CatalogConstraintsTable.categoryCode] = row.categoryCode
                this[CatalogConstraintsTable.brand] = row.brand
                this[CatalogConstraintsTable.model] = row.model
                this[CatalogConstraintsTable.effectiveFrom] = row.effectiveFrom
                this[CatalogConstraintsTable.effectiveTo] = row.effectiveTo
                this[CatalogConstraintsTable.attributeConstraints] = row.attributeConstraints
                this[CatalogConstraintsTable.compatibilityRules] = row.compatibilityRules
            }
        }
    }

    private fun syncFacetDefinitions(
        definitions: List<FacetDefinition>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = definitions
            .map { definition ->
                definition.copy(
                    facetKey = definition.facetKey.trim(),
                    title = localizedTextOf(
                        "ru" to definition.title.storageRu(definition.facetKey),
                        "en" to definition.title.storageEn(),
                    ),
                    effectiveFrom = definition.effectiveFrom?.trim()?.takeIf { it.isNotEmpty() },
                    effectiveTo = definition.effectiveTo?.trim()?.takeIf { it.isNotEmpty() },
                    appliesToCategoryCodes = definition.appliesToCategoryCodes
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct(),
                )
            }
            .filter { it.facetKey.isNotEmpty() }
            .distinctBy { it.facetKey }
            .sortedBy { it.facetKey }

        val seedKeys = seedRows.map { it.facetKey }.toSet()
        val existingRows = FacetDefinitionsTable.selectAll().map { row ->
            ExistingFacetDefinitionRow(
                facetKey = row[FacetDefinitionsTable.facetKey],
                payload = FacetDefinition(
                    facetKey = row[FacetDefinitionsTable.facetKey],
                    title = localizedTextFromStorage(
                        localized = row[FacetDefinitionsTable.titleLocalized],
                        titleRu = row[FacetDefinitionsTable.titleRu],
                        titleEn = row[FacetDefinitionsTable.titleEn],
                    ),
                    valueType = com.example.shoppingassistant.domain.facet.FacetDataType.valueOf(row[FacetDefinitionsTable.valueType]),
                    appliesToCategoryCodes = row[FacetDefinitionsTable.appliesToCategoryCodes],
                    source = com.example.shoppingassistant.domain.facet.FacetValueSource.valueOf(row[FacetDefinitionsTable.valueSource]),
                    effectiveFrom = row[FacetDefinitionsTable.effectiveFrom],
                    effectiveTo = row[FacetDefinitionsTable.effectiveTo],
                    ui = com.example.shoppingassistant.domain.facet.FacetUiConfig(
                        order = row[FacetDefinitionsTable.uiOrder],
                        pinned = row[FacetDefinitionsTable.uiPinned],
                        hidden = row[FacetDefinitionsTable.uiHidden],
                        format = row[FacetDefinitionsTable.uiFormat],
                    ),
                ),
            )
        }.associateBy { it.facetKey }

        val staleKeys = existingRows.keys - seedKeys
        if (syncMode.allowsDestructiveOps && staleKeys.isNotEmpty()) {
            FacetDefinitionsTable.deleteWhere { FacetDefinitionsTable.facetKey inList staleKeys.toList() }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.facetKey]?.payload
            if (existing == null) {
                FacetDefinitionsTable.insert { stmt ->
                    stmt[FacetDefinitionsTable.facetKey] = seed.facetKey
                    stmt[FacetDefinitionsTable.titleLocalized] = seed.title
                    stmt[FacetDefinitionsTable.titleRu] = seed.title.storageRu(seed.facetKey)
                    stmt[FacetDefinitionsTable.titleEn] = seed.title.storageEn()
                    stmt[FacetDefinitionsTable.valueType] = seed.valueType.name
                    stmt[FacetDefinitionsTable.valueSource] = seed.source.name
                    stmt[FacetDefinitionsTable.effectiveFrom] = seed.effectiveFrom
                    stmt[FacetDefinitionsTable.effectiveTo] = seed.effectiveTo
                    stmt[FacetDefinitionsTable.appliesToCategoryCodes] = seed.appliesToCategoryCodes
                    stmt[FacetDefinitionsTable.uiOrder] = seed.ui.order
                    stmt[FacetDefinitionsTable.uiPinned] = seed.ui.pinned
                    stmt[FacetDefinitionsTable.uiHidden] = seed.ui.hidden
                    stmt[FacetDefinitionsTable.uiFormat] = seed.ui.format
                }
            } else if (existing != seed) {
                FacetDefinitionsTable.update({ FacetDefinitionsTable.facetKey eq seed.facetKey }) { stmt ->
                    stmt[FacetDefinitionsTable.titleLocalized] = seed.title
                    stmt[FacetDefinitionsTable.titleRu] = seed.title.storageRu(seed.facetKey)
                    stmt[FacetDefinitionsTable.titleEn] = seed.title.storageEn()
                    stmt[FacetDefinitionsTable.valueType] = seed.valueType.name
                    stmt[FacetDefinitionsTable.valueSource] = seed.source.name
                    stmt[FacetDefinitionsTable.effectiveFrom] = seed.effectiveFrom
                    stmt[FacetDefinitionsTable.effectiveTo] = seed.effectiveTo
                    stmt[FacetDefinitionsTable.appliesToCategoryCodes] = seed.appliesToCategoryCodes
                    stmt[FacetDefinitionsTable.uiOrder] = seed.ui.order
                    stmt[FacetDefinitionsTable.uiPinned] = seed.ui.pinned
                    stmt[FacetDefinitionsTable.uiHidden] = seed.ui.hidden
                    stmt[FacetDefinitionsTable.uiFormat] = seed.ui.format
                }
            }
        }
    }

    private fun syncFacetPresets(
        presets: List<FacetPreset>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = presets
            .map { preset ->
                preset.copy(
                    presetCode = preset.presetCode.trim(),
                    categoryCode = preset.categoryCode.trim(),
                    title = localizedTextOf(
                        "ru" to preset.title.storageRu(preset.presetCode),
                        "en" to preset.title.storageEn(),
                    ),
                    effectiveFrom = preset.effectiveFrom?.trim()?.takeIf { it.isNotEmpty() },
                    effectiveTo = preset.effectiveTo?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .filter { it.presetCode.isNotEmpty() && it.categoryCode.isNotEmpty() }
            .distinctBy { it.presetCode }
            .sortedBy { it.presetCode }

        val seedCodes = seedRows.map { it.presetCode }.toSet()
        val existingRows = FacetPresetsTable.selectAll().map { row ->
            ExistingFacetPresetRow(
                presetCode = row[FacetPresetsTable.presetCode],
                payload = FacetPreset(
                    presetCode = row[FacetPresetsTable.presetCode],
                    categoryCode = row[FacetPresetsTable.categoryCode],
                    title = localizedTextFromStorage(
                        localized = row[FacetPresetsTable.titleLocalized],
                        titleRu = row[FacetPresetsTable.titleRu],
                        titleEn = row[FacetPresetsTable.titleEn],
                    ),
                    order = row[FacetPresetsTable.order],
                    effectiveFrom = row[FacetPresetsTable.effectiveFrom],
                    effectiveTo = row[FacetPresetsTable.effectiveTo],
                    rules = row[FacetPresetsTable.rules],
                    notes = row[FacetPresetsTable.notes],
                ),
            )
        }.associateBy { it.presetCode }

        val staleCodes = existingRows.keys - seedCodes
        if (syncMode.allowsDestructiveOps && staleCodes.isNotEmpty()) {
            FacetPresetsTable.deleteWhere { FacetPresetsTable.presetCode inList staleCodes.toList() }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.presetCode]?.payload
            if (existing == null) {
                FacetPresetsTable.insert { stmt ->
                    stmt[FacetPresetsTable.presetCode] = seed.presetCode
                    stmt[FacetPresetsTable.categoryCode] = seed.categoryCode
                    stmt[FacetPresetsTable.titleLocalized] = seed.title
                    stmt[FacetPresetsTable.titleRu] = seed.title.storageRu(seed.presetCode)
                    stmt[FacetPresetsTable.titleEn] = seed.title.storageEn()
                    stmt[FacetPresetsTable.order] = seed.order
                    stmt[FacetPresetsTable.effectiveFrom] = seed.effectiveFrom
                    stmt[FacetPresetsTable.effectiveTo] = seed.effectiveTo
                    stmt[FacetPresetsTable.rules] = seed.rules
                    stmt[FacetPresetsTable.notes] = seed.notes
                }
            } else if (existing != seed) {
                FacetPresetsTable.update({ FacetPresetsTable.presetCode eq seed.presetCode }) { stmt ->
                    stmt[FacetPresetsTable.categoryCode] = seed.categoryCode
                    stmt[FacetPresetsTable.titleLocalized] = seed.title
                    stmt[FacetPresetsTable.titleRu] = seed.title.storageRu(seed.presetCode)
                    stmt[FacetPresetsTable.titleEn] = seed.title.storageEn()
                    stmt[FacetPresetsTable.order] = seed.order
                    stmt[FacetPresetsTable.effectiveFrom] = seed.effectiveFrom
                    stmt[FacetPresetsTable.effectiveTo] = seed.effectiveTo
                    stmt[FacetPresetsTable.rules] = seed.rules
                    stmt[FacetPresetsTable.notes] = seed.notes
                }
            }
        }
    }

    private fun syncFacetCollections(
        collections: List<FacetCollection>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = collections
            .map { collection ->
                collection.copy(
                    collectionCode = collection.collectionCode.trim(),
                    categoryCode = collection.categoryCode.trim(),
                    title = localizedTextOf(
                        "ru" to collection.title.storageRu(collection.collectionCode),
                        "en" to collection.title.storageEn(),
                    ),
                    browseCode = collection.browseCode?.trim()?.takeIf { it.isNotEmpty() },
                    presetCode = collection.presetCode?.trim()?.takeIf { it.isNotEmpty() },
                    tags = collection.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                )
            }
            .filter { it.collectionCode.isNotEmpty() && it.categoryCode.isNotEmpty() }
            .distinctBy { it.collectionCode }
            .sortedBy { it.collectionCode }

        val seedCodes = seedRows.map { it.collectionCode }.toSet()
        val existingRows = FacetCollectionsTable.selectAll().map { row ->
            ExistingFacetCollectionRow(
                collectionCode = row[FacetCollectionsTable.collectionCode],
                payload = FacetCollection(
                    collectionCode = row[FacetCollectionsTable.collectionCode],
                    categoryCode = row[FacetCollectionsTable.categoryCode],
                    title = localizedTextFromStorage(
                        localized = row[FacetCollectionsTable.titleLocalized],
                        titleRu = row[FacetCollectionsTable.titleRu],
                        titleEn = row[FacetCollectionsTable.titleEn],
                    ),
                    browseCode = row[FacetCollectionsTable.browseCode],
                    presetCode = row[FacetCollectionsTable.presetCode],
                    order = row[FacetCollectionsTable.order],
                    tags = row[FacetCollectionsTable.tags],
                    notes = row[FacetCollectionsTable.notes],
                ),
            )
        }.associateBy { it.collectionCode }

        val staleCodes = existingRows.keys - seedCodes
        if (syncMode.allowsDestructiveOps && staleCodes.isNotEmpty()) {
            FacetCollectionsTable.deleteWhere { FacetCollectionsTable.collectionCode inList staleCodes.toList() }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.collectionCode]?.payload
            if (existing == null) {
                FacetCollectionsTable.insert { stmt ->
                    stmt[FacetCollectionsTable.collectionCode] = seed.collectionCode
                    stmt[FacetCollectionsTable.categoryCode] = seed.categoryCode
                    stmt[FacetCollectionsTable.titleLocalized] = seed.title
                    stmt[FacetCollectionsTable.titleRu] = seed.title.storageRu(seed.collectionCode)
                    stmt[FacetCollectionsTable.titleEn] = seed.title.storageEn()
                    stmt[FacetCollectionsTable.browseCode] = seed.browseCode
                    stmt[FacetCollectionsTable.presetCode] = seed.presetCode
                    stmt[FacetCollectionsTable.order] = seed.order
                    stmt[FacetCollectionsTable.tags] = seed.tags
                    stmt[FacetCollectionsTable.notes] = seed.notes
                }
            } else if (existing != seed) {
                FacetCollectionsTable.update({ FacetCollectionsTable.collectionCode eq seed.collectionCode }) { stmt ->
                    stmt[FacetCollectionsTable.categoryCode] = seed.categoryCode
                    stmt[FacetCollectionsTable.titleLocalized] = seed.title
                    stmt[FacetCollectionsTable.titleRu] = seed.title.storageRu(seed.collectionCode)
                    stmt[FacetCollectionsTable.titleEn] = seed.title.storageEn()
                    stmt[FacetCollectionsTable.browseCode] = seed.browseCode
                    stmt[FacetCollectionsTable.presetCode] = seed.presetCode
                    stmt[FacetCollectionsTable.order] = seed.order
                    stmt[FacetCollectionsTable.tags] = seed.tags
                    stmt[FacetCollectionsTable.notes] = seed.notes
                }
            }
        }
    }

    private fun syncStage40Contract(
        syncMode: CatalogSeedSyncMode,
    ) {
        val immutableDoc = CatalogSeed.stage40ImmutableSchema
        val normalizationDoc = CatalogSeed.stage40NormalizationContract
        val dedupDoc = CatalogSeed.stage40DedupKeys
        val typedConstraintsDoc = CatalogSeed.stage40TypedConstraints

        syncStage40Meta(immutableDoc, syncMode = syncMode)
        syncStage40ImmutableAttributes(immutableDoc.attributes, syncMode = syncMode)
        syncStage40NormalizationRules(normalizationDoc.rules, syncMode = syncMode)
        syncStage40DedupTemplates(dedupDoc.templates, syncMode = syncMode)
        syncStage40TypedConstraints(typedConstraintsDoc.constraints, syncMode = syncMode)
    }

    private fun syncStage40Meta(
        immutableDoc: com.example.shoppingassistant.domain.catalog.Stage40ImmutableSchemaDocument,
        syncMode: CatalogSeedSyncMode,
    ) {
        val stage = immutableDoc.stage.trim()
        if (stage.isEmpty()) return

        val seed = Stage40MetaPayload(
            stage = stage,
            schemaVersion = immutableDoc.schemaVersion.trim(),
            stage22DataVersion = immutableDoc.generatedFrom.stage22DataVersion.trim(),
            stage22SchemaVersion = immutableDoc.generatedFrom.stage22SchemaVersion.trim(),
            stage22GeneratedAt = immutableDoc.generatedFrom.stage22GeneratedAt.trim(),
            stage3Version = immutableDoc.generatedFrom.stage3Version.trim(),
        )

        val existingRows = CatalogStage4ContractMetaTable.selectAll().map { row ->
            Stage40MetaPayload(
                stage = row[CatalogStage4ContractMetaTable.stage],
                schemaVersion = row[CatalogStage4ContractMetaTable.schemaVersion],
                stage22DataVersion = row[CatalogStage4ContractMetaTable.stage22DataVersion],
                stage22SchemaVersion = row[CatalogStage4ContractMetaTable.stage22SchemaVersion],
                stage22GeneratedAt = row[CatalogStage4ContractMetaTable.stage22GeneratedAt],
                stage3Version = row[CatalogStage4ContractMetaTable.stage3Version],
            )
        }.associateBy { it.stage }

        val staleStages = existingRows.keys - setOf(seed.stage)
        if (syncMode.allowsDestructiveOps && staleStages.isNotEmpty()) {
            CatalogStage4ContractMetaTable.deleteWhere { CatalogStage4ContractMetaTable.stage inList staleStages.toList() }
        }

        val existing = existingRows[seed.stage]
        if (existing == null) {
            CatalogStage4ContractMetaTable.insert { stmt ->
                stmt[CatalogStage4ContractMetaTable.stage] = seed.stage
                stmt[CatalogStage4ContractMetaTable.schemaVersion] = seed.schemaVersion
                stmt[CatalogStage4ContractMetaTable.stage22DataVersion] = seed.stage22DataVersion
                stmt[CatalogStage4ContractMetaTable.stage22SchemaVersion] = seed.stage22SchemaVersion
                stmt[CatalogStage4ContractMetaTable.stage22GeneratedAt] = seed.stage22GeneratedAt
                stmt[CatalogStage4ContractMetaTable.stage3Version] = seed.stage3Version
                stmt[CatalogStage4ContractMetaTable.updatedAt] = System.currentTimeMillis()
            }
        } else if (existing != seed) {
            CatalogStage4ContractMetaTable.update({ CatalogStage4ContractMetaTable.stage eq seed.stage }) { stmt ->
                stmt[CatalogStage4ContractMetaTable.schemaVersion] = seed.schemaVersion
                stmt[CatalogStage4ContractMetaTable.stage22DataVersion] = seed.stage22DataVersion
                stmt[CatalogStage4ContractMetaTable.stage22SchemaVersion] = seed.stage22SchemaVersion
                stmt[CatalogStage4ContractMetaTable.stage22GeneratedAt] = seed.stage22GeneratedAt
                stmt[CatalogStage4ContractMetaTable.stage3Version] = seed.stage3Version
                stmt[CatalogStage4ContractMetaTable.updatedAt] = System.currentTimeMillis()
            }
        }
    }

    private fun syncStage40ImmutableAttributes(
        attributes: List<com.example.shoppingassistant.domain.catalog.Stage40ImmutableAttribute>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = attributes
            .map { attribute ->
                Stage40ImmutableAttributePayload(
                    attributeCode = attribute.attributeCode.trim(),
                    valueType = attribute.valueType.name,
                    valueSetType = attribute.valueSetType.name,
                    unit = attribute.unit?.trim()?.takeIf { it.isNotEmpty() },
                    isIdentity = attribute.isIdentity,
                    isFacet = attribute.isFacet,
                    normalization = attribute.normalization?.trim()?.takeIf { it.isNotEmpty() },
                    dictionaryRequired = attribute.dictionaryRequired,
                    immutableFingerprint = attribute.immutableFingerprint.trim(),
                )
            }
            .filter { it.attributeCode.isNotEmpty() }
            .distinctBy { it.attributeCode }
            .sortedBy { it.attributeCode }

        val seedCodes = seedRows.map { it.attributeCode }.toSet()
        val existingRows = CatalogStage4ImmutableAttributesTable.selectAll().map { row ->
            Stage40ImmutableAttributePayload(
                attributeCode = row[CatalogStage4ImmutableAttributesTable.attributeCode],
                valueType = row[CatalogStage4ImmutableAttributesTable.valueType],
                valueSetType = row[CatalogStage4ImmutableAttributesTable.valueSetType],
                unit = row[CatalogStage4ImmutableAttributesTable.unit],
                isIdentity = row[CatalogStage4ImmutableAttributesTable.isIdentity],
                isFacet = row[CatalogStage4ImmutableAttributesTable.isFacet],
                normalization = row[CatalogStage4ImmutableAttributesTable.normalization],
                dictionaryRequired = row[CatalogStage4ImmutableAttributesTable.dictionaryRequired],
                immutableFingerprint = row[CatalogStage4ImmutableAttributesTable.immutableFingerprint],
            )
        }.associateBy { it.attributeCode }

        val staleCodes = existingRows.keys - seedCodes
        if (syncMode.allowsDestructiveOps && staleCodes.isNotEmpty()) {
            CatalogStage4ImmutableAttributesTable.deleteWhere {
                CatalogStage4ImmutableAttributesTable.attributeCode inList staleCodes.toList()
            }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.attributeCode]
            if (existing == null) {
                CatalogStage4ImmutableAttributesTable.insert { stmt ->
                    stmt[CatalogStage4ImmutableAttributesTable.attributeCode] = seed.attributeCode
                    stmt[CatalogStage4ImmutableAttributesTable.valueType] = seed.valueType
                    stmt[CatalogStage4ImmutableAttributesTable.valueSetType] = seed.valueSetType
                    stmt[CatalogStage4ImmutableAttributesTable.unit] = seed.unit
                    stmt[CatalogStage4ImmutableAttributesTable.isIdentity] = seed.isIdentity
                    stmt[CatalogStage4ImmutableAttributesTable.isFacet] = seed.isFacet
                    stmt[CatalogStage4ImmutableAttributesTable.normalization] = seed.normalization
                    stmt[CatalogStage4ImmutableAttributesTable.dictionaryRequired] = seed.dictionaryRequired
                    stmt[CatalogStage4ImmutableAttributesTable.immutableFingerprint] = seed.immutableFingerprint
                }
            } else if (existing != seed) {
                CatalogStage4ImmutableAttributesTable.update({
                    CatalogStage4ImmutableAttributesTable.attributeCode eq seed.attributeCode
                }) { stmt ->
                    stmt[CatalogStage4ImmutableAttributesTable.valueType] = seed.valueType
                    stmt[CatalogStage4ImmutableAttributesTable.valueSetType] = seed.valueSetType
                    stmt[CatalogStage4ImmutableAttributesTable.unit] = seed.unit
                    stmt[CatalogStage4ImmutableAttributesTable.isIdentity] = seed.isIdentity
                    stmt[CatalogStage4ImmutableAttributesTable.isFacet] = seed.isFacet
                    stmt[CatalogStage4ImmutableAttributesTable.normalization] = seed.normalization
                    stmt[CatalogStage4ImmutableAttributesTable.dictionaryRequired] = seed.dictionaryRequired
                    stmt[CatalogStage4ImmutableAttributesTable.immutableFingerprint] = seed.immutableFingerprint
                }
            }
        }
    }

    private fun syncStage40NormalizationRules(
        rules: List<com.example.shoppingassistant.domain.catalog.Stage40NormalizationRule>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = rules
            .map { rule ->
                Stage40NormalizationRulePayload(
                    attributeCode = rule.attributeCode.trim(),
                    normalization = rule.normalization.trim(),
                    valueSetType = rule.valueSetType.name,
                    dictionaryBacked = rule.dictionaryBacked,
                    acceptsFreeText = rule.acceptsFreeText,
                    canonicalSource = rule.canonicalSource.trim(),
                    dedupTokenMode = rule.dedupTokenMode.name,
                )
            }
            .filter { it.attributeCode.isNotEmpty() }
            .distinctBy { it.attributeCode }
            .sortedBy { it.attributeCode }

        val seedCodes = seedRows.map { it.attributeCode }.toSet()
        val existingRows = CatalogStage4NormalizationRulesTable.selectAll().map { row ->
            Stage40NormalizationRulePayload(
                attributeCode = row[CatalogStage4NormalizationRulesTable.attributeCode],
                normalization = row[CatalogStage4NormalizationRulesTable.normalization],
                valueSetType = row[CatalogStage4NormalizationRulesTable.valueSetType],
                dictionaryBacked = row[CatalogStage4NormalizationRulesTable.dictionaryBacked],
                acceptsFreeText = row[CatalogStage4NormalizationRulesTable.acceptsFreeText],
                canonicalSource = row[CatalogStage4NormalizationRulesTable.canonicalSource],
                dedupTokenMode = row[CatalogStage4NormalizationRulesTable.dedupTokenMode],
            )
        }.associateBy { it.attributeCode }

        val staleCodes = existingRows.keys - seedCodes
        if (syncMode.allowsDestructiveOps && staleCodes.isNotEmpty()) {
            CatalogStage4NormalizationRulesTable.deleteWhere {
                CatalogStage4NormalizationRulesTable.attributeCode inList staleCodes.toList()
            }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.attributeCode]
            if (existing == null) {
                CatalogStage4NormalizationRulesTable.insert { stmt ->
                    stmt[CatalogStage4NormalizationRulesTable.attributeCode] = seed.attributeCode
                    stmt[CatalogStage4NormalizationRulesTable.normalization] = seed.normalization
                    stmt[CatalogStage4NormalizationRulesTable.valueSetType] = seed.valueSetType
                    stmt[CatalogStage4NormalizationRulesTable.dictionaryBacked] = seed.dictionaryBacked
                    stmt[CatalogStage4NormalizationRulesTable.acceptsFreeText] = seed.acceptsFreeText
                    stmt[CatalogStage4NormalizationRulesTable.canonicalSource] = seed.canonicalSource
                    stmt[CatalogStage4NormalizationRulesTable.dedupTokenMode] = seed.dedupTokenMode
                }
            } else if (existing != seed) {
                CatalogStage4NormalizationRulesTable.update({
                    CatalogStage4NormalizationRulesTable.attributeCode eq seed.attributeCode
                }) { stmt ->
                    stmt[CatalogStage4NormalizationRulesTable.normalization] = seed.normalization
                    stmt[CatalogStage4NormalizationRulesTable.valueSetType] = seed.valueSetType
                    stmt[CatalogStage4NormalizationRulesTable.dictionaryBacked] = seed.dictionaryBacked
                    stmt[CatalogStage4NormalizationRulesTable.acceptsFreeText] = seed.acceptsFreeText
                    stmt[CatalogStage4NormalizationRulesTable.canonicalSource] = seed.canonicalSource
                    stmt[CatalogStage4NormalizationRulesTable.dedupTokenMode] = seed.dedupTokenMode
                }
            }
        }
    }

    private fun syncStage40DedupTemplates(
        templates: List<com.example.shoppingassistant.domain.catalog.Stage40DedupTemplate>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = templates
            .map { template ->
                Stage40DedupTemplatePayload(
                    entity = template.entity.name,
                    templateExpr = template.template.trim(),
                    fields = template.fields
                        .map { field -> field.trim() }
                        .filter { field -> field.isNotEmpty() },
                    description = template.description.trim(),
                )
            }
            .filter { it.entity.isNotEmpty() && it.templateExpr.isNotEmpty() }
            .distinctBy { it.entity }
            .sortedBy { it.entity }

        val seedEntities = seedRows.map { it.entity }.toSet()
        val existingRows = CatalogStage4DedupTemplatesTable.selectAll().map { row ->
            Stage40DedupTemplatePayload(
                entity = row[CatalogStage4DedupTemplatesTable.entity],
                templateExpr = row[CatalogStage4DedupTemplatesTable.templateExpr],
                fields = row[CatalogStage4DedupTemplatesTable.fieldNames],
                description = row[CatalogStage4DedupTemplatesTable.description],
            )
        }.associateBy { it.entity }

        val staleEntities = existingRows.keys - seedEntities
        if (syncMode.allowsDestructiveOps && staleEntities.isNotEmpty()) {
            CatalogStage4DedupTemplatesTable.deleteWhere {
                CatalogStage4DedupTemplatesTable.entity inList staleEntities.toList()
            }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.entity]
            if (existing == null) {
                CatalogStage4DedupTemplatesTable.insert { stmt ->
                    stmt[CatalogStage4DedupTemplatesTable.entity] = seed.entity
                    stmt[CatalogStage4DedupTemplatesTable.templateExpr] = seed.templateExpr
                    stmt[CatalogStage4DedupTemplatesTable.fieldNames] = seed.fields
                    stmt[CatalogStage4DedupTemplatesTable.description] = seed.description
                }
            } else if (existing != seed) {
                CatalogStage4DedupTemplatesTable.update({
                    CatalogStage4DedupTemplatesTable.entity eq seed.entity
                }) { stmt ->
                    stmt[CatalogStage4DedupTemplatesTable.templateExpr] = seed.templateExpr
                    stmt[CatalogStage4DedupTemplatesTable.fieldNames] = seed.fields
                    stmt[CatalogStage4DedupTemplatesTable.description] = seed.description
                }
            }
        }
    }

    private fun syncStage40TypedConstraints(
        constraints: List<com.example.shoppingassistant.domain.catalog.Stage40TypedConstraint>,
        syncMode: CatalogSeedSyncMode,
    ) {
        val seedRows = constraints
            .mapNotNull { constraint ->
                val attributeCode = constraint.attributeCode.trim()
                if (attributeCode.isEmpty()) return@mapNotNull null
                Stage40TypedConstraintPayload(
                    attributeCode = attributeCode,
                    valueType = constraint.valueType.name,
                    enumOnly = constraint.enumOnly,
                    expectedUnit = constraint.unit?.trim()?.takeIf { it.isNotEmpty() },
                    regexPattern = constraint.regex?.trim()?.takeIf { it.isNotEmpty() },
                    minValue = constraint.minValue,
                    maxValue = constraint.maxValue,
                    requiredIf = normalizeStage40RequiredIfRules(constraint.requiredIf),
                )
            }
            .distinctBy { it.attributeCode }
            .sortedBy { it.attributeCode }

        val seedCodes = seedRows.map { it.attributeCode }.toSet()
        val existingRows = CatalogStage4TypedConstraintsTable.selectAll().map { row ->
            Stage40TypedConstraintPayload(
                attributeCode = row[CatalogStage4TypedConstraintsTable.attributeCode],
                valueType = row[CatalogStage4TypedConstraintsTable.valueType],
                enumOnly = row[CatalogStage4TypedConstraintsTable.enumOnly],
                expectedUnit = row[CatalogStage4TypedConstraintsTable.expectedUnit],
                regexPattern = row[CatalogStage4TypedConstraintsTable.regexPattern],
                minValue = row[CatalogStage4TypedConstraintsTable.minValue],
                maxValue = row[CatalogStage4TypedConstraintsTable.maxValue],
                requiredIf = normalizeStage40RequiredIfRules(
                    row[CatalogStage4TypedConstraintsTable.requiredIf],
                ),
            )
        }.associateBy { it.attributeCode }

        val staleCodes = existingRows.keys - seedCodes
        if (syncMode.allowsDestructiveOps && staleCodes.isNotEmpty()) {
            CatalogStage4TypedConstraintsTable.deleteWhere {
                CatalogStage4TypedConstraintsTable.attributeCode inList staleCodes.toList()
            }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.attributeCode]
            if (existing == null) {
                CatalogStage4TypedConstraintsTable.insert { stmt ->
                    stmt[CatalogStage4TypedConstraintsTable.attributeCode] = seed.attributeCode
                    stmt[CatalogStage4TypedConstraintsTable.valueType] = seed.valueType
                    stmt[CatalogStage4TypedConstraintsTable.enumOnly] = seed.enumOnly
                    stmt[CatalogStage4TypedConstraintsTable.expectedUnit] = seed.expectedUnit
                    stmt[CatalogStage4TypedConstraintsTable.regexPattern] = seed.regexPattern
                    stmt[CatalogStage4TypedConstraintsTable.minValue] = seed.minValue
                    stmt[CatalogStage4TypedConstraintsTable.maxValue] = seed.maxValue
                    stmt[CatalogStage4TypedConstraintsTable.requiredIf] = seed.requiredIf
                    stmt[CatalogStage4TypedConstraintsTable.updatedAt] = System.currentTimeMillis()
                }
            } else if (existing != seed) {
                CatalogStage4TypedConstraintsTable.update({
                    CatalogStage4TypedConstraintsTable.attributeCode eq seed.attributeCode
                }) { stmt ->
                    stmt[CatalogStage4TypedConstraintsTable.valueType] = seed.valueType
                    stmt[CatalogStage4TypedConstraintsTable.enumOnly] = seed.enumOnly
                    stmt[CatalogStage4TypedConstraintsTable.expectedUnit] = seed.expectedUnit
                    stmt[CatalogStage4TypedConstraintsTable.regexPattern] = seed.regexPattern
                    stmt[CatalogStage4TypedConstraintsTable.minValue] = seed.minValue
                    stmt[CatalogStage4TypedConstraintsTable.maxValue] = seed.maxValue
                    stmt[CatalogStage4TypedConstraintsTable.requiredIf] = seed.requiredIf
                    stmt[CatalogStage4TypedConstraintsTable.updatedAt] = System.currentTimeMillis()
                }
            }
        }
    }

    private fun normalizeStage40RequiredIfRules(
        rules: List<com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule>,
    ): List<com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule> =
        rules
            .mapNotNull { rule ->
                val categoryCode = rule.categoryCode.trim().uppercase()
                if (categoryCode.isEmpty()) return@mapNotNull null
                val whenAll = rule.whenAll
                    .mapNotNull { condition ->
                        val attributeCode = condition.attributeCode.trim()
                        val values = condition.values
                            .map { value -> value.trim() }
                            .filter { value -> value.isNotEmpty() }
                        if (attributeCode.isEmpty() || values.isEmpty()) {
                            null
                        } else {
                            com.example.shoppingassistant.domain.catalog.Stage40RequiredIfCondition(
                                attributeCode = attributeCode,
                                op = condition.op,
                                values = values,
                            )
                        }
                    }
                    .sortedWith(
                        compareBy<com.example.shoppingassistant.domain.catalog.Stage40RequiredIfCondition> { it.attributeCode }
                            .thenBy { it.op.name }
                            .thenBy { it.values.joinToString("|") },
                    )
                if (whenAll.isEmpty()) return@mapNotNull null
                com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule(
                    categoryCode = categoryCode,
                    whenAll = whenAll,
                )
            }
            .distinct()
            .sortedWith(
                compareBy<com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule> { it.categoryCode }
                    .thenBy { it.whenAll.joinToString("|") { c -> "${c.attributeCode}:${c.op}:${c.values.joinToString(",")}" } },
            )

    private fun normalizeConstraintRow(constraint: CatalogConstraints): ConstraintPayload {
        val scope = constraint.scope.name
        val categoryCode = constraint.categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        val brand = constraint.brand?.trim()?.takeIf { it.isNotEmpty() }
        val model = constraint.model?.trim()?.takeIf { it.isNotEmpty() }
        val effectiveFrom = constraint.effectiveFrom?.trim()?.takeIf { it.isNotEmpty() }
        val effectiveTo = constraint.effectiveTo?.trim()?.takeIf { it.isNotEmpty() }
        return ConstraintPayload(
            scope = scope,
            categoryCode = categoryCode,
            brand = brand,
            model = model,
            effectiveFrom = effectiveFrom,
            effectiveTo = effectiveTo,
            attributeConstraints = constraint.attributeConstraints,
            compatibilityRules = constraint.compatibilityRules,
        )
    }

    private data class ExistingConstraintRow(
        val id: Long,
        val payload: ConstraintPayload,
    )

    private data class ExistingFacetDefinitionRow(
        val facetKey: String,
        val payload: FacetDefinition,
    )

    private data class ExistingFacetPresetRow(
        val presetCode: String,
        val payload: FacetPreset,
    )

    private data class ExistingFacetCollectionRow(
        val collectionCode: String,
        val payload: FacetCollection,
    )

    private data class AttributeDictRow(
        val attributeCode: String,
        val canonicalCode: String,
        val canonicalValue: String,
        val synonyms: List<String>,
    )

    private data class CategoryAliasPayload(
        val alias: String,
        val categoryCode: String,
    )

    private data class BrowseNodePayload(
        val browseCode: String,
        val parentBrowseCode: String?,
        val nodeKind: String,
        val titleKey: String?,
        val title: LocalizedText,
        val titleRu: String,
        val titleEn: String?,
        val targetCategoryCode: String?,
        val targetType: String?,
        val order: Int,
        val availabilityScope: String,
        val iconKey: String?,
        val analyticsKey: String?,
        val searchKeywordsRu: List<String>,
        val status: String,
        val tags: List<String>,
        val notes: String?,
    )

    private data class AliasEntryPayload(
        val locale: String,
        val term: String,
        val normalizedTerm: String,
        val kind: String,
        val targetCode: String,
        val weight: Int,
        val matchKind: String,
        val isBlocked: Boolean,
        val source: String,
        val notes: String?,
    )

    private data class AliasEntryPrimaryKey(
        val locale: String,
        val normalizedTerm: String,
        val kind: String,
        val targetCode: String,
    )

    private data class GoogleMappingPayload(
        val canonicalCode: String,
        val mappingType: String,
        val googleIds: List<Long>,
        val googlePaths: List<String>,
        val notes: String?,
    )

    private data class Stage40MetaPayload(
        val stage: String,
        val schemaVersion: String,
        val stage22DataVersion: String,
        val stage22SchemaVersion: String,
        val stage22GeneratedAt: String,
        val stage3Version: String,
    )

    private data class Stage40ImmutableAttributePayload(
        val attributeCode: String,
        val valueType: String,
        val valueSetType: String,
        val unit: String?,
        val isIdentity: Boolean,
        val isFacet: Boolean,
        val normalization: String?,
        val dictionaryRequired: Boolean,
        val immutableFingerprint: String,
    )

    private data class Stage40NormalizationRulePayload(
        val attributeCode: String,
        val normalization: String,
        val valueSetType: String,
        val dictionaryBacked: Boolean,
        val acceptsFreeText: Boolean,
        val canonicalSource: String,
        val dedupTokenMode: String,
    )

    private data class Stage40DedupTemplatePayload(
        val entity: String,
        val templateExpr: String,
        val fields: List<String>,
        val description: String,
    )

    private data class Stage40TypedConstraintPayload(
        val attributeCode: String,
        val valueType: String,
        val enumOnly: Boolean,
        val expectedUnit: String?,
        val regexPattern: String?,
        val minValue: Double?,
        val maxValue: Double?,
        val requiredIf: List<com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule>,
    )

    private data class ConstraintPayload(
        val scope: String,
        val categoryCode: String?,
        val brand: String?,
        val model: String?,
        val effectiveFrom: String?,
        val effectiveTo: String?,
        val attributeConstraints: List<AttributeValueConstraint>,
        val compatibilityRules: List<CompatibilityRule>,
    ) {
        val identityKey: String = buildString {
            append(scope)
            append("|")
            append(categoryCode.orEmpty())
            append("|")
            append(brand?.lowercase().orEmpty())
            append("|")
            append(model?.lowercase().orEmpty())
        }

        fun mergeWith(incoming: ConstraintPayload): ConstraintPayload = ConstraintPayload(
            scope = incoming.scope,
            categoryCode = incoming.categoryCode ?: categoryCode,
            brand = incoming.brand ?: brand,
            model = incoming.model ?: model,
            effectiveFrom = incoming.effectiveFrom ?: effectiveFrom,
            effectiveTo = incoming.effectiveTo ?: effectiveTo,
            attributeConstraints = mergeAttributeConstraints(attributeConstraints, incoming.attributeConstraints),
            compatibilityRules = normalizeCompatibilityRules(compatibilityRules + incoming.compatibilityRules),
        )
    }

    private fun mergeAttributeConstraints(
        base: List<AttributeValueConstraint>,
        incoming: List<AttributeValueConstraint>,
    ): List<AttributeValueConstraint> {
        val merged = LinkedHashMap<String, AttributeValueConstraint>()
        (base + incoming).forEach { rawConstraint ->
            val constraint = normalizeAttributeConstraint(rawConstraint) ?: return@forEach
            val key = normalizeAttributeKey(constraint.attributeCode)
            val existing = merged[key]
            merged[key] = if (existing == null) {
                constraint
            } else {
                AttributeValueConstraint(
                    attributeCode = constraint.attributeCode,
                    allowedValues = if (constraint.allowedValues.isNotEmpty()) constraint.allowedValues else existing.allowedValues,
                    forbiddenValues = if (constraint.forbiddenValues.isNotEmpty()) constraint.forbiddenValues else existing.forbiddenValues,
                    reason = constraint.reason?.takeIf { it.isNotEmpty() } ?: existing.reason,
                )
            }
        }
        return merged.values.sortedBy { constraint -> normalizeAttributeKey(constraint.attributeCode) }
    }

    private fun normalizeAttributeConstraint(
        constraint: AttributeValueConstraint,
    ): AttributeValueConstraint? {
        val attributeCode = constraint.attributeCode.trim().takeIf { it.isNotEmpty() } ?: return null
        return AttributeValueConstraint(
            attributeCode = attributeCode,
            allowedValues = constraint.allowedValues
                .map { value -> value.trim() }
                .filter { value -> value.isNotEmpty() }
                .distinct(),
            forbiddenValues = constraint.forbiddenValues
                .map { value -> value.trim() }
                .filter { value -> value.isNotEmpty() }
                .distinct(),
            reason = constraint.reason?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun normalizeCompatibilityRules(
        rules: List<CompatibilityRule>,
    ): List<CompatibilityRule> =
        rules
            .mapNotNull { rule ->
                val normalizedWhenAll = rule.whenAll
                    .mapNotNull { condition ->
                        val attributeCode = condition.attributeCode.trim().takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        val values = condition.values
                            .map { value -> value.trim() }
                            .filter { value -> value.isNotEmpty() }
                            .distinct()
                            .sorted()
                        if (values.isEmpty()) {
                            null
                        } else {
                            com.example.shoppingassistant.domain.catalog.AttributeCondition(
                                attributeCode = attributeCode,
                                op = condition.op,
                                values = values,
                            )
                        }
                    }
                    .sortedBy { condition ->
                        "${normalizeAttributeKey(condition.attributeCode)}|${condition.op.name}|${condition.values.joinToString(",")}"
                    }
                val normalizedApply = mergeAttributeConstraints(emptyList(), rule.apply)
                if (normalizedWhenAll.isEmpty() || normalizedApply.isEmpty()) {
                    null
                } else {
                    CompatibilityRule(
                        whenAll = normalizedWhenAll,
                        apply = normalizedApply,
                    )
                }
            }
            .distinctBy { rule ->
                buildString {
                    append(
                        rule.whenAll.joinToString(";") { condition ->
                            "${normalizeAttributeKey(condition.attributeCode)}:${condition.op.name}:${condition.values.joinToString(",")}"
                        },
                    )
                    append("->")
                    append(
                        rule.apply.joinToString(";") { apply ->
                            "${normalizeAttributeKey(apply.attributeCode)}:${apply.allowedValues.joinToString(",")}!${apply.forbiddenValues.joinToString(",")}@${apply.reason.orEmpty()}"
                        },
                    )
                }
            }

    private fun normalizeAttributeKey(value: String): String =
        value.trim().lowercase(Locale.ROOT)

    private fun normalizeAliasTerm(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace("[-‐‑‒–—]+".toRegex(), " ")
        .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    private fun aliasEntryLogicalKey(
        locale: String,
        term: String,
        kind: String,
        targetCode: String,
    ): String = listOf(locale, term, kind, targetCode).joinToString("|")

    private fun normalizeWriteSpec(spec: CatalogCategoryWriteSpec): CatalogCategoryWriteSpec {
        val normalizedCategory = normalizeSeedCategory(spec.category)
        val normalizedAttributes = spec.attributes.map { attribute ->
            attribute.copy(
                code = attribute.code.trim(),
                title = attribute.title.trim(),
                valueDictCode = attribute.valueDictCode?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        val normalizedCategoryAttributes = spec.categoryAttributes.map { categoryAttribute ->
            categoryAttribute.copy(
                categoryCode = categoryAttribute.categoryCode.trim(),
                attributeCode = categoryAttribute.attributeCode.trim(),
            )
        }
        val normalizedDicts = spec.valueDictionaries.map { dictionary ->
            dictionary.copy(
                attributeCode = dictionary.attributeCode.trim(),
                code = dictionary.code?.trim()?.takeIf { it.isNotEmpty() },
                entries = dictionary.entries.map { entry ->
                    entry.copy(
                        canonicalCode = entry.canonicalCode.trim(),
                        canonicalValue = entry.canonicalValue.trim(),
                        synonyms = entry.synonyms
                            .map { synonym -> synonym.trim() }
                            .filter { synonym -> synonym.isNotEmpty() }
                            .distinct(),
                    )
                },
            )
        }

        return spec.copy(
            category = normalizedCategory,
            attributes = normalizedAttributes,
            categoryAttributes = normalizedCategoryAttributes,
            valueDictionaries = normalizedDicts,
        )
    }

    private fun normalizeSeedCategory(
        category: com.example.shoppingassistant.domain.catalog.Category,
    ): com.example.shoppingassistant.domain.catalog.Category = category.copy(
        code = category.code.trim(),
        title = localizedTextOf(
            "ru" to category.title["ru"]?.trim()?.takeIf { it.isNotEmpty() },
            "en" to category.title["en"]?.trim()?.takeIf { it.isNotEmpty() },
        ),
        parentCode = category.parentCode?.trim()?.takeIf { it.isNotEmpty() },
        description = category.description?.trim()?.takeIf { it.isNotEmpty() },
        replacementCode = category.replacementCode?.trim()?.takeIf { it.isNotEmpty() },
    )

    private fun LocalizedText.storageRu(fallback: String): String =
        resolve(locale = "ru", fallback = fallback)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

    private fun LocalizedText.storageEn(): String? =
        this["en"]?.trim()?.takeIf { it.isNotEmpty() }
}
