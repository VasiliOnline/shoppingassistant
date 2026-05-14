package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FashAccessoriesProductionPackGateTest {
    @Test
    fun accessories_production_pack_artifacts_are_checked_in_with_pass_report() {
        val expectedFiles = listOf(
            "schema_pack.fash_accessories.v1_0.json",
            "attributes.fash_accessories.v1_0.tsv",
            "values.fash_accessories.v1_0.tsv",
            "aliases.fash_accessories.ru.v1_0.tsv",
            "conditional_rules.fash_accessories.v1_0.yaml",
            "routing_guardrails.fash_accessories.v1_0.yaml",
            "route_conflict_layer.fash_common.v1_0.yaml",
            "vision_rules.fash_accessories.v1_0.yaml",
            "user_surface.fash_accessories.v1_0.yaml",
            "facet_schema.fash_accessories.v1_0.yaml",
            "golden_queries.fash_accessories.v1_0.tsv",
            "sample_offers.fash_accessories.v1_0.jsonl",
            "integration_mapping.fash_accessories.v1_0.yaml",
            "quality_gates.fash_accessories.v1_0.yaml",
            "validation_report.fash_accessories.v1_0.json",
            "checksums.sha256.json",
            "manifest.fash_accessories.v1_0.json",
            "README_FASH_ACCESSORIES_PRODUCTION_v1_0_RU.md",
        )

        expectedFiles.forEach { fileName ->
            assertTrue(
                "$fileName must be present in the FASH.ACCESSORIES production pack.",
                CatalogSeedResourceReader.readText("$BASE_PATH/$fileName").isNotBlank(),
            )
        }

        val schemaPack = CatalogSeedResourceReader.readText("$BASE_PATH/schema_pack.fash_accessories.v1_0.json")
        val validationReport = CatalogSeedResourceReader.readText("$BASE_PATH/validation_report.fash_accessories.v1_0.json")
        val manifest = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.fash_accessories.v1_0.json")
        val checksums = CatalogSeedResourceReader.readText("$BASE_PATH/checksums.sha256.json")

        assertTrue(schemaPack.contains("\"categoryCode\": \"FASH.ACCESSORIES\""))
        assertTrue(schemaPack.contains("\"pack_type\": \"category_schema_pack\""))
        assertTrue(schemaPack.contains("\"residual_policy_ru\""))
        assertTrue(validationReport.contains("\"status\": \"PASS\""))
        assertTrue(validationReport.contains("\"attributes\": 67"))
        assertTrue(validationReport.contains("\"value_rows\": 298"))
        assertTrue(validationReport.contains("\"aliases\": 157"))
        assertTrue(validationReport.contains("\"golden_queries\": 54"))
        assertTrue(manifest.contains("\"file_count_expected\": 18"))
        assertTrue(checksums.contains("\"algorithm\": \"sha256\""))
        assertTrue(checksums.contains("\"schema_pack.fash_accessories.v1_0.json\""))
    }

    @Test
    fun accessories_effective_spec_matches_production_pack_shape() {
        val spec = accessoriesEffectiveSpec()
        val attributes = spec.attributes.map { it.attributeCode }.toSet()

        assertFalse("FASH.ACCESSORIES must use a product-ready materialized profile.", spec.meta.isFallback)
        assertEquals(67, attributes.size)
        listOf(
            "accessory_type",
            "accessory_group",
            "target_gender",
            "brand",
            "model_name_text",
            "condition",
            "color",
            "material_primary",
            "metal_type",
            "movement_type",
            "umbrella_type",
            "route_confidence",
        ).forEach { attribute ->
            assertTrue("FASH.ACCESSORIES must expose '$attribute'.", attribute in attributes)
        }

        assertEquals(
            setOf("accessory_type", "condition"),
            spec.attributes.filter { it.required }.map { it.attributeCode }.toSet(),
        )
        assertTrue("accessory_type" in spec.identityAttributes)
        assertTrue("brand" in spec.identityAttributes)
        assertTrue("model_name_text" in spec.identityAttributes)
        assertTrue("accessory_type" in spec.facetAttributes)
        assertTrue("condition" in spec.facetAttributes)
        assertTrue("material_primary" in spec.facetAttributes)
        assertFalse("Route confidence is a routing evidence field, not a runtime facet.", "route_confidence" in spec.facetAttributes)
    }

    @Test
    fun accessories_constraints_keep_residual_value_surface_and_required_rules() {
        val spec = accessoriesEffectiveSpec()

        val allowedAccessoryTypes = spec.constraints.allowedValueCodesByAttribute["accessory_type"].orEmpty().toSet()
        assertEquals(45, allowedAccessoryTypes.size)
        listOf("WATCHES_NON_SMART", "WATCH_STRAP", "RING", "UMBRELLA", "KEYCHAIN").forEach { value ->
            assertTrue("$value must be accepted by FASH.ACCESSORIES accessory_type.", value in allowedAccessoryTypes)
        }

        assertEquals(
            setOf("NEW_WITH_TAGS", "NEW_WITHOUT_TAGS", "LIKE_NEW", "GOOD", "ACCEPTABLE", "FOR_PARTS"),
            spec.constraints.allowedValueCodesByAttribute["condition"].orEmpty().toSet(),
        )

        val requiredRules = CatalogSeed.categoryWriteSpecs.first { it.category.code == CATEGORY }.requiredIfRules
        assertTrue(requiredRules.requires("belt_size_cm", "accessory_type", "BELT"))
        assertTrue(requiredRules.requires("glove_size", "accessory_type", "GLOVES"))
        assertTrue(requiredRules.requires("ring_size_ru", "accessory_type", "RING"))
        assertTrue(requiredRules.requires("watch_strap_lug_width_mm", "accessory_type", "WATCH_STRAP"))
        assertTrue(requiredRules.requires("umbrella_type", "accessory_type", "UMBRELLA"))
    }

    @Test
    fun accessories_presentation_profile_promotes_core_facets_and_hides_route_confidence() {
        val presentation = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        requireNotNull(presentation)
        assertEquals("accessory_type", presentation.mainTypedFacetKeys.first())
        listOf("accessory_type", "target_gender", "age_group", "brand", "condition", "color", "material_primary").forEach { facet ->
            assertTrue("$facet must be a main FASH.ACCESSORIES facet.", facet in presentation.mainTypedFacetKeys)
        }
        listOf("metal_type", "movement_type", "umbrella_type", "watch_strap_lug_width_mm").forEach { facet ->
            assertTrue("$facet must be an additional FASH.ACCESSORIES facet.", facet in presentation.additionalTypedFacetKeys)
        }
        assertTrue("route_confidence" in presentation.hiddenTypedFacetKeys)
        assertTrue("model_name_text" in presentation.requiresBrandContextTypedFacetKeys)
    }

    @Test
    fun accessories_route_guards_keep_residual_branch_clean() = runBlocking {
        val router = Stage21FashQueryRouter()
        val cases = mapOf(
            "женский кожаный ремень gucci 90 см" to "FASH.ACCESSORIES",
            "зонт складной черный" to "FASH.ACCESSORIES",
            "серебряное кольцо 17 размер" to "FASH.ACCESSORIES",
            "кошелек женский кожаный" to "FASH.BAGS",
            "кардхолдер michael kors" to "FASH.BAGS",
            "косметичка victoria secret" to "FASH.BAGS",
            "кроссовки nike air force 1" to "FASH.SHOES",
            "женское платье zara" to "FASH.WOMEN",
            "мужская футболка nike" to "FASH.MEN",
            "детская куртка зимняя" to "FASH.KIDS",
            "apple watch series 9" to "TECH.WEARABLES",
            "ремешок для apple watch 44 мм" to "TECH.WEARABLES",
            "чехол для телефона с ремешком" to "TECH.PHONE_ACCESSORIES",
        )

        cases.forEach { (query, expected) ->
            val result = router.route(query = query, locale = "ru-RU")
            assertEquals("query='$query'", expected, result.primaryTargetCode)
        }
    }

    private fun accessoriesEffectiveSpec(): Stage22EffectiveCategorySpec {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        return engine.getEffectiveSpec(CATEGORY)
    }

    private fun List<RequiredIfRule>.requires(
        requiredAttribute: String,
        attribute: String,
        value: String,
    ): Boolean =
        any { rule ->
            rule.requiredAttributeCode == requiredAttribute &&
                rule.whenAll.any { condition ->
                    condition.attributeCode == attribute && value in condition.values
                }
        }

    private companion object {
        private const val CATEGORY = "FASH.ACCESSORIES"
        private const val BASE_PATH = "taxonomy/stage2/2.2/FASH/overrides/fash_accessories/v1_0"
    }
}
