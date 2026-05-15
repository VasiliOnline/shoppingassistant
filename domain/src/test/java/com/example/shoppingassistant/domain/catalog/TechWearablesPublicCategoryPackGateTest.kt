package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechWearablesPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_wearable_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(BASE_PATH, manifest.basePath)
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.SIZE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_wearables.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("wearable_model_head_seed.tech_wearables.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("wearable_model_aliases.tech_wearables.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_wearables.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun wearables_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.APPLE_WATCH",
            "TECH.GALAXY_WATCH",
            "TECH.SMARTWATCHES",
            "TECH.WATCH_BANDS",
            "TECH.SMART_RINGS",
            "TECH.AR_GLASSES",
            "TECH.MI_BAND",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_wearable_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val wearableType = registry.attributes.getValue("wearable_type")
        assertEquals(Stage22ValueType.ENUM, wearableType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, wearableType.valueSetType)
        assertTrue(wearableType.isIdentity)
        assertTrue(wearableType.isFacet)

        val wearableTypes = registry.dictionaries.getValue("wearable_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            wearableTypes.containsAll(
                setOf(
                    "SMARTWATCH",
                    "FITNESS_TRACKER",
                    "SMART_RING",
                    "AR_GLASSES",
                    "WEARABLE_ACCESSORY",
                    "WATCH_BAND",
                ),
            ),
        )

        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("case_size_mm").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("case_size_mm").valueSetType)
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("battery_life_days").valueType)
        assertTrue(registry.dictionaries.getValue("display_type").entries.any { it.valueCode == "AMOLED" })
        assertTrue(registry.dictionaries.getValue("compatible_os").entries.any { it.valueCode == "WATCHOS" })
        assertTrue(registry.dictionaries.getValue("ecosystem").entries.any { it.valueCode == "GARMIN" })
        assertTrue(registry.dictionaries.getValue("band_connector_type").entries.any { it.valueCode == "APPLE_WATCH_CONNECTOR" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "GARMIN" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "XREAL" })
    }

    @Test
    fun effective_spec_exposes_wearable_facets_and_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "wearable_type",
            "brand",
            "model_name_text",
            "case_size_mm",
            "display_type",
            "compatible_os",
            "ecosystem",
            "battery_life_days",
            "strap_width_mm",
            "band_connector_type",
            "ring_size",
            "ar_display_mode",
            "activation_lock_status",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.WEARABLES effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("wearable_type", "brand", "condition", "compatible_os", "connectivity_set", "battery_life_days").forEach { facet ->
            assertTrue("$facet must be a TECH.WEARABLES facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("wearable_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("brand").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("condition").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("case_size_mm").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("compatible_os").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("band_connector_type").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("ring_size").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("ar_display_mode").role)

        listOf("imei_or_serial_presence", "identity_confidence", "vision_confidence").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_wearable_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("wearable_type" in profile.mainTypedFacetKeys)
        assertTrue("compatible_os" in profile.mainTypedFacetKeys)
        assertTrue("connectivity_set" in profile.mainTypedFacetKeys)
        assertTrue("battery_life_days" in profile.mainTypedFacetKeys)
        assertTrue("case_size_mm" in profile.additionalTypedFacetKeys)
        assertTrue("band_connector_type" in profile.additionalTypedFacetKeys)
        assertTrue("ring_size" in profile.additionalTypedFacetKeys)
        assertTrue("ar_display_mode" in profile.additionalTypedFacetKeys)
        assertTrue("activation_lock_status" in profile.additionalTypedFacetKeys)
        assertTrue("imei_or_serial_presence" in profile.hiddenTypedFacetKeys)
        assertTrue("vision_confidence" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun wearable_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/wearable_model_head_seed.tech_wearables.v1_0.tsv")

        assertTrue("TECH.WEARABLES model head seed should contain at least 60 rows.", rows.size >= 60)
        assertTrue(rows.any { it["brand"] == "APPLE" && it["model"] == "Apple Watch Series 11" })
        assertTrue(rows.any { it["brand"] == "SAMSUNG" && it["model"] == "Galaxy Watch8 Classic" })
        assertTrue(rows.any { it["brand"] == "GARMIN" && it["model"] == "Garmin Fenix 8" })
        assertTrue(rows.any { it["brand"] == "OURA" && it["wearable_type"] == "SMART_RING" })
        assertTrue(rows.any { it["brand"] == "XREAL" && it["wearable_type"] == "AR_GLASSES" })
    }

    @Test
    fun route_guard_conflicts_keep_wearables_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("apple watch series 11", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("galaxy watch8 classic", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("garmin fenix 8", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("oura ring gen3", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("ремешок apple watch 45 мм", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("ray ban meta smart glasses", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("xreal air", "ru-RU").primaryTargetCode)

        assertEquals("TECH.PHONES", router.route("iphone 15", "ru-RU").primaryTargetCode)
        assertEquals("TECH.AUDIO", router.route("airpods pro 2", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол iphone 13", "ru-RU").primaryTargetCode)
    }

    private fun readTsv(resourcePath: String): List<Map<String, String>> {
        val lines = CatalogSeedResourceReader.readText(resourcePath)
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        val header = lines.first().split('\t')
        return lines.drop(1).map { line ->
            val cells = line.split('\t')
            header.mapIndexed { index, key -> key to cells.getOrElse(index) { "" } }.toMap()
        }
    }

    private companion object {
        private const val PACK_ID = "TECH_WEARABLES_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.WEARABLES"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_wearables/v1_0"
    }
}
