package com.example.shoppingassistant.domain.catalog

object CatalogArtifactPaths {
    val runtimeCompatibilityPolicy: String
        get() = "${CatalogContractPaths.stage22RegistryBase}/runtime_compatibility_policy.json"

    val runtimeContractSchema: String
        get() = "${CatalogContractPaths.stage22RegistryBase}/catalog_runtime_contract.schema.json"

    val openApiDocument: String
        get() = "${CatalogContractPaths.stage22RegistryBase}/catalog_openapi.json"
}
