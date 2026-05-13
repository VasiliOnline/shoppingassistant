package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage22RegistryLoaderTest {
    @Test
    fun loads_registry_snapshot_and_package_descriptors() {
        val snapshot = Stage22RegistryLoader.loadSnapshot()
        val descriptors = Stage22RegistryLoader.loadPackageDescriptors()

        assertTrue("Registry attributes must not be empty", snapshot.attributes.isNotEmpty())
        assertTrue("Registry dictionaries must not be empty", snapshot.dictionaries.isNotEmpty())
        assertTrue("Schema version must not be blank", snapshot.meta.schemaVersion.isNotBlank())
        assertTrue("Data version must not be blank", snapshot.meta.dataVersion.isNotBlank())
        assertTrue("Package descriptors must not be empty", descriptors.isNotEmpty())

        val tech = descriptors.firstOrNull { it.l0Code == "TECH" }
        assertEquals("taxonomy/stage2/2.2/TECH", tech?.basePath)
        assertEquals("profiles.tech.json", tech?.profilesFile)
        assertEquals("constraints.tech.json", tech?.constraintsFile)
    }

    @Test
    fun package_descriptors_match_catalog_l0_registry() {
        val descriptorCodes = Stage22RegistryLoader.loadPackageDescriptors()
            .map { it.l0Code }
            .toSet()

        assertEquals(
            "Stage 2.2 package descriptors drifted from CatalogL0Registry",
            CatalogL0Registry.requiredPackageCodes.toSet(),
            descriptorCodes,
        )
    }
}
