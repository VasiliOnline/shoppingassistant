package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechElectronicsCommonSharedStandardGateTest {
    @Test
    fun electronics_common_is_inherited_without_public_category() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        assertFalse(
            "TECH.ELECTRONICS_COMMON must remain a shared standard, not a public category.",
            "TECH.ELECTRONICS_COMMON" in categoryCodes,
        )

        val rawProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/TECH/profiles.tech.json")
        val rawSharedProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/TECH/shared_profiles.tech.json")
        assertTrue(rawProfiles.contains("\"extendsProfiles\""))
        assertTrue(rawSharedProfiles.contains("\"profileCode\": \"TECH.ELECTRONICS_COMMON\""))
        assertTrue(rawSharedProfiles.contains("\"profileCode\": \"TECH.DEVICE_IDENTITY_COMMON\""))

        val techPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "TECH" }
        assertEquals(1, techPackage.sharedProfiles.count { it.profileCode == "TECH.ELECTRONICS_COMMON" })
        assertEquals(1, techPackage.sharedProfiles.count { it.profileCode == "TECH.DEVICE_IDENTITY_COMMON" })

        val publicTechCodes = CatalogSeed.categories
            .filter { it.parentCode == "TECH" && it.status == CategoryStatus.ACTIVE }
            .map { it.code }
        val expandedProfiles = techPackage.profiles.associateBy { it.category.code }
        publicTechCodes.forEach { categoryCode ->
            val attributes = expandedProfiles.getValue(categoryCode).attributes.map { it.code }.toSet()
            assertTrue("$categoryCode must inherit color_family.", "color_family" in attributes)
            assertTrue("$categoryCode must inherit warranty_status.", "warranty_status" in attributes)
            assertTrue("$categoryCode must inherit region.", "region" in attributes)
            assertTrue("$categoryCode must inherit model_name_text_raw as hidden raw evidence.", "model_name_text_raw" in attributes)
            assertTrue("$categoryCode must inherit family.", "family" in attributes)
            assertTrue("$categoryCode must inherit model_name_text.", "model_name_text" in attributes)
            assertTrue("$categoryCode must inherit region_variant.", "region_variant" in attributes)
            assertTrue("$categoryCode must inherit identity_resolution_status.", "identity_resolution_status" in attributes)
        }
    }

    @Test
    fun electronics_common_runtime_surface_keeps_t3_fields_hidden() {
        val spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec("TECH.PHONES")

        assertTrue("color_family" in spec.facetAttributes)
        assertTrue("warranty_status" in spec.facetAttributes)
        assertTrue("region" in spec.facetAttributes)
        assertTrue("included_accessories" in spec.facetAttributes)
        assertTrue("family" in spec.facetAttributes)
        assertTrue("region_variant" in spec.facetAttributes)
        assertTrue("identity_resolution_status" in spec.facetAttributes)
        assertFalse("model_name_text_raw must not become a runtime facet.", "model_name_text_raw" in spec.facetAttributes)

        val effectiveSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == "TECH.PHONES" }
            .toCategoryEffectiveSpec()
        val modelRaw = effectiveSpec.allAttributes().firstOrNull { it.code == "model_name_text_raw" }
        assertNotNull(modelRaw)
        requireNotNull(modelRaw)
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, modelRaw.role)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in modelRaw.usageScopes)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in modelRaw.usageScopes)
        assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in modelRaw.usageScopes)

        val identityStatus = effectiveSpec.allAttributes().firstOrNull { it.code == "identity_resolution_status" }
        assertNotNull(identityStatus)
        requireNotNull(identityStatus)
        assertEquals(CatalogAttributeRole.T0_CORE, identityStatus.role)

        val imeiHash = effectiveSpec.allAttributes().firstOrNull { it.code == "imei_hash" }
        assertNotNull(imeiHash)
        requireNotNull(imeiHash)
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, imeiHash.role)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in imeiHash.usageScopes)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in imeiHash.usageScopes)
    }

    @Test
    fun electronics_common_pack_is_registered_as_shared_standard() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        assertTrue(registry.dictionaries.getValue("color_family").entries.any { it.valueCode == "BLACK" })
        assertTrue(registry.dictionaries.getValue("warranty_status").entries.any { it.valueCode == "NO_WARRANTY" })
        assertTrue(registry.dictionaries.getValue("included_accessories").entries.any { it.valueCode == "ORIGINAL_BOX" })
        assertTrue(registry.dictionaries.getValue("identity_resolution_status").entries.any { it.valueCode == "FULL" })
        assertTrue(registry.dictionaries.getValue("region_variant").entries.any { it.valueCode == "GLOBAL" })
        assertTrue(registry.dictionaries.getValue("model_granularity").entries.any { it.valueCode == "EXACT_MODEL" })

        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments.assignments.associateBy { it.categoryCode }
        listOf("TECH", "TECH.PHONES", "TECH.PHONE_ACCESSORIES", "TECH.COMPUTERS", "TECH.WEARABLES").forEach { categoryCode ->
            assertEquals(
                listOf("TECH.ELECTRONICS_COMMON", "TECH.DEVICE_IDENTITY_COMMON", "TECH.SPECS_COMMON"),
                assignments.getValue(categoryCode).sharedStandards,
            )
        }

        val electronicsManifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == "TECH_ELECTRONICS_COMMON_SHARED_STANDARD_v1_0" }
        assertNotNull(electronicsManifest)
        requireNotNull(electronicsManifest)
        assertEquals(null, electronicsManifest.categoryCode)
        assertTrue("shared_standard" in electronicsManifest.packSections)
        assertTrue("schema_pack.tech_electronics_common.v1_0.json" in electronicsManifest.requiredFiles)
        assertTrue("user_surface.tech_electronics_common.v1_0.yaml" in electronicsManifest.requiredFiles)

        val identityManifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == "TECH_DEVICE_IDENTITY_COMMON_SHARED_STANDARD_v1_0" }
        assertNotNull(identityManifest)
        requireNotNull(identityManifest)
        assertEquals(null, identityManifest.categoryCode)
        assertTrue("shared_standard" in identityManifest.packSections)
        assertTrue("identity_pack" in identityManifest.packSections)
        assertTrue("schema_pack.tech_device_identity_common.v1_0.json" in identityManifest.requiredFiles)
        assertTrue("shared_standard.tech_device_identity_common.v1_0.yaml" in identityManifest.requiredFiles)
        assertTrue("docs/IDENTITY_POLICY_RU.md" in identityManifest.requiredFiles)
    }

    @Test
    fun electronics_common_facets_are_available_to_tech_presentation() {
        val presentation = CatalogFacetPresentationProfiles.resolve("TECH.PHONES")

        assertNotNull(presentation)
        requireNotNull(presentation)
        assertTrue("color_family" in presentation.mainTypedFacetKeys)
        assertTrue("warranty_status" in presentation.mainTypedFacetKeys)
        assertTrue("region" in presentation.mainTypedFacetKeys)
        assertTrue("family" in presentation.mainTypedFacetKeys)
        assertTrue("region_variant" in presentation.mainTypedFacetKeys)
        assertTrue("identity_resolution_status" in presentation.mainTypedFacetKeys)
        assertTrue("model_number" in presentation.additionalTypedFacetKeys)
        assertTrue("mpn" in presentation.additionalTypedFacetKeys)
        assertTrue("gtin" in presentation.additionalTypedFacetKeys)
        assertTrue("model_name_text_raw" in presentation.hiddenTypedFacetKeys)
        assertTrue("imei_hash" in presentation.hiddenTypedFacetKeys)
    }
}
