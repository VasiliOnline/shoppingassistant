package com.example.shoppingassistant.domain.catalog

object CatalogSchemaVersion {
    val current: String by lazy { CatalogContractPaths.schemaVersion }

    val minSupportedClient: String
        get() = current.let(::schemaMajorFloor)
}

private fun schemaMajorFloor(version: String): String {
    val parts = version.trim().split('.')
    require(parts.size == 3) { "Expected x.y.z semantic version, got '$version'" }
    return "${parts[0]}.0.0"
}
