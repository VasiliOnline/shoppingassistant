package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryWriteSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryReplacementResolver
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import java.util.Locale

/**
 * Resource-backed catalog repository for offline eval and non-DB tooling.
 *
 * Uses the same taxonomy/resources as production seeding, but does not depend on
 * Postgres/Flyway bootstrap. This keeps AI eval deterministic and decoupled from
 * local database drift.
 */
class ResourceCatalogRepository : CatalogReadRepository, CatalogTaxonomyRepository {

    private val categories: List<Category> = CatalogSeed.categories
    private val categoriesByCode: Map<String, Category> = categories.associateBy { category ->
        normalizeCode(category.code)
    }
    private val writeSpecsByCode: Map<String, CatalogCategoryWriteSpec> =
        CatalogSeed.categoryWriteSpecs.associateBy { spec -> normalizeCode(spec.category.code) }

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val normalizedCode = normalizeCode(categoryCode)
        if (normalizedCode.isEmpty()) return null

        val writeSpec = writeSpecsByCode[normalizedCode] ?: return null
        val constraints = CatalogConstraintsSelection.select(
            constraints = CatalogSeed.constraints,
            categoryCode = normalizedCode,
            brand = brand,
            model = model,
        )
        return writeSpec.toCategoryEffectiveSpec(constraints = constraints)
    }

    override suspend fun listCategories(): List<Category> = categories

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ) = CategoryReplacementResolver.resolve(
        requestedCode = categoryCode,
        categoriesByCode = categoriesByCode,
        maxHops = maxHops,
    )

    private fun normalizeCode(code: String): String =
        code.trim().uppercase(Locale.ROOT)
}
