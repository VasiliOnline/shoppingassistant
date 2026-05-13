package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FashKidsChildOverrideGateTest {
    @Test
    fun kids_child_pack_is_override_only_and_extends_common() {
        val profilesRaw = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/profiles.fash.json")
        val sharedRaw = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/shared_profiles.fash.json")
        val schemaPack = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/schema_pack.fash_kids.v1_0.json",
        )
        val validationReport = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/validation_report.fash_kids.v1_0.json",
        )

        assertTrue(profilesRaw.contains("\"code\": \"FASH.KIDS\""))
        assertTrue(profilesRaw.contains("\"extendsProfiles\""))
        assertTrue(profilesRaw.contains("\"FASH.APPAREL_COMMON\""))
        assertTrue(sharedRaw.contains("\"profileCode\": \"FASH.APPAREL_COMMON\""))
        assertTrue(schemaPack.contains("\"pack_type\": \"child_override_pack\""))
        assertTrue(schemaPack.contains("\"must_not_duplicate_common_attributes\": true"))
        assertTrue(validationReport.contains("\"aliases\": 84"))
        assertTrue(validationReport.contains("\"golden_queries\": 63"))

        val fashPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "FASH" }
        assertTrue("Raw child profile must be thin and inherit common attributes.", profilesRaw.contains("\"attributes\": []"))
        assertEquals(61, fashPackage.profiles.first { it.category.code == "FASH.KIDS" }.attributes.size)
    }

    @Test
    fun kids_effective_spec_matches_child_override_snapshot_shape() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )
        val spec = engine.getEffectiveSpec("FASH.KIDS")
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
            assertTrue("FASH.KIDS must inherit '$attribute' from common apparel standard.", attribute in attributes)
        }

        assertEquals(
            setOf("BOYS", "GIRLS", "KIDS", "UNISEX"),
            spec.constraints.allowedValueCodesByAttribute["target_gender"].orEmpty().toSet(),
        )
        assertEquals(
            setOf("NEWBORN", "BABY", "TODDLER", "KIDS", "TEEN"),
            spec.constraints.allowedValueCodesByAttribute["age_group"].orEmpty().toSet(),
        )
        assertEquals(
            setOf("NEW", "LIKE_NEW", "GOOD", "FAIR", "USED", "UNKNOWN"),
            spec.constraints.allowedValueCodesByAttribute["condition"].orEmpty().toSet(),
        )
        assertEquals(37, spec.constraints.allowedValueCodesByAttribute["apparel_type"]?.size)

        val allowedTypes = spec.constraints.allowedValueCodesByAttribute["apparel_type"].orEmpty().toSet()
        listOf("BODYSUIT", "SCHOOL_UNIFORM", "BABY_SET", "SPORTSWEAR", "HOMEWEAR").forEach { expected ->
            assertTrue("$expected must be a primary accepted FASH.KIDS apparel_type.", expected in allowedTypes)
        }
        listOf("SHOES", "SNEAKERS", "BACKPACK", "TOY", "STROLLER", "DIAPER").forEach { blocked ->
            assertFalse("$blocked must not be a primary accepted FASH.KIDS apparel_type.", blocked in allowedTypes)
        }
    }

    @Test
    fun kids_defaults_surface_and_vision_contracts_are_declared() {
        val childDefaults = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/child_defaults.fash_kids.v1_0.yaml",
        )
        val userSurface = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/user_surface.fash_kids.v1_0.yaml",
        )
        val visionRules = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/vision_rules.fash_kids.v1_0.yaml",
        )
        val routingGuardrails = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/overrides/fash_kids/v1_0/routing_guardrails.fash_kids.v1_0.yaml",
        )

        assertTrue(childDefaults.contains("\"target_gender\": \"KIDS\""))
        assertTrue(childDefaults.contains("\"age_group\": \"KIDS\""))
        assertTrue(userSurface.contains("\"initial_fields_max\": 8"))
        assertTrue(userSurface.contains("\"primary_facets_max\": 7"))
        assertTrue(visionRules.contains("\"color\""))
        assertTrue(visionRules.contains("\"cartoon character\""))
        assertTrue(visionRules.contains("\"must_not_infer_exact_age_from_photo\": true"))
        assertTrue(routingGuardrails.contains("\"target\": \"FASH.SHOES\""))
        assertTrue(routingGuardrails.contains("\"target\": \"KIDS.BABY_GEAR\""))

        val presentation = CatalogFacetPresentationProfiles.resolve("FASH.KIDS")
        requireNotNull(presentation)
        assertEquals("apparel_type", presentation.mainTypedFacetKeys.first())
        assertTrue("target_gender" in presentation.mainTypedFacetKeys)
        assertTrue("age_group" in presentation.mainTypedFacetKeys)
        assertTrue("size_label" in presentation.mainTypedFacetKeys)
        assertTrue("bra_cup" in presentation.hiddenTypedFacetKeys)
    }

    @Test
    fun kids_category_keeps_category_level_compatibility_rule() {
        val kidsConstraint = CatalogSeed.constraints.first {
            it.scope == ConstraintScope.CATEGORY && it.categoryCode == "FASH.KIDS"
        }

        assertTrue(kidsConstraint.compatibilityRules.isNotEmpty())
        assertTrue(
            kidsConstraint.compatibilityRules.any { rule ->
                rule.whenAll.any { condition ->
                    condition.attributeCode == "target_gender" && "KIDS" in condition.values
                }
            },
        )
    }
}
