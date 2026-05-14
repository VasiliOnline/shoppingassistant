package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechSpecsCommonSharedStandardGateTest {
    @Test
    fun specs_common_is_inherited_without_public_category() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        assertFalse(
            "TECH.SPECS_COMMON must remain a shared standard, not a public category.",
            "TECH.SPECS_COMMON" in categoryCodes,
        )

        val rawProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/TECH/profiles.tech.json")
        val rawSharedProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/TECH/shared_profiles.tech.json")
        assertTrue(rawProfiles.contains("\"TECH.SPECS_COMMON\""))
        assertTrue(rawSharedProfiles.contains("\"profileCode\": \"TECH.SPECS_COMMON\""))

        val techPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "TECH" }
        assertEquals(1, techPackage.sharedProfiles.count { it.profileCode == "TECH.SPECS_COMMON" })

        val publicTechCodes = CatalogSeed.categories
            .filter { it.parentCode == "TECH" && it.status == CategoryStatus.ACTIVE }
            .map { it.code }
        val expandedProfiles = techPackage.profiles.associateBy { it.category.code }
        publicTechCodes.forEach { categoryCode ->
            val attributes = expandedProfiles.getValue(categoryCode).attributes.map { it.code }.toSet()
            assertTrue("$categoryCode must inherit spec_profile_status.", "spec_profile_status" in attributes)
            assertTrue("$categoryCode must inherit storage_capacity.", "storage_capacity" in attributes)
            assertTrue("$categoryCode must inherit ram_capacity.", "ram_capacity" in attributes)
            assertTrue("$categoryCode must inherit screen_size.", "screen_size" in attributes)
            assertTrue("$categoryCode must inherit connector_type.", "connector_type" in attributes)
            assertTrue("$categoryCode must inherit raw spec evidence fields.", "raw_spec_tokens" in attributes)
        }
    }

    @Test
    fun specs_common_pack_is_registered_as_shared_standard() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        assertTrue(registry.attributes.containsKey("spec_profile_status"))
        assertTrue(registry.attributes.containsKey("storage_capacity"))
        assertTrue(registry.attributes.containsKey("connector_type"))
        assertTrue(registry.dictionaries.getValue("spec_profile_status").entries.any { it.valueCode == "NORMALIZED" })
        assertTrue(registry.dictionaries.getValue("connector_type").entries.any { it.valueCode == "USB_C" })

        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments.assignments.associateBy { it.categoryCode }
        listOf("TECH", "TECH.PHONES", "TECH.COMPUTERS", "TECH.WEARABLES").forEach { categoryCode ->
            assertEquals(
                listOf(
                    "TECH.ELECTRONICS_COMMON",
                    "TECH.DEVICE_IDENTITY_COMMON",
                    "TECH.SPECS_COMMON",
                    "TECH.COMPATIBILITY_COMMON",
                ),
                assignments.getValue(categoryCode).sharedStandards,
            )
        }

        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == "TECH_SPECS_COMMON_SHARED_STANDARD_v1_0" }
        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(null, manifest.categoryCode)
        assertTrue("shared_standard" in manifest.packSections)
        assertTrue("schema_pack.tech_specs_common.v1_0.json" in manifest.requiredFiles)
        assertTrue("unit_normalization_rules.tech_specs_common.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("docs/SPECS_POLICY_RU.md" in manifest.requiredFiles)
    }

    @Test
    fun specs_common_roles_and_facets_are_runtime_visible() {
        val spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec("TECH.PHONES")

        assertTrue("storage_capacity" in spec.facetAttributes)
        assertTrue("ram_capacity" in spec.facetAttributes)
        assertTrue("screen_size" in spec.facetAttributes)
        assertTrue("connector_type" in spec.facetAttributes)
        assertFalse("raw_spec_tokens must not become a runtime facet.", "raw_spec_tokens" in spec.facetAttributes)

        val effectiveSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == "TECH.PHONES" }
            .toCategoryEffectiveSpec()
        val status = effectiveSpec.allAttributes().firstOrNull { it.code == "spec_profile_status" }
        assertNotNull(status)
        requireNotNull(status)
        assertEquals(CatalogAttributeRole.T0_CORE, status.role)

        val storage = effectiveSpec.allAttributes().firstOrNull { it.code == "storage_capacity" }
        assertNotNull(storage)
        requireNotNull(storage)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, storage.role)

        val rawSpecTokens = effectiveSpec.allAttributes().firstOrNull { it.code == "raw_spec_tokens" }
        assertNotNull(rawSpecTokens)
        requireNotNull(rawSpecTokens)
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, rawSpecTokens.role)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in rawSpecTokens.usageScopes)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in rawSpecTokens.usageScopes)
        assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in rawSpecTokens.usageScopes)

        val presentation = CatalogFacetPresentationProfiles.resolve("TECH.PHONES")
        assertNotNull(presentation)
        requireNotNull(presentation)
        assertTrue("storage_capacity" in presentation.mainTypedFacetKeys)
        assertTrue("ram_capacity" in presentation.mainTypedFacetKeys)
        assertTrue("connector_type" in presentation.mainTypedFacetKeys)
        assertTrue("screen_resolution" in presentation.additionalTypedFacetKeys)
        assertTrue("battery_capacity_wh" in presentation.additionalTypedFacetKeys)
        assertTrue("raw_spec_tokens" in presentation.hiddenTypedFacetKeys)
    }
}
