package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPowerChargingCablesRoutePublishDeltaPackGateTest {

    @Test
    fun route_publish_delta_is_registered_as_overlay_only() {
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
        assertEquals(16, manifest.requiredFiles.size)
        assertTrue("aliases_delta.tech_power_charging_cables.ru.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("golden_queries_delta.tech_power_charging_cables.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("publish_sample_offers_delta.tech_power_charging_cables.v1_1.jsonl" in manifest.requiredFiles)
        assertTrue("route_guardrails_delta.tech_power_charging_cables.v1_1.yaml" in manifest.requiredFiles)
        assertTrue("validation_report.tech_power_charging_cables_delta.v1_1.json" in manifest.requiredFiles)
        assertNull(manifest.userSurfaceFile)
        assertNull(manifest.routeGuardLayer)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted power route/publish delta file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun package_manifest_keeps_delta_out_of_schema_and_public_tree() {
        val packageManifest = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.json")

        assertTrue(packageManifest.contains("\"package_type\": \"data_overlay_patch\""))
        assertTrue(packageManifest.contains("\"public_category\": false"))
        assertTrue(packageManifest.contains("\"schema_breaking_changes\": false"))
        assertTrue(packageManifest.contains("\"ui_surface_changes\": false"))
        assertTrue(packageManifest.contains("\"presets_collections\": false"))

        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        listOf(
            "TECH.POWER_STRIPS",
            "TECH.SURGE_PROTECTORS",
            "TECH.CABLE_MANAGEMENT",
            "TECH.EXTENSION_CORDS",
            "TECH.NETWORK_ADAPTERS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created by route/publish overlay.", forbidden in categoryCodes)
        }
    }

    @Test
    fun delta_artifacts_keep_declared_runtime_counts() {
        assertEquals(35, dataRowCount("$BASE_PATH/aliases_delta.tech_power_charging_cables.ru.v1_1.tsv"))
        assertEquals(20, dataRowCount("$BASE_PATH/golden_queries_delta.tech_power_charging_cables.v1_1.tsv"))
        assertEquals(17, jsonlRowCount("$BASE_PATH/sample_offers_delta.tech_power_charging_cables.v1_1.jsonl"))
        assertEquals(10, jsonlRowCount("$BASE_PATH/publish_sample_offers_delta.tech_power_charging_cables.v1_1.jsonl"))

        val validationReport = CatalogSeedResourceReader.readText("$BASE_PATH/validation_report.tech_power_charging_cables_delta.v1_1.json")
        assertTrue(validationReport.contains("\"json_syntax\": \"PASS\""))
        assertTrue(validationReport.contains("\"new_categoryCode\": 0"))
        assertTrue(validationReport.contains("\"schema_breaking_changes\": false"))
        assertTrue(validationReport.contains("\"data_coverage_verdict_after_patch\": \"PASS\""))
        assertTrue(validationReport.contains("\"patch_needed_after_this\": \"NO\""))
    }

    @Test
    fun publish_delta_closes_surge_and_cable_management_fixture_gaps() {
        val rows = readJsonl("$BASE_PATH/publish_sample_offers_delta.tech_power_charging_cables.v1_1.jsonl")

        val surgeRows = rows.filter { it.contains("\"power_cable_type\": \"SURGE_PROTECTOR\"") }
        val cableManagementRows = rows.filter { it.contains("\"power_cable_type\": \"CABLE_MANAGEMENT\"") }

        assertTrue("SURGE_PROTECTOR publish fixtures must be present.", surgeRows.size >= 4)
        assertTrue("CABLE_MANAGEMENT publish fixtures must be present.", cableManagementRows.size >= 6)
        assertTrue(rows.any { it.contains("\"brand\": \"APC\"") && it.contains("\"ac_outlet_count\": 6") })
        assertTrue(rows.any { it.contains("\"title\": \"ORICO Cable Management Box\"") })
        assertTrue(rows.all { it.contains("\"expected_publish_status\": \"PASS\"") })
    }

    @Test
    fun route_publish_delta_queries_cover_power_and_network_boundaries() = runBlocking {
        val router = Stage21TechQueryRouter()

        listOf(
            "сетевой фильтр pilot 5 розеток",
            "удлинитель электрический 3 метра",
            "удлинитель с usb c розетками",
            "power strip 6 outlets eu",
            "apc surge protector 8 outlets",
            "кабель канал белый",
            "кабельные клипсы самоклеящиеся",
            "лоток для кабелей под стол",
            "короб для проводов orico",
            "органайзер зарядок на стол",
            "cable sleeve organizer",
            "адаптер питания ноутбука lenovo",
            "зарядка macbook 67w magsafe",
        ).forEach { query ->
            val debug = router.routeWithCandidates(query, "ru-RU")
            assertEquals("$query must route to TECH.POWER_CHARGING_CABLES. top=${debug.topCandidates}", CATEGORY, debug.result.primaryTargetCode)
        }

        assertEquals("TECH.NETWORKING", router.route("сетевой адаптер usb wifi", "ru-RU").primaryTargetCode)
        assertEquals("TECH.NETWORKING", router.route("сетевой адаптер ethernet usb c", "ru-RU").primaryTargetCode)
        assertEquals("TECH.NETWORKING", router.route("lan кабель 10 метров", "ru-RU").primaryTargetCode)
        assertEquals("TECH.NETWORKING", router.route("сетевой кабель rj45 cat6", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("блок питания atx 750w", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол зарядный iphone", "ru-RU").primaryTargetCode)
    }

    private fun dataRowCount(resourcePath: String): Int =
        CatalogSeedResourceReader.readText(resourcePath)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .drop(1)
            .count()

    private fun jsonlRowCount(resourcePath: String): Int = readJsonl(resourcePath).size

    private fun readJsonl(resourcePath: String): List<String> =
        CatalogSeedResourceReader.readText(resourcePath)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    private companion object {
        private const val PACK_ID = "TECH_POWER_CHARGING_CABLES_route_publish_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.POWER_CHARGING_CABLES"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/category_packs/tech_power_charging_cables/data_overlays/route_publish_delta/v1_1"
    }
}
