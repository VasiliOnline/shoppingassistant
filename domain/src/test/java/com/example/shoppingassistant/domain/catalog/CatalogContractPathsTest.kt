package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogContractPathsTest {
    @Test
    fun contract_paths_resource_is_valid_and_resolvable() {
        assertTrue(CatalogSeedResourceReader.resourceExists(CatalogContractPaths.RESOURCE_PATH))
        assertTrue(CatalogSeedResourceReader.resourceExists("${CatalogContractPaths.stage20Base}/categories.json"))
        Stage21ContractCatalog.requiredResourcePaths().forEach { path ->
            assertTrue("Missing Stage 2.1 resource: $path", CatalogSeedResourceReader.resourceExists(path))
        }
        assertTrue(CatalogSeedResourceReader.resourceExists("${CatalogContractPaths.stage22RegistryBase}/registry_meta.json"))
        assertTrue(
            CatalogSeedResourceReader.resourceExists(
                CatalogArtifactPaths.runtimeCompatibilityPolicy,
            ),
        )
        assertTrue(
            CatalogSeedResourceReader.resourceExists(
                CatalogArtifactPaths.runtimeContractSchema,
            ),
        )
        assertTrue(CatalogSeedResourceReader.resourceExists(CatalogArtifactPaths.openApiDocument))
        assertTrue(CatalogSeedResourceReader.resourceExists("${CatalogContractPaths.stage30Base}/facet_definitions.json"))
        assertTrue(CatalogSeedResourceReader.resourceExists("${CatalogContractPaths.stage40Base}/immutable_attribute_schema.json"))
    }

    @Test
    fun stage22_default_package_base_uses_contract_base_path() {
        val defaultBase = CatalogContractPaths.stage22DefaultPackageBase("tech")
        assertEquals("${CatalogContractPaths.stage22Base}/TECH", defaultBase)
    }
}
