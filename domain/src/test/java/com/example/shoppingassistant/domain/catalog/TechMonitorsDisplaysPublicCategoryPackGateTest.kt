package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechMonitorsDisplaysPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_display_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_monitors_displays/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.SIZE_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_monitors_displays.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("display_model_head_seed.tech_monitors_displays.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("display_model_aliases.tech_monitors_displays.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_monitors_displays.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun monitors_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.MONITOR",
            "TECH.DISPLAY",
            "TECH.GAMING_MONITOR",
            "TECH.PORTABLE_MONITOR",
            "TECH.SMART_MONITOR",
            "TECH.TOUCHSCREEN_MONITOR",
            "TECH.MONITOR_ACCESSORIES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_monitor_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val displayType = registry.attributes.getValue("display_type")
        assertEquals(Stage22ValueType.ENUM, displayType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, displayType.valueSetType)
        assertTrue(displayType.isIdentity)
        assertTrue(displayType.isFacet)

        val displayTypes = registry.dictionaries.getValue("display_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            displayTypes.containsAll(
                setOf(
                    "COMPUTER_MONITOR",
                    "GAMING_MONITOR",
                    "PROFESSIONAL_MONITOR",
                    "PORTABLE_MONITOR",
                    "SMART_MONITOR",
                    "TOUCHSCREEN_MONITOR",
                    "MONITOR_ACCESSORY",
                ),
            ),
        )

        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("screen_size_in").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("screen_size_in").valueSetType)
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("refresh_rate_hz").valueType)
        assertTrue(registry.dictionaries.getValue("resolution_class").entries.any { it.valueCode == "4K_UHD" })
        assertTrue(registry.dictionaries.getValue("panel_technology").entries.any { it.valueCode == "QD_OLED" })
        assertTrue(registry.dictionaries.getValue("connector_input_primary").entries.any { it.valueCode == "USB_C" })
        assertTrue(registry.dictionaries.getValue("adaptive_sync").entries.any { it.valueCode == "G_SYNC_COMPATIBLE" })
        assertTrue(registry.dictionaries.getValue("monitor_accessory_type").entries.any { it.valueCode == "VESA_ADAPTER" })
    }

    @Test
    fun effective_spec_exposes_monitor_facets_and_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "display_type",
            "model_name_text",
            "screen_size_in",
            "resolution_class",
            "refresh_rate_hz",
            "panel_technology",
            "connector_input_primary",
            "adaptive_sync",
            "monitor_accessory_type",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.MONITORS_DISPLAYS effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("display_type", "screen_size_in", "resolution_class", "refresh_rate_hz").forEach { facet ->
            assertTrue("$facet must be a TECH.MONITORS_DISPLAYS facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("display_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("screen_size_in").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("resolution_class").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("refresh_rate_hz").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("panel_technology").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("connector_input_primary").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("adaptive_sync").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("monitor_accessory_type").role)

        listOf("route_confidence", "dedup_fingerprint", "seller_claims_raw").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_monitor_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("display_type" in profile.mainTypedFacetKeys)
        assertTrue("screen_size_in" in profile.mainTypedFacetKeys)
        assertTrue("resolution_class" in profile.mainTypedFacetKeys)
        assertTrue("refresh_rate_hz" in profile.mainTypedFacetKeys)
        assertTrue("panel_technology" in profile.additionalTypedFacetKeys)
        assertTrue("connector_input_primary" in profile.additionalTypedFacetKeys)
        assertTrue("adaptive_sync" in profile.additionalTypedFacetKeys)
        assertTrue("monitor_accessory_type" in profile.additionalTypedFacetKeys)
        assertTrue("route_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("seller_claims_raw" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun display_model_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_monitors_displays/v1_0/display_model_head_seed.tech_monitors_displays.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.MONITORS_DISPLAYS model head seed should contain at least 40 rows.", rows.size >= 40)
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "DELL" && cells.getOrNull(3) == "U2723QE" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "SAMSUNG" && cells.getOrNull(3) == "Odyssey G9" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "APPLE" && cells.getOrNull(3) == "Studio Display" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "ASUS" && cells.getOrNull(5) == "PORTABLE_MONITOR" })
    }

    @Test
    fun route_guard_conflicts_keep_displays_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("монитор 27 дюймов", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("игровой монитор 27 144 гц", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("портативный монитор usb c", "ru-RU").primaryTargetCode)
        assertEquals("TECH.TV_HOME_THEATER", router.route("телевизор samsung 55 4k", "ru-RU").primaryTargetCode)
        assertEquals("TECH.TV_HOME_THEATER", router.route("проектор full hd", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTERS", router.route("ноутбук lenovo 15 дюймов", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("матрица ноутбука hp", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("кронштейн для монитора dual arm", "ru-RU").primaryTargetCode)
        assertEquals("TECH.TABLETS_E_READERS", router.route("wacom cintiq 16", "en-US").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("кабель hdmi 2 метра", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("видеокарта rtx 4070", "ru-RU").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_MONITORS_DISPLAYS_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.MONITORS_DISPLAYS"
    }
}
