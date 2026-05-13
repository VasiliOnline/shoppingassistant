package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.Category

/**
 * Источник данных каталога категорий/атрибутов (локальный или удалённый).
 * Реализации лежат в отдельных *Impl.
 */
interface CatalogDataSource {
    suspend fun listCategories(): List<Category>
    suspend fun getEffectiveSpec(
        code: String,
        brand: String? = null,
        model: String? = null,
    ): CatalogCategoryEffectiveSpec?
}
