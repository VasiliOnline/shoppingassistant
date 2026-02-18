package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPreset
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

/**
 * Инициализация справочников категорий/атрибутов из domain-сидов.
 */
object CatalogSeeder {
    fun seedIfEmpty() {
        if (CategoriesTable.selectAll().limit(1).empty()) {
            seedProfiles(CatalogSeed.profiles)
        }
        syncConstraints(CatalogSeed.constraints)
        syncFacetDefinitions(CatalogSeed.facetDefinitions)
        syncFacetPresets(CatalogSeed.facetPresets)
        syncFacetCollections(CatalogSeed.facetCollections)
    }

    private fun seedProfiles(profiles: List<CategoryProfile>) {
        profiles.forEach { profile ->
            CategoriesTable.insert { stmt ->
                stmt[code] = profile.category.code
                stmt[segment] = profile.category.segment.name
                stmt[title] = profile.category.title
                stmt[parentCode] = profile.category.parentCode
                stmt[description] = profile.category.description
            }

            profile.attributes.forEach { def ->
                AttributeDefsTable.insertIgnore { stmt ->
                    stmt[code] = def.code
                    stmt[title] = def.title
                    stmt[dataType] = def.dataType.name
                    stmt[requiredForSearch] = def.requiredForSearch
                    stmt[requiredForOffer] = def.requiredForOffer
                    stmt[requiredForExpress] = def.requiredForExpress
                    stmt[facetEnabled] = def.facetEnabled
                    stmt[multiValued] = def.multiValued
                    stmt[valueDictCode] = def.valueDictCode
                }
            }

            CategoryAttributesTable.batchInsert(profile.categoryAttributes) { attr ->
                this[CategoryAttributesTable.categoryCode] = attr.categoryCode
                this[CategoryAttributesTable.attributeCode] = attr.attributeCode
                this[CategoryAttributesTable.uiOrder] = attr.uiOrder
                this[CategoryAttributesTable.isRequired] = attr.isRequiredForCategory
            }

            profile.valueDictionaries
                .flatMap { dict -> dict.entries.map { entry -> dict.attributeCode to entry } }
                .distinctBy { pair -> "${pair.first}|${pair.second.canonicalCode}" }
                .forEach { (attrCode, entry) ->
                    AttributeValueDictTable.insertIgnore { stmt ->
                        stmt[AttributeValueDictTable.attributeCode] = attrCode
                        stmt[AttributeValueDictTable.canonicalCode] = entry.canonicalCode
                        stmt[AttributeValueDictTable.canonicalValue] = entry.canonicalValue
                        stmt[AttributeValueDictTable.synonyms] = entry.synonyms
                    }
                }
        }
    }

