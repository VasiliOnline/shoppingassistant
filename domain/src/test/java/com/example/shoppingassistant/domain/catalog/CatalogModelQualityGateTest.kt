package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetSchemaValidator
import com.example.shoppingassistant.domain.facet.FacetValueSource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogModelQualityGateTest {
    @Test
    fun leaf_constraints_coverage_must_be_100_percent() {
        val leafCodes = leafCategoryCodes(CatalogSeed.categories)
        val constrainedLeafs = CatalogSeed.constraints
            .asSequence()
            .filter { it.scope == ConstraintScope.CATEGORY }
            .mapNotNull { it.categoryCode?.trim()?.takeIf(String::isNotBlank) }
            .toSet()
        val missing = (leafCodes - constrainedLeafs).sorted()

        assertTrue(
            "Leaf constraints coverage must be 100%. Missing: ${missing.take(10)}",
            missing.isEmpty(),
        )
    }

    @Test
    fun stage2_facet_enabled_must_match_stage3_definitions() {
        val stage2FacetEnabled = CatalogSeed.categoryWriteSpecs
            .asSequence()
            .flatMap { spec ->
                spec.attributes
                    .asSequence()
                    .filter { attribute -> attribute.facetEnabled }
                    .map { attribute -> attribute.code.trim().lowercase() }
            }
            .filter { it.isNotEmpty() }
            .toSet()

        val stage3ProductFacetKeys = CatalogSeed.facetDefinitions
            .asSequence()
            .filter { definition -> definition.source == FacetValueSource.PRODUCT }
            .map { definition -> definition.facetKey.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val stage3OfferFacetKeys = CatalogSeed.facetDefinitions
            .asSequence()
            .filter { definition -> definition.source == FacetValueSource.OFFER }
            .map { definition -> definition.facetKey.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val stage3OfferExpectedInProfiles = stage3OfferFacetKeys - OFFER_FACET_ALLOWLIST
        val stage3ExpectedFromProfiles = stage3ProductFacetKeys + stage3OfferExpectedInProfiles

        val missingInStage3 = (stage2FacetEnabled - stage3ExpectedFromProfiles).sorted()
        val unexpectedInStage3Product = (stage3ProductFacetKeys - stage2FacetEnabled).sorted()
        val unexpectedInStage3Offer = (stage3OfferExpectedInProfiles - stage2FacetEnabled).sorted()

        assertTrue(
            "Stage2 facetEnabled attributes missing in Stage3 facet definitions: ${missingInStage3.take(10)}",
            missingInStage3.isEmpty(),
        )
        assertTrue(
            "Stage3 PRODUCT facet definitions not backed by Stage2 facetEnabled attributes: ${unexpectedInStage3Product.take(10)}",
            unexpectedInStage3Product.isEmpty(),
        )
        assertTrue(
            "Stage3 OFFER facet definitions (excluding allowlist) not backed by Stage2 facetEnabled attributes: ${unexpectedInStage3Offer.take(10)}",
            unexpectedInStage3Offer.isEmpty(),
        )
    }

    @Test
    fun temporal_policy_and_leaf_profile_allowlist_must_be_valid() {
        val stage22Report = Stage22SeedValidator().validate(
            categories = CatalogSeed.categories,
            registry = Stage22RegistryLoader.loadSnapshot(),
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        assertTrue(stage22Report.summary(), stage22Report.isValid)

        val stage3Report = FacetSchemaValidator().validate(
            categories = CatalogSeed.categories,
            definitions = CatalogSeed.facetDefinitions,
            presets = CatalogSeed.facetPresets,
            collections = CatalogSeed.facetCollections,
        )
        assertTrue(stage3Report.summary(), stage3Report.isValid)
    }

    @Test
    fun food_production_grade_required_fields_must_be_required_and_facetable() {
        val categoriesByCode = CatalogSeed.categories.associateBy { it.code }
        val foodLeafCodes = leafCategoryCodes(CatalogSeed.categories)
            .filter { code ->
                categoriesByCode[code]?.segment == CategorySegment.FOOD
            }
            .toSet()

        val specsByCode = CatalogSeed.categoryWriteSpecs.associateBy { it.category.code }
        val requiredForValidation = setOf(
            "allergen_profile",
            "storage_regime",
            "shelf_life_days",
            "calories_kcal_per_serving",
            "protein_per_serving_gram",
            "fat_per_serving_gram",
            "carbs_per_serving_gram",
            "ingredient_list",
        )
        val requiredForFacets = setOf(
            "allergen_profile",
            "storage_regime",
            "shelf_life_days",
            "calories_kcal_per_serving",
            "protein_per_serving_gram",
            "fat_per_serving_gram",
            "carbs_per_serving_gram",
        )

        val missingInProfiles = mutableListOf<String>()
        val notRequiredInProfiles = mutableListOf<String>()
        val notFacetEnabledInProfiles = mutableListOf<String>()

        foodLeafCodes.forEach { code ->
            val spec = specsByCode[code]
            if (spec == null) {
                missingInProfiles += "$code:*profile_missing*"
                return@forEach
            }
            val defsByCode = spec.attributes.associateBy { it.code }
            requiredForValidation.forEach { attributeCode ->
                val def = defsByCode[attributeCode]
                if (def == null) {
                    missingInProfiles += "$code:$attributeCode"
                } else if (!def.requiredForOffer) {
                    notRequiredInProfiles += "$code:$attributeCode"
                }
            }
            requiredForFacets.forEach { attributeCode ->
                val def = defsByCode[attributeCode]
                if (def == null || !def.facetEnabled) {
                    notFacetEnabledInProfiles += "$code:$attributeCode"
                }
            }
        }

        val stage3FoodFacetsByKey = CatalogSeed.facetDefinitions
            .asSequence()
            .filter { definition ->
                definition.valueType != FacetDataType.TEXT &&
                    definition.appliesToCategoryCodes.any { it in foodLeafCodes }
            }
            .associateBy { it.facetKey }
        val missingStage3FacetDefs = requiredForFacets
            .filterNot { key -> key in stage3FoodFacetsByKey }

        assertTrue(
            "FOOD required attributes missing in profiles: ${missingInProfiles.take(10)}",
            missingInProfiles.isEmpty(),
        )
        assertTrue(
            "FOOD required attributes must be requiredForOffer=true: ${notRequiredInProfiles.take(10)}",
            notRequiredInProfiles.isEmpty(),
        )
        assertTrue(
            "FOOD required attributes must be facetEnabled=true: ${notFacetEnabledInProfiles.take(10)}",
            notFacetEnabledInProfiles.isEmpty(),
        )
        assertTrue(
            "Stage3 facet_definitions missing FOOD production-grade facets: ${missingStage3FacetDefs.joinToString()}",
            missingStage3FacetDefs.isEmpty(),
        )
    }

    @Test
    fun every_preset_must_have_collection_anchor_with_same_category() {
        val anchorsByPreset = CatalogSeed.facetCollections
            .asSequence()
            .mapNotNull { collection ->
                collection.presetCode
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { presetCode -> presetCode to collection.categoryCode.trim() }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )

        val missingAnchors = mutableListOf<String>()
        val mismatchedAnchors = mutableListOf<String>()

        CatalogSeed.facetPresets.forEach { preset ->
            val presetCode = preset.presetCode.trim()
            val categoryCode = preset.categoryCode.trim()
            val anchorCategories = anchorsByPreset[presetCode].orEmpty()
            if (anchorCategories.isEmpty()) {
                missingAnchors += presetCode
            } else if (anchorCategories.none { it.equals(categoryCode, ignoreCase = true) }) {
                mismatchedAnchors += "$presetCode:${anchorCategories.joinToString("|")}"
            }
        }

        assertTrue(
            "Each preset must have at least one collection anchor. Missing: ${missingAnchors.joinToString()}",
            missingAnchors.isEmpty(),
        )
        assertTrue(
            "Preset anchors must match preset category. Mismatched: ${mismatchedAnchors.joinToString()}",
            mismatchedAnchors.isEmpty(),
        )
    }

    @Test
    fun top_leaf_categories_must_not_contain_stage3_stub_presets() {
        val topLeafCategories = CatalogSeedResourceReader.readJson(
            resourcePath = TOP_LEAF_CATEGORIES_PATH,
            deserializer = ListSerializer(String.serializer()),
        )
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val stubPresetsInTopCategories = CatalogSeed.facetPresets
            .asSequence()
            .filter { preset ->
                preset.categoryCode.trim() in topLeafCategories &&
                    preset.notes?.trim().orEmpty().equals(STAGE3_STUB_NOTE, ignoreCase = true)
            }
            .map { preset -> preset.presetCode.trim() }
            .sorted()
            .toList()

        assertTrue(
            "Top leaf category presets must not be marked as Stage 3.0 stubs: ${stubPresetsInTopCategories.joinToString()}",
            stubPresetsInTopCategories.isEmpty(),
        )
    }

    @Test
    fun targeted_ab_pass_presets_must_not_be_stage3_stubs() {
        val stubbedTargetPresets = CatalogSeed.facetPresets
            .asSequence()
            .filter { preset ->
                preset.presetCode.trim() in AB_PASS_TARGET_PRESETS &&
                    preset.notes?.trim().orEmpty().equals(STAGE3_STUB_NOTE, ignoreCase = true)
            }
            .map { preset -> preset.presetCode.trim() }
            .sorted()
            .toList()

        assertTrue(
            "A/B passed target presets must not remain Stage 3.0 stubs: ${stubbedTargetPresets.joinToString()}",
            stubbedTargetPresets.isEmpty(),
        )
    }

    @Test
    fun stage3_presets_must_not_contain_stub_markers() {
        val stubbedPresets = CatalogSeed.facetPresets
            .asSequence()
            .filter { preset ->
                preset.notes?.trim().orEmpty().equals(STAGE3_STUB_NOTE, ignoreCase = true)
            }
            .map { preset -> preset.presetCode.trim() }
            .sorted()
            .toList()

        assertTrue(
            "Stage3 preset catalog must be fully production-ready without stub markers: ${stubbedPresets.joinToString()}",
            stubbedPresets.isEmpty(),
        )
    }

    private fun leafCategoryCodes(categories: List<Category>): Set<String> {
        val categoryCodes = categories
            .asSequence()
            .map { it.code.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val parentCodes = categories
            .asSequence()
            .mapNotNull { it.parentCode?.trim()?.takeIf(String::isNotBlank) }
            .toSet()
        return categoryCodes - parentCodes
    }

    private companion object {
        private val OFFER_FACET_ALLOWLIST = setOf("price")
        private const val TOP_LEAF_CATEGORIES_PATH = "taxonomy/stage3/3.0/top_leaf_categories.json"
        private const val STAGE3_STUB_NOTE = "Stage 3.0 baseline preset"
        private val AB_PASS_TARGET_PRESETS = setOf(
            "FP.FOOD.READY.DEFAULT",
            "FP.FOOD.READY.PIZZA",
            "FP.FOOD.READY.SUSHI",
            "FP.TECH.PHONES.DEFAULT",
        )
    }
}
