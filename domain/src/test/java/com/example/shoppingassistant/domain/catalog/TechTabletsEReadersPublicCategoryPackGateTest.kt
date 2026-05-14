package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechTabletsEReadersPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_tablet_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_tablets_e_readers/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_tablets_e_readers.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(31, manifest.requiredFiles.size)
        assertTrue("tablet_model_head_seed.tech_tablets_e_readers.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("tablet_model_aliases.tech_tablets_e_readers.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_tablets_e_readers.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun tablets_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.IPAD",
            "TECH.KINDLE",
            "TECH.GALAXY_TAB",
            "TECH.WACOM",
            "TECH.E_READER",
            "TECH.GRAPHICS_TABLETS",
            "TECH.TABLET_STANDS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_tablet_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val tabletType = registry.attributes.getValue("tablet_type")
        assertEquals(Stage22ValueType.ENUM, tabletType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, tabletType.valueSetType)
        assertTrue(tabletType.isIdentity)
        assertTrue(tabletType.isFacet)

        val tabletTypes = registry.dictionaries.getValue("tablet_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf("TABLET", "KIDS_TABLET", "E_READER", "GRAPHICS_TABLET", "TABLET_DOCK", "TABLET_STAND"),
            tabletTypes,
        )

        val connectivity = registry.attributes.getValue("connectivity")
        assertEquals(Stage22ValueType.ENUM, connectivity.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, connectivity.valueSetType)
        assertTrue(connectivity.isFacet)
        assertTrue(registry.dictionaries.getValue("connectivity").entries.any { it.valueCode == "WIFI_CELLULAR" })

        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("screen_size_in").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("screen_size_in").valueSetType)
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("storage_capacity_gb").valueType)
        assertTrue(registry.attributes.containsKey("display_technology"))
        assertTrue(registry.attributes.containsKey("active_area"))
        assertTrue(registry.attributes.containsKey("dock_type"))
        assertTrue(registry.attributes.containsKey("stand_type"))
        assertTrue(registry.dictionaries.getValue("display_technology").entries.any { it.valueCode == "E_INK_BW" })
        assertTrue(registry.dictionaries.getValue("pen_technology").entries.any { it.valueCode == "EMR" })
    }

    @Test
    fun effective_spec_exposes_tablet_facets_and_hides_system_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "tablet_type",
            "model_name_text",
            "screen_size_in",
            "storage_capacity_gb",
            "connectivity",
            "display_technology",
            "active_area",
            "dock_type",
            "stand_type",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.TABLETS_E_READERS effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("tablet_type", "screen_size_in", "storage_capacity_gb", "connectivity").forEach { facet ->
            assertTrue("$facet must be a TECH.TABLETS_E_READERS facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("tablet_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("connectivity").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("screen_size_in").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("storage_capacity_gb").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("display_technology").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("active_area").role)

        listOf("specs_confidence", "route_confidence", "extraction_evidence").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_tablet_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("tablet_type" in profile.mainTypedFacetKeys)
        assertTrue("screen_size_in" in profile.mainTypedFacetKeys)
        assertTrue("storage_capacity_gb" in profile.mainTypedFacetKeys)
        assertTrue("connectivity" in profile.mainTypedFacetKeys)
        assertTrue("display_technology" in profile.additionalTypedFacetKeys)
        assertTrue("active_area" in profile.additionalTypedFacetKeys)
        assertTrue("dock_type" in profile.additionalTypedFacetKeys)
        assertTrue("specs_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("source_trace_id" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun model_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_tablets_e_readers/v1_0/tablet_model_head_seed.tech_tablets_e_readers.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.TABLETS_E_READERS model head seed should contain at least 70 rows.", rows.size >= 70)
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "APPLE" && cells.getOrNull(1) == "IPAD" && cells.getOrNull(3) == "iPad Pro 13" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "AMAZON" && cells.getOrNull(1) == "KINDLE" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "WACOM" && cells.getOrNull(4) == "GRAPHICS_TABLET" })
    }

    @Test
    fun route_guard_conflicts_keep_tablets_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("ipad pro 11 256 wifi", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("kindle paperwhite", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("графический планшет wacom intuos", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONES", router.route("iphone 13", "en-US").primaryTargetCode)
        assertEquals("TECH.COMPUTERS", router.route("ноутбук lenovo", "ru-RU").primaryTargetCode)
        assertEquals("TECH.MONITORS_DISPLAYS", router.route("монитор 27 дюймов", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("клавиатура ipad bluetooth", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол iphone 13", "ru-RU").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_TABLETS_E_READERS_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.TABLETS_E_READERS"
    }
}
