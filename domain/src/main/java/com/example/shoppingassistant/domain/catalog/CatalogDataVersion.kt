package com.example.shoppingassistant.domain.catalog

object CatalogDataVersion {
    val current: String by lazy {
        runCatching { Stage22RegistryLoader.loadSnapshot().meta.dataVersion }
            .map { value -> value.trim().ifEmpty { "unknown" } }
            .getOrDefault("unknown")
    }
}
