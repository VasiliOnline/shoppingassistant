package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechStorageMemoryPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_storage_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_storage_memory/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_storage_memory.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("storage_model_head_seed.tech_storage_memory.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("storage_model_aliases.tech_storage_memory.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_storage_memory.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun storage_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.SSD",
            "TECH.HDD",
            "TECH.NAS",
            "TECH.MEMORY_CARDS",
            "TECH.USB_FLASH_DRIVES",
            "TECH.SAMSUNG_SSD",
            "TECH.WD_BLACK",
            "TECH.SYNOLOGY_NAS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_storage_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val storageType = registry.attributes.getValue("storage_type")
        assertEquals(Stage22ValueType.ENUM, storageType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, storageType.valueSetType)
        assertTrue(storageType.isFacet)

        val storageTypes = registry.dictionaries.getValue("storage_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            storageTypes.containsAll(
                setOf(
                    "INTERNAL_SSD",
                    "INTERNAL_HDD",
                    "EXTERNAL_SSD",
                    "EXTERNAL_HDD",
                    "USB_FLASH_DRIVE",
                    "MEMORY_CARD",
                    "CARD_READER",
                    "NAS",
                    "STORAGE_ACCESSORY",
                ),
            ),
        )
        assertTrue(registry.dictionaries.getValue("interface_type").entries.any { it.valueCode == "NVME" })
        assertTrue(registry.dictionaries.getValue("interface_type").entries.any { it.valueCode == "THUNDERBOLT" })
        assertTrue(registry.dictionaries.getValue("form_factor").entries.any { it.valueCode == "M2_2280" })
        assertTrue(registry.dictionaries.getValue("memory_card_format").entries.any { it.valueCode == "MICROSDXC" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "SAMSUNG" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("read_speed_mb_s").valueType)
        assertEquals(Stage22ValueType.ENUM, registry.attributes.getValue("dram_cache").valueType)
    }

    @Test
    fun effective_spec_exposes_storage_facets_and_hides_system_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "storage_type",
            "brand",
            "storage_capacity_gb",
            "interface_type",
            "form_factor",
            "memory_card_format",
            "bay_count",
            "raid_support",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.STORAGE_MEMORY effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("storage_type", "brand", "storage_capacity_gb", "condition", "interface_type", "form_factor").forEach { facet ->
            assertTrue("$facet must be a TECH.STORAGE_MEMORY facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("storage_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("storage_capacity_gb").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("interface_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("form_factor").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("memory_card_format").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("bay_count").role)

        listOf(
            "dedup_key",
            "normalization_confidence",
            "vision_evidence",
            "source_hash",
            "route_guard_trace",
            "candidate_value_status",
            "model_seed_match_level",
            "compatibility_hint_confidence",
        ).forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_storage_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("storage_type" in profile.mainTypedFacetKeys)
        assertTrue("storage_capacity_gb" in profile.mainTypedFacetKeys)
        assertTrue("interface_type" in profile.mainTypedFacetKeys)
        assertTrue("form_factor" in profile.mainTypedFacetKeys)
        assertTrue("memory_card_format" in profile.additionalTypedFacetKeys)
        assertTrue("raid_support" in profile.additionalTypedFacetKeys)
        assertTrue("dedup_key" in profile.hiddenTypedFacetKeys)
        assertTrue("route_guard_trace" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun storage_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_storage_memory/v1_0/storage_model_head_seed.tech_storage_memory.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.STORAGE_MEMORY head seed should contain at least 50 rows.", rows.size >= 50)
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "INTERNAL_SSD" && cells.getOrNull(2) == "SAMSUNG" && cells.getOrNull(5) == "990 PRO" })
        assertTrue(rows.any { cells -> cells.getOrNull(2) == "WESTERN_DIGITAL" && cells.getOrNull(5).orEmpty().contains("SN850X") })
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "NAS" && cells.getOrNull(2) == "SYNOLOGY" })
    }

    @Test
    fun route_guard_conflicts_keep_storage_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("ssd samsung 980 pro 1tb", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("nvme ssd", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("внешний hdd 2tb", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("флешка usb 128gb", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("карта памяти microsd", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("кардридер usb c", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("synology nas ds923", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("карта памяти для nintendo switch", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("ddr5 32gb kingston fury", "en-US").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("usb hub type c", "en-US").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол iphone 13", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAMERAS_DRONES", router.route("камера sony a7", "ru-RU").primaryTargetCode)
        assertEquals("TECH.GAMING", router.route("nintendo switch oled", "en-US").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_STORAGE_MEMORY_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.STORAGE_MEMORY"
    }
}
