package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPhonesPublicCategoryPackGateTest {
    @Test
    fun phones_pack_is_registered_with_category_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_phones/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertEquals("user_surface.tech_phones.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(34, manifest.requiredFiles.size)
        assertTrue("model_head_seed.tech_phones.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("phone_model_aliases.tech_phones.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("docs/USED_PHONE_RESALE_POLICY_RU.md" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)
    }

    @Test
    fun phones_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.IPHONE",
            "TECH.SAMSUNG_PHONES",
            "TECH.GALAXY",
            "TECH.PIXEL",
            "TECH.CASES",
            "TECH.CHARGERS",
            "TECH.CABLES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun phones_registry_adds_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        assertEquals(Stage22ValueType.ENUM, registry.attributes.getValue("phone_type").valueType)
        assertEquals(Stage22ValueSetType.CLOSED, registry.attributes.getValue("phone_type").valueSetType)
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("storage_capacity_gb").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("storage_capacity_gb").valueSetType)
        assertTrue(registry.attributes.getValue("account_lock_status").isFacet)
        assertTrue(registry.attributes.containsKey("identity_evidence"))
        assertTrue(registry.attributes.containsKey("dedup_key"))

        val phoneTypeValues = registry.dictionaries.getValue("phone_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf(
                "SMARTPHONE",
                "FEATURE_PHONE",
                "FOLDABLE_PHONE",
                "RUGGED_PHONE",
                "RETRO_PHONE",
                "SATELLITE_PHONE",
            ),
            phoneTypeValues,
        )
        assertTrue(registry.dictionaries.getValue("network_lock_status").entries.any { it.valueCode == "UNLOCKED" })
        assertTrue(registry.dictionaries.getValue("account_lock_status").entries.any { it.valueCode == "ICLOUD_LOCKED" })
        assertTrue(registry.dictionaries.getValue("imei_status").entries.any { it.valueCode == "BLACKLISTED" })
    }

    @Test
    fun phones_effective_spec_exposes_user_facets_and_hides_system_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        listOf(
            "phone_type",
            "model_name_text",
            "storage_capacity_gb",
            "network_lock_status",
            "battery_health_bucket",
            "screen_size_inches",
            "mobile_network_generation",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.PHONES effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf(
            "phone_type",
            "storage_capacity_gb",
            "network_lock_status",
            "battery_health_bucket",
            "account_lock_status",
        ).forEach { facet ->
            assertTrue("$facet must be a TECH.PHONES facet.", facet in stage22Spec.facetAttributes)
        }

        val effectiveSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val byCode = effectiveSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, byCode.getValue("phone_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, byCode.getValue("storage_capacity_gb").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, byCode.getValue("network_lock_status").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, byCode.getValue("account_lock_status").role)

        listOf("identity_evidence", "dedup_key").forEach { hidden ->
            val attribute = byCode.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun phones_presentation_prioritizes_public_facets_and_hides_evidence() {
        val presentation = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(presentation)
        requireNotNull(presentation)
        assertTrue("phone_type" in presentation.mainTypedFacetKeys)
        assertTrue("storage_capacity_gb" in presentation.mainTypedFacetKeys)
        assertTrue("network_lock_status" in presentation.mainTypedFacetKeys)
        assertTrue("identity_evidence" in presentation.hiddenTypedFacetKeys)
        assertTrue("dedup_key" in presentation.hiddenTypedFacetKeys)
    }

    @Test
    fun phones_model_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_phones/v1_0/model_head_seed.tech_phones.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.PHONES model head seed should contain at least 120 rows.", rows.size >= 120)
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "IPHONE" })
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "GALAXY" })
    }

    private companion object {
        private const val PACK_ID = "TECH_PHONES_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.PHONES"
    }
}
