package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechPrintersScannersPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_printer_artifacts() {
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
        assertEquals("user_surface.tech_printers_scanners.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(29, manifest.requiredFiles.size)
        assertTrue("printer_model_head_seed.tech_printers_scanners.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("printer_model_aliases.tech_printers_scanners.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_printers_scanners.v1_0.yaml" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted printers/scanners package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun printers_pack_keeps_public_tree_coarse() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.PRINTERS",
            "TECH.SCANNERS",
            "TECH.INK",
            "TECH.TONER",
            "TECH.HP_PRINTERS",
            "TECH.OTHER_ELECTRONICS",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be created as a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_printer_attributes_dictionaries_and_consumable_heads() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val printerType = registry.attributes.getValue("printer_scanner_type")
        assertEquals(Stage22ValueType.ENUM, printerType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, printerType.valueSetType)
        assertTrue(printerType.isIdentity)
        assertTrue(printerType.isFacet)

        assertEquals(Stage22ValueType.STRING, registry.attributes.getValue("compatible_cartridge_code").valueType)
        assertEquals(Stage22ValueSetType.OPEN, registry.attributes.getValue("compatible_cartridge_code").valueSetType)
        assertEquals(Stage22ValueType.ENUM, registry.attributes.getValue("compatible_printer_brand").valueType)

        val printerTypes = registry.dictionaries.getValue("printer_scanner_type").entries.map { it.valueCode }.toSet()
        assertEquals(
            setOf(
                "PRINTER",
                "MULTIFUNCTION_PRINTER",
                "SCANNER",
                "LABEL_PRINTER",
                "INK_CARTRIDGE",
                "TONER_CARTRIDGE",
                "PRINTER_ACCESSORY",
            ),
            printerTypes,
        )

        val cartridgeTypes = registry.dictionaries.getValue("cartridge_type").entries.map { it.valueCode }.toSet()
        assertTrue(cartridgeTypes.containsAll(setOf("INK_CARTRIDGE", "TONER_CARTRIDGE", "DRUM_UNIT", "MAINTENANCE_BOX")))

        val cartridgeCodes = registry.dictionaries.getValue("compatible_cartridge_code").entries.map { it.valueCode }.toSet()
        assertTrue(cartridgeCodes.containsAll(setOf("HP_305XL", "HP_106A", "CANON_PG_445", "EPSON_103", "BROTHER_TN_2421")))

        val brands = registry.dictionaries.getValue("brand").entries.map { it.valueCode }.toSet()
        assertTrue(brands.containsAll(setOf("HP", "CANON", "EPSON", "BROTHER", "XEROX", "ZEBRA", "DYMO")))
    }

    @Test
    fun effective_spec_exposes_printer_facets_and_runtime_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "printer_scanner_type",
            "brand",
            "model_name_text",
            "condition",
            "print_technology",
            "color_printing",
            "connectivity",
            "scanner_type",
            "cartridge_type",
            "compatible_cartridge_code",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.PRINTERS_SCANNERS effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("printer_scanner_type", "brand", "condition", "print_technology", "color_printing", "connectivity").forEach { facet ->
            assertTrue("$facet must be a TECH.PRINTERS_SCANNERS facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        listOf("printer_scanner_type", "brand", "condition", "model_name_text").forEach { core ->
            assertEquals("$core must be T0 core.", CatalogAttributeRole.T0_CORE, attributes.getValue(core).role)
        }
        listOf("print_technology", "scanner_type", "cartridge_type", "compatible_cartridge_code", "compatible_printer_brand").forEach { typeCritical ->
            assertEquals("$typeCritical must be T1 type critical.", CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue(typeCritical).role)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_printer_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("printer_scanner_type" in profile.mainTypedFacetKeys)
        assertTrue("print_technology" in profile.mainTypedFacetKeys)
        assertTrue("color_printing" in profile.mainTypedFacetKeys)
        assertTrue("connectivity" in profile.mainTypedFacetKeys)
        assertTrue("scanner_type" in profile.additionalTypedFacetKeys)
        assertTrue("cartridge_type" in profile.additionalTypedFacetKeys)
        assertTrue("compatible_cartridge_code" in profile.additionalTypedFacetKeys)
        assertTrue("compatibility_confidence" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun route_guards_cover_printers_and_neighbor_boundaries() = runBlocking {
        val router = Stage21TechQueryRouter()

        listOf(
            "лазерный принтер hp",
            "мфу canon pixma g3411",
            "сканер документов",
            "принтер этикеток zebra",
            "картридж hp 305",
            "тонер hp 106a",
            "чернила epson 103",
            "кабель принтера usb",
        ).forEach { query ->
            val debug = router.routeWithCandidates(query, "ru-RU")
            assertEquals("$query must route to TECH.PRINTERS_SCANNERS. top=${debug.topCandidates}", CATEGORY, debug.result.primaryTargetCode)
        }

        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("obd2 scanner", "ru-RU").primaryTargetCode)
        assertEquals("TECH.POWER_CHARGING_CABLES", router.route("usb c кабель 100w", "ru-RU").primaryTargetCode)
        assertEquals("B.TECH", router.route("3d printer", "ru-RU").primaryTargetCode)
    }

    private companion object {
        private const val PACK_ID = "TECH_PRINTERS_SCANNERS_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.PRINTERS_SCANNERS"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_printers_scanners/v1_0"
    }
}
