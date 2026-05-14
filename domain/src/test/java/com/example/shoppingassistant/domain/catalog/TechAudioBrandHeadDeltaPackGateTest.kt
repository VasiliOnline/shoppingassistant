package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechAudioBrandHeadDeltaPackGateTest {

    @Test
    fun brand_head_delta_is_registered_as_overlay_only() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(listOf("data_overlay_only"), manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in manifest.archetypes)
        assertEquals(BASE_PATH, manifest.basePath)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals(15, manifest.requiredFiles.size)
        assertTrue("audio_brand_head_delta.tech_audio.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("audio_brand_aliases_delta.ru.tech_audio.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("brand_facet_smoke_queries.tech_audio.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("sample_offers.audio_brand_delta.v1_1.jsonl" in manifest.requiredFiles)
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
    fun package_manifest_keeps_delta_out_of_public_tree_and_merchandising() {
        val packageManifest = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.json")

        assertTrue(packageManifest.contains("\"pack_type\": \"data_overlay_patch\""))
        assertTrue(packageManifest.contains("\"public_category\": false"))
        assertTrue(packageManifest.contains("\"accepts_offers_directly\": false"))
        assertTrue(packageManifest.contains("\"schema_breaking_changes\": false"))
        assertTrue(packageManifest.contains("\"new_category_codes\": []"))

        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests.first { it.packId == PACK_ID }
        listOf("preset", "collection", "seo", "landing").forEach { forbidden ->
            assertFalse(
                "Delta must not mount $forbidden files.",
                manifest.requiredFiles.any { it.contains(forbidden, ignoreCase = true) },
            )
        }
    }

    @Test
    fun delta_artifacts_have_expected_runtime_scale() {
        assertEquals(38, dataRowCount("$BASE_PATH/audio_brand_head_delta.tech_audio.v1_1.tsv"))
        assertEquals(179, dataRowCount("$BASE_PATH/audio_brand_aliases_delta.ru.tech_audio.v1_1.tsv"))
        assertEquals(54, dataRowCount("$BASE_PATH/brand_facet_smoke_queries.tech_audio.v1_1.tsv"))
        assertEquals(53, jsonlRowCount("$BASE_PATH/sample_offers.audio_brand_delta.v1_1.jsonl"))
    }

    @Test
    fun registry_brand_dictionary_includes_delta_heads_and_aliases() {
        val brands = Stage22RegistryLoader.loadSnapshot()
            .dictionaries
            .getValue("brand")
            .entries
            .associateBy { it.valueCode }

        assertTrue(brands.keys.containsAll(
            setOf(
                "SENNHEISER",
                "AUDIO_TECHNICA",
                "SHURE",
                "ANKER_SOUNDCORE",
                "BOWERS_WILKINS",
                "HYPERX",
                "STEELSERIES",
                "CREATIVE",
            ),
        ))
        assertTrue("сенхайзер" in brands.getValue("SENNHEISER").aliases)
        assertTrue("аудиотехника" in brands.getValue("AUDIO_TECHNICA").aliases)
        assertTrue("шур" in brands.getValue("SHURE").aliases)
        assertTrue("саундкор" in brands.getValue("ANKER_SOUNDCORE").aliases)
        assertTrue("b&w" in brands.getValue("BOWERS_WILKINS").aliases)
        assertTrue("creative sound blaster" in brands.getValue("CREATIVE").aliases)
    }

    @Test
    fun brand_smoke_queries_are_mounted_as_audio_routes_without_cross_branch_leaks() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("sennheiser momentum 4", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("сенхайзер hd 560s", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("audio technica ath m50x", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("yamaha receiver", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("hyperx cloud headset", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("creative sound blaster", "ru-RU").primaryTargetCode)

        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("hyperx клавиатура", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("steelseries мышь", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("creative webcam", "en-US").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("pioneer магнитола автомобильная", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_AUDIO_brand_head_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.AUDIO"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_audio/data_overlays/brand_head_delta/v1_1"
    }
}
