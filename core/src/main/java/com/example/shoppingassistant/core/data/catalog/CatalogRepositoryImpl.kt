package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.CatalogRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints

/**
 * Реализация CatalogRepository для client/core.
 */
class CatalogRepositoryImpl(
    private val dataSource: CatalogDataSource = SeededCatalogDataSource(),
) : CatalogRepository {

    override suspend fun listCategories(): List<Category> =
        dataSource.listCategories()

    override suspend fun getCategoryProfile(categoryCode: String): CategoryProfile? =
        dataSource.getProfile(categoryCode)

    override suspend fun listAttributeValueDict(attributeCode: String): AttributeValueDict? =
        dataSource.getAttributeValueDict(attributeCode)

    override suspend fun listConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        return dataSource.listConstraints(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
    }

    override suspend fun upsertProfile(profile: CategoryProfile) {
        dataSource.saveProfile(profile)
    }
}
