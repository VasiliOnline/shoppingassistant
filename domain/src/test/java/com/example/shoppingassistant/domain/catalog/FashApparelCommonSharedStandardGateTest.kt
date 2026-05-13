package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FashApparelCommonSharedStandardGateTest {
    @Test
    fun common_standard_is_inherited_without_public_category() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        assertFalse("FASH.APPAREL_COMMON must remain a shared standard, not a public category.", "FASH.APPAREL_COMMON" in categoryCodes)

        val rawProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/profiles.fash.json")
        val rawSharedProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/FASH/shared_profiles.fash.json")
        assertTrue(rawProfiles.contains("\"extendsProfiles\""))
        assertTrue(rawSharedProfiles.contains("\"profileCode\": \"FASH.APPAREL_COMMON\""))

        val fashPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "FASH" }
        assertEquals(1, fashPackage.sharedProfiles.count { it.profileCode == "FASH.APPAREL_COMMON" })
        assertEquals(61, fashPackage.profiles.first { it.category.code == "FASH.MEN" }.attributes.size)

        val registry = Stage22RegistryLoader.loadSnapshot()
        val engine = Stage22EffectiveSpecEngine.fromSeed(
            categories = CatalogSeed.categories,
            registry = registry,
            packages = GenericStage22PackageLoader.loadAll(),
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )

        listOf("FASH.MEN", "FASH.WOMEN", "FASH.KIDS").forEach { categoryCode ->
            val spec = engine.getEffectiveSpec(categoryCode)
            val attributes = spec.attributes.map { it.attributeCode }.toSet()

            assertFalse("$categoryCode must use a product-ready materialized apparel profile.", spec.meta.isFallback)
            assertTrue("apparel_type" in attributes)
            assertTrue("size_label" in attributes)
            assertTrue("color_primary" in attributes)
            assertTrue("material_primary" in attributes)
            assertTrue("model_or_line_text" in attributes)
            assertEquals(61, attributes.size)
            assertTrue("apparel_type" in spec.identityAttributes)
            assertTrue("apparel_type" in spec.facetAttributes)
            assertTrue("size_label" in spec.facetAttributes)
            assertFalse("apparel_type_group is system-derived and must not be a runtime facet.", "apparel_type_group" in spec.facetAttributes)
            assertFalse("visual evidence flags are AI/admin-only.", "visual_evidence_flags" in spec.facetAttributes)
        }

        assertEquals(55, registry.dictionaries["apparel_type"]?.entries?.size)
        assertTrue(registry.dictionaries["apparel_type"]?.entries.orEmpty().any { it.valueCode == "BODYSUIT" })
        assertTrue(registry.dictionaries["apparel_type"]?.entries.orEmpty().any { it.valueCode == "SCHOOL_UNIFORM" })
        assertEquals(90, registry.dictionaries["size_label"]?.entries?.size)
        assertEquals(20, registry.dictionaries["color_primary"]?.entries?.size)
        assertEquals(29, registry.dictionaries["material_primary"]?.entries?.size)
        assertEquals(3, registry.dictionaries["hood"]?.entries?.size)
    }

    @Test
    fun common_standard_declares_hardening_contracts() {
        val contract = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/shared_standard.fash_apparel_common.v1_0.yaml",
        )

        assertTrue(contract.contains("do_not_create_canonical_category: true"))
        assertTrue(contract.contains("measurements:"))
        assertTrue(contract.contains("type: ui_group"))
        assertTrue(contract.contains("chest_cm"))
        assertTrue(contract.contains("format: '{dictionary_code}:{value_code}'"))

        val aiContract = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/FASH/ai_vision_contract.fash_apparel_common.v1_0.json",
        )
        assertTrue(aiContract.contains("\"neverRouteToCommonCode\": true"))
        assertTrue(aiContract.contains("\"FASH.SHOES\""))
        assertTrue(aiContract.contains("\"material_primary\""))
    }

    @Test
    fun common_standard_required_rules_and_facets_are_runtime_visible() {
        listOf("FASH.MEN", "FASH.WOMEN", "FASH.KIDS").forEach { categoryCode ->
            val spec = CatalogSeed.categoryWriteSpecs.first { it.category.code == categoryCode }
            assertTrue(
                "$categoryCode must require defect_type when condition is DEFECTS.",
                spec.requiredIfRules.any { rule ->
                    rule.requiredAttributeCode == "defect_type" &&
                        rule.whenAll.any { condition ->
                            condition.attributeCode == "condition" && "DEFECTS" in condition.values
                        }
                },
            )
            assertTrue(
                "$categoryCode must require bra_cup for BRA apparel_type.",
                spec.requiredIfRules.any { rule ->
                    rule.requiredAttributeCode == "bra_cup" &&
                        rule.whenAll.any { condition ->
                            condition.attributeCode == "apparel_type" && "BRA" in condition.values
                        }
                },
            )
        }

        val apparelTypeFacet = CatalogSeed.facetDefinitions.firstOrNull { it.facetKey == "apparel_type" }
        assertNotNull(apparelTypeFacet)
        requireNotNull(apparelTypeFacet)
        assertTrue("FASH.MEN" in apparelTypeFacet.appliesToCategoryCodes)
        assertTrue("FASH.WOMEN" in apparelTypeFacet.appliesToCategoryCodes)
        assertTrue("FASH.KIDS" in apparelTypeFacet.appliesToCategoryCodes)

        val presentation = CatalogFacetPresentationProfiles.resolve("FASH.MEN")
        requireNotNull(presentation)
        assertTrue("apparel_type" in presentation.mainTypedFacetKeys)
        assertTrue("size_label" in presentation.mainTypedFacetKeys)
        assertTrue("apparel_type_group" in presentation.hiddenTypedFacetKeys)
        assertTrue("visual_evidence_flags" in presentation.hiddenTypedFacetKeys)
    }

    @Test
    fun routing_guardrails_keep_non_apparel_branches_separate() = runBlocking {
        val router = Stage21FashQueryRouter()

        assertEquals("FASH.MEN", router.route("мужские джинсы", "ru-RU").primaryTargetCode)
        assertEquals("FASH.WOMEN", router.route("женское платье", "ru-RU").primaryTargetCode)
        assertEquals("FASH.KIDS", router.route("детская куртка", "ru-RU").primaryTargetCode)
        assertEquals("FASH.SHOES", router.route("кроссовки nike", "ru-RU").primaryTargetCode)
        assertEquals("FASH.BAGS", router.route("сумка кожаная", "ru-RU").primaryTargetCode)
        assertEquals("FASH.ACCESSORIES", router.route("ремень мужской", "ru-RU").primaryTargetCode)
    }
}
