package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints

/**
 * In-memory источник профилей категорий на основе доменного сида.
 * Подходит для клиентского слоя: не требует БД или сети.
 */
class SeededCatalogDataSource(
    seed: List<CategoryProfile> = CatalogSeed.profiles,
    seedConstraints: List<CatalogConstraints> = CatalogSeed.constraints,
) : CatalogDataSource {

    private val profiles: MutableMap<String, CategoryProfile> = LinkedHashMap<String, CategoryProfile>().apply {
        seed.forEach { put(it.category.code, it) }
    }
    private val constraints: List<CatalogConstraints> = seedConstraints.toList()

    override suspend fun listProfiles(): List<CategoryProfile> = profiles.values.toList()

    override suspend fun getProfile(code: String): CategoryProfile? = profiles[code]

    override suspend fun listConstraints(): List<CatalogConstraints> = constraints

    override suspend fun saveProfile(profile: CategoryProfile) {
        profiles[profile.category.code] = profile
    }
}
