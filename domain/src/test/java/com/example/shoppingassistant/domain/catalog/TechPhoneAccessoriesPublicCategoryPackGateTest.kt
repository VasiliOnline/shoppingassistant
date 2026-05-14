package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPhoneAccessoriesPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_without_microcategory_mounts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertEquals(setOf(CategoryArchetype.COMPATIBILITY_DRIVEN, CategoryArchetype.TYPE_DRIVEN), manifest.archetypes.toSet())
        assertEquals(28, manifest.requiredFiles.size)
        assertTrue("user_surface.tech_phone_accessories.v1_0.yaml" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }

        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        listOf("TECH.CASES", "TECH.CHARGERS", "TECH.CABLES", "TECH.IPHONE_CASES", "TECH.MAGSAFE")
            .forEach { forbidden ->
                assertFalse("$forbidden must remain blocked as a microcategory.", forbidden in categoryCodes)
            }
    }

    @Test
    fun registry_and_effective_spec_expose_public_runtime_surface() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val typeAttribute = registry.attributes.getValue("phone_accessory_type")
        assertEquals(Stage22ValueType.ENUM, typeAttribute.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, typeAttribute.valueSetType)
        assertTrue(typeAttribute.isIdentity)
        assertTrue(typeAttribute.isFacet)

        val accessoryTypes = registry.dictionaries.getValue("phone_accessory_type").entries.map { it.valueCode }.toSet()
        assertEquals(18, accessoryTypes.size)
        assertTrue(setOf("CASE", "SCREEN_PROTECTOR", "CHARGER", "CABLE", "POWER_BANK", "REPLACEMENT_PART").all { it in accessoryTypes })

        val compatibleBrand = registry.attributes.getValue("compatible_brand")
        assertEquals(Stage22ValueType.ENUM, compatibleBrand.valueType)
        assertEquals(Stage22ValueSetType.SEMI_CLOSED, compatibleBrand.valueSetType)
        assertTrue(registry.dictionaries.getValue("compatible_brand").entries.any { it.valueCode == "APPLE" })

        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = registry).getEffectiveSpec(CATEGORY)
        assertFalse(stage22Spec.meta.isFallback)
        assertTrue(stage22Spec.attributes.any { it.attributeCode == "accessory_type" && it.required })
        assertTrue(stage22Spec.attributes.any { it.attributeCode == "phone_accessory_type" && it.required })
        assertTrue("accessory_type" in stage22Spec.identityAttributes)
        assertTrue("phone_accessory_type" in stage22Spec.identityAttributes)
        assertTrue("phone_accessory_type" in stage22Spec.facetAttributes)

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("phone_accessory_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("compatible_device_category").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("compatibility_evidence_level").role)
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attributes.getValue("compatible_model_ref").role)
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attributes.getValue("extraction_evidence").role)
    }

    @Test
    fun stage4_presentation_keeps_legacy_and_new_fields() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("accessory_type" in profile.mainTypedFacetKeys)
        assertTrue("phone_accessory_type" in profile.mainTypedFacetKeys)
        assertTrue("compatible_brand" in profile.mainTypedFacetKeys)
        assertTrue("compatible_device_category" in profile.mainTypedFacetKeys)
        assertTrue("compatibility_evidence_level" in profile.mainTypedFacetKeys)
        assertTrue("compatible_model_ref" in profile.hiddenTypedFacetKeys)
        assertTrue("source_trace_id" in profile.hiddenTypedFacetKeys)
        assertTrue("normalization_notes" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun route_guard_conflicts_match_public_pack_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals("TECH.PHONES", router.route("iphone 13", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("чехол iphone 13", "ru-RU").primaryTargetCode)
        assertEquals("TECH.AUDIO", router.route("airpods", "en-US").primaryTargetCode)
        assertEquals("TECH.WEARABLES", router.route("apple watch ремешок", "ru-RU").primaryTargetCode)
        assertEquals("TECH.WEARABLES", router.route("ремешок apple watch", "ru-RU").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_PHONE_ACCESSORIES_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.PHONE_ACCESSORIES"
    }
}
