package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPrintersScannersConsumablesCompatibilityDeltaPackGateTest {

    @Test
    fun consumables_delta_is_registered_as_overlay_only() {
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
        assertTrue("values_delta.tech_printers_scanners_consumables.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("aliases_delta.ru.tech_printers_scanners_consumables.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("publish_sample_offers_delta.tech_printers_scanners_consumables.v1_1.jsonl" in manifest.requiredFiles)
        assertTrue("consumable_compatibility_policy.tech_printers_scanners.v1_1.yaml" in manifest.requiredFiles)
        assertNull(manifest.userSurfaceFile)
        assertNull(manifest.routeGuardLayer)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted printers consumables delta file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun package_manifest_keeps_delta_out_of_schema_and_public_tree() {
        val packageManifest = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.json")

        assertTrue(packageManifest.contains("\"pack_type\": \"data_overlay_patch\""))
        assertTrue(packageManifest.contains("\"public_category\": false"))
        assertTrue(packageManifest.contains("\"schema_breaking_changes\": false"))
        assertTrue(packageManifest.contains("\"ui_changes\": false"))
        assertTrue(packageManifest.contains("\"new_category_codes\": 0"))

        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        listOf(
            "TECH.PRINTER_CONSUMABLES",
            "TECH.INK",
            "TECH.TONER",
            "TECH.CARTRIDGES",
            "TECH.OTHER_ELECTRONICS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created by consumables overlay.", forbidden in categoryCodes)
        }
    }

    @Test
    fun delta_artifacts_keep_declared_runtime_counts() {
        assertEquals(33, dataRowCount("$BASE_PATH/values_delta.tech_printers_scanners_consumables.v1_1.tsv"))
        assertEquals(149, dataRowCount("$BASE_PATH/aliases_delta.ru.tech_printers_scanners_consumables.v1_1.tsv"))
        assertEquals(25, dataRowCount("$BASE_PATH/golden_queries_delta.tech_printers_scanners_consumables.v1_1.tsv"))
        assertEquals(21, jsonlRowCount("$BASE_PATH/sample_offers_delta.tech_printers_scanners_consumables.v1_1.jsonl"))
        assertEquals(18, jsonlRowCount("$BASE_PATH/publish_sample_offers_delta.tech_printers_scanners_consumables.v1_1.jsonl"))

        val snapshot = CatalogSeedResourceReader.readText("$BASE_PATH/effective_spec_snapshot.expected.tech_printers_scanners_consumables.v1_1.json")
        assertTrue(snapshot.contains("\"expected_no_schema_breaking_changes\": true"))
        assertTrue(snapshot.contains("\"expected_new_values\": 33"))
        assertTrue(snapshot.contains("\"expected_new_aliases\": 149"))
        assertTrue(snapshot.contains("\"compatibility_policy\": \"soft_suggest_only\""))
    }

    @Test
    fun publish_delta_closes_consumable_fixture_gaps() {
        val rows = readJsonl("$BASE_PATH/publish_sample_offers_delta.tech_printers_scanners_consumables.v1_1.jsonl")

        assertTrue(rows.all { it.contains("\"categoryCode\": \"TECH.PRINTERS_SCANNERS\"") })
        assertTrue(rows.any { it.contains("\"compatible_cartridge_code\": \"HP_305XL\"") })
        assertTrue(rows.any { it.contains("\"compatible_cartridge_code\": \"HP_106A\"") })
        assertTrue(rows.any { it.contains("\"compatible_cartridge_code\": \"CANON_PG_445\"") })
        assertTrue(rows.any { it.contains("\"compatible_cartridge_code\": \"EPSON_103\"") })
        assertTrue(rows.any { it.contains("\"compatible_cartridge_code\": \"BROTHER_TN_2421\"") })
        assertTrue(rows.any { it.contains("\"printer_scanner_type\": \"PRINTER_ACCESSORY\"") })
    }

    @Test
    fun consumables_delta_queries_cover_cartridges_and_conflict_boundaries() = runBlocking {
        val router = Stage21TechQueryRouter()

        listOf(
            "картридж hp 305",
            "hp 305xl color",
            "тонер hp 106a",
            "картридж hp 12a q2612a",
            "canon pg 445 black",
            "epson 103 чернила",
            "brother tn 2421",
            "brother lc3219 картридж",
            "dymo d1 tape",
            "zebra labels roll",
        ).forEach { query ->
            val debug = router.routeWithCandidates(query, "ru-RU")
            assertEquals("$query must route to TECH.PRINTERS_SCANNERS. top=${debug.topCandidates}", CATEGORY, debug.result.primaryTargetCode)
        }

        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("obd2 scanner", "ru-RU").primaryTargetCode)
        assertEquals("B.TECH", router.route("3d printer", "ru-RU").primaryTargetCode)
        assertEquals("B.TECH", router.route("филамент pla", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_PRINTERS_SCANNERS_consumables_compatibility_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.PRINTERS_SCANNERS"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/category_packs/tech_printers_scanners/data_overlays/consumables_compatibility_delta/v1_1"
    }
}
