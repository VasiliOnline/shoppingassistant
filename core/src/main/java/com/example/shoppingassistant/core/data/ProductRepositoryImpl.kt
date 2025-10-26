package com.example.shoppingassistant.core.data
import kotlinx.coroutines.delay

class ProductRepositoryImpl : ProductRepository {
    override suspend fun searchNormalized(
        category: String,
        filters: Map<String, String>,
    ): List<ProductBrief> {
        TODO("Not yet implemented")
    }

    override suspend fun offersCount(category: String, filters: Map<String, String>): Int {
        // имитируем сетевую задержку, чтобы увидеть обновление UI
        delay(250)
        // пока просто считаем фильтры для «живости» числа
        return (20..120).random()
    }
}
