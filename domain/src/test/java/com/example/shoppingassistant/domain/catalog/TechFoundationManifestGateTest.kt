package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechFoundationManifestGateTest {
    @Test
    fun foundation_manifest_is_registered_without_public_category_or_payload_mount() {
        val categoryCodes = CatalogSeed.categories.map { it.code }.toSet()
        assertFalse("TECH.FOUNDATION_MANIFEST must not be a public category.", "TECH.FOUNDATION_MANIFEST" in categoryCodes)
        forbiddenMicrocategories.forEach { categoryCode ->
            assertFalse("$categoryCode must not be mounted by foundation.", categoryCode in categoryCodes)
        }

        val manifest = CatalogPackV2RegistryLoader.packManifests.manifests
            .firstOrNull { it.packId == PACK_ID }
        assertNotNull(manifest)
        requireNotNull(manifest)
        assertEquals(null, manifest.categoryCode)
        assertTrue(manifest.categoryCodes.isEmpty())
        assertEquals(listOf("foundation_manifest"), manifest.packSections)
        assertEquals(null, manifest.userSurfaceFile)
        assertEquals(null, manifest.routeGuardLayer)
        assertEquals(20, manifest.requiredFiles.size)
        assertTrue("foundation_manifest.tech.v1_0.json" in manifest.requiredFiles)
        assertTrue("package_index.tech_foundation.v1_0.tsv" in manifest.requiredFiles)
        assertTrue("qa/self_check.tech_foundation_manifest.v1_0.json" in manifest.requiredFiles)
        assertTrue("docs/FOUNDATION_MANIFEST_POLICY_RU.md" in manifest.requiredFiles)
        assertTrue("docs/RUNTIME_MOUNT_CHECKLIST_RU.md" in manifest.requiredFiles)
        assertFalse(
            "Foundation manifest must not copy attributes/values/aliases payload files.",
            manifest.requiredFiles.any { fileName ->
                fileName.startsWith("attributes.") ||
                    fileName.startsWith("values.") ||
                    fileName.startsWith("aliases.")
            },
        )

        manifest.requiredFiles.forEach { fileName ->
            assertTrue(
                "Foundation manifest required file is missing: $fileName",
                CatalogSeedResourceReader.resourceExists("$BASE_PATH/$fileName"),
            )
        }

        val packManifest = readJsonObject("manifest.json")
        assertEquals(PACK_ID, packManifest.getValue("pack_id").jsonPrimitive.content)
        assertEquals("foundation_manifest_pack", packManifest.getValue("pack_type").jsonPrimitive.content)
        assertFalse(packManifest.getValue("public_category").jsonPrimitive.boolean)
        assertFalse(packManifest.getValue("accepts_offers_directly").jsonPrimitive.boolean)
    }

    @Test
    fun foundation_manifest_declares_strict_shared_mount_order() {
        val foundation = readJsonObject("foundation_manifest.tech.v1_0.json")
        val order = foundation.getValue("shared_foundation_order").jsonArray
        assertEquals(expectedSharedStandards, order.map { it.jsonObject.getValue("category_code").jsonPrimitive.content })
        assertEquals(expectedSourcePackIds, order.map { it.jsonObject.getValue("pack_id").jsonPrimitive.content })
        assertTrue(order.all { entry ->
            entry.jsonObject.getValue("artifact_sha256").jsonPrimitive.content.matches(Regex("[a-f0-9]{64}"))
        })

        val runtimeContract = foundation.getValue("runtime_contract").jsonObject
        assertTrue(runtimeContract.getValue("no_public_category_mounts_in_this_pack").jsonPrimitive.boolean)
        assertTrue(runtimeContract.getValue("no_offer_routing_target").jsonPrimitive.boolean)
        assertTrue(runtimeContract.getValue("no_attribute_data_duplication").jsonPrimitive.boolean)

        val expectedSnapshot = readJsonObject("effective_spec_snapshot.expected.tech_foundation.v1_0.json")
            .getValue("expected_registry_state_after_mount")
            .jsonObject
        assertEquals(
            expectedSharedStandards,
            expectedSnapshot.getValue("shared_standards_registered").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            expectedSharedStandards,
            expectedSnapshot.getValue("shared_dependency_order").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(expectedSnapshot.getValue("public_categories_mounted_by_manifest").jsonArray.isEmpty())
        assertTrue(expectedSnapshot.getValue("offer_routes_added_by_manifest").jsonArray.isEmpty())
        assertTrue(expectedSnapshot.getValue("forbidden_microcategories_mounted").jsonArray.isEmpty())
    }

    @Test
    fun foundation_manifest_matches_runtime_tech_shared_standard_state() {
        val assignments = CatalogPackV2RegistryLoader.archetypeAssignments.assignments.associateBy { it.categoryCode }
        listOf("TECH", "TECH.PHONES", "TECH.PHONE_ACCESSORIES", "TECH.COMPUTERS", "TECH.WEARABLES")
            .forEach { categoryCode ->
                assertEquals(expectedSharedStandards, assignments.getValue(categoryCode).sharedStandards)
            }

        val techPackage = GenericStage22PackageLoader.loadAll().first { it.descriptor.l0Code == "TECH" }
        assertEquals(expectedSharedStandards, techPackage.sharedProfiles.map { it.profileCode })

        val indexRows = readTsv("package_index.tech_foundation.v1_0.tsv")
        assertEquals(4, indexRows.size)
        assertEquals(expectedSharedStandards, indexRows.map { it.getValue("category_code") })
        assertEquals(expectedSourcePackIds, indexRows.map { it.getValue("pack_id") })
        assertTrue(indexRows.all { it.getValue("registry_target") == "shared_standard_registry" })
        assertTrue(indexRows.all { it.getValue("public_category") == "false" })
        assertTrue(indexRows.all { it.getValue("accepts_offers_directly") == "false" })

        val qualityGates = CatalogSeedResourceReader.readText("$BASE_PATH/quality_gates.tech_foundation.v1_0.yaml")
        assertTrue(qualityGates.contains("NoPublicCategoryMountGate"))
        assertTrue(qualityGates.contains("NoMicrocategoryGate"))
        assertTrue(qualityGates.contains("NoPayloadDuplicationGate"))
    }

    private fun readJsonObject(fileName: String) =
        CatalogSeedResourceReader.json
            .parseToJsonElement(CatalogSeedResourceReader.readText("$BASE_PATH/$fileName"))
            .jsonObject

    private fun readTsv(fileName: String): List<Map<String, String>> {
        val lines = CatalogSeedResourceReader.readText("$BASE_PATH/$fileName")
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        val header = lines.first().split('\t')
        return lines.drop(1).map { line ->
            header.zip(line.split('\t')).toMap()
        }
    }

    private companion object {
        private const val PACK_ID = "TECH_FOUNDATION_MANIFEST_v1_0_RU"
        private const val BASE_PATH =
            "taxonomy/stage2/2.2/TECH/foundation/manifest/TECH_FOUNDATION_MANIFEST_v1_0_RU"

        private val expectedSharedStandards = listOf(
            "TECH.ELECTRONICS_COMMON",
            "TECH.DEVICE_IDENTITY_COMMON",
            "TECH.SPECS_COMMON",
            "TECH.COMPATIBILITY_COMMON",
        )

        private val expectedSourcePackIds = listOf(
            "TECH_ELECTRONICS_COMMON_shared_standard_pack_v1_0_RU",
            "TECH_DEVICE_IDENTITY_COMMON_shared_standard_pack_v1_0_RU",
            "TECH_SPECS_COMMON_shared_standard_pack_v1_0_RU",
            "TECH_COMPATIBILITY_COMMON_shared_standard_pack_v1_0_RU",
        )

        private val forbiddenMicrocategories = setOf(
            "TECH.IPHONE",
            "TECH.SAMSUNG_PHONES",
            "TECH.CASES",
            "TECH.CHARGERS",
            "TECH.CABLES",
            "TECH.MACBOOK",
            "TECH.AIRPODS",
        )
    }
}
