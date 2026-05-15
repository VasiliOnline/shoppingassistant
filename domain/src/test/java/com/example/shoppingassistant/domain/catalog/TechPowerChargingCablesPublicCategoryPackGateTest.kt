package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPowerChargingCablesPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_power_artifacts() {
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
        assertEquals("user_surface.tech_power_charging_cables.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("power_model_head_seed.tech_power_charging_cables.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("power_model_aliases.tech_power_charging_cables.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_power_charging_cables.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("docs/ROUTE_BOUNDARIES_RU.md" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted power package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun power_pack_keeps_public_tree_coarse() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.CHARGERS",
            "TECH.CABLES",
            "TECH.POWER_BANKS",
            "TECH.UPS",
            "TECH.HDMI_CABLES",
            "TECH.USB_CABLES",
            "TECH.ADAPTERS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_power_attributes_dictionaries_and_brand_heads() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val powerType = registry.attributes.getValue("power_cable_type")
        assertEquals(Stage22ValueType.ENUM, powerType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, powerType.valueSetType)
        assertTrue(powerType.isIdentity)
        assertTrue(powerType.isFacet)

        val powerTypes = registry.dictionaries.getValue("power_cable_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf(
                "WALL_CHARGER",
                "WIRELESS_CHARGER",
                "CAR_CHARGER",
                "USB_CABLE",
                "HDMI_CABLE",
                "AUDIO_VIDEO_CABLE",
                "ADAPTER",
                "POWER_BANK",
                "PORTABLE_POWER_STATION",
                "UPS",
                "BATTERY",
                "SURGE_PROTECTOR",
                "CABLE_MANAGEMENT",
            ),
            powerTypes,
        )

        val connectorTypes = registry.dictionaries.getValue("connector_type").entries.map { it.valueCode }.toSet()
        assertTrue(connectorTypes.containsAll(setOf("USB_C", "LIGHTNING", "HDMI", "DISPLAYPORT", "MAGSAFE_APPLE", "IEC_C13")))

        val fastChargeProtocols = registry.dictionaries.getValue("fast_charge_protocol").entries.map { it.valueCode }.toSet()
        assertTrue(fastChargeProtocols.containsAll(setOf("USB_PD", "QC_3_0", "SAMSUNG_SUPER_FAST", "APPLE_FAST_CHARGE")))

        val batterySizes = registry.dictionaries.getValue("battery_size").entries.map { it.valueCode }.toSet()
        assertTrue(batterySizes.containsAll(setOf("AA", "AAA", "CR2032", "18650", "LAPTOP_BATTERY")))

        val liveStates = registry.dictionaries.getValue("live_value_candidate_state").entries.map { it.valueCode }.toSet()
        assertEquals(setOf("CANDIDATE", "PROMOTED", "REJECTED"), liveStates)

        val brands = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        assertTrue(brands.containsAll(setOf("ANKER", "UGREEN", "BASEUS", "ECOFLOW", "BLUETTI", "APC", "CYBERPOWER", "BELKIN")))

        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("wattage_w").valueType)
        assertEquals("W", registry.attributes.getValue("wattage_w").unit)
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("capacity_mah").valueType)
        assertEquals("mAh", registry.attributes.getValue("capacity_mah").unit)
    }

    @Test
    fun effective_spec_exposes_power_facets_and_runtime_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "power_cable_type",
            "brand",
            "model_name_text",
            "condition",
            "connector_type",
            "wattage_w",
            "capacity_mah",
            "fast_charge_protocol",
            "qi_standard",
            "cable_standard",
            "hdmi_version",
            "battery_size",
            "ups_topology",
            "routing_confidence",
            "ingestion_confidence",
            "live_value_candidate_state",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.POWER_CHARGING_CABLES effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("power_cable_type", "brand", "condition", "connector_type", "wattage_w", "capacity_mah").forEach { facet ->
            assertTrue("$facet must be a TECH.POWER_CHARGING_CABLES facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        listOf("power_cable_type", "brand", "condition", "model_name_text", "connector_type", "wattage_w", "capacity_mah").forEach { core ->
            assertEquals("$core must be T0 core.", CatalogAttributeRole.T0_CORE, attributes.getValue(core).role)
        }
        listOf("port_count", "fast_charge_protocol", "usb_pd_support", "gan_charger", "qi_standard", "cable_length_m", "cable_standard", "hdmi_version", "battery_size", "ups_topology").forEach { typeCritical ->
            assertEquals("$typeCritical must be T1 type critical.", CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue(typeCritical).role)
        }
        listOf("routing_confidence", "ingestion_confidence", "dedup_key", "source_evidence", "live_value_candidate_state").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_power_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("power_cable_type" in profile.mainTypedFacetKeys)
        assertTrue("connector_type" in profile.mainTypedFacetKeys)
        assertTrue("wattage_w" in profile.mainTypedFacetKeys)
        assertTrue("capacity_mah" in profile.mainTypedFacetKeys)
        assertTrue("fast_charge_protocol" in profile.additionalTypedFacetKeys)
        assertTrue("cable_standard" in profile.additionalTypedFacetKeys)
        assertTrue("hdmi_version" in profile.additionalTypedFacetKeys)
        assertTrue("battery_size" in profile.additionalTypedFacetKeys)
        assertTrue("ups_topology" in profile.additionalTypedFacetKeys)
        assertTrue("routing_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("live_value_candidate_state" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun power_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/power_model_head_seed.tech_power_charging_cables.v1_0.tsv")

        assertTrue("TECH.POWER_CHARGING_CABLES model head seed should contain at least 50 rows.", rows.size >= 50)
        assertTrue(rows.any { it["brand"] == "ANKER" && it["model"] == "Anker 737 Power Bank 24000mAh" })
        assertTrue(rows.any { it["brand"] == "ECOFLOW" && it["model"] == "EcoFlow River 2" })
        assertTrue(rows.any { it["brand"] == "APC" && it["model"] == "APC Back-UPS 650VA" })
        assertTrue(rows.any { it["brand"] == "UGREEN" && it["model"] == "UGREEN HDMI 2.1 Cable 8K" })
    }

    @Test
    fun route_guards_cover_power_and_neighbor_boundaries() = runBlocking {
        val router = Stage21TechQueryRouter()

        listOf(
            "зарядка 20w usb c",
            "anker 65w gan charger",
            "беспроводная зарядка magsafe",
            "автозарядка usb c 65w",
            "кабель usb c 100w",
            "hdmi 2.1 кабель 8k",
            "aux кабель 3.5",
            "usb c hdmi adapter",
            "power bank",
            "ecoflow river 2",
            "ибп 650va",
            "батарейки aa duracell",
            "сетевой фильтр 6 розеток",
            "органайзер проводов",
            "кабель usb c lightning mfi",
        ).forEach { query ->
            val debug = router.routeWithCandidates(query, "ru-RU")
            assertEquals("$query must route to TECH.POWER_CHARGING_CABLES. top=${debug.topCandidates}", CATEGORY, debug.result.primaryTargetCode)
        }

        assertEquals("TECH.PHONE_ACCESSORIES", router.route("чехол iphone 13 magsafe", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("защитное стекло iphone 15", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("usb hub type c 7 in 1", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("док станция thunderbolt", "ru-RU").primaryTargetCode)
        assertEquals("TECH.NETWORKING", router.route("rj45 кабель 10 метров", "ru-RU").primaryTargetCode)
        assertEquals("TECH.NETWORKING", router.route("патч корд cat6", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("блок питания пк 750w", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", router.route("аккумулятор iphone 12", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("dash cam кабель питания", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("power supply atx corsair", "en-US").primaryTargetCode)
        val ethernetAdapterDebug = router.routeWithCandidates("адаптер type c ethernet", "ru-RU")
        assertEquals(
            "адаптер type c ethernet must route to TECH.COMPUTER_ACCESSORIES. top=${ethernetAdapterDebug.topCandidates}",
            "TECH.COMPUTER_ACCESSORIES",
            ethernetAdapterDebug.result.primaryTargetCode,
        )
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
        private const val PACK_ID = "TECH_POWER_CHARGING_CABLES_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.POWER_CHARGING_CABLES"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_power_charging_cables/v1_0"
    }
}
