package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.AliasKind
import com.example.shoppingassistant.domain.catalog.AliasMatchKind
import com.example.shoppingassistant.domain.catalog.AliasSource
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeKind
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.BrowseNodeStatus
import com.example.shoppingassistant.domain.catalog.BrowseTargetType
import com.example.shoppingassistant.domain.catalog.CategoryAlias
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingType
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

class TaxonomyRepositoryImpl :
    CategoryAliasRepository,
    BrowseNodeRepository,
    AliasEntryRepository,
    GoogleTaxonomyMappingRepository {

    override suspend fun listAliases(): List<CategoryAlias> = DatabaseFactory.dbQuery {
        CategoryAliasesTable
            .selectAll()
            .map { row -> row.toCategoryAlias() }
            .sortedWith(compareBy<CategoryAlias> { it.alias.lowercase() }.thenBy { it.categoryCode })
    }

    override suspend fun listBrowseNodes(): List<BrowseNode> = DatabaseFactory.dbQuery {
        BrowseNodesTable
            .selectAll()
            .map { row -> row.toBrowseNode() }
            .sortedWith(compareBy<BrowseNode> { it.order }.thenBy { it.browseCode })
    }

    override suspend fun getBrowseNode(browseCode: String): BrowseNode? = DatabaseFactory.dbQuery {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return@dbQuery null

        val exactQuery = BrowseNodesTable.selectAll()
        exactQuery.andWhere { BrowseNodesTable.browseCode eq normalized }
        val exact = exactQuery.singleOrNull()?.toBrowseNode()
        if (exact != null) {
            return@dbQuery exact
        }

        BrowseNodesTable.selectAll()
            .map { row -> row.toBrowseNode() }
            .firstOrNull { node -> node.browseCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun listAliasEntries(locale: String?): List<AliasEntry> = DatabaseFactory.dbQuery {
        val normalizedLocale = locale?.trim()?.takeIf { value -> value.isNotEmpty() }
        val rows = AliasEntriesTable.selectAll().map { row -> row.toAliasEntry() }
        if (normalizedLocale == null) {
            rows.sortedWith(compareBy<AliasEntry> { it.locale.lowercase() }.thenByDescending { it.weight })
        } else {
            rows
                .filter { entry -> entry.locale.equals(normalizedLocale, ignoreCase = true) }
                .sortedByDescending { entry -> entry.weight }
        }
    }

    override suspend fun listMappings(): List<GoogleTaxonomyMapping> = DatabaseFactory.dbQuery {
        GoogleTaxonomyMappingsTable
            .selectAll()
            .map { row -> row.toGoogleTaxonomyMapping() }
            .sortedBy { mapping -> mapping.canonicalCode }
    }

    override suspend fun getMapping(categoryCode: String): GoogleTaxonomyMapping? = DatabaseFactory.dbQuery {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return@dbQuery null

        val exactQuery = GoogleTaxonomyMappingsTable.selectAll()
        exactQuery.andWhere { GoogleTaxonomyMappingsTable.canonicalCode eq normalized }
        val exact = exactQuery.singleOrNull()?.toGoogleTaxonomyMapping()
        if (exact != null) {
            return@dbQuery exact
        }

        GoogleTaxonomyMappingsTable.selectAll()
            .map { row -> row.toGoogleTaxonomyMapping() }
            .firstOrNull { mapping -> mapping.canonicalCode.equals(normalized, ignoreCase = true) }
    }
}

private fun ResultRow.toCategoryAlias(): CategoryAlias = CategoryAlias(
    alias = this[CategoryAliasesTable.alias],
    categoryCode = this[CategoryAliasesTable.categoryCode],
)

private fun ResultRow.toBrowseNode(): BrowseNode = BrowseNode(
    browseCode = this[BrowseNodesTable.browseCode],
    parentBrowseCode = this[BrowseNodesTable.parentBrowseCode],
    nodeKind = runCatching { BrowseNodeKind.valueOf(this[BrowseNodesTable.nodeKind]) }
        .getOrDefault(BrowseNodeKind.GROUP),
    titleKey = this[BrowseNodesTable.titleKey],
    title = localizedTextFromStorage(
        localized = this[BrowseNodesTable.titleLocalized],
        titleRu = this[BrowseNodesTable.titleRu],
        titleEn = this[BrowseNodesTable.titleEn],
    ),
    targetCategoryCode = this[BrowseNodesTable.targetCategoryCode],
    targetType = this[BrowseNodesTable.targetType]
        ?.let { value -> runCatching { BrowseTargetType.valueOf(value) }.getOrNull() },
    order = this[BrowseNodesTable.order],
    availabilityScope = this[BrowseNodesTable.availabilityScope],
    iconKey = this[BrowseNodesTable.iconKey],
    analyticsKey = this[BrowseNodesTable.analyticsKey],
    searchKeywordsRu = this[BrowseNodesTable.searchKeywordsRu],
    status = runCatching { BrowseNodeStatus.valueOf(this[BrowseNodesTable.status]) }
        .getOrDefault(BrowseNodeStatus.ACTIVE),
    tags = this[BrowseNodesTable.tags],
    notes = this[BrowseNodesTable.notes],
)

private fun ResultRow.toAliasEntry(): AliasEntry = AliasEntry(
    locale = this[AliasEntriesTable.locale],
    term = this[AliasEntriesTable.term],
    normalizedTerm = this[AliasEntriesTable.normalizedTerm],
    kind = runCatching { AliasKind.valueOf(this[AliasEntriesTable.kind]) }
        .getOrDefault(AliasKind.CATEGORY),
    targetCode = this[AliasEntriesTable.targetCode],
    weight = this[AliasEntriesTable.weight],
    matchKind = runCatching { AliasMatchKind.valueOf(this[AliasEntriesTable.matchKind]) }
        .getOrDefault(AliasMatchKind.EXACT),
    isBlocked = this[AliasEntriesTable.isBlocked],
    source = runCatching { AliasSource.valueOf(this[AliasEntriesTable.aliasSource]) }
        .getOrDefault(AliasSource.MANUAL),
    notes = this[AliasEntriesTable.notes],
)

private fun ResultRow.toGoogleTaxonomyMapping(): GoogleTaxonomyMapping = GoogleTaxonomyMapping(
    canonicalCode = this[GoogleTaxonomyMappingsTable.canonicalCode],
    mappingType = runCatching { GoogleTaxonomyMappingType.valueOf(this[GoogleTaxonomyMappingsTable.mappingType]) }
        .getOrDefault(GoogleTaxonomyMappingType.NONE),
    googleIds = this[GoogleTaxonomyMappingsTable.googleIds],
    googlePaths = this[GoogleTaxonomyMappingsTable.googlePaths],
    notes = this[GoogleTaxonomyMappingsTable.notes],
)
