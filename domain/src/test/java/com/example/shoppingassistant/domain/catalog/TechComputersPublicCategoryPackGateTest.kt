package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechComputersPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_computer_artifacts() {
        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }

        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(CATEGORY, manifest.categoryCode)
        assertEquals(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_computers/v1_0",
            manifest.basePath,
        )
        assertTrue("category_schema_pack" in manifest.packSections)
        assertTrue("identity_pack" in manifest.packSections)
        assertTrue("route_guard_pack" in manifest.packSections)
        assertTrue(CategoryArchetype.IDENTITY_CRITICAL in manifest.archetypes)
        assertTrue(CategoryArchetype.SPEC_HEAVY in manifest.archetypes)
        assertEquals("user_surface.tech_computers.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(31, manifest.requiredFiles.size)
        assertTrue("computer_model_head_seed.tech_computers.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("computer_model_aliases.tech_computers.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_computers.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun computers_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.MACBOOK",
            "TECH.THINKPAD",
            "TECH.DELL_XPS",
            "TECH.GAMING_LAPTOPS",
            "TECH.LAPTOPS",
            "TECH.DESKTOPS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_computer_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val computerType = registry.attributes.getValue("computer_type")
        assertEquals(Stage22ValueType.ENUM, computerType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, computerType.valueSetType)
        assertTrue(computerType.isIdentity)
        assertTrue(computerType.isFacet)

        val computerTypeValues = registry.dictionaries.getValue("computer_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf(
                "LAPTOP",
                "ULTRABOOK",
                "GAMING_LAPTOP",
                "TWO_IN_ONE_LAPTOP",
                "CHROMEBOOK",
                "DESKTOP_PC",
                "MINI_PC",
                "ALL_IN_ONE",
                "WORKSTATION",
                "THIN_CLIENT",
            ),
            computerTypeValues,
        )

        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("ram_gb").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("ram_gb").valueSetType)
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("storage_capacity_gb").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("storage_capacity_gb").valueSetType)
        assertTrue(registry.attributes.containsKey("cpu_model_text"))
        assertTrue(registry.attributes.containsKey("screen_size_in"))
        assertTrue(registry.attributes.containsKey("desktop_form_factor"))
        assertTrue(registry.attributes.containsKey("specs_confidence"))
        assertTrue(registry.dictionaries.getValue("cpu_family").entries.any { it.valueCode == "INTEL_CORE_I7" })
        assertTrue(registry.dictionaries.getValue("gpu_type").entries.any { it.valueCode == "DEDICATED" })
    }

    @Test
    fun effective_spec_exposes_computer_facets_and_hides_system_fields() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "computer_type",
            "model_name_text",
            "ram_gb",
            "storage_capacity_gb",
            "cpu_model_text",
            "screen_size_in",
            "gpu_type",
            "desktop_form_factor",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.COMPUTERS effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("computer_type", "ram_gb", "storage_capacity_gb", "cpu_family", "screen_size_in").forEach { facet ->
            assertTrue("$facet must be a TECH.COMPUTERS facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("computer_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("model_name_text").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("ram_gb").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("storage_capacity_gb").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("cpu_model_text").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("gpu_type").role)

        listOf("specs_confidence", "route_confidence", "extraction_evidence").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_computer_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("computer_type" in profile.mainTypedFacetKeys)
        assertTrue("ram_gb" in profile.mainTypedFacetKeys)
        assertTrue("storage_capacity_gb" in profile.mainTypedFacetKeys)
        assertTrue("cpu_family" in profile.mainTypedFacetKeys)
        assertTrue("screen_size_in" in profile.additionalTypedFacetKeys)
        assertTrue("specs_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("source_trace_id" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun model_head_seed_contains_expected_runtime_heads() {
        val rows = CatalogSeedResourceReader.readText(
            "taxonomy/stage2/2.2/TECH/category_packs/tech_computers/v1_0/computer_model_head_seed.tech_computers.v1_0.tsv",
        ).lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .drop(1)
            .map { line -> line.split('\t') }
            .toList()

        assertTrue("TECH.COMPUTERS model head seed should contain at least 70 rows.", rows.size >= 70)
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "APPLE" && cells.getOrNull(1) == "MACBOOK" && cells.getOrNull(3) == "MacBook Air 13" })
        assertTrue(rows.any { cells -> cells.getOrNull(0) == "LENOVO" && cells.getOrNull(1) == "THINKPAD" && cells.getOrNull(3) == "ThinkPad X1 Carbon" })
    }

    @Test
    fun route_guard_conflicts_keep_computers_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("laptop lenovo thinkpad t14", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("macbook air 13 m2", "en-US").primaryTargetCode)
        assertEquals(CATEGORY, router.route("chromebook acer 14", "en-US").primaryTargetCode)
        assertEquals("TECH.MONITORS_DISPLAYS", router.route("монитор samsung 27 144hz", "ru-RU").primaryTargetCode)
        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("клавиатура logitech mx keys", "ru-RU").primaryTargetCode)
        assertEquals("TECH.STORAGE_MEMORY", router.route("ssd samsung 980 pro 1tb", "en-US").primaryTargetCode)
        assertEquals("TECH.PC_COMPONENTS", router.route("видеокарта rtx 4070", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONES", router.route("iphone 15 pro 256", "en-US").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_COMPUTERS_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.COMPUTERS"
    }
}
