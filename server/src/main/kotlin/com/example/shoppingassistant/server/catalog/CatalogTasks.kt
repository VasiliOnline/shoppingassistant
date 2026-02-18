package com.example.shoppingassistant.server.catalog

/**
 * Манифест задач для серверного каталога категорий/атрибутов.
 */
enum class CatalogBackendTaskId {
    SCHEMA,
    SEED,
    REPOSITORY,
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
        description = "Сид минимальных профилей категорий из domain CatalogSeed.",
    ),
    CatalogBackendTask(
        id = CatalogBackendTaskId.REPOSITORY,
        path = "server/src/main/kotlin/com/example/shoppingassistant/server/catalog/CatalogRepositoryImpl.kt",
        description = "Exposed-репозиторий CatalogRepository (list/lookup/upsert профилей).",
    ),
)
