package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPcComponentsPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_component_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_pc_components/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("compatibility_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertTrue(CategoryArchetype.COMPATIBILITY_DRIVEN in manifest.archetypes)
        assertEquals("user_surface.tech_pc_components.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(30, manifest.requiredFiles.size)
        assertTrue("component_head_seed.tech_pc_components.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("pc_component_compatibility_matrix.tech_pc_components.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_pc_components.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun pc_components_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.CPU",
            "TECH.GPU",
            "TECH.MOTHERBOARDS",
            "TECH.RAM",
            "TECH.NVIDIA_RTX",
            "TECH.RYZEN",
            "TECH.INTEL_CORE",
            "TECH.SSD",
            "TECH.CABLES",
            "TECH.CHARGERS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_component_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val componentType = registry.attributes.getValue("pc_component_type")
        assertEquals(Stage22ValueType.ENUM, componentType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, componentType.valueSetType)
        assertTrue(componentType.isIdentity)
        assertTrue(componentType.isFacet)

        val componentTypes = registry.dictionaries.getValue("pc_component_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf(
                "CPU",
                "GPU",
                "MOTHERBOARD",
                "RAM_MODULE",
                "POWER_SUPPLY",
                "PC_CASE",
                "CPU_COOLER",
                "CASE_FAN",
                "THERMAL_PASTE",
                "CAPTURE_CARD",
                "SOUND_CARD",
                "NETWORK_CARD",
                "EXPANSION_CARD",
            ),
            componentTypes,
        )

        assertTrue(registry.dictionaries.getValue("cpu_socket").entries.any { it.valueCode == "AMD_AM5" })
        assertTrue(registry.dictionaries.getValue("gpu_series").entries.any { it.valueCode == "NVIDIA_RTX_50" })
        assertTrue(registry.dictionaries.getValue("power_connector").entries.any { it.valueCode == "12VHPWR" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "NVIDIA" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("vram_gb").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("vram_gb").valueSetType)
        assertEquals(Stage22ValueType.BOOLEAN, registry.attributes.getValue("rgb_lighting").valueType)
    }

    @Test
    fun effective_spec_exposes_component_facets_and_hides_system_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "pc_component_type",
            "brand",
            "model_name_text",
            "cpu_socket",
            "gpu_series",
            "ram_type",
            "psu_wattage_w",
            "case_size",
            "thermal_conductivity_w_mk",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.PC_COMPONENTS effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("pc_component_type", "cpu_socket", "gpu_series", "ram_type", "psu_wattage_w").forEach { facet ->
            assertTrue("$facet must be a TECH.PC_COMPONENTS facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("pc_component_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("model_name_text").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("cpu_socket").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("gpu_series").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("psu_wattage_w").role)

        listOf("source_offer_id", "source_url", "ingestion_timestamp", "normalization_trace_id", "risk_flags", "moderation_status").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_component_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("pc_component_type" in profile.mainTypedFacetKeys)
        assertTrue("cpu_socket" in profile.mainTypedFacetKeys)
        assertTrue("gpu_series" in profile.mainTypedFacetKeys)
        assertTrue("ram_type" in profile.mainTypedFacetKeys)
        assertTrue("psu_wattage_w" in profile.additionalTypedFacetKeys)
        assertTrue("vram_gb" in profile.additionalTypedFacetKeys)
        assertTrue("source_offer_id" in profile.hiddenTypedFacetKeys)
        assertTrue("normalization_trace_id" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun component_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_pc_components/v1_0/component_head_seed.tech_pc_components.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.PC_COMPONENTS head seed should contain at least 70 rows.", rows.size >= 70)
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "CPU" && cells.getOrNull(2) == "AMD" && cells.getOrNull(3) == "Ryzen 9 9950X3D" })
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "GPU" && cells.getOrNull(2) == "NVIDIA" && cells.getOrNull(3) == "GeForce RTX 5090" })
        assertTrue(rows.any { cells -> cells.getOrNull(1) == "MOTHERBOARD" && cells.getOrNull(2) == "ASUS" && cells.getOrNull(3).orEmpty().contains("B650") })
    }

    @Test
    fun route_guard_conflicts_keep_pc_components_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("процессор ryzen 7 9800x3d", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("rtx 5090", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("термопаста arctic mx 6", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("сетевая карта pcie wifi", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("корпус для пк fractal", "ru-RU").primaryTargetCode)
        assertEquals("TECH.STORAGE_MEMORY", router.route("ssd samsung 980 pro 1tb", "en-US").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("клавиатура logitech mx keys", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTERS", router.route("ноутбук lenovo thinkpad", "ru-RU").primaryTargetCode)
        assertEquals("TECH.MONITORS_DISPLAYS", router.route("монитор samsung 27 144hz", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("hdmi кабель 2 метра", "ru-RU").primaryTargetCode)
        assertEquals("TECH.AUDIO", router.route("наушники sony", "ru-RU").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_PC_COMPONENTS_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.PC_COMPONENTS"
    }
}
