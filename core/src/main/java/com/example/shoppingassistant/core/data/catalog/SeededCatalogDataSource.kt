package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryWriteSpec
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope

/**
 * In-memory источник профилей категорий на основе доменного сида.
 * Подходит для клиентского слоя: не требует БД или сети.
 */
class SeededCatalogDataSource(
    seed: List<CatalogCategoryWriteSpec> = CatalogSeed.categoryWriteSpecs,
    seedConstraints: List<CatalogConstraints> = CatalogSeed.constraints,
) : CatalogDataSource {

    private val categorySpecs: Map<String, CatalogCategoryWriteSpec> = seed.associateBy { it.category.code }
    private val constraints: List<CatalogConstraints> = seedConstraints.toList()

    override suspend fun listCategories(): List<Category> = categorySpecs.values.map { it.category }

    override suspend fun getEffectiveSpec(
        code: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val spec = categorySpecs[code] ?: return null
        val constraints = scopedConstraints(
            categoryCode = code,
            brand = brand,
            model = model,
        )
        return spec.toCategoryEffectiveSpec(constraints = constraints)
    }

    private fun scopedConstraints(
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
}
