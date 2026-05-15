package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechCamerasDronesPublicCategoryPackGateTest {

    @Test
    fun public_category_pack_is_registered_with_camera_artifacts() {
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
        assertEquals("user_surface.tech_cameras_drones.v1_0.yaml", manifest.userSurfaceFile)
        assertEquals("TECH_ROUTE_GUARDRAILS_COMMON", manifest.routeGuardLayer)
        assertEquals(30, manifest.requiredFiles.size)
        assertTrue("camera_model_head_seed.tech_cameras_drones.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("camera_model_aliases.tech_cameras_drones.ru.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("route_guardrails.tech_cameras_drones.v1_0.yaml" in manifest.requiredFiles)
        assertTrue("checksums.sha256.json" in manifest.requiredFiles)

        manifest.requiredFiles.forEach { file ->
            assertTrue(
                "Missing mounted package file: $file",
                CatalogSeedResourceReader.resourceExists("${manifest.basePath}/$file"),
            )
        }
    }

    @Test
    fun cameras_pack_does_not_create_forbidden_microcategories() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()

        assertTrue(CATEGORY in categoryCodes)
        listOf(
            "TECH.CANON_CAMERAS",
            "TECH.SONY_ALPHA",
            "TECH.DJI_DRONES",
            "TECH.GOPRO",
            "TECH.LENSES",
            "TECH.TRIPODS",
            "TECH.CAMERA_ACCESSORIES",
        ).forEach { forbidden ->
            assertFalse("$forbidden must not be a public category.", forbidden in categoryCodes)
        }
    }

    @Test
    fun registry_adds_camera_core_attributes_and_dictionaries() {
        val registry = Stage22RegistryLoader.loadSnapshot()

        val cameraType = registry.attributes.getValue("camera_type")
        assertEquals(Stage22ValueType.ENUM, cameraType.valueType)
        assertEquals(Stage22ValueSetType.CLOSED, cameraType.valueSetType)
        assertTrue(cameraType.isIdentity)
        assertTrue(cameraType.isFacet)

        val cameraTypes = registry.dictionaries.getValue("camera_type").entries.map { it.valueCode }.toSet()
        assertTrue(
            cameraTypes.containsAll(
                setOf(
                    "DIGITAL_CAMERA",
                    "MIRRORLESS_CAMERA",
                    "DSLR_CAMERA",
                    "ACTION_CAMERA",
                    "INSTANT_CAMERA",
                    "CAMCORDER",
                    "DRONE",
                    "LENS",
                    "TRIPOD",
                    "GIMBAL",
                    "PHOTO_LIGHTING",
                    "BINOCULARS_TELESCOPE",
                    "CAMERA_ACCESSORY",
                ),
            ),
        )

        assertTrue(registry.dictionaries.getValue("sensor_format").entries.any { it.valueCode == "FULL_FRAME" })
        assertTrue(registry.dictionaries.getValue("sensor_format").entries.any { it.valueCode == "APS_C" })
        assertTrue(registry.dictionaries.getValue("lens_mount").entries.any { it.valueCode == "CANON_RF" })
        assertTrue(registry.dictionaries.getValue("lens_mount").entries.any { it.valueCode == "SONY_E" })
        assertTrue(registry.dictionaries.getValue("lens_mount").entries.any { it.valueCode == "NIKON_Z" })
        assertTrue(registry.dictionaries.getValue("video_resolution_max").entries.any { it.valueCode == "4K" })
        assertTrue(registry.dictionaries.getValue("video_resolution_max").entries.any { it.valueCode == "8K" })
        assertTrue(registry.dictionaries.getValue("accessory_type").entries.any { it.valueCode == "FILTER" })
        assertTrue(registry.dictionaries.getValue("accessory_type").entries.any { it.valueCode == "BATTERY" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "CANON" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "SONY" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "DJI" })
        assertTrue(registry.dictionaries.getValue("brand").entries.any { it.valueCode == "GOPRO" })
        assertEquals(Stage22ValueType.NUMBER, registry.attributes.getValue("drone_weight_g").valueType)
        assertEquals(Stage22ValueType.BOOLEAN, registry.attributes.getValue("remote_controller_included").valueType)
    }

    @Test
    fun effective_spec_exposes_camera_facets_and_roles() {
        val stage22Spec = Stage22EffectiveSpecEngine.fromSeed(registry = Stage22RegistryLoader.loadSnapshot())
            .getEffectiveSpec(CATEGORY)

        assertFalse(stage22Spec.meta.isFallback)
        listOf(
            "camera_type",
            "brand",
            "model_name_text",
            "sensor_format",
            "lens_mount",
            "video_resolution_max",
            "drone_weight_g",
            "drone_flight_time_min",
            "obstacle_avoidance",
            "gimbal_axis_count",
            "tripod_head_type",
            "evidence_level",
            "vision_confidence",
            "route_confidence",
        ).forEach { attribute ->
            assertTrue("$attribute must be in TECH.CAMERAS_DRONES effective spec.", stage22Spec.attributes.any { it.attributeCode == attribute })
        }
        listOf("camera_type", "brand", "condition", "sensor_format", "lens_mount", "video_resolution_max").forEach { facet ->
            assertTrue("$facet must be a TECH.CAMERAS_DRONES facet.", facet in stage22Spec.facetAttributes)
        }

        val runtimeSpec = CatalogSeed.categoryWriteSpecs
            .first { it.category.code == CATEGORY }
            .toCategoryEffectiveSpec()
        val attributes = runtimeSpec.allAttributes().associateBy { it.code }

        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("camera_type").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("brand").role)
        assertEquals(CatalogAttributeRole.T0_CORE, attributes.getValue("condition").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("sensor_format").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("lens_mount").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("video_resolution_max").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("drone_weight_g").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("drone_flight_time_min").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("obstacle_avoidance").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("gimbal_axis_count").role)
        assertEquals(CatalogAttributeRole.T1_TYPE_CRITICAL, attributes.getValue("tripod_head_type").role)

        listOf("evidence_level", "vision_confidence", "route_confidence", "normalized_title").forEach { hidden ->
            val attribute = attributes.getValue(hidden)
            assertEquals(CatalogAttributeRole.T3_SYSTEM_HIDDEN, attribute.role)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED in attribute.usageScopes)
            assertFalse(CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL in attribute.usageScopes)
            assertTrue(CatalogAttributeUsageScope.AI_EXTRACTION_ONLY in attribute.usageScopes)
        }
    }

    @Test
    fun stage4_presentation_prioritizes_public_camera_facets() {
        val profile = CatalogFacetPresentationProfiles.resolve(CATEGORY)

        assertNotNull(profile)
        requireNotNull(profile)
        assertTrue("camera_type" in profile.mainTypedFacetKeys)
        assertTrue("sensor_format" in profile.mainTypedFacetKeys)
        assertTrue("lens_mount" in profile.mainTypedFacetKeys)
        assertTrue("video_resolution_max" in profile.mainTypedFacetKeys)
        assertTrue("drone_weight_g" in profile.additionalTypedFacetKeys)
        assertTrue("drone_flight_time_min" in profile.additionalTypedFacetKeys)
        assertTrue("obstacle_avoidance" in profile.additionalTypedFacetKeys)
        assertTrue("gimbal_axis_count" in profile.additionalTypedFacetKeys)
        assertTrue("tripod_head_type" in profile.additionalTypedFacetKeys)
        assertTrue("evidence_level" in profile.hiddenTypedFacetKeys)
        assertTrue("vision_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("route_confidence" in profile.hiddenTypedFacetKeys)
        assertTrue("normalized_title" in profile.hiddenTypedFacetKeys)
    }

    @Test
    fun camera_head_seed_contains_expected_runtime_heads() {
        val rows = readTsv("$BASE_PATH/camera_model_head_seed.tech_cameras_drones.v1_0.tsv")

        assertTrue("TECH.CAMERAS_DRONES model head seed should contain at least 60 rows.", rows.size >= 60)
        assertTrue(rows.any { it["brand"] == "CANON" && it["model"] == "EOS R5 Mark II" })
        assertTrue(rows.any { it["brand"] == "SONY" && it["model"] == "Alpha A7 IV" })
        assertTrue(rows.any { it["brand"] == "NIKON" && it["model"] == "Z8" })
        assertTrue(rows.any { it["brand"] == "DJI" && it["model"] == "Mini 4 Pro" })
        assertTrue(rows.any { it["brand"] == "GOPRO" && it["model"] == "HERO13 Black" })
    }

    @Test
    fun route_guard_conflicts_keep_cameras_at_public_category_boundary() = runBlocking {
        val router = Stage21TechQueryRouter()

        assertEquals(CATEGORY, router.route("canon eos r6 mark ii", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("sony a7 iv body", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("dji mini 4 pro fly more", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("canon rf 50mm 1.8", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("штатив manfrotto befree", "ru-RU").primaryTargetCode)
        assertEquals(CATEGORY, router.route("gopro hero 12", "ru-RU").primaryTargetCode)

        assertEquals("TECH.COMPUTER_ACCESSORIES", router.route("webcam logitech", "ru-RU").primaryTargetCode)
        assertEquals("TECH.SMART_HOME_SECURITY", router.route("камера видеонаблюдения wifi", "ru-RU").primaryTargetCode)
        assertEquals("TECH.CAR_ELECTRONICS_GPS", router.route("видеорегистратор 4k", "ru-RU").primaryTargetCode)
        assertEquals("TECH.PHONES", router.route("iphone 15 камера", "ru-RU").primaryTargetCode)
        assertEquals("TECH.GAMING", router.route("ps5 camera", "en-US").primaryTargetCode)
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
        private const val PACK_ID = "TECH_CAMERAS_DRONES_public_category_pack_v1_0_RU"
        private const val CATEGORY = "TECH.CAMERAS_DRONES"
        private const val BASE_PATH = "taxonomy/stage2/2.2/TECH/category_packs/tech_cameras_drones/v1_0"
    }
}
