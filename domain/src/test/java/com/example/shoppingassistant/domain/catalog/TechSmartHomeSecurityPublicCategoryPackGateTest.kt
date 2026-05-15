package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechSmartHomeSecurityPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_smart_home_artifacts() {
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
        assertEquals("user_surface.tech_smart_home_security.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("smart_home_model_head_seed.tech_smart_home_security.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("smart_home_model_aliases.tech_smart_home_security.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_smart_home_security.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun smart_home_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.SMART_SPEAKERS",
            "TECH.SMART_CAMERAS",
            "TECH.AQARA",
            "TECH.RING",
            "TECH.NEST",
            "TECH.SMART_LOCKS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_smart_home_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val smartHomeType = registry.attributes.getValue("smart_home_type")
        assertEquals(Stage22ValueType.ENUM, smartHomeType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, smartHomeType.valueSetType)
        assertTrue(smartHomeType.isIdentity)
        assertTrue(smartHomeType.isFacet)

        val smartHomeTypes = registry.dictionaries.getValue("smart_home_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            smartHomeTypes.containsAll(
                setOf(
                    "SMART_SPEAKER",
                    "SMART_DISPLAY",
                    "SMART_LIGHT",
                    "SMART_PLUG",
                    "SMART_SWITCH",
                    "SMART_SENSOR",
                    "SECURITY_CAMERA",
                    "VIDEO_DOORBELL",
                    "SMART_LOCK",
                    "SMART_THERMOSTAT",
                    "HOME_HUB",
                    "ALARM_SIREN",
                    "AUTOMATION_KIT",
                ),
            ),
        )

        val protocols = registry.dictionaries.getValue("protocol").entries.map { it.valueCode }.toSet()
        assertTrue(protocols.containsAll(setOf("WIFI", "ZIGBEE", "Z_WAVE", "THREAD", "MATTER", "BLUETOOTH", "RF_433", "INFRARED", "ETHERNET")))

        val ecosystems = registry.dictionaries.getValue("ecosystem").entries.map { it.valueCode }.toSet()
        assertTrue(ecosystems.containsAll(setOf("APPLE_HOME", "GOOGLE_HOME", "AMAZON_ALEXA", "MATTER_ECOSYSTEM", "AQARA_HOME", "XIAOMI_MI_HOME", "PROPRIETARY_APP")))

        val brands = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        assertTrue(brands.containsAll(setOf("AQARA", "SONOFF", "REOLINK", "YANDEX")))
    }

    @Test
    fun effective_spec_exposes_smart_home_facets_and_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "smart_home_type",
            "brand",
            "model_name_text",
            "protocol",
            "ecosystem",
            "power_source",
            "voice_assistant_support",
            "hub_required",
            "indoor_outdoor",
            "video_resolution",
            "sensor_kind",
            "lock_type",
            "privacy_risk_flag",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.SMART_HOME_SECURITY effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("smart_home_type", "brand", "condition", "protocol", "ecosystem", "power_source").forEach { facet ->
            assertTrue("$facet must be a TECH.SMART_HOME_SECURITY facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        listOf("smart_home_type", "brand", "condition", "protocol", "ecosystem", "power_source").forEach { core ->
            assertEquals("$core must be T0 core.", CatalogAttributeRole.T0_CORE, attributes.getValue(core).role)
        }
        listOf("voice_assistant_support", "hub_required", "indoor_outdoor", "video_resolution", "sensor_kind", "lock_type").forEach { typeCritical ->
            assertEquals("$typeCritical must be T1 type critical.", CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue(typeCritical).role)
        }
        listOf(
            "normalization_confidence",
            "identity_evidence",
            "route_confidence",
            "source_quality_score",
            "dedup_fingerprint",
            "privacy_risk_flag",
        ).forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_smart_home_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("smart_home_type" in profile.mainTypedFacetKeys)
        assertTrue("protocol" in profile.mainTypedFacetKeys)
        assertTrue("ecosystem" in profile.mainTypedFacetKeys)
        assertTrue("power_source" in profile.mainTypedFacetKeys)
        assertTrue("matter_support" in profile.additionalTypedFacetKeys)
        assertTrue("thread_support" in profile.additionalTypedFacetKeys)
        assertTrue("hub_required" in profile.additionalTypedFacetKeys)
        assertTrue("indoor_outdoor" in profile.additionalTypedFacetKeys)
        assertTrue("video_resolution" in profile.additionalTypedFacetKeys)
        assertTrue("sensor_kind" in profile.additionalTypedFacetKeys)
        assertTrue("privacy_risk_flag" in profile.hiddenTypedFacetKeys)
        assertTrue("route_confidence" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun smart_home_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/smart_home_model_head_seed.tech_smart_home_security.v1_0.tsv")

        assertTrue("TECH.SMART_HOME_SECURITY model head seed should contain at least 40 rows.", rows.size >= 40)
        assertTrue(rows.any { it["brand"] == "GOOGLE_NEST" && it["model_name"] == "Nest Hub 2nd Gen" })
        assertTrue(rows.any { it["brand"] == "AQARA" && it["model_name"] == "Aqara Hub M3" })
        assertTrue(rows.any { it["brand"] == "TP_LINK_TAPO_KASA" && it["model_name"] == "Tapo C200" })
        assertTrue(rows.any { it["brand"] == "SONOFF" && it["model_name"] == "Sonoff ZBMINI" })
        assertTrue(rows.any { it["brand"] == "YANDEX" && it["model_name"] == "Яндекс Станция Мини" })
    }

    @Test
    fun route_guard_conflicts_keep_smart_home_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("умная колонка алиса", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("яндекс станция мини", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("google nest hub", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("умная лампа e27", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("tapo p110", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("sonoff zbmini", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("датчик открытия aqara", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("камера видеонаблюдения wifi", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("nuki lock", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("zigbee хаб", "ru-RU").primaryTargetCode)

        assertEquals("TECH.AUDIO", router.route("bluetooth колонка jbl flip", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("web камера logitech c920", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("видеорегистратор xiaomi 70mai", "ru-RU").primaryTargetCode)
        assertEquals("TECH.NETWORKING", router.route("роутер tp link archer", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAMERAS_DRONES", router.route("экшн камера gopro", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONES", router.route("iphone 15 pro", "ru-RU").primaryTargetCode)
        assertEquals("TECH.WEARABLES", router.route("apple watch series 9", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("hdmi кабель", "ru-RU").primaryTargetCode)
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
        private const val PACK_ID = "TECH_SMART_HOME_SECURITY_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.SMART_HOME_SECURITY"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_smart_home_security/v1_0"
    }
}
