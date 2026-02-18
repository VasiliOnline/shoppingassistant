package com.example.shoppingassistant.core.data

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.ProductDto

interface ProductRepository {
    suspend fun offersCount(q: NormalizedQuery): Int
    suspend fun searchTop(q: NormalizedQuery, limit: Int): List<ProductDto>
}

