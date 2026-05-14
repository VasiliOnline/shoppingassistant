package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechCompatibilityCommonSharedStandardGateTest {
    @Test
    fun compatibility_common_is_inherited_without_public_category() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        assertFalse(
            "TECH.COMPATIBILITY_COMMON must remain a shared standard, not a public category.",
            "TECH.COMPATIBILITY_COMMON" in categoryCodes,
        )

        val rawProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/TECH/profiles.tech.json")
        val rawSharedProfiles = CatalogSeedResourceReader.readText("taxonomy/stage2/2.2/TECH/shared_profiles.tech.json")
        assertTrue(rawProfiles.contains("\"TECH.COMPATIBILITY_COMMON\""))
        assertTrue(rawSharedProfiles.contains("\"profileCode\": \"TECH.COMPATIBILITY_COMMON\""))

        val techPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "TECH" }
        assertEquals(1, techPackage.sharedProfiles.count { it.profileCode == "TECH.COMPATIBILITY_COMMON" })

        val publicTechCodes = CatalogSeed.categories
            .filter { it.parentCode == "TECH" && it.status == CategoryStatus.ACTIVE }
            .map { it.code }
        val expandedProfiles = techPackage.profiles.associateBy { it.category.code }
        publicTechCodes.forEach { categoryCode ->
            val attributes = expandedProfiles.getValue(categoryCode).attributes.map { it.code }.toSet()
            assertTrue("$categoryCode must inherit compatibility_mode.", "compatibility_mode" in attributes)
            assertTrue("$categoryCode must inherit compatibility_scope.", "compatibility_scope" in attributes)
            assertTrue(
                "$categoryCode must inherit compatible_target_category_code.",
                "compatible_target_category_code" in attributes,
            )
            assertTrue("$categoryCode must inherit compatible_brand.", "compatible_brand" in attributes)
            assertTrue("$categoryCode must inherit compatible_model_text.", "compatible_model_text" in attributes)
            assertTrue("$categoryCode must inherit compatibility evidence.", "compatibility_evidence_text" in attributes)
        }
    }

    @Test
    fun compatibility_common_pack_is_registered_as_shared_standard() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        assertTrue(registry.attributes.containsKey("compatibility_scope"))
        assertTrue(registry.attributes.containsKey("compatible_target_category_code"))
        assertTrue(registry.attributes.containsKey("compatibility_confidence"))
        assertTrue(registry.attributes.containsKey("protocol_standard"))
        assertTrue(registry.attributes.containsKey("compatibility_review_status"))
        assertEquals(Stage22ValueType.ENUM, registry.attributes.getValue("compatibility_confidence").valueType)
        assertEquals(Stage22ValueSetType.CLOSED, registry.attributes.getValue("compatibility_confidence").valueSetType)
        assertTrue(registry.dictionaries.getValue("compatibility_confidence").entries.any { it.valueCode == "HIGH" })
        assertTrue(registry.dictionaries.getValue("compatible_connector_type").entries.any { it.valueCode == "USB_C" })

        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments.assignments.associateBy { it.categoryCode }
        listOf("TECH", "TECH.PHONES", "TECH.PHONE_ACCESSORIES", "TECH.COMPUTERS", "TECH.WEARABLES")
            .forEach { categoryCode ->
                assertEquals(expectedTechSharedStandards, assignments.getValue(categoryCode).sharedStandards)
            }

        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == "TECH_COMPATIBILITY_COMMON_SHARED_STANDARD_v1_0" }
        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(null, manifest.categoryCode)
        assertTrue("shared_standard" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("schema_pack.tech_compatibility_common.v1_0.json" in manifest.requiredFiles)
        assertTrue("compatibility_resolution_policy.tech_compatibility_common.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("docs/COMPATIBILITY_POLICY_RU.md" in manifest.requiredFiles)
    }

    @Test
    fun compatibility_common_runtime_roles_and_facets_are_available_to_tech() {
        val spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec("TECH.PHONE_ACCESSORIES")

        assertTrue("compatibility_mode" in spec.facetAttributes)
        assertTrue("compatible_target_category_code" in spec.facetAttributes)
        assertTrue("compatible_connector_type" in spec.facetAttributes)
        assertTrue("protocol_standard" in spec.facetAttributes)
        assertTrue("compatibility_confidence" in spec.facetAttributes)
        assertFalse("compatibility_evidence_text must not become a runtime facet.", "compatibility_evidence_text" in spec.facetAttributes)

        val effectiveSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == "TECH.PHONE_ACCESSORIES" }
            .toCategoryEffectiveSpec()
        val attributes = effectiveSpec.allAttributes().associateBy { it.code }

        val compatibilityMode = attributes.getValue("compatibility_mode")
        assertEquals(CatalogAttributeRole.T0_CORE, compatibilityMode.role)

        val compatibleConnectorType = attributes.getValue("compatible_connector_type")
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, compatibleConnectorType.role)

        val evidenceText = attributes.getValue("compatibility_evidence_text")
        assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, evidenceText.role)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in evidenceText.usageScopes)
        assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in evidenceText.usageScopes)
        assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in evidenceText.usageScopes)

        val presentation = CatalogFacetPresentationProfiles.resolve("TECH.PHONE_ACCESSORIES")
        assertNotNull(presentation)
        requireNotNull(presentation)
        assertTrue("compatibility_mode" in presentation.mainTypedFacetKeys)
        assertTrue("compatibility_confidence" in presentation.mainTypedFacetKeys)
        assertTrue("compatible_connector_type" in presentation.mainTypedFacetKeys)
        assertTrue("compatibility_evidence_text" in presentation.hiddenTypedFacetKeys)
    }

    private companion object {
        private val expectedTechSharedStandards = listOf(
            "TECH.ELECTRONICS_COMMON",
            "TECH.DEVICE_IDENTITY_COMMON",
            "TECH.SPECS_COMMON",
            "TECH.COMPATIBILITY_COMMON",
        )
    }
}
