package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope

/**
 * Реализация CatalogRepository для client/core.
 * Хранит профили в памяти (SeededCatalogDataSource), но реализует upsert для будущих расширений.
 */
class CatalogRepositoryImpl(
    private val dataSource: CatalogDataSource = SeededCatalogDataSource(),
) : CatalogRepository {

    override suspend fun listCategories(): List<Category> =
        dataSource.listProfiles().map { it.category }

    override suspend fun getCategoryProfile(categoryCode: String): CategoryProfile? =
        dataSource.getProfile(categoryCode)

    override suspend fun listAttributeValueDict(attributeCode: String): AttributeValueDict? =
        dataSource.listProfiles()
            .asSequence()
            .mapNotNull { profile -> profile.valueDictionaries.firstOrNull { it.attributeCode == attributeCode } }
            .firstOrNull()

    override suspend fun listConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        if (categoryCode.isBlank()) return emptyList()
        return dataSource.listConstraints()
            .filter { c ->
                when (c.scope) {
                    ConstraintScope.GLOBAL -> true
                    ConstraintScope.CATEGORY ->
                        c.categoryCode?.equals(categoryCode, ignoreCase = true) == true
                    ConstraintScope.BRAND ->
                        c.categoryCode?.equals(categoryCode, ignoreCase = true) == true &&
                            c.brand?.equals(brand ?: "", ignoreCase = true) == true
                    ConstraintScope.MODEL ->
                        c.categoryCode?.equals(categoryCode, ignoreCase = true) == true &&
                            c.brand?.equals(brand ?: "", ignoreCase = true) == true &&
                            c.model?.equals(model ?: "", ignoreCase = true) == true
                }
            }
    }

    override suspend fun upsertProfile(profile: CategoryProfile) {
        dataSource.saveProfile(profile)
    }
}
