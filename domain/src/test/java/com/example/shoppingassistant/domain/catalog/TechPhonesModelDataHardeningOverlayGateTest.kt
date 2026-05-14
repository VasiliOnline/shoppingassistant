package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPhonesModelDataHardeningOverlayGateTest {
    @Test
    fun overlay_is_registered_as_data_only_mount_over_phones() {
        assertTrue("data_overlay_only" in CatalogPackV2RegistryLoader.contract.sections)

        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(listOf("data_overlay_only"), manifest.packSections)
        assertEquals(BASE_PATH, manifest.basePath)
        assertEquals(20, manifest.requiredFiles.size)
        assertTrue("model_head_seed_delta.tech_phones.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("model_search_priority_seed.tech_phones.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("model_variant_minimal_matrix.tech_phones.v1_1.tsv" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)
        assertFalse(manifest.requiredFiles.any { it.startsWith("facet_presets", ignoreCase = true) })
        assertFalse(manifest.requiredFiles.any { it.startsWith("facet_collections", ignoreCase = true) })
        assertFalse(manifest.requiredFiles.any { it.contains("seo", ignoreCase = true) })

        val packageManifest = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.json")
        assertTrue(packageManifest.contains("\"mode\": \"data_overlay_only\""))
        assertTrue(packageManifest.contains("\"no_new_category_codes\": true"))
        assertTrue(packageManifest.contains("\"target_base_pack\": \"TECH_PHONES_public_category_pack_v1_0_RU\""))
    }

    @Test
    fun overlay_does_not_create_categories_or_touch_accessories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.IPHONE",
            "TECH.SAMSUNG_PHONES",
            "TECH.GALAXY",
            "TECH.PIXEL",
            "TECH.CASES",
            "TECH.CHARGERS",
            "TECH.CABLES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created by model-data overlay.", forbidden in categoryCodes)
        }

        val manifestText = CatalogSeedResourceReader.readText("$BASE_PATH/manifest.json")
        assertTrue(manifestText.contains("\"phone_accessories\""))
        assertFalse(manifestText.contains("\"TECH.PHONE_ACCESSORIES\""))
    }

    @Test
    fun overlay_artifacts_keep_declared_runtime_counts_and_soft_variant_policy() {
        assertEquals(46, readTsv("brand_family_head_seed.tech_phones.v1_1.tsv").size)
        assertEquals(121, readTsv("model_head_seed_delta.tech_phones.v1_1.tsv").size)
        assertEquals(1128, readTsv("model_aliases_delta.ru.tech_phones.v1_1.tsv").size)
        assertEquals(121, readTsv("model_search_priority_seed.tech_phones.v1_1.tsv").size)

        val variantRows = readTsv("model_variant_minimal_matrix.tech_phones.v1_1.tsv")
        assertEquals(121, variantRows.size)
        assertTrue(variantRows.all { row -> row.getValue("validation_mode") == "soft_suggest_only" })
        assertTrue(
            readTsv("model_search_priority_seed.tech_phones.v1_1.tsv")
                .all { row -> row.getValue("not_a_collection") == "true" },
        )
    }

    @Test
    fun overlay_projects_model_data_to_runtime_identity_search() {
        val pack = CatalogGovernanceCuratedSeed.snapshot.packs
            .firstOrNull { it.packCode == "TECH_PHONES_MODEL_DATA_HARDENING_V1_1" }

        assertNotNull(pack)
        requireNotNull(pack)
        assertEquals("data_overlay_only", pack.metadata["mode"])
        assertEquals("TECH_PHONES_public_category_pack_v1_0_RU", pack.metadata["targetBasePack"])
        assertEquals("soft_suggest_only", pack.metadata["variantValidationMode"])
        assertEquals(121, pack.models.size)
        assertTrue(pack.canonicalValues.isEmpty())
        assertTrue(pack.models.all { it.defaultCategoryCode == CATEGORY })
        assertTrue(pack.models.all { it.metadata["notACollection"] == "true" })
        assertTrue(pack.models.all { it.metadata["variantValidationMode"] == "soft_suggest_only" })

        val models = CatalogCanonicalModelRegistry.models().associateBy { it.modelCode }
        assertEquals(100, models.getValue("IPHONE_13").searchWeight)
        assertEquals(100, models.getValue("GALAXY_S24_ULTRA").searchWeight)
        assertEquals("TECH.PHONES", models.getValue("GOOGLE_PIXEL_10_PRO").defaultCategoryCode)
    }

    @Test
    fun overlay_model_aliases_resolve_popular_storage_queries_without_hard_validation() {
        CatalogCanonicalModelRegistry.resetProvider()

        assertModelMatch("iphone 13 128", "IPHONE_13")
        assertModelMatch("s24 ultra 512", "GALAXY_S24_ULTRA")
        assertModelMatch("redmi note 14 pro 256", "REDMI_REDMI_NOTE_14_PRO_5G")
        assertModelMatch("pixel 9 pro 256", "PIXEL_9_PRO")
    }

    private fun assertModelMatch(
        query: String,
        expectedModelCode: String,
    ) {
        val match = CatalogCanonicalModelRegistry.matchQuery(query)
        assertNotNull("Expected '$query' to resolve to a canonical phone model.", match)
        requireNotNull(match)
        assertEquals(expectedModelCode, match.modelCode)
        assertEquals(CATEGORY, match.defaultCategoryCode)
    }

    private fun readTsv(fileName: String): List<Map<String, String>> {
        val lines = CatalogSeedResourceReader.readText("$BASE_PATH/$fileName")
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        val header = lines.first().split('\t')
        return lines.drop(1).map { line ->
            val cells = line.split('\t')
            header.indices.associate { index -> header[index] to cells.getOrElse(index) { "" } }
        }
    }

    private companion object {
        private const val PACK_ID = "TECH_PHONES_model_data_hardening_pack_v1_1_RU"
        private const val CATEGORY = "TECH.PHONES"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/category_packs/tech_phones/data_overlays/model_data_hardening/v1_1"
    }
}
