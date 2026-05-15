package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechNetworkingPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_and_type_delta_are_registered_with_artifacts() {
        val manifests = CatalogPackV2RegistryLoader.packManifests.manifests.associateBy { it.packId }
        val base = manifests[BASE_PACK_ID]
        val delta = manifests[DELTA_PACK_ID]

        assertNotNull(base)
        requireNotNull(base)
        assertEquals(CATEGORY, base.categoryCode)
        assertEquals(BASE_PATH, base.basePath)
        assertTrue("category_schema_pack" in base.packSections)
        assertTrue("identity_pack" in base.packSections)
        assertTrue("compatibility_pack" in base.packSections)
        assertTrue("route_guard_pack" in base.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in base.archetypes)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in base.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in base.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in base.archetypes)
        assertEquals("user_surface.tech_networking.v1_0.yaml", base.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", base.routeGuardLayer)
        assertEquals(29, base.requiredFiles.size)
        assertTrue("networking_model_head_seed.tech_networking.v1_0.tsv" in base.requiredFiles)
        assertTrue("networking_model_aliases.tech_networking.ru.v1_0.tsv" in base.requiredFiles)
        assertTrue("route_guardrails.tech_networking.v1_0.yaml" in base.requiredFiles)
        assertTrue("docs/ROUTE_BOUNDARIES_RU.md" in base.requiredFiles)

        assertNotNull(delta)
        requireNotNull(delta)
        assertEquals(CATEGORY, delta.categoryCode)
        assertEquals(DELTA_PATH, delta.basePath)
        assertEquals(listOf("data_overlay_only"), delta.packSections)
        assertEquals(18, delta.requiredFiles.size)
        assertTrue("values_delta.tech_networking.v1_1.tsv" in delta.requiredFiles)
        assertTrue("aliases_delta.ru.tech_networking.v1_1.tsv" in delta.requiredFiles)
        assertTrue("route_guardrails_delta.tech_networking.v1_1.yaml" in delta.requiredFiles)
        assertTrue("model_head_seed_delta.tech_networking.v1_1.tsv" in delta.requiredFiles)

        (base.requiredFiles.map { "${base.basePath}/$it" } + delta.requiredFiles.map { "${delta.basePath}/$it" })
            .forEach { resource ->
                assertTrue("Missing mounted networking package file: $resource", CatalogSeedResourceReader.resourceExists(resource))
            }
    }

    @Test
    fun networking_pack_keeps_public_tree_coarse() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.ROUTERS",
            "TECH.SWITCHES",
            "TECH.MESH_WIFI",
            "TECH.TP_LINK",
            "TECH.UBIQUITI",
            "TECH.MIKROTIK",
            "TECH.GPON",
            "TECH.RJ45",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_networking_attributes_and_delta_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val networkingType = registry.attributes.getValue("networking_type")
        assertEquals(Stage22ValueType.ENUM, networkingType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, networkingType.valueSetType)
        assertTrue(networkingType.isIdentity)
        assertTrue(networkingType.isFacet)

        val networkingTypes = registry.dictionaries.getValue("networking_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            networkingTypes.containsAll(
                setOf(
                    "ROUTER",
                    "MESH_WIFI_SYSTEM",
                    "MODEM",
                    "ACCESS_POINT",
                    "SWITCH",
                    "WIFI_EXTENDER",
                    "NETWORK_ADAPTER",
                    "ANTENNA",
                    "POE_INJECTOR",
                    "NETWORK_ACCESSORY",
                ),
            ),
        )

        val brands = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        assertTrue(brands.containsAll(setOf("TP_LINK", "UBIQUITI", "MIKROTIK", "KEENETIC", "RUIJIE_REYEE", "CUDY", "GL_INET", "DRAYTEK")))

        val modemTypes = registry.dictionaries.getValue("modem_type").entries.map { it.valueCode }.toSet()
        assertTrue(modemTypes.containsAll(setOf("FIBER_ONT", "GPON_ONT", "XGPON_XGSPON_ONT")))

        val antennaTypes = registry.dictionaries.getValue("antenna_type").entries.map { it.valueCode }.toSet()
        assertTrue(antennaTypes.containsAll(setOf("OMNI", "SECTOR", "PARABOLIC")))

        val ethernetSpeeds = registry.dictionaries.getValue("ethernet_max_speed").entries.map { it.valueCode }.toSet()
        assertTrue(ethernetSpeeds.containsAll(setOf("1_GBPS", "2_5_GBPS", "10_GBPS")))

        val poeSupport = registry.dictionaries.getValue("poe_support").entries.map { it.valueCode }.toSet()
        assertTrue(poeSupport.containsAll(setOf("NO_POE", "POE_802_3AF", "PASSIVE_POE", "POE_INPUT")))
    }

    @Test
    fun effective_spec_exposes_networking_facets_and_runtime_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "networking_type",
            "brand",
            "model_name_text",
            "wifi_standard",
            "ethernet_max_speed",
            "port_count",
            "poe_support",
            "managed_switch",
            "modem_type",
            "antenna_type",
            "controller_ecosystem",
            "routing_confidence",
            "ingestion_confidence",
            "live_value_candidate_state",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.NETWORKING effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("networking_type", "brand", "condition", "wifi_standard", "ethernet_max_speed", "poe_support", "managed_switch").forEach { facet ->
            assertTrue("$facet must be a TECH.NETWORKING facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        listOf("networking_type", "brand", "condition", "model_name_text", "wifi_standard", "ethernet_max_speed", "port_count", "poe_support").forEach { core ->
            assertEquals("$core must be T0 core.", CatalogAttributeRole.T0_CORE, attributes.getValue(core).role)
        }
        listOf("mesh_node_count", "modem_type", "managed_switch", "lan_port_count", "wan_port_count", "antenna_gain_dbi", "antenna_type", "controller_ecosystem").forEach { typeCritical ->
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
    fun stage4_presentation_prioritizes_public_networking_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("networking_type" in profile.mainTypedFacetKeys)
        assertTrue("wifi_standard" in profile.mainTypedFacetKeys)
        assertTrue("ethernet_max_speed" in profile.mainTypedFacetKeys)
        assertTrue("poe_support" in profile.mainTypedFacetKeys)
        assertTrue("managed_switch" in profile.additionalTypedFacetKeys)
        assertTrue("modem_type" in profile.additionalTypedFacetKeys)
        assertTrue("antenna_type" in profile.additionalTypedFacetKeys)
        assertTrue("controller_ecosystem" in profile.additionalTypedFacetKeys)
        assertTrue("routing_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("live_value_candidate_state" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun head_seed_and_delta_cover_runtime_networking_heads() {
        val baseRows = readTsv("$BASE_PATH/networking_model_head_seed.tech_networking.v1_0.tsv")
        val deltaRows = readTsv("$DELTA_PATH/model_head_seed_delta.tech_networking.v1_1.tsv")

        assertTrue(baseRows.size >= 40)
        assertTrue(baseRows.any { it["brand"] == "TP_LINK" && it["model"] == "Archer AX55" })
        assertTrue(baseRows.any { it["brand"] == "UBIQUITI" && it["model"] == "U6 Pro" })
        assertTrue(baseRows.any { it["brand"] == "MIKROTIK" && it["model"] == "hAP ax3" })
        assertTrue(baseRows.any { it["brand"] == "KEENETIC" && it["model"] == "Keenetic Giga KN-1011" })

        assertTrue(deltaRows.size >= 16)
        assertTrue(deltaRows.any { it["brand"] == "UBIQUITI" && it["model"] == "LiteBeam 5AC" })
        assertTrue(deltaRows.any { it["brand"] == "HUAWEI" && it["model"] == "EchoLife HG8245H" })
        assertTrue(deltaRows.any { it["brand"] == "GL_INET" && it["model"] == "Flint 2 GL-MT6000" })
        assertTrue(deltaRows.any { it["brand"] == "RUIJIE_REYEE" && it["model"] == "Reyee RG-EW3200GX Pro" })
    }

    @Test
    fun route_guards_cover_networking_and_neighbor_boundaries() = runBlocking {
        val router = Stage21TechQueryRouter()

        listOf(
            "роутер tp link archer",
            "wifi роутер",
            "mesh wifi system",
            "точка доступа wifi",
            "poe switch 8 портов",
            "патч корд cat6 1 метр",
            "keystone rj45 cat6",
            "lan tester тестер витой пары",
            "LiteBeam 5AC Ubiquiti",
            "Huawei EchoLife HG8245H GPON ONT",
            "GL.iNet Flint 2 роутер",
            "Cudy WR3000 роутер",
            "Reyee RG EW3200GX Pro",
            "PoE инжектор 48V гигабитный",
        ).forEach { query ->
            assertEquals("$query must route to TECH.NETWORKING.", CATEGORY, router.route(query, "ru-RU").primaryTargetCode)
        }

        assertEquals("TECH.STORAGE_MEMORY", router.route("nas synology ds220j", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("usb hub type c 7 in 1", "ru-RU").primaryTargetCode)
        assertEquals("TECH.SMART_HOME_SECURITY", router.route("камера видеонаблюдения hikvision", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("hdmi кабель 2 метра", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("ИБП для роутера", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("gps трекер автомобильный", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTERS", router.route("компьютер lenovo thinkpad", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONES", router.route("iphone 15", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("сетевой фильтр 6 розеток", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("usb c кабель 100w", "ru-RU").primaryTargetCode)
        assertEquals("TECH.SMART_HOME_SECURITY", router.route("ip камера hikvision", "ru-RU").primaryTargetCode)
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
        private const val BASE_PACK_ID = "TECH_NETWORKING_public_category_pack_v1_0_RU"
        private const val DELTA_PACK_ID = "TECH_NETWORKING_type_coverage_delta_pack_v1_1_RU"
        private const val CATEGORY = "TECH.NETWORKING"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_networking/v1_0"
        private const val DELTA_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_networking/data_overlays/type_coverage_delta/v1_1"
    }
}