    private fun syncConstraints(constraints: List<CatalogConstraints>) {
        val seedRows = constraints
            .map(::normalizeConstraintRow)
            .sortedBy { it.key }

        if (seedRows.isEmpty()) {
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
                        attributeConstraints = row[CatalogConstraintsTable.attributeConstraints],
                        compatibilityRules = row[CatalogConstraintsTable.compatibilityRules],
                    ),
                )
            }
            .groupBy { it.payload.key }

        val currentByKey = LinkedHashMap<String, ExistingConstraintRow>()
        val duplicateIdsToDelete = mutableListOf<Long>()
        existingByKey.forEach { (key, rows) ->
            val ordered = rows.sortedBy { it.id }
            currentByKey[key] = ordered.first()
            duplicateIdsToDelete += ordered.drop(1).map { it.id }
        }

        if (duplicateIdsToDelete.isNotEmpty()) {
            CatalogConstraintsTable.deleteWhere { CatalogConstraintsTable.id inList duplicateIdsToDelete }
        }

        val seedKeys = seedRows.map { it.key }.toSet()
        val staleIdsToDelete = currentByKey
            .filterKeys { it !in seedKeys }
            .values
            .map { it.id }
        if (staleIdsToDelete.isNotEmpty()) {
            CatalogConstraintsTable.deleteWhere { CatalogConstraintsTable.id inList staleIdsToDelete }
        }

        val toInsert = mutableListOf<ConstraintPayload>()
        seedRows.forEach { seedRow ->
            val current = currentByKey[seedRow.key]?.payload
            if (current == null) {
                toInsert += seedRow
                return@forEach
            }
            if (current != seedRow) {
                val id = currentByKey[seedRow.key]?.id ?: return@forEach
                CatalogConstraintsTable.update({ CatalogConstraintsTable.id eq id }) { stmt ->
                    stmt[CatalogConstraintsTable.scope] = seedRow.scope
                    stmt[CatalogConstraintsTable.categoryCode] = seedRow.categoryCode
                    stmt[CatalogConstraintsTable.brand] = seedRow.brand
                    stmt[CatalogConstraintsTable.model] = seedRow.model
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
                this[CatalogConstraintsTable.attributeConstraints] = row.attributeConstraints
                this[CatalogConstraintsTable.compatibilityRules] = row.compatibilityRules
            }
        }
    }

    private fun syncFacetDefinitions(definitions: List<FacetDefinition>) {
        val seedRows = definitions
            .map { definition ->
                definition.copy(
                    facetKey = definition.facetKey.trim(),
                    titleRu = definition.titleRu.trim(),
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
                    titleRu = row[FacetDefinitionsTable.titleRu],
                    valueType = com.example.shoppingassistant.domain.facet.FacetDataType.valueOf(row[FacetDefinitionsTable.valueType]),
                    appliesToCategoryCodes = row[FacetDefinitionsTable.appliesToCategoryCodes],
                    source = com.example.shoppingassistant.domain.facet.FacetValueSource.valueOf(row[FacetDefinitionsTable.valueSource]),
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
        if (staleKeys.isNotEmpty()) {
            FacetDefinitionsTable.deleteWhere { FacetDefinitionsTable.facetKey inList staleKeys.toList() }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.facetKey]?.payload
            if (existing == null) {
                FacetDefinitionsTable.insert { stmt ->
                    stmt[FacetDefinitionsTable.facetKey] = seed.facetKey
                    stmt[FacetDefinitionsTable.titleRu] = seed.titleRu
                    stmt[FacetDefinitionsTable.valueType] = seed.valueType.name
                    stmt[FacetDefinitionsTable.valueSource] = seed.source.name
                    stmt[FacetDefinitionsTable.appliesToCategoryCodes] = seed.appliesToCategoryCodes
                    stmt[FacetDefinitionsTable.uiOrder] = seed.ui.order
                    stmt[FacetDefinitionsTable.uiPinned] = seed.ui.pinned
                    stmt[FacetDefinitionsTable.uiHidden] = seed.ui.hidden
                    stmt[FacetDefinitionsTable.uiFormat] = seed.ui.format
                }
            } else if (existing != seed) {
                FacetDefinitionsTable.update({ FacetDefinitionsTable.facetKey eq seed.facetKey }) { stmt ->
                    stmt[FacetDefinitionsTable.titleRu] = seed.titleRu
                    stmt[FacetDefinitionsTable.valueType] = seed.valueType.name
                    stmt[FacetDefinitionsTable.valueSource] = seed.source.name
                    stmt[FacetDefinitionsTable.appliesToCategoryCodes] = seed.appliesToCategoryCodes
                    stmt[FacetDefinitionsTable.uiOrder] = seed.ui.order
                    stmt[FacetDefinitionsTable.uiPinned] = seed.ui.pinned
                    stmt[FacetDefinitionsTable.uiHidden] = seed.ui.hidden
                    stmt[FacetDefinitionsTable.uiFormat] = seed.ui.format
                }
            }
        }
    }

    private fun syncFacetPresets(presets: List<FacetPreset>) {
        val seedRows = presets
            .map { preset ->
                preset.copy(
                    presetCode = preset.presetCode.trim(),
                    categoryCode = preset.categoryCode.trim(),
                    titleRu = preset.titleRu.trim(),
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
                    titleRu = row[FacetPresetsTable.titleRu],
                    order = row[FacetPresetsTable.order],
                    rules = row[FacetPresetsTable.rules],
                    notes = row[FacetPresetsTable.notes],
                ),
            )
        }.associateBy { it.presetCode }

        val staleCodes = existingRows.keys - seedCodes
        if (staleCodes.isNotEmpty()) {
            FacetPresetsTable.deleteWhere { FacetPresetsTable.presetCode inList staleCodes.toList() }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.presetCode]?.payload
            if (existing == null) {
                FacetPresetsTable.insert { stmt ->
                    stmt[FacetPresetsTable.presetCode] = seed.presetCode
                    stmt[FacetPresetsTable.categoryCode] = seed.categoryCode
                    stmt[FacetPresetsTable.titleRu] = seed.titleRu
                    stmt[FacetPresetsTable.order] = seed.order
                    stmt[FacetPresetsTable.rules] = seed.rules
                    stmt[FacetPresetsTable.notes] = seed.notes
                }
            } else if (existing != seed) {
                FacetPresetsTable.update({ FacetPresetsTable.presetCode eq seed.presetCode }) { stmt ->
                    stmt[FacetPresetsTable.categoryCode] = seed.categoryCode
                    stmt[FacetPresetsTable.titleRu] = seed.titleRu
                    stmt[FacetPresetsTable.order] = seed.order
                    stmt[FacetPresetsTable.rules] = seed.rules
                    stmt[FacetPresetsTable.notes] = seed.notes
                }
            }
        }
    }

    private fun syncFacetCollections(collections: List<FacetCollection>) {
        val seedRows = collections
            .map { collection ->
                collection.copy(
                    collectionCode = collection.collectionCode.trim(),
                    categoryCode = collection.categoryCode.trim(),
                    titleRu = collection.titleRu.trim(),
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
                    titleRu = row[FacetCollectionsTable.titleRu],
                    browseCode = row[FacetCollectionsTable.browseCode],
                    presetCode = row[FacetCollectionsTable.presetCode],
                    order = row[FacetCollectionsTable.order],
                    tags = row[FacetCollectionsTable.tags],
                    notes = row[FacetCollectionsTable.notes],
                ),
            )
        }.associateBy { it.collectionCode }

        val staleCodes = existingRows.keys - seedCodes
        if (staleCodes.isNotEmpty()) {
            FacetCollectionsTable.deleteWhere { FacetCollectionsTable.collectionCode inList staleCodes.toList() }
        }

        seedRows.forEach { seed ->
            val existing = existingRows[seed.collectionCode]?.payload
            if (existing == null) {
                FacetCollectionsTable.insert { stmt ->
                    stmt[FacetCollectionsTable.collectionCode] = seed.collectionCode
                    stmt[FacetCollectionsTable.categoryCode] = seed.categoryCode
                    stmt[FacetCollectionsTable.titleRu] = seed.titleRu
                    stmt[FacetCollectionsTable.browseCode] = seed.browseCode
                    stmt[FacetCollectionsTable.presetCode] = seed.presetCode
                    stmt[FacetCollectionsTable.order] = seed.order
                    stmt[FacetCollectionsTable.tags] = seed.tags
                    stmt[FacetCollectionsTable.notes] = seed.notes
                }
            } else if (existing != seed) {
                FacetCollectionsTable.update({ FacetCollectionsTable.collectionCode eq seed.collectionCode }) { stmt ->
                    stmt[FacetCollectionsTable.categoryCode] = seed.categoryCode
                    stmt[FacetCollectionsTable.titleRu] = seed.titleRu
                    stmt[FacetCollectionsTable.browseCode] = seed.browseCode
                    stmt[FacetCollectionsTable.presetCode] = seed.presetCode
                    stmt[FacetCollectionsTable.order] = seed.order
                    stmt[FacetCollectionsTable.tags] = seed.tags
                    stmt[FacetCollectionsTable.notes] = seed.notes
                }
            }
        }
    }

    private fun normalizeConstraintRow(constraint: CatalogConstraints): ConstraintPayload {
        val scope = constraint.scope.name
        val categoryCode = constraint.categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        val brand = constraint.brand?.trim()?.takeIf { it.isNotEmpty() }
        val model = constraint.model?.trim()?.takeIf { it.isNotEmpty() }
        return ConstraintPayload(
            scope = scope,
            categoryCode = categoryCode,
            brand = brand,
            model = model,
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

    private data class ConstraintPayload(
        val scope: String,
        val categoryCode: String?,
        val brand: String?,
        val model: String?,
        val attributeConstraints: List<AttributeValueConstraint>,
        val compatibilityRules: List<CompatibilityRule>,
    ) {
        val key: String = buildString {
            append(scope)
            append("|")
            append(categoryCode.orEmpty())
            append("|")
            append(brand?.lowercase().orEmpty())
            append("|")
            append(model?.lowercase().orEmpty())
        }
    }
}
