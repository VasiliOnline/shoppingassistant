package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechCamerasDronesTypeCoverageDeltaPackGateTest {

    @Test
    fun type_coverage_delta_is_registered_as_overlay_only() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(listOf("data_overlay_only"), manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.SIZE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals(BASE_PATH, manifest.basePath)
        assertEquals(16, manifest.requiredFiles.size)
        assertTrue("values_delta.tech_cameras_drones.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("aliases_delta.ru.tech_cameras_drones.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("model_head_seed_delta.tech_cameras_drones.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("golden_queries_delta.tech_cameras_drones.v1_1.tsv" in manifest.requiredFiles)
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

        assertTrue(packageManifest.contains("\"package_type\": \"data_overlay_patch\""))
        assertTrue(packageManifest.contains("\"public_category\": false"))
        assertTrue(packageManifest.contains("\"accepts_offers_directly\": false"))
        assertTrue(packageManifest.contains("\"schema_breaking_changes\": false"))
        assertTrue(packageManifest.contains("\"new_category_codes\": 0"))
        assertTrue(packageManifest.contains("\"presets_collections\": 0"))

        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        listOf(
            "TECH.CANON_DSLR",
            "TECH.DJI_ACCESSORIES",
            "TECH.CAMCORDER",
            "TECH.BINOCULARS",
            "TECH.TELESCOPES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created by type coverage overlay.", forbidden in categoryCodes)
        }

        val registry = Stage22RegistryLoader.loadSnapshot()
        assertFalse("Overlay must not create a schema attribute for soft camcorder subtype.", "camcorder_type" in registry.attributes)
        assertFalse("Overlay must not create a schema attribute for soft optics subtype.", "optics_type" in registry.attributes)
    }

    @Test
    fun delta_artifacts_keep_declared_runtime_counts() {
        assertEquals(29, dataRowCount("$BASE_PATH/values_delta.tech_cameras_drones.v1_1.tsv"))
        assertEquals(35, dataRowCount("$BASE_PATH/aliases_delta.ru.tech_cameras_drones.v1_1.tsv"))
        assertEquals(20, dataRowCount("$BASE_PATH/model_head_seed_delta.tech_cameras_drones.v1_1.tsv"))
        assertEquals(28, dataRowCount("$BASE_PATH/golden_queries_delta.tech_cameras_drones.v1_1.tsv"))
        assertEquals(22, jsonlRowCount("$BASE_PATH/sample_offers_delta.tech_cameras_drones.v1_1.jsonl"))
        assertEquals(12, jsonlRowCount("$BASE_PATH/publish_sample_offers_delta.tech_cameras_drones.v1_1.jsonl"))

        val validationReport = CatalogSeedResourceReader.readText(
            "$BASE_PATH/validation_report.tech_cameras_drones_type_delta.v1_1.json",
        )
        assertTrue(validationReport.contains("\"zip_integrity\": \"PASS\""))
        assertTrue(validationReport.contains("\"new_category_codes\": 0"))
        assertTrue(validationReport.contains("\"schema_breaking_changes\": false"))
    }

    @Test
    fun registry_value_dictionaries_include_delta_heads_without_schema_breaks() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val brands = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        val accessoryTypes = registry.dictionaries.getValue("accessory_type").entries.map { it.valueCode }.toSet()

        assertTrue(brands.containsAll(setOf("SKY_WATCHER", "BUSHNELL", "ATN", "VIXEN", "MEADE")))
        assertTrue(
            accessoryTypes.containsAll(
                setOf(
                    "BATTERY_GRIP",
                    "CAMERA_BATTERY",
                    "CAMERA_CHARGER",
                    "ND_FILTER",
                    "UV_FILTER",
                    "CAMERA_CAGE_RIG",
                    "DRONE_BATTERY",
                    "DRONE_GOGGLES",
                ),
            ),
        )
        assertFalse("camera_accessory_type must remain a value source, not a schema attribute.", "camera_accessory_type" in registry.attributes)
    }

    @Test
    fun type_coverage_queries_route_to_cameras_without_known_cross_branch_leaks() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("canon 5d mark iv body", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("sony handycam ax43", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("аккумулятор np-fz100 sony", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("nd фильтр 67мм", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("dji goggles 3 fpv", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("sky watcher 200p", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("бинокль bushnell 10x50", "ru-RU").primaryTargetCode)

        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("веб камера logitech c920", "ru-RU").primaryTargetCode)
        assertEquals("TECH.SMART_HOME_SECURITY", router.route("камера видеонаблюдения xiaomi", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("видеорегистратор автомобильный 4k", "ru-RU").primaryTargetCode)
        assertEquals("TECH.STORAGE_MEMORY", router.route("карта памяти sd 128gb", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("кабель hdmi для камеры", "ru-RU").primaryTargetCode)
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

    private companion object {
        private const val PACK_ID = "TECH_CAMERAS_DRONES_type_coverage_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.CAMERAS_DRONES"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/category_packs/tech_cameras_drones/data_overlays/type_coverage_delta/v1_1"
    }
}
