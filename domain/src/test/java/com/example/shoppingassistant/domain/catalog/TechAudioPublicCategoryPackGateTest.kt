package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechAudioPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_audio_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_audio/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_audio.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(30, manifest.requiredFiles.size)
        assertTrue("audio_model_head_seed.tech_audio.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("audio_model_aliases.tech_audio.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_audio.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun audio_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.HEADPHONES",
            "TECH.EARBUDS",
            "TECH.SPEAKERS",
            "TECH.AIRPODS",
            "TECH.BOSE_QC",
            "TECH.SOUNDBARS",
            "TECH.MICROPHONES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_audio_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val audioType = registry.attributes.getValue("audio_type")
        assertEquals(Stage22ValueType.ENUM, audioType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, audioType.valueSetType)
        assertTrue(audioType.isIdentity)
        assertTrue(audioType.isFacet)

        val audioTypes = registry.dictionaries.getValue("audio_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            audioTypes.containsAll(
                setOf(
                    "HEADPHONES",
                    "EARBUDS",
                    "HEADSET",
                    "SPEAKER",
                    "SMART_SPEAKER",
                    "SOUNDBAR",
                    "MICROPHONE",
                    "AUDIO_INTERFACE",
                    "DAC_AMP",
                    "TURNTABLE",
                    "RECEIVER_AMPLIFIER",
                    "STUDIO_MONITOR",
                    "MP3_PLAYER",
                    "RADIO",
                    "AUDIO_ACCESSORY",
                ),
            ),
        )

        assertTrue(registry.dictionaries.getValue("form_factor").entries.any { it.valueCode == "TRUE_WIRELESS" })
        assertTrue(registry.dictionaries.getValue("connection_type").entries.any { it.valueCode == "HDMI_ARC" })
        assertTrue(registry.dictionaries.getValue("codec_support").entries.any { it.valueCode == "LDAC" })
        assertTrue(registry.dictionaries.getValue("driver_type").entries.any { it.valueCode == "PLANAR_MAGNETIC" })
        assertTrue(registry.dictionaries.getValue("audio_accessory_type").entries.any { it.valueCode == "BOOM_ARM" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "SENNHEISER" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "AUDIO_TECHNICA" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("battery_life_hours").valueType)
        assertEquals(Stage22ValueType.BOOLEAN, registry.attributes.getValue("microphone_included").valueType)
    }

    @Test
    fun effective_spec_exposes_audio_facets_and_hides_runtime_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "audio_type",
            "brand",
            "connection_type",
            "form_factor",
            "noise_control",
            "codec_support",
            "driver_type",
            "audio_accessory_type",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.AUDIO effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("audio_type", "brand", "connection_type", "form_factor", "noise_control", "audio_accessory_type").forEach { facet ->
            assertTrue("$facet must be a TECH.AUDIO facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("audio_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("brand").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("connection_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("form_factor").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("noise_control").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("codec_support").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("audio_accessory_type").role)

        listOf(
            "identity_confidence",
            "spec_confidence",
            "route_confidence",
            "source_evidence",
            "dedup_key",
            "image_fingerprint",
            "normalized_title",
            "counterfeit_risk",
        ).forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_audio_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("audio_type" in profile.mainTypedFacetKeys)
        assertTrue("connection_type" in profile.mainTypedFacetKeys)
        assertTrue("form_factor" in profile.mainTypedFacetKeys)
        assertTrue("noise_control" in profile.additionalTypedFacetKeys)
        assertTrue("codec_support" in profile.additionalTypedFacetKeys)
        assertTrue("audio_accessory_type" in profile.additionalTypedFacetKeys)
        assertTrue("counterfeit_risk" in profile.hiddenTypedFacetKeys)
        assertTrue("route_confidence" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun audio_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/audio_model_head_seed.tech_audio.v1_0.tsv")

        assertTrue("TECH.AUDIO model head seed should contain at least 60 rows.", rows.size >= 60)
        assertTrue(rows.any { it["brand"] == "APPLE" && it["model_code"] == "AIRPODS_PRO_2_USB_C" })
        assertTrue(rows.any { it["brand"] == "SONY" && it["model_code"] == "WH_1000XM5" })
        assertTrue(rows.any { it["brand"] == "SHURE" && it["model_code"] == "SM7B" })
        assertTrue(rows.any { it["brand"] == "YAMAHA" && it["model_code"] == "HS5" })
        assertTrue(rows.any { it["brand"] == "FOCUSRITE" && it["model_code"] == "SCARLETT_2I2_4TH_GEN" })
    }

    @Test
    fun route_guard_conflicts_keep_audio_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("airpods pro 2 usb c", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("sony wh-1000xm5", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("shure sm7b микрофон", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("focusrite scarlett 2i2", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("yamaha hs5", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("кабель для наушников", "ru-RU").primaryTargetCode)
        assertEquals("TECH.GAMING", router.route("игровая гарнитура ps5", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("автомагнитола 2 din", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("колонки автомобильные", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("кабель usb c зарядка", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("переходник hdmi usb c", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("webcam logitech c920", "en-US").primaryTargetCode)
        assertEquals("TECH.MONITORS_DISPLAYS", router.route("монитор 27 дюймов", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_AUDIO_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.AUDIO"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_audio/v1_0"
    }
}
