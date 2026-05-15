package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechGamingRouteAliasTypeCoverageDeltaPackGateTest {

    @Test
    fun route_alias_type_delta_is_registered_as_overlay_only() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(listOf("data_overlay_only"), manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals(BASE_PATH, manifest.basePath)
        assertEquals(18, manifest.requiredFiles.size)
        assertTrue("model_alias_retractions.tech_gaming.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("gaming_aliases_delta.ru.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails_delta.tech_gaming.v1_1.yaml" in manifest.requiredFiles)
        assertTrue("model_head_seed_delta.tech_gaming.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("golden_queries_delta.tech_gaming.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)
        assertNull(manifest.userSurfaceFile)
        assertNull(manifest.routeGuardLayer)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted delta file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun package_manifest_keeps_delta_out_of_public_tree_and_schema() {
        val packageManifest = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.json")

        assertTrue(packageManifest.contains("\"pack_type\": \"data_overlay_patch\""))
        assertTrue(packageManifest.contains("\"public_category\": false"))
        assertTrue(packageManifest.contains("\"accepts_offers_directly\": false"))
        assertTrue(packageManifest.contains("\"schema_breaking_changes\": false"))
        assertTrue(packageManifest.contains("\"new_category_codes\": []"))
        assertTrue(packageManifest.contains("\"presets_collections\": false"))

        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        listOf(
            "TECH.PS5",
            "TECH.XBOX",
            "TECH.NINTENDO_SWITCH",
            "TECH.VR",
            "TECH.GAMEPADS",
            "TECH.ARCADE",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created by route alias overlay.", forbidden in categoryCodes)
        }
    }

    @Test
    fun delta_artifacts_keep_declared_runtime_counts() {
        assertEquals(55, dataRowCount("$BASE_PATH/model_alias_retractions.tech_gaming.v1_1.tsv"))
        assertEquals(99, dataRowCount("$BASE_PATH/gaming_aliases_delta.ru.v1_1.tsv"))
        assertEquals(16, dataRowCount("$BASE_PATH/model_head_seed_delta.tech_gaming.v1_1.tsv"))
        assertEquals(32, dataRowCount("$BASE_PATH/golden_queries_delta.tech_gaming.v1_1.tsv"))
        assertEquals(31, jsonlRowCount("$BASE_PATH/sample_offers_delta.tech_gaming.v1_1.jsonl"))
        assertEquals(18, jsonlRowCount("$BASE_PATH/publish_sample_offers_delta.tech_gaming.v1_1.jsonl"))

        val validationReport = CatalogSeedResourceReader.readText("$BASE_PATH/validation_report.tech_gaming_delta.v1_1.json")
        assertTrue(validationReport.contains("\"json_syntax\": \"PASS\""))
        assertTrue(validationReport.contains("\"new_category_codes\": 0"))
        assertTrue(validationReport.contains("\"schema_breaking_changes\": false"))
        assertTrue(validationReport.contains("\"patch_needed\": \"NO\""))
    }

    @Test
    fun source_note_model_aliases_are_retracted_from_base_pack() {
        val aliases = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_gaming/v1_0/gaming_model_aliases.tech_gaming.ru.v1_0.tsv",
        )

        assertFalse(aliases.contains("official playstation ps5 lineup cue"))
        assertFalse(aliases.contains("official xbox consoles cue"))
        assertFalse(aliases.contains("resale head"))
        assertFalse(aliases.contains("head controller"))
        assertFalse(aliases.contains("head mouse"))
        assertFalse(aliases.contains("head headset"))
        assertFalse(aliases.contains("arcade head"))
    }

    @Test
    fun delta_model_heads_and_runtime_dictionaries_are_available() {
        val rows = readTsv("$BASE_PATH/model_head_seed_delta.tech_gaming.v1_1.tsv")
        assertTrue(rows.any { it["model_id"] == "META_QUEST_2_128" && it["gaming_type"] == "VR_HEADSET" })
        assertTrue(rows.any { it["model_id"] == "PICO_4_128" && it["brand"] == "PICO" })
        assertTrue(rows.any { it["model_id"] == "ASTRO_A50_X" && it["gaming_type"] == "GAMING_HEADSET" })
        assertTrue(rows.any { it["model_id"] == "ARCADE1UP_STREET_FIGHTER_CABINET" && it["gaming_type"] == "ARCADE_EQUIPMENT" })
        assertTrue(rows.any { it["model_id"] == "BACKBONE_ONE_USB_C" && it["platform"] == "MOBILE" })

        val registry = Stage22RegistryLoader.loadSnapshot()
        val brands = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        val platforms = registry.dictionaries.getValue("platform").entries.map { it.valueCode }.toSet()

        assertTrue(brands.containsAll(setOf("PICO", "HTC", "ASTRO", "ARCADE1UP", "BACKBONE", "GAMESIR")))
        assertTrue("MOBILE" in platforms)
    }

    @Test
    fun delta_route_queries_close_thin_gaming_types_without_cross_branch_leaks() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("meta quest 3s 128gb", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("oculus quest 2", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("pico 4 vr", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("htc vive pro 2", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("dualsense edge ps5", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("геймпад xbox elite series 2", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("backbone one usb c", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("игровая мышь logitech g502 x plus", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("turtle beach stealth 700 gen 3", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("quest link cable", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("arcade1up street fighter cabinet", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("картридж nintendo switch zelda", "ru-RU").primaryTargetCode)

        assertEquals("TECH.AUDIO", router.route("airpods для ps5", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("rtx 5070 для игр", "ru-RU").primaryTargetCode)
    }

    private fun dataRowCount(resourcePath: String): Int =
        CatalogSeedResourceReader.readText(resourcePath)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .drop(1)
            .count()

    private fun jsonlRowCount(resourcePath: String): Int =
        CatalogSeedResourceReader.readText(resourcePath)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .count()

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
        private const val PACK_ID = "TECH_GAMING_route_alias_type_coverage_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.GAMING"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/category_packs/tech_gaming/data_overlays/route_alias_type_coverage_delta/v1_1"
    }
}
