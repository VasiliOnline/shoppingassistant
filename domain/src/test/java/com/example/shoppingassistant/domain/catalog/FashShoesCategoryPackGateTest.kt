package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FashShoesCategoryPackGateTest {
    @Test
    fun shoes_category_pack_artifacts_are_checked_in_with_pass_report() {
        val basePath = "taxonomy/stage2/2.2/FASH/category_packs/fash_shoes/v1_0"
        val expectedFiles = listOf(
            "schema_pack.fash_shoes.v1_0.json",
            "attributes.fash_shoes.v1_0.tsv",
            "values.fash_shoes.v1_0.tsv",
            "aliases.fash_shoes.ru.v1_0.tsv",
            "conditional_rules.fash_shoes.v1_0.yaml",
            "size_policy.fash_shoes.v1_0.yaml",
            "route_conflict_layer.fash.v1_0.yaml",
            "routing_guardrails.fash_shoes.v1_0.yaml",
            "vision_rules.fash_shoes.v1_0.yaml",
            "user_surface.fash_shoes.v1_0.yaml",
            "facet_schema.fash_shoes.v1_0.yaml",
            "golden_queries.fash_shoes.v1_0.tsv",
            "sample_offers.fash_shoes.v1_0.jsonl",
            "integration_mapping.fash_shoes.v1_0.yaml",
            "quality_gates.fash_shoes.v1_0.yaml",
            "validation_report.fash_shoes.v1_0.json",
            "checksums.sha256.json",
            "README_FASH_SHOES_PRODUCTION_v1_0_RU.md",
        )

        expectedFiles.forEach { fileName ->
            assertTrue(
                "$fileName must be present in the FASH.SHOES production pack.",
                CatalogSeedResourceReader.readText("$basePath/$fileName").isNotBlank(),
            )
        }

        val schemaPack = CatalogSeedResourceReader.readText("$basePath/schema_pack.fash_shoes.v1_0.json")
        val validationReport = CatalogSeedResourceReader.readText("$basePath/validation_report.fash_shoes.v1_0.json")
        val checksums = CatalogSeedResourceReader.readText("$basePath/checksums.sha256.json")

        assertTrue(schemaPack.contains("\"categoryCode\": \"FASH.SHOES\""))
        assertTrue(schemaPack.contains("\"pack_type\": \"category_schema_pack\""))
        assertTrue(schemaPack.contains("\"maturity_mode\": \"brand-aware + optional model_name_text\""))
        assertTrue(schemaPack.contains("\"enabled\": false"))
        assertTrue(validationReport.contains("\"status\": \"PASS\""))
        assertTrue(validationReport.contains("\"attribute_count\": 37"))
        assertTrue(validationReport.contains("\"value_row_count\": 186"))
        assertTrue(validationReport.contains("\"value_sets_count\": 22"))
        assertTrue(validationReport.contains("\"alias_count\": 520"))
        assertTrue(validationReport.contains("\"golden_query_count\": 82"))
        assertTrue(checksums.contains("\"algorithm\": \"sha256\""))
        assertTrue(checksums.contains("\"schema_pack.fash_shoes.v1_0.json\""))
    }

    @Test
    fun shoes_effective_spec_matches_production_pack_shape() {
        val spec = shoesEffectiveSpec()
        val attributes = spec.attributes.map { it.attributeCode }.toSet()

        assertFalse("FASH.SHOES must use a product-ready materialized profile.", spec.meta.isFallback)
        assertEquals(37, attributes.size)
        listOf(
            "shoe_type",
            "target_gender",
            "age_group",
            "brand",
            "model_name_text",
            "size_eu",
            "size_ru",
            "raw_size_text",
            "foot_length_cm",
            "insole_length_cm",
            "condition",
            "pair_completeness",
            "material_upper",
            "waterproof_level",
            "authenticity_status",
        ).forEach { attribute ->
            assertTrue("FASH.SHOES must expose '$attribute'.", attribute in attributes)
        }

        assertEquals(
            setOf("shoe_type", "condition", "pair_completeness"),
            spec.attributes.filter { it.required }.map { it.attributeCode }.toSet(),
        )
        assertTrue("shoe_type" in spec.identityAttributes)
        assertTrue("size_eu" in spec.identityAttributes)
        assertTrue("model_name_text" in spec.identityAttributes)
        assertTrue("shoe_type" in spec.facetAttributes)
        assertTrue("target_gender" in spec.facetAttributes)
        assertTrue("raw_size_text" !in spec.facetAttributes)
    }

    @Test
    fun shoes_constraints_keep_pack_value_surface_and_required_rules() {
        val spec = shoesEffectiveSpec()

        assertEquals(
            setOf("MEN", "WOMEN", "BOYS", "GIRLS", "KIDS", "UNISEX"),
            spec.constraints.allowedValueCodesByAttribute["target_gender"].orEmpty().toSet(),
        )
        assertEquals(
            setOf("NEW", "LIKE_NEW", "GOOD", "FAIR", "HEAVILY_USED", "DEFECTS_OR_REPAIR"),
            spec.constraints.allowedValueCodesByAttribute["condition"].orEmpty().toSet(),
        )
        assertEquals(
            setOf("PAIR", "LEFT_ONLY", "RIGHT_ONLY", "MISMATCHED"),
            spec.constraints.allowedValueCodesByAttribute["pair_completeness"].orEmpty().toSet(),
        )

        val allowedShoeTypes = spec.constraints.allowedValueCodesByAttribute["shoe_type"].orEmpty().toSet()
        assertEquals(26, allowedShoeTypes.size)
        listOf("SNEAKERS", "RUNNING_SHOES", "WINTER_BOOTS", "RUBBER_BOOTS", "KIDS_SHOES", "BABY_SHOES").forEach { value ->
            assertTrue("$value must be accepted by FASH.SHOES shoe_type.", value in allowedShoeTypes)
        }

        assertTrue(
            "RUNNING_SHOES must require sport_purpose.",
            spec.constraints.requiredIfRules.any { rule ->
                rule.requireAttributeCode == "sport_purpose" &&
                    rule.whenAll.any { condition ->
                        condition.attributeCode == "shoe_type" && condition.valueCode == "RUNNING_SHOES"
                    }
            },
        )
        assertTrue(
            "FOOTBALL_BOOTS must require sole_type.",
            spec.constraints.requiredIfRules.any { rule ->
                rule.requireAttributeCode == "sole_type" &&
                    rule.whenAll.any { condition ->
                        condition.attributeCode == "shoe_type" && condition.valueCode == "FOOTBALL_BOOTS"
                    }
            },
        )
        assertTrue(
            "Defective shoes must require defect_notes.",
            CatalogSeed.categoryWriteSpecs.first { it.category.code == "FASH.SHOES" }.requiredIfRules.any { rule ->
                rule.requiredAttributeCode == "defect_notes" &&
                    rule.whenAll.any { condition ->
                        condition.attributeCode == "condition" && "DEFECTS_OR_REPAIR" in condition.values
                    }
            },
        )

        val shoesConstraint = CatalogSeed.constraints.first {
            it.scope == ConstraintScope.CATEGORY && it.categoryCode == "FASH.SHOES"
        }
        assertTrue(shoesConstraint.compatibilityRules.isNotEmpty())
        assertTrue(
            shoesConstraint.compatibilityRules.any { rule ->
                rule.whenAll.any { condition ->
                    condition.attributeCode == "shoe_type" && "RUNNING_SHOES" in condition.values
                }
            },
        )
    }

    @Test
    fun shoes_surface_and_vision_guardrails_are_declared() {
        val basePath = "taxonomy/stage2/2.2/FASH/category_packs/fash_shoes/v1_0"
        val sizePolicy = CatalogSeedResourceReader.readText("$basePath/size_policy.fash_shoes.v1_0.yaml")
        val visionRules = CatalogSeedResourceReader.readText("$basePath/vision_rules.fash_shoes.v1_0.yaml")
        val routingGuardrails = CatalogSeedResourceReader.readText("$basePath/routing_guardrails.fash_shoes.v1_0.yaml")
        val routeConflictLayer = CatalogSeedResourceReader.readText("$basePath/route_conflict_layer.fash.v1_0.yaml")

        assertTrue(sizePolicy.contains("primary: size_eu"))
        assertTrue(sizePolicy.contains("secondary: raw_size_text"))
        assertTrue(sizePolicy.contains("NO_VISUAL_SIZE_INFERENCE"))
        assertTrue(sizePolicy.contains("never_infer_target_gender_from_color_only"))
        assertTrue(visionRules.contains("attribute: size_eu"))
        assertTrue(visionRules.contains("attribute: target_gender"))
        assertTrue(visionRules.contains("attribute: brand"))
        assertTrue(visionRules.contains("attribute: authenticity_status"))
        assertTrue(routingGuardrails.contains("route_to: FASH.BAGS"))
        assertTrue(routingGuardrails.contains("route_to: FASH.ACCESSORIES"))
        assertTrue(routingGuardrails.contains("route_to: TECH.WEARABLES"))
        assertTrue(routeConflictLayer.contains("FASH.SHOES"))
        assertTrue(routeConflictLayer.contains("FASH.BAGS"))
    }

    @Test
    fun shoes_presentation_profile_promotes_core_facets_and_hides_evidence_fields() {
        val presentation = CatalogFacetPresentationProfiles.resolve("FASH.SHOES")

        requireNotNull(presentation)
        assertEquals("shoe_type", presentation.mainTypedFacetKeys.first())
        listOf("shoe_type", "target_gender", "size_eu", "brand", "condition").forEach { facet ->
            assertTrue("$facet must be a main FASH.SHOES facet.", facet in presentation.mainTypedFacetKeys)
        }
        listOf("age_group", "color_primary", "season", "material_upper", "sport_purpose", "pair_completeness").forEach { facet ->
            assertTrue("$facet must be an additional FASH.SHOES facet.", facet in presentation.additionalTypedFacetKeys)
        }
        listOf("raw_size_text", "size_ru", "child_height_cm", "defect_notes", "authenticity_status").forEach { facet ->
            assertTrue("$facet must be hidden from primary FASH.SHOES UI.", facet in presentation.hiddenTypedFacetKeys)
        }
        assertTrue("model_name_text" in presentation.requiresBrandContextTypedFacetKeys)
    }

    private fun shoesEffectiveSpec(): Stage22EffectiveCategorySpec {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        return engine.getEffectiveSpec("FASH.SHOES")
    }
}
