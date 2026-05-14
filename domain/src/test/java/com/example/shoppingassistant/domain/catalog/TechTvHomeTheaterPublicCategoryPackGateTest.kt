package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechTvHomeTheaterPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_av_artifacts() {
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
        assertEquals("user_surface.tech_tv_home_theater.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("av_model_head_seed.tech_tv_home_theater.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("av_model_aliases.tech_tv_home_theater.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_tv_home_theater.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun tv_home_theater_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.TV",
            "TECH.PROJECTORS",
            "TECH.APPLE_TV",
            "TECH.ROKU",
            "TECH.DENON_RECEIVERS",
            "TECH.TV_MOUNTS",
            "TECH.PROJECTOR_SCREENS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_av_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val avType = registry.attributes.getValue("av_type")
        assertEquals(Stage22ValueType.ENUM, avType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, avType.valueSetType)
        assertTrue(avType.isIdentity)
        assertTrue(avType.isFacet)

        val avTypes = registry.dictionaries.getValue("av_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            avTypes.containsAll(
                setOf(
                    "TV",
                    "PROJECTOR",
                    "STREAMING_DEVICE",
                    "TV_BOX",
                    "HOME_THEATER_SYSTEM",
                    "AV_RECEIVER",
                    "DVD_BLU_RAY_PLAYER",
                    "SET_TOP_BOX",
                    "REMOTE_CONTROL",
                    "TV_MOUNT",
                    "PROJECTOR_SCREEN",
                ),
            ),
        )

        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("screen_size_in").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("screen_size_in").valueSetType)
        assertEquals(Stage22ValueType.ENUM, registry.attributes.getValue("resolution").valueType)
        assertTrue(registry.dictionaries.getValue("resolution").entries.any { it.valueCode == "UHD_4K" })
        assertTrue(registry.dictionaries.getValue("display_technology").entries.any { it.valueCode == "QD_OLED" })
        assertTrue(registry.dictionaries.getValue("streaming_os").entries.any { it.valueCode == "TVOS" })
        assertTrue(registry.dictionaries.getValue("receiver_channels").entries.any { it.valueCode == "SEVEN_TWO" })
        assertTrue(registry.dictionaries.getValue("mount_type").entries.any { it.valueCode == "FULL_MOTION" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "DENON" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "ELITE_SCREENS" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("hdmi_ports").valueType)
        assertEquals(Stage22ValueType.BOOLEAN, registry.attributes.getValue("local_dimming").valueType)
    }

    @Test
    fun effective_spec_exposes_av_facets_and_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "av_type",
            "brand",
            "model_name_text",
            "screen_size_in",
            "resolution",
            "display_technology",
            "projector_type",
            "streaming_os",
            "receiver_channels",
            "mount_type",
            "screen_format",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.TV_HOME_THEATER effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("av_type", "brand", "condition", "screen_size_in", "resolution", "display_technology").forEach { facet ->
            assertTrue("$facet must be a TECH.TV_HOME_THEATER facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("av_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("screen_size_in").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("resolution").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("display_technology").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("projector_type").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("receiver_channels").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("mount_type").role)

        listOf(
            "identity_confidence",
            "spec_confidence",
            "route_confidence",
            "source_evidence",
            "dedup_key",
            "image_fingerprint",
            "normalized_title",
        ).forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_av_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("av_type" in profile.mainTypedFacetKeys)
        assertTrue("screen_size_in" in profile.mainTypedFacetKeys)
        assertTrue("resolution" in profile.mainTypedFacetKeys)
        assertTrue("display_technology" in profile.mainTypedFacetKeys)
        assertTrue("projector_type" in profile.additionalTypedFacetKeys)
        assertTrue("streaming_os" in profile.additionalTypedFacetKeys)
        assertTrue("receiver_channels" in profile.additionalTypedFacetKeys)
        assertTrue("mount_type" in profile.additionalTypedFacetKeys)
        assertTrue("route_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("source_evidence" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun av_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/av_model_head_seed.tech_tv_home_theater.v1_0.tsv")

        assertTrue("TECH.TV_HOME_THEATER model head seed should contain at least 60 rows.", rows.size >= 60)
        assertTrue(rows.any { it["brand"] == "SAMSUNG" && it["model"] == "QN90D" })
        assertTrue(rows.any { it["brand"] == "LG" && it["model"] == "OLED C4" })
        assertTrue(rows.any { it["brand"] == "APPLE" && it["model"] == "Apple TV 4K" })
        assertTrue(rows.any { it["brand"] == "DENON" && it["model"] == "AVR-X1800H" })
        assertTrue(rows.any { it["brand"] == "SANUS" && it["av_type"] == "TV_MOUNT" })
    }

    @Test
    fun route_guard_conflicts_keep_av_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("телевизор samsung 55 4k smart tv", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("лазерный проектор короткофокусный", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("apple tv 4k", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("av ресивер 7.2 atmos", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("кронштейн для телевизора vesa 400x400", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("экран для проектора 100 дюймов", "ru-RU").primaryTargetCode)
        assertEquals("TECH.AUDIO", router.route("саундбар samsung q990d", "ru-RU").primaryTargetCode)
        assertEquals("TECH.MONITORS_DISPLAYS", router.route("монитор 27 дюймов 144hz", "ru-RU").primaryTargetCode)
        assertEquals("TECH.GAMING", router.route("playstation 5 slim", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("hdmi кабель 2.1 3 метра", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("кронштейн для монитора vesa", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_TV_HOME_THEATER_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.TV_HOME_THEATER"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_tv_home_theater/v1_0"
    }
}
