package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryReplacementResolver
import com.example.shoppingassistant.domain.catalog.Category

/**
 * Реализация CatalogReadRepository для client/core.
 */
class CatalogRepositoryImpl(
    private val dataSource: CatalogDataSource = SeededCatalogDataSource(),
) : CatalogReadRepository, CatalogTaxonomyRepository {

    override suspend fun listCategories(): List<Category> =
        dataSource.listCategories()

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? = dataSource.getEffectiveSpec(
        code = categoryCode,
        brand = brand,
        model = model,
    )

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ) = CategoryReplacementResolver.resolve(
        requestedCode = categoryCode,
        categories = listCategories(),
        maxHops = maxHops,
    )
}

