package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.facet.FacetSchemaValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class FacetSchemaGateTest {
    @Test
    fun validateOrThrow_passes_for_seeded_schema() = runBlocking {
        val gate = FacetSchemaGate(
            catalogRepository = CatalogRepositoryImpl(SeededCatalogDataSource()),
            facetDefinitionRepository = FacetDefinitionRepositoryImpl(),
            facetPresetRepository = FacetPresetRepositoryImpl(),
            facetCollectionRepository = FacetCollectionRepositoryImpl(),
            validator = FacetSchemaValidator(),
        )

        gate.validateOrThrow()
    }

    @Test
    fun validate_returnsUsefulStats() = runBlocking {
        val gate = FacetSchemaGate(
            catalogRepository = CatalogRepositoryImpl(SeededCatalogDataSource()),
            facetDefinitionRepository = FacetDefinitionRepositoryImpl(),
            facetPresetRepository = FacetPresetRepositoryImpl(),
            facetCollectionRepository = FacetCollectionRepositoryImpl(),
            validator = FacetSchemaValidator(),
        )

        val report = gate.validate()
        assertTrue(report.stats.definitionsCount > 0)
        assertTrue(report.stats.presetsCount > 0)
        assertTrue(report.stats.collectionsCount > 0)
    }
}
