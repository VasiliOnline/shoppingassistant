package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechComputerAccessoriesPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_accessory_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_computer_accessories/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.TYPE_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertEquals("user_surface.tech_computer_accessories.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("accessory_model_head_seed.tech_computer_accessories.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("accessory_model_aliases.tech_computer_accessories.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_computer_accessories.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun computer_accessories_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.KEYBOARDS",
            "TECH.MICE",
            "TECH.WEBCAMS",
            "TECH.DOCKS",
            "TECH.USB_HUBS",
            "TECH.MONITOR_ARMS",
            "TECH.MACBOOK_ACCESSORIES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_accessory_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val accessoryType = registry.attributes.getValue("computer_accessory_type")
        assertEquals(Stage22ValueType.ENUM, accessoryType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, accessoryType.valueSetType)
        assertTrue(accessoryType.isIdentity)
        assertTrue(accessoryType.isFacet)

        val accessoryTypes = registry.dictionaries.getValue("computer_accessory_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf(
                "KEYBOARD",
                "MOUSE",
                "KEYBOARD_MOUSE_SET",
                "WEBCAM",
                "DOCKING_STATION",
                "USB_HUB",
                "LAPTOP_STAND",
                "MONITOR_ARM",
                "MOUSE_PAD",
                "STYLUS_PEN",
                "ADAPTER_DONGLE",
                "COOLING_PAD",
                "COMPUTER_COVER",
                "SCREEN_FILTER",
                "CLEANING_KIT",
            ),
            accessoryTypes,
        )

        assertTrue(registry.dictionaries.getValue("connection_type").entries.any { it.valueCode == "BLUETOOTH" })
        assertTrue(registry.dictionaries.getValue("compatible_device_type").entries.any { it.valueCode == "MAC" })
        assertTrue(registry.dictionaries.getValue("connector_type").entries.any { it.valueCode == "THUNDERBOLT_4" })
        assertTrue(registry.dictionaries.getValue("keyboard_layout").entries.any { it.valueCode == "ANSI" })
        assertTrue(registry.dictionaries.getValue("mount_type").entries.any { it.valueCode == "VESA_ARM" })
        assertTrue(registry.dictionaries.getValue("cover_form_factor").entries.any { it.valueCode == "HARD_SHELL" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("power_delivery_watts").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("power_delivery_watts").valueSetType)
        assertEquals(Stage22ValueType.BOOLEAN, registry.attributes.getValue("vesa_supported").valueType)
    }

    @Test
    fun effective_spec_exposes_accessory_facets_and_hides_system_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "computer_accessory_type",
            "model_name_text",
            "connection_type",
            "connector_type",
            "compatible_device_type",
            "keyboard_layout",
            "power_delivery_watts",
            "mount_type",
            "cover_form_factor",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.COMPUTER_ACCESSORIES effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("computer_accessory_type", "connection_type", "connector_type", "compatible_device_type").forEach { facet ->
            assertTrue("$facet must be a TECH.COMPUTER_ACCESSORIES facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("computer_accessory_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("connection_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("connector_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("compatible_device_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("color_family").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("keyboard_layout").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("power_delivery_watts").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("mount_type").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("cover_form_factor").role)

        listOf("route_confidence", "image_fingerprint", "blocked_route_reason").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_accessory_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("computer_accessory_type" in profile.mainTypedFacetKeys)
        assertTrue("connection_type" in profile.mainTypedFacetKeys)
        assertTrue("connector_type" in profile.mainTypedFacetKeys)
        assertTrue("compatible_device_type" in profile.mainTypedFacetKeys)
        assertTrue("keyboard_layout" in profile.additionalTypedFacetKeys)
        assertTrue("power_delivery_watts" in profile.additionalTypedFacetKeys)
        assertTrue("mount_type" in profile.additionalTypedFacetKeys)
        assertTrue("cover_form_factor" in profile.additionalTypedFacetKeys)
        assertTrue("blocked_route_reason" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun accessory_model_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_computer_accessories/v1_0/accessory_model_head_seed.tech_computer_accessories.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.COMPUTER_ACCESSORIES model head seed should contain at least 60 rows.", rows.size >= 60)
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "KEYBOARD" && cells.getOrNull(1) == "LOGITECH" && cells.getOrNull(3) == "Logitech MX Keys" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "MOUSE" && cells.getOrNull(1) == "LOGITECH" && cells.getOrNull(3) == "Logitech MX Master 3S" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "DOCKING_STATION" && cells.getOrNull(1) == "DELL" && cells.getOrNull(3) == "Dell WD19" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "MONITOR_ARM" && cells.getOrNull(1) == "ERGOTRON" && cells.getOrNull(3) == "Ergotron LX" })
    }

    @Test
    fun route_guard_conflicts_keep_accessories_at_public_category_boundary() = runBlocking {
        val techRouter = Stage21TechQueryRouter()

        assertEquals(CATEGORY, techRouter.route("клавиатура logitech mx keys", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, techRouter.route("usb c hub hdmi ethernet", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, techRouter.route("док станция thunderbolt 4 macbook", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, techRouter.route("кронштейн для монитора vesa", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, techRouter.route("hard shell macbook air m2", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, techRouter.route("накладка на клавиатуру macbook", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, techRouter.route("privacy filter 14 laptop", "en-US").primaryTargetCode)
        assertEquals("TECH.COMPUTERS", techRouter.route("ноутбук lenovo thinkpad", "ru-RU").primaryTargetCode)
        assertEquals("TECH.MONITORS_DISPLAYS", techRouter.route("монитор samsung 27 144hz", "ru-RU").primaryTargetCode)
        assertEquals("TECH.STORAGE_MEMORY", techRouter.route("ssd samsung 1tb", "en-US").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", techRouter.route("оперативная память ddr4 16gb", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", techRouter.route("видеокарта rtx 4070", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONE_ACCESSORIES", techRouter.route("чехол iphone 13", "ru-RU").primaryTargetCode)
    }

    @Test
    fun runtime_router_keeps_laptop_bags_in_fashion_bags() = runBlocking {
        val runtimeRouter = Stage21RuntimeQueryRouter()

        assertEquals("FASH.BAGS", runtimeRouter.route("сумка для ноутбука", "ru-RU").primaryTargetCode)
        assertEquals("FASH.BAGS", runtimeRouter.route("laptop bag", "en-US").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_COMPUTER_ACCESSORIES_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.COMPUTER_ACCESSORIES"
    }
}
