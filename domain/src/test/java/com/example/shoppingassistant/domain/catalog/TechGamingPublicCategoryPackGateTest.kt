package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechGamingPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_gaming_artifacts() {
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
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_gaming.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(30, manifest.requiredFiles.size)
        assertTrue("gaming_model_head_seed.tech_gaming.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("gaming_model_aliases.tech_gaming.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_gaming.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun gaming_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.PS5",
            "TECH.XBOX",
            "TECH.NINTENDO_SWITCH",
            "TECH.VR",
            "TECH.GAMEPADS",
            "TECH.PLAYSTATION",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_gaming_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val gamingType = registry.attributes.getValue("gaming_type")
        assertEquals(Stage22ValueType.ENUM, gamingType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, gamingType.valueSetType)
        assertTrue(gamingType.isIdentity)
        assertTrue(gamingType.isFacet)

        val gamingTypes = registry.dictionaries.getValue("gaming_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            gamingTypes.containsAll(
                setOf(
                    "GAME_CONSOLE",
                    "HANDHELD_CONSOLE",
                    "VR_HEADSET",
                    "GAME_CONTROLLER",
                    "GAMING_KEYBOARD",
                    "GAMING_MOUSE",
                    "GAMING_HEADSET",
                    "CONSOLE_ACCESSORY",
                    "PHYSICAL_GAME",
                    "ARCADE_EQUIPMENT",
                ),
            ),
        )

        assertTrue(registry.dictionaries.getValue("platform").entries.any { it.valueCode == "PLAYSTATION" })
        assertTrue(registry.dictionaries.getValue("platform").entries.any { it.valueCode == "XBOX" })
        assertTrue(registry.dictionaries.getValue("platform").entries.any { it.valueCode == "NINTENDO" })
        assertTrue(registry.dictionaries.getValue("platform").entries.any { it.valueCode == "STEAM_DECK" })
        assertTrue(registry.dictionaries.getValue("game_platform").entries.any { it.valueCode == "PS5" })
        assertTrue(registry.dictionaries.getValue("game_platform").entries.any { it.valueCode == "NINTENDO_SWITCH_2" })
        assertTrue(registry.dictionaries.getValue("compatible_platform").entries.any { it.valueCode == "META_QUEST" })
        assertTrue(registry.dictionaries.getValue("accessory_subtype").entries.any { it.valueCode == "CHARGING_DOCK" })
        assertTrue(registry.dictionaries.getValue("arcade_equipment_type").entries.any { it.valueCode == "ARCADE_CABINET" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "SONY" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "MICROSOFT" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "NINTENDO" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "RAZER" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("storage_capacity_gb").valueType)
        assertEquals(Stage22ValueType.BOOLEAN, registry.attributes.getValue("vr_standalone_mode").valueType)
    }

    @Test
    fun effective_spec_exposes_gaming_facets_and_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "gaming_type",
            "brand",
            "platform",
            "model_name_text",
            "storage_capacity_gb",
            "disc_drive",
            "media_format",
            "game_platform",
            "controller_subtype",
            "connection_type",
            "compatible_platform",
            "accessory_subtype",
            "arcade_equipment_type",
            "title_confidence",
            "route_confidence",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.GAMING effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("gaming_type", "platform", "brand", "condition", "model_name_text", "compatible_platform").forEach { facet ->
            assertTrue("$facet must be a TECH.GAMING facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("gaming_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("brand").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("condition").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("platform").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("media_format").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("game_platform").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("controller_subtype").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("compatible_platform").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("accessory_subtype").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("arcade_equipment_type").role)

        listOf("title_confidence", "route_confidence", "evidence_source").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_gaming_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("gaming_type" in profile.mainTypedFacetKeys)
        assertTrue("platform" in profile.mainTypedFacetKeys)
        assertTrue("compatible_platform" in profile.mainTypedFacetKeys)
        assertTrue("storage_capacity_gb" in profile.additionalTypedFacetKeys)
        assertTrue("disc_drive" in profile.additionalTypedFacetKeys)
        assertTrue("media_format" in profile.additionalTypedFacetKeys)
        assertTrue("controller_subtype" in profile.additionalTypedFacetKeys)
        assertTrue("accessory_subtype" in profile.additionalTypedFacetKeys)
        assertTrue("arcade_equipment_type" in profile.additionalTypedFacetKeys)
        assertTrue("title_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("route_confidence" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun gaming_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/gaming_model_head_seed.tech_gaming.v1_0.tsv")

        assertTrue("TECH.GAMING model head seed should contain at least 55 rows.", rows.size >= 55)
        assertTrue(rows.any { it["brand"] == "SONY" && it["model_id"] == "PS5_PRO_2TB" })
        assertTrue(rows.any { it["brand"] == "MICROSOFT" && it["model_id"] == "XBOX_SERIES_X_1TB" })
        assertTrue(rows.any { it["brand"] == "NINTENDO" && it["model_id"] == "NINTENDO_SWITCH_2" })
        assertTrue(rows.any { it["brand"] == "VALVE" && it["model_id"] == "STEAM_DECK_OLED" })
        assertTrue(rows.any { it["brand"] == "META" && it["model_id"] == "META_QUEST_3S_128" })
        assertTrue(rows.any { it["brand"] == "RAZER" && it["model_id"] == "RAZER_DEATHADDER_V3_PRO" })
    }

    @Test
    fun route_guard_conflicts_keep_gaming_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("ps5 1tb", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("steam deck oled", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("meta quest 3s", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("dualsense ps5", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("игровая механическая клавиатура", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("игровая мышь wireless", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("гарнитура ps5 gaming", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("чехол для nintendo switch", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("диск ps5 spider man 2", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("аркадный автомат arcade1up", "ru-RU").primaryTargetCode)

        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("обычная клавиатура офисная", "ru-RU").primaryTargetCode)
        assertEquals("TECH.AUDIO", router.route("наушники sony wh-1000xm5", "ru-RU").primaryTargetCode)
        assertEquals("TECH.TV_HOME_THEATER", router.route("телевизор samsung 55 oled", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("rtx 4070 видеокарта", "ru-RU").primaryTargetCode)
        assertEquals("TECH.STORAGE_MEMORY", router.route("ssd samsung 990 pro", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONES", router.route("iphone 15 pro", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол iphone 15", "ru-RU").primaryTargetCode)
        assertEquals("TECH.SMART_HOME_SECURITY", router.route("камера безопасности wifi", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("dash cam xiaomi", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_GAMING_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.GAMING"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_gaming/v1_0"
    }
}
