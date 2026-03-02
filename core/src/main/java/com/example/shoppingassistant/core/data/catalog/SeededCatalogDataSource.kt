package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope

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

    override suspend fun listCategories(): List<Category> = profiles.values.map { it.category }

    override suspend fun listProfiles(): List<CategoryProfile> = profiles.values.toList()

    override suspend fun getProfile(code: String): CategoryProfile? = profiles[code]

    override suspend fun getAttributeValueDict(attributeCode: String): AttributeValueDict? {
        val normalized = attributeCode.trim()
        if (normalized.isBlank()) return null
        return profiles.values
            .asSequence()
            .flatMap { profile -> profile.valueDictionaries.asSequence() }
            .firstOrNull { dictionary ->
                dictionary.attributeCode.equals(normalized, ignoreCase = true)
            }
    }

    override suspend fun listConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        val normalizedCategoryCode = categoryCode.trim()
        if (normalizedCategoryCode.isBlank()) return emptyList()
        return constraints.filter { constraint ->
            when (constraint.scope) {
                ConstraintScope.GLOBAL -> true
                ConstraintScope.CATEGORY ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true
                ConstraintScope.BRAND ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand.orEmpty(), ignoreCase = true) == true
                ConstraintScope.MODEL ->
                    constraint.categoryCode?.equals(normalizedCategoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand.orEmpty(), ignoreCase = true) == true &&
                        constraint.model?.equals(model.orEmpty(), ignoreCase = true) == true
            }
        }
    }

    override suspend fun saveProfile(profile: CategoryProfile) {
        profiles[profile.category.code] = profile
    }
}
