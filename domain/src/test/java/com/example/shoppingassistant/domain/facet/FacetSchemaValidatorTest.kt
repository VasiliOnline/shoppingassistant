package com.example.shoppingassistant.domain.facet

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacetSchemaValidatorTest {
    private val validator = FacetSchemaValidator()

    @Test
    fun seededStage3Stub_isValid() {
        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = CatalogSeed.facetPresets,
            collections = CatalogSeed.facetCollections,
        )

        assertTrue(report.summary(), report.isValid)
        assertTrue(report.stats.definitionsCount > 0)
        assertTrue(report.stats.presetsCount > 0)
        assertTrue(report.stats.collectionsCount > 0)
    }

    @Test
    fun collectionWithMissingPreset_failsValidation() {
        val brokenCollections = CatalogSeed.facetCollections + FacetCollection(
            collectionCode = "B.FOOD.READY.BROKEN",
            categoryCode = "FOOD.READY_MEALS",
            titleRu = "Broken",
            browseCode = "B.FOOD.READY.99",
            presetCode = "FP.MISSING.PRESET",
        )

        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = CatalogSeed.facetPresets,
            collections = brokenCollections,
        )

        assertFalse(report.isValid)
        assertTrue(report.failIssues.any { it.code == "COLLECTION_PRESET_MISSING" })
    }

    @Test
    fun missingLeafFacetCoverage_failsValidation() {
        val reducedDefinitions = CatalogSeed.facetDefinitions.map { definition ->
            definition.copy(appliesToCategoryCodes = listOf("TECH.PHONES"))
        }
        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = reducedDefinitions,
            presets = CatalogSeed.facetPresets,
            collections = CatalogSeed.facetCollections,
        )

        assertFalse(report.isValid)
        assertTrue(report.failIssues.any { it.code == "FACET_DEFINITION_COVERAGE_INCOMPLETE" })
    }
}
