package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetCollectionRepository
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetDefinitionRepository
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetPresetRepository
import com.example.shoppingassistant.domain.facet.FacetSchemaValidationReport
import com.example.shoppingassistant.domain.facet.FacetSchemaValidator

class FacetDefinitionRepositoryImpl(
    private val seed: List<FacetDefinition> = CatalogSeed.facetDefinitions,
) : FacetDefinitionRepository {
    override suspend fun listFacetDefinitions(): List<FacetDefinition> = seed

    override suspend fun listFacetDefinitions(categoryCode: String): List<FacetDefinition> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()
        return seed.filter { definition ->
            definition.appliesToCategoryCodes.any { it.equals(normalized, ignoreCase = true) }
        }
    }

    override suspend fun getFacetDefinition(facetKey: String): FacetDefinition? {
        val normalized = facetKey.trim()
        if (normalized.isBlank()) return null
        return seed.firstOrNull { it.facetKey.equals(normalized, ignoreCase = true) }
    }
}

class FacetPresetRepositoryImpl(
    private val seed: List<FacetPreset> = CatalogSeed.facetPresets,
) : FacetPresetRepository {
    override suspend fun listFacetPresets(): List<FacetPreset> = seed

    override suspend fun listFacetPresets(categoryCode: String): List<FacetPreset> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()
        return seed.filter { it.categoryCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun getFacetPreset(presetCode: String): FacetPreset? {
        val normalized = presetCode.trim()
        if (normalized.isBlank()) return null
        return seed.firstOrNull { it.presetCode.equals(normalized, ignoreCase = true) }
    }
}

class FacetCollectionRepositoryImpl(
    private val seed: List<FacetCollection> = CatalogSeed.facetCollections,
) : FacetCollectionRepository {
    override suspend fun listFacetCollections(): List<FacetCollection> = seed

    override suspend fun listFacetCollections(categoryCode: String): List<FacetCollection> {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return emptyList()
        return seed.filter { it.categoryCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun getFacetCollection(collectionCode: String): FacetCollection? {
        val normalized = collectionCode.trim()
        if (normalized.isBlank()) return null
        return seed.firstOrNull { it.collectionCode.equals(normalized, ignoreCase = true) }
    }

    override suspend fun getFacetCollectionByBrowseCode(browseCode: String): FacetCollection? {
        val normalized = browseCode.trim()
        if (normalized.isBlank()) return null
        return seed.firstOrNull { it.browseCode?.equals(normalized, ignoreCase = true) == true }
    }
}

class FacetSchemaGate(
    private val catalogRepository: CatalogTaxonomyRepository,
    private val facetDefinitionRepository: FacetDefinitionRepository,
    private val facetPresetRepository: FacetPresetRepository,
    private val facetCollectionRepository: FacetCollectionRepository,
    private val validator: FacetSchemaValidator = FacetSchemaValidator(),
) {
    suspend fun validate(maxIssues: Int = DEFAULT_MAX_ISSUES): FacetSchemaValidationReport {
        val categories = catalogRepository.listCategories()
        val definitions = facetDefinitionRepository.listFacetDefinitions()
        val presets = facetPresetRepository.listFacetPresets()
        val collections = facetCollectionRepository.listFacetCollections()
        return validator.validate(
            categories = categories,
            definitions = definitions,
            presets = presets,
            collections = collections,
        )
    }

    suspend fun validateOrThrow(maxIssues: Int = DEFAULT_MAX_ISSUES) {
        val report = validate(maxIssues = maxIssues)
        if (!report.isValid) {
            throw IllegalStateException(report.summary(maxIssues))
        }
    }

    private companion object {
        private const val DEFAULT_MAX_ISSUES = 20
    }
}

