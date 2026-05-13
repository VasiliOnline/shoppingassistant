package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FashWomenChildOverrideGateTest {
    @Test
    fun women_child_pack_is_override_only_and_extends_common() {
        val profilesRaw = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/profiles.fash.json")
        val sharedRaw = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/shared_profiles.fash.json")
        val schemaPack = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_women/v1_0/schema_pack.fash_women.v1_0.json",
        )

        assertTrue(profilesRaw.contains("\"code\": \"FASH.WOMEN\""))
        assertTrue(profilesRaw.contains("\"extendsProfiles\""))
        assertTrue(profilesRaw.contains("\"FASH.APPAREL_COMMON\""))
        assertTrue(sharedRaw.contains("\"profileCode\": \"FASH.APPAREL_COMMON\""))
        assertTrue(schemaPack.contains("\"artifact_type\": \"child_override_pack\""))
        assertTrue(schemaPack.contains("\"no_duplicate_common_attribute_codes\": true"))

        val fashPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "FASH" }
        assertTrue("Raw child profile must be thin and inherit common attributes.", profilesRaw.contains("\"attributes\": []"))
        assertEquals(61, fashPackage.profiles.first { it.category.code == "FASH.WOMEN" }.attributes.size)
    }

    @Test
    fun women_effective_spec_matches_child_override_snapshot_shape() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        val spec = engine.getEffectiveSpec("FASH.WOMEN")
        val attributes = spec.attributes.map { it.attributeCode }.toSet()

        listOf(
            "apparel_type",
            "condition",
            "size_system",
            "size_label",
            "color_primary",
            "material_primary",
            "brand",
            "target_gender",
            "age_group",
            "fit",
            "season",
            "bra_cup",
        ).forEach { attribute ->
            assertTrue("FASH.WOMEN must inherit '$attribute' from common apparel standard.", attribute in attributes)
        }

        assertEquals(listOf("ADULT", "TEEN"), spec.constraints.allowedValueCodesByAttribute["age_group"])
        assertEquals(listOf("UNISEX", "WOMEN"), spec.constraints.allowedValueCodesByAttribute["target_gender"])
        assertEquals(46, spec.constraints.allowedValueCodesByAttribute["apparel_type"]?.size)

        val allowedTypes = spec.constraints.allowedValueCodesByAttribute["apparel_type"].orEmpty().toSet()
        listOf("DRESS", "SKIRT", "BRA", "BLOUSE", "TOP", "TANK_TOP", "TIGHTS", "SWIMWEAR").forEach { expected ->
            assertTrue("$expected must be a primary accepted FASH.WOMEN apparel_type.", expected in allowedTypes)
        }
        listOf("BOXERS_MALE_ONLY", "BOYS_UNIFORM_ONLY", "JOCKSTRAP").forEach { blocked ->
            assertFalse("$blocked must not be a primary accepted FASH.WOMEN apparel_type.", blocked in allowedTypes)
        }
    }

    @Test
    fun women_defaults_surface_and_vision_contracts_are_declared() {
        val childDefaults = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_women/v1_0/child_defaults.fash_women.v1_0.yaml",
        )
        val userSurface = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_women/v1_0/user_surface.fash_women.v1_0.yaml",
        )
        val visionRules = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_women/v1_0/vision_rules.fash_women.v1_0.yaml",
        )

        assertTrue(childDefaults.contains("default: WOMEN"))
        assertTrue(childDefaults.contains("default: ADULT"))
        assertTrue(userSurface.contains("initial_form_limit: 8"))
        assertTrue(userSurface.contains("advanced_field_groups:"))
        assertTrue(visionRules.contains("must_not_set_WOMEN_if_only:"))
        assertTrue(visionRules.contains("pink_color"))
        assertTrue(visionRules.contains("target_gender_for_unisex_basics"))

        val presentation = CatalogFacetPresentationProfiles.resolve("FASH.WOMEN")
        requireNotNull(presentation)
        assertEquals("apparel_type", presentation.mainTypedFacetKeys.first())
        assertTrue("condition" in presentation.mainTypedFacetKeys)
        assertTrue("bra_cup" in presentation.additionalTypedFacetKeys)
        assertFalse("bra_cup must remain available for women underwear/swimwear filters.", "bra_cup" in presentation.hiddenTypedFacetKeys)
        assertTrue("age_group" in presentation.hiddenTypedFacetKeys)
    }

    @Test
    fun women_category_keeps_category_level_compatibility_rule() {
        val womenConstraint = CatalogSeed.constraints.first {
            it.scope == ConstraintScope.CATEGORY && it.categoryCode == "FASH.WOMEN"
        }

        assertTrue(womenConstraint.compatibilityRules.isNotEmpty())
        assertTrue(
            womenConstraint.compatibilityRules.any { rule ->
                rule.whenAll.any { condition ->
                    condition.attributeCode == "target_gender" && "WOMEN" in condition.values
                }
            },
        )
    }
}
