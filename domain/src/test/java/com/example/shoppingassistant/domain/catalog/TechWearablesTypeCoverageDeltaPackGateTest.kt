package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechWearablesTypeCoverageDeltaPackGateTest {

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
        assertEquals(17, manifest.requiredFiles.size)
        assertTrue("values_delta.tech_wearables.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("aliases_delta.ru.tech_wearables.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("wearable_type_gap_audit.tech_wearables.v1_1.tsv" in manifest.requiredFiles)
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

        assertTrue(packageManifest.contains("\"patch_type\": \"data_overlay_patch\""))
        assertTrue(packageManifest.contains("\"public_category\": false"))
        assertTrue(packageManifest.contains("\"accepts_offers_directly\": false"))
        assertTrue(packageManifest.contains("\"schema_breaking_changes\": false"))
        assertTrue(packageManifest.contains("\"new_category_codes\": []"))
        assertTrue(packageManifest.contains("\"runtime_mode\": \"overlay_values_aliases_fixtures_only\""))

        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        listOf(
            "TECH.APPLE_WATCH",
            "TECH.WATCH_BANDS",
            "TECH.WEARABLE_ACCESSORIES",
            "TECH.AR_GLASSES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created by type coverage overlay.", forbidden in categoryCodes)
        }

        val registry = Stage22RegistryLoader.loadSnapshot()
        assertFalse("Overlay must not create a schema attribute for soft accessory subtype.", "wearable_accessory_soft_type" in registry.attributes)
    }

    @Test
    fun delta_artifacts_keep_declared_runtime_counts() {
        assertEquals(35, dataRowCount("$BASE_PATH/values_delta.tech_wearables.v1_1.tsv"))
        assertEquals(59, dataRowCount("$BASE_PATH/aliases_delta.ru.tech_wearables.v1_1.tsv"))
        assertEquals(25, dataRowCount("$BASE_PATH/golden_queries_delta.tech_wearables.v1_1.tsv"))
        assertEquals(36, jsonlRowCount("$BASE_PATH/sample_offers_delta.tech_wearables.v1_1.jsonl"))
        assertEquals(9, jsonlRowCount("$BASE_PATH/publish_sample_offers_delta.tech_wearables.v1_1.jsonl"))

        val validationReport = CatalogSeedResourceReader.readText(
            "$BASE_PATH/validation_report.tech_wearables_type_coverage_delta.v1_1.json",
        )
        assertTrue(validationReport.contains("\"static_status\": \"PASS\""))
        assertTrue(validationReport.contains("\"new_category_codes\": 0"))
        assertTrue(validationReport.contains("\"schema_breaking_changes\": false"))
    }

    @Test
    fun registry_value_dictionaries_include_delta_heads_without_schema_breaks() {
        val registry = Stage22RegistryLoader.loadSnapshot()
        val brandCodes = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        val compatibleBrandCodes = registry.dictionaries.getValue("compatible_wearable_brand").entries.map { it.valueCode }.toSet()
        val bandConnectors = registry.dictionaries.getValue("band_connector_type").entries.map { it.valueCode }.toSet()

        assertTrue(brandCodes.containsAll(setOf("NOMAD", "SPIGEN", "ESR", "VITURE", "ROKID", "RAYNEO", "VUZIX")))
        assertTrue(compatibleBrandCodes.containsAll(setOf("NOMAD", "SPIGEN", "VITURE", "ROKID", "RAYNEO")))
        assertTrue(
            bandConnectors.containsAll(
                setOf(
                    "APPLE_WATCH_LUG",
                    "GALAXY_WATCH_QUICK_RELEASE",
                    "GARMIN_QUICKFIT",
                    "UNIVERSAL_20MM",
                    "UNIVERSAL_22MM",
                ),
            ),
        )
    }

    @Test
    fun type_coverage_queries_route_to_wearables_without_known_cross_branch_leaks() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("ремешок для apple watch ultra", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("чехол galaxy watch", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("защитное стекло apple watch", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("viture one", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("rokid max", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("ar glasses", "en-US").primaryTargetCode)

        assertEquals("TECH.PHONES", router.route("iphone 13", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол iphone 13", "ru-RU").primaryTargetCode)
        assertEquals("TECH.AUDIO", router.route("airpods", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_WEARABLES_type_coverage_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.WEARABLES"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/category_packs/tech_wearables/data_overlays/type_coverage_delta/v1_1"
    }
}
