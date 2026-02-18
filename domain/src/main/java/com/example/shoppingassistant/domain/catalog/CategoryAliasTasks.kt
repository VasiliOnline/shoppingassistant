package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CategoryAlias(
    val alias: String,
    val categoryCode: String,
)

/**
 * Алиасы/синонимы категорий для более дружелюбного поиска категорий по тексту.
 */
interface CategoryAliasRepository {
    suspend fun listAliases(): List<CategoryAlias>
}

