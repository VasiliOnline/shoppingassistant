package com.example.shoppingassistant.core.data

interface ProductRepository {
    suspend fun searchNormalized(
        category: String,
        filters: Map<String, String>
    ): List<ProductBrief>

    suspend fun offersCount(
        category: String,
        filters: Map<String, String>
    ): Int
}