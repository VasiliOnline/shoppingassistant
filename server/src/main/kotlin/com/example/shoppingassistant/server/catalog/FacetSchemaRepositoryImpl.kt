package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import com.example.shoppingassistant.domain.facet.FacetUiConfig
import com.example.shoppingassistant.domain.facet.FacetValueSource
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll

class FacetSchemaRepositoryImpl :
    FacetDefinitionRepository,
    FacetPresetRepository,
    FacetCollectionRepository {

    override suspend fun listFacetDefinitions(): List<FacetDefinition> = DatabaseFactory.dbQuery {
        FacetDefinitionsTable.selectAll().map { it.toFacetDefinition() }
    }

    override suspend fun listFacetDefinitions(categoryCode: String): List<FacetDefinition> = DatabaseFactory.dbQuery {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return@dbQuery emptyList()
        FacetDefinitionsTable.selectAll()
            .map { it.toFacetDefinition() }
            .filter { definition ->
                definition.appliesToCategoryCodes.any { it.equals(normalized, ignoreCase = true) }
            }
    }

    override suspend fun getFacetDefinition(facetKey: String): FacetDefinition? = DatabaseFactory.dbQuery {
        val normalized = facetKey.trim()
        if (normalized.isBlank()) return@dbQuery null
        val query = FacetDefinitionsTable.selectAll()
        query.andWhere { FacetDefinitionsTable.facetKey eq normalized }
        query.singleOrNull()?.toFacetDefinition()
    }

    override suspend fun listFacetPresets(): List<FacetPreset> = DatabaseFactory.dbQuery {
        FacetPresetsTable.selectAll().map { it.toFacetPreset() }
    }

    override suspend fun listFacetPresets(categoryCode: String): List<FacetPreset> = DatabaseFactory.dbQuery {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return@dbQuery emptyList()
        val query = FacetPresetsTable.selectAll()
        query.andWhere { FacetPresetsTable.categoryCode eq normalized }
        query.map { it.toFacetPreset() }
    }

    override suspend fun getFacetPreset(presetCode: String): FacetPreset? = DatabaseFactory.dbQuery {
        val normalized = presetCode.trim()
        if (normalized.isBlank()) return@dbQuery null
        val query = FacetPresetsTable.selectAll()
        query.andWhere { FacetPresetsTable.presetCode eq normalized }
        query.singleOrNull()?.toFacetPreset()
    }

    override suspend fun listFacetCollections(): List<FacetCollection> = DatabaseFactory.dbQuery {
        FacetCollectionsTable.selectAll().map { it.toFacetCollection() }
    }

    override suspend fun listFacetCollections(categoryCode: String): List<FacetCollection> = DatabaseFactory.dbQuery {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return@dbQuery emptyList()
        val query = FacetCollectionsTable.selectAll()
        query.andWhere { FacetCollectionsTable.categoryCode eq normalized }
        query.map { it.toFacetCollection() }
    }

    override suspend fun getFacetCollection(collectionCode: String): FacetCollection? = DatabaseFactory.dbQuery {
        val normalized = collectionCode.trim()
        if (normalized.isBlank()) return@dbQuery null
        val query = FacetCollectionsTable.selectAll()
        query.andWhere { FacetCollectionsTable.collectionCode eq normalized }
        query.singleOrNull()?.toFacetCollection()
    }

    override suspend fun getFacetCollectionByBrowseCode(browseCode: String): FacetCollection? = DatabaseFactory.dbQuery {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return@dbQuery null
        val query = FacetCollectionsTable.selectAll()
        query.andWhere { FacetCollectionsTable.browseCode eq normalized }
        query.singleOrNull()?.toFacetCollection()
    }
}

private fun ResultRow.toFacetDefinition(): FacetDefinition = FacetDefinition(
    facetKey = this[FacetDefinitionsTable.facetKey],
    title = localizedTextFromStorage(
        localized = this[FacetDefinitionsTable.titleLocalized],
        titleRu = this[FacetDefinitionsTable.titleRu],
        titleEn = this[FacetDefinitionsTable.titleEn],
    ),
    valueType = runCatching { FacetDataType.valueOf(this[FacetDefinitionsTable.valueType]) }
        .getOrDefault(FacetDataType.ENUM),
    appliesToCategoryCodes = this[FacetDefinitionsTable.appliesToCategoryCodes],
    source = runCatching { FacetValueSource.valueOf(this[FacetDefinitionsTable.valueSource]) }
        .getOrDefault(FacetValueSource.OFFER),
    effectiveFrom = this[FacetDefinitionsTable.effectiveFrom],
    effectiveTo = this[FacetDefinitionsTable.effectiveTo],
    ui = FacetUiConfig(
        order = this[FacetDefinitionsTable.uiOrder],
        pinned = this[FacetDefinitionsTable.uiPinned],
        hidden = this[FacetDefinitionsTable.uiHidden],
        format = this[FacetDefinitionsTable.uiFormat],
    ),
)

private fun ResultRow.toFacetPreset(): FacetPreset = FacetPreset(
    presetCode = this[FacetPresetsTable.presetCode],
    categoryCode = this[FacetPresetsTable.categoryCode],
    title = localizedTextFromStorage(
        localized = this[FacetPresetsTable.titleLocalized],
        titleRu = this[FacetPresetsTable.titleRu],
        titleEn = this[FacetPresetsTable.titleEn],
    ),
    order = this[FacetPresetsTable.order],
    effectiveFrom = this[FacetPresetsTable.effectiveFrom],
    effectiveTo = this[FacetPresetsTable.effectiveTo],
    rules = this[FacetPresetsTable.rules],
    notes = this[FacetPresetsTable.notes],
)

private fun ResultRow.toFacetCollection(): FacetCollection = FacetCollection(
    collectionCode = this[FacetCollectionsTable.collectionCode],
    categoryCode = this[FacetCollectionsTable.categoryCode],
    title = localizedTextFromStorage(
        localized = this[FacetCollectionsTable.titleLocalized],
        titleRu = this[FacetCollectionsTable.titleRu],
        titleEn = this[FacetCollectionsTable.titleEn],
    ),
    browseCode = this[FacetCollectionsTable.browseCode],
    presetCode = this[FacetCollectionsTable.presetCode],
    order = this[FacetCollectionsTable.order],
    tags = this[FacetCollectionsTable.tags],
    notes = this[FacetCollectionsTable.notes],
)
