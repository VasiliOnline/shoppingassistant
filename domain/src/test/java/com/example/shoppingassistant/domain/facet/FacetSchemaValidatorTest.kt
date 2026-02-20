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

    @Test
    fun invalid_facet_effective_window_failsValidation() {
        val brokenDefinitions = CatalogSeed.facetDefinitions.map { definition ->
            if (definition.facetKey == "price") {
                definition.copy(
                    effectiveFrom = "2026-12-31",
                    effectiveTo = "2026-01-01",
                )
            } else {
                definition
            }
        }

        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = brokenDefinitions,
            presets = CatalogSeed.facetPresets,
            collections = CatalogSeed.facetCollections,
        )

        assertFalse(report.isValid)
        assertTrue(report.failIssues.any { it.code == "FACET_EFFECTIVE_WINDOW_INVALID" })
    }

    @Test
    fun duplicate_preset_rule_facet_failsValidation() {
        val baseline = CatalogSeed.facetPresets.first { it.presetCode == "FP.TECH.PHONES.DEFAULT" }
        val duplicated = baseline.copy(
            rules = baseline.rules + baseline.rules.first().copy(),
        )
        val presets = CatalogSeed.facetPresets.map { preset ->
            if (preset.presetCode == duplicated.presetCode) duplicated else preset
        }

        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = presets,
            collections = CatalogSeed.facetCollections,
        )

        assertFalse(report.isValid)
        assertTrue(report.failIssues.any { it.code == "PRESET_RULE_FACET_DUPLICATE" })
    }

    @Test
    fun include_exclude_overlap_failsValidation() {
        val baseline = CatalogSeed.facetPresets.first { it.presetCode == "FP.FOOD.READY.PIZZA" }
        val rule = baseline.rules.first { it.facetKey == "cuisine" }
        val overlappedRule = rule.copy(
            includeValues = listOf("pizza"),
            excludeValues = listOf("pizza"),
        )
        val mutated = baseline.copy(
            rules = baseline.rules.map { candidate ->
                if (candidate.facetKey == "cuisine") overlappedRule else candidate
            },
        )
        val presets = CatalogSeed.facetPresets.map { preset ->
            if (preset.presetCode == mutated.presetCode) mutated else preset
        }

        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = presets,
            collections = CatalogSeed.facetCollections,
        )

        assertFalse(report.isValid)
        assertTrue(report.failIssues.any { it.code == "PRESET_RULE_INCLUDE_EXCLUDE_CONFLICT" })
    }

    @Test
    fun bool_rule_with_non_bool_payload_failsValidation() {
        val baseline = CatalogSeed.facetPresets.first { it.presetCode == "FP.FOOD.GROCERIES.HEALTHY" }
        val boolRule = baseline.rules.first { it.facetKey == "is_gluten_free" }
        val brokenRule = boolRule.copy(
            includeValues = listOf("TRUE"),
            boolValue = true,
        )
        val mutated = baseline.copy(
            rules = baseline.rules.map { candidate ->
                if (candidate.facetKey == "is_gluten_free") brokenRule else candidate
            },
        )
        val presets = CatalogSeed.facetPresets.map { preset ->
            if (preset.presetCode == mutated.presetCode) mutated else preset
        }

        val report = validator.validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = presets,
            collections = CatalogSeed.facetCollections,
        )

        assertFalse(report.isValid)
        assertTrue(report.failIssues.any { it.code == "PRESET_BOOL_HAS_EXTRA_FIELDS" })
    }
}
