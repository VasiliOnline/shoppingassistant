package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FashMenChildOverrideGateTest {
    @Test
    fun men_child_pack_is_override_only_and_extends_common() {
        val profilesRaw = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/profiles.fash.json")
        val sharedRaw = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/shared_profiles.fash.json")
        val schemaPack = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_men/v1_0/schema_pack.fash_men.v1_0.json",
        )

        assertTrue(profilesRaw.contains("\"code\": \"FASH.MEN\""))
        assertTrue(profilesRaw.contains("\"extendsProfiles\""))
        assertTrue(profilesRaw.contains("\"FASH.APPAREL_COMMON\""))
        assertTrue(sharedRaw.contains("\"profileCode\": \"FASH.APPAREL_COMMON\""))
        assertTrue(schemaPack.contains("\"artifact_type\": \"child_override_pack\""))
        assertTrue(schemaPack.contains("\"no_duplicate_common_attribute_codes\": true"))

        val fashPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "FASH" }
        val rawMenProfile = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/profiles.fash.json")
        assertTrue("Raw child profile must be thin and inherit common attributes.", rawMenProfile.contains("\"attributes\": []"))
        assertEquals(61, fashPackage.profiles.first { it.category.code == "FASH.MEN" }.attributes.size)
    }

    @Test
    fun men_effective_spec_matches_child_override_snapshot_shape() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        val spec = engine.getEffectiveSpec("FASH.MEN")
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
        ).forEach { attribute ->
            assertTrue("FASH.MEN must inherit '$attribute' from common apparel standard.", attribute in attributes)
        }

        assertEquals(listOf("ADULT", "TEEN"), spec.constraints.allowedValueCodesByAttribute["age_group"])
        assertEquals(listOf("MEN", "UNISEX"), spec.constraints.allowedValueCodesByAttribute["target_gender"])
        assertEquals(36, spec.constraints.allowedValueCodesByAttribute["apparel_type"]?.size)

        val allowedTypes = spec.constraints.allowedValueCodesByAttribute["apparel_type"].orEmpty().toSet()
        listOf("DRESS", "SKIRT", "BRA", "BLOUSE", "ROMPER", "TOP", "TANK_TOP", "TIGHTS", "JUMPSUIT").forEach { blocked ->
            assertFalse("$blocked must not be a primary accepted FASH.MEN apparel_type.", blocked in allowedTypes)
        }
    }

    @Test
    fun men_defaults_and_surface_contracts_are_declared() {
        val childDefaults = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_men/v1_0/child_defaults.fash_men.v1_0.yaml",
        )
        val userSurface = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_men/v1_0/user_surface.fash_men.v1_0.yaml",
        )
        val visionRules = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_men/v1_0/vision_rules.fash_men.v1_0.yaml",
        )

        assertTrue(childDefaults.contains("default: MEN"))
        assertTrue(childDefaults.contains("default: ADULT"))
        assertTrue(userSurface.contains("initial_form_limit: 8"))
        assertTrue(userSurface.contains("common_hidden_defaults:"))
        assertTrue(visionRules.contains("dark color"))
        assertTrue(visionRules.contains("must_return_NEED_MORE_CONTEXT_for_weak_gender_evidence: true"))

        val presentation = CatalogFacetPresentationProfiles.resolve("FASH.MEN")
        requireNotNull(presentation)
        assertEquals("apparel_type", presentation.mainTypedFacetKeys.first())
        assertTrue("condition" in presentation.mainTypedFacetKeys)
        assertTrue("bra_cup" in presentation.hiddenTypedFacetKeys)
        assertTrue("age_group" in presentation.hiddenTypedFacetKeys)
    }

    @Test
    fun men_category_keeps_category_level_compatibility_rule() {
        val menConstraint = CatalogSeed.constraints.first {
            it.scope == ConstraintScope.CATEGORY && it.categoryCode == "FASH.MEN"
        }

        assertTrue(menConstraint.compatibilityRules.isNotEmpty())
        assertTrue(
            menConstraint.compatibilityRules.any { rule ->
                rule.whenAll.any { condition ->
                    condition.attributeCode == "target_gender" && "MEN" in condition.values
                }
            },
        )
    }
}
