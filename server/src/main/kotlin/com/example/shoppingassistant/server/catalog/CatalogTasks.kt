package com.example.shoppingassistant.server.catalog

/**
 * Манифест задач для серверного каталога категорий/атрибутов.
 */
enum class CatalogBackendTaskId {
    SCHEMA,
    SEED,
    REPOSITORY,
    FACET_SCHEMA,
}

data class CatalogBackendTask(
    val id: CatalogBackendTaskId,
    val path: String,
    val description: String,
)

val catalogBackendTasks = listOf(
    CatalogBackendTask(
        id = CatalogBackendTaskId.SCHEMA,
        path = "server/src/main/resources/db/migration/V4__catalog_taxonomy.sql",
        description = "Схема Postgres для categories/attribute_defs/category_attributes/attribute_value_dict.",
    ),
    CatalogBackendTask(
        id = CatalogBackendTaskId.SEED,
        path = "server/src/main/kotlin/com/example/shoppingassistant/server/catalog/CatalogSeeder.kt",
        description = "Сид каталога и write-side sync из domain CatalogSeed в Postgres.",
    ),
    CatalogBackendTask(
        id = CatalogBackendTaskId.REPOSITORY,
        path = "server/src/main/kotlin/com/example/shoppingassistant/server/catalog/CatalogRepositoryImpl.kt",
        description = "Exposed-репозиторий CatalogReadRepository + admin write-spec persistence.",
    ),
    CatalogBackendTask(
        id = CatalogBackendTaskId.FACET_SCHEMA,
        path = "server/src/main/kotlin/com/example/shoppingassistant/server/catalog/FacetSchemaRepositoryImpl.kt",
        description = "Stage 3.0: контракт facet definitions/presets/collections + DB seeding.",
    ),
)

