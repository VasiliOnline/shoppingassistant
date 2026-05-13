package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FashBagsCategoryPackGateTest {
    @Test
    fun bags_category_pack_artifacts_are_checked_in_with_pass_report() {
        val basePath = "taxonomy/stage2/2.2/FASH/overrides/fash_bags/v1_0"
        val expectedFiles = listOf(
            "schema_pack.fash_bags.v1_0.json",
            "attributes.fash_bags.v1_0.tsv",
            "values.fash_bags.v1_0.tsv",
            "aliases.fash_bags.ru.v1_0.tsv",
            "conditional_rules.fash_bags.v1_0.yaml",
            "size_policy.fash_bags.v1_0.yaml",
            "routing_guardrails.fash_bags.v1_0.yaml",
            "route_conflict_layer.fash_bags.v1_0.yaml",
            "vision_rules.fash_bags.v1_0.yaml",
            "user_surface.fash_bags.v1_0.yaml",
            "facet_schema.fash_bags.v1_0.yaml",
            "golden_queries.fash_bags.v1_0.tsv",
            "sample_offers.fash_bags.v1_0.jsonl",
            "integration_mapping.fash_bags.v1_0.yaml",
            "quality_gates.fash_bags.v1_0.yaml",
            "validation_report.fash_bags.v1_0.json",
            "checksums.sha256.json",
            "README_FASH_BAGS_PRODUCTION_v1_0_RU.md",
        )

        expectedFiles.forEach { fileName ->
            assertTrue(
                "$fileName must be present in the FASH.BAGS production pack.",
                CatalogSeedResourceReader.readText("$basePath/$fileName").isNotBlank(),
            )
        }

        val schemaPack = CatalogSeedResourceReader.readText("$basePath/schema_pack.fash_bags.v1_0.json")
        val validationReport = CatalogSeedResourceReader.readText("$basePath/validation_report.fash_bags.v1_0.json")
        val routeConflictLayer = CatalogSeedResourceReader.readText("$basePath/route_conflict_layer.fash_bags.v1_0.yaml")
        val checksums = CatalogSeedResourceReader.readText("$basePath/checksums.sha256.json")

        assertTrue(schemaPack.contains("\"categoryCode\": \"FASH.BAGS\""))
        assertTrue(schemaPack.contains("\"pack_type\": \"category_schema_pack\""))
        assertTrue(schemaPack.contains("\"maturity_mode\": \"brand-aware + optional model_name_text; no curated brand/family/model graph\""))
        assertTrue(schemaPack.contains("\"wallet_cardholder_policy\""))
        assertTrue(validationReport.contains("\"status\": \"PASS\""))
        assertTrue(validationReport.contains("\"attributes_count\": 55"))
        assertTrue(validationReport.contains("\"value_rows_count\": 236"))
        assertTrue(validationReport.contains("\"alias_rows_count\": 149"))
        assertTrue(validationReport.contains("\"golden_queries_count\": 46"))
        assertTrue(routeConflictLayer.contains("wallet/cardholder"))
        assertTrue(routeConflictLayer.contains("phone_case"))
        assertTrue(routeConflictLayer.contains("winner: FASH.BAGS"))
        assertTrue(routeConflictLayer.contains("winner: TECH.ACCESSORIES"))
        assertTrue(checksums.contains("\"algorithm\": \"SHA-256\""))
        assertTrue(checksums.contains("\"schema_pack.fash_bags.v1_0.json\""))
    }

    @Test
    fun bags_effective_spec_matches_production_pack_shape() {
        val spec = bagsEffectiveSpec()
        val attributes = spec.attributes.map { it.attributeCode }.toSet()

        assertFalse("FASH.BAGS must use a product-ready materialized profile.", spec.meta.isFallback)
        assertEquals(55, attributes.size)
        listOf(
            "bag_type",
            "brand",
            "model_name_text",
            "condition",
            "material_outer",
            "color_primary",
            "size_class",
            "fits_laptop_inch",
            "authenticity_state",
            "defect_notes",
        ).forEach { attribute ->
            assertTrue("FASH.BAGS must expose '$attribute'.", attribute in attributes)
        }

        assertEquals(
            setOf("bag_type", "condition"),
            spec.attributes.filter { it.required }.map { it.attributeCode }.toSet(),
        )
        assertTrue("bag_type" in spec.identityAttributes)
        assertTrue("brand" in spec.identityAttributes)
        assertTrue("model_name_text" in spec.identityAttributes)
        assertTrue("bag_type" in spec.facetAttributes)
        assertTrue("material_outer" in spec.facetAttributes)
        assertTrue("defect_notes" !in spec.facetAttributes)
    }

    @Test
    fun bags_constraints_keep_pack_value_surface_and_required_rules() {
        val spec = bagsEffectiveSpec()

        val allowedBagTypes = spec.constraints.allowedValueCodesByAttribute["bag_type"].orEmpty().toSet()
        assertEquals(22, allowedBagTypes.size)
        listOf(
            "BACKPACK",
            "SCHOOL_BAG",
            "HANDBAG",
            "LAPTOP_BAG",
            "LAPTOP_SLEEVE",
            "SUITCASE",
            "WALLET",
            "CARDHOLDER",
            "DRAWSTRING_BAG",
        ).forEach { value ->
            assertTrue("$value must be accepted by FASH.BAGS bag_type.", value in allowedBagTypes)
        }

        assertEquals(
            setOf("NEW_WITH_TAGS", "NEW_WITHOUT_TAGS", "LIKE_NEW", "EXCELLENT", "GOOD", "FAIR", "FOR_REPAIR"),
            spec.constraints.allowedValueCodesByAttribute["condition"].orEmpty().toSet(),
        )
        assertEquals(
            setOf("NOT_CLAIMED", "ORIGINAL_CLAIMED", "ORIGINAL_WITH_DOCS", "AUTHENTICITY_UNCERTAIN"),
            spec.constraints.allowedValueCodesByAttribute["authenticity_state"].orEmpty().toSet(),
        )
        val writeSpec = CatalogSeed.categoryWriteSpecs.first { it.category.code == "FASH.BAGS" }
        assertTrue(
            "Worn or repair-state bags must require defect_notes.",
            writeSpec.requiredIfRules.any { rule ->
                rule.requiredAttributeCode == "defect_notes" &&
                    rule.whenAll.any { condition ->
                        condition.attributeCode == "condition" &&
                            setOf("FAIR", "FOR_REPAIR").all { value -> value in condition.values }
                    }
            },
        )

        val bagsConstraint = CatalogSeed.constraints.first {
            it.scope == ConstraintScope.CATEGORY && it.categoryCode == "FASH.BAGS"
        }
        assertTrue(
            bagsConstraint.compatibilityRules.any { rule ->
                rule.whenAll.any { condition ->
                    condition.attributeCode == "bag_type" && "WALLET" in condition.values && "CARDHOLDER" in condition.values
                }
            },
        )
    }

    @Test
    fun bags_presentation_profile_promotes_core_facets_and_hides_evidence_fields() {
        val presentation = CatalogFacetPresentationProfiles.resolve("FASH.BAGS")

        requireNotNull(presentation)
        assertEquals("bag_type", presentation.mainTypedFacetKeys.first())
        listOf("bag_type", "brand", "condition", "color_primary", "material_outer", "size_class").forEach { facet ->
            assertTrue("$facet must be a main FASH.BAGS facet.", facet in presentation.mainTypedFacetKeys)
        }
        listOf("target_gender", "usage_context", "strap_type", "volume_liter", "fits_laptop_inch").forEach { facet ->
            assertTrue("$facet must be an additional FASH.BAGS facet.", facet in presentation.additionalTypedFacetKeys)
        }
        listOf("strap_count", "handle_drop_cm", "strap_length_cm", "set_includes", "defect_notes").forEach { facet ->
            assertTrue("$facet must be hidden from primary FASH.BAGS UI.", facet in presentation.hiddenTypedFacetKeys)
        }
        assertTrue("model_name_text" in presentation.requiresBrandContextTypedFacetKeys)
    }

    @Test
    fun bags_route_conflict_cases_follow_pack_policy() = runBlocking {
        val router = Stage21FashQueryRouter()
        val cases = mapOf(
            "рюкзак nike городской" to "FASH.BAGS",
            "чехол для ноутбука 13 дюймов" to "FASH.BAGS",
            "кошелек женский красный кожаный" to "FASH.BAGS",
            "кардхолдер мужской черный" to "FASH.BAGS",
            "чехол для телефона iphone 15" to "TECH.PHONE_ACCESSORIES",
            "сумка переноска для кошки" to "PETS.ACCESSORIES",
            "сумка для инструментов bosch" to "HOME.REPAIR_TOOLS",
            "фоторюкзак canon" to "TECH.CAMERAS",
            "реплика gucci сумка" to "POLICY_REVIEW",
            "мешок для обуви школьный" to "FASH.BAGS",
        )

        cases.forEach { (query, expected) ->
            val result = router.route(query = query, locale = "ru-RU")
            assertEquals("query='$query'", QueryRouteType.OPEN_CATEGORY, result.routeType)
            assertEquals("query='$query'", expected, result.primaryTargetCode)
        }
    }

    private fun bagsEffectiveSpec(): Stage22EffectiveCategorySpec {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        return engine.getEffectiveSpec("FASH.BAGS")
    }
}
