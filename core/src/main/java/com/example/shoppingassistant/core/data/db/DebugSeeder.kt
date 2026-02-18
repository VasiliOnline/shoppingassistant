package com.example.shoppingassistant.core.data.db

import com.example.shoppingassistant.core.data.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DebugSeeder {
    suspend fun seedOnce(dao: ProductDao) = withContext(Dispatchers.IO) {
        // если уже есть — выходим
        val existing = dao.countByBrandModel("Apple", "iPhone 16 Pro")
        if (existing > 0) return@withContext

        val items = listOf(
            ProductEntity(
                id = 1L,
                title = "iPhone 16 Pro 256GB Blue",
                brand = "Apple",
                model = "iPhone 16 Pro",
                categoryCode = "TECH.PHONES",
                priceCents = 1_199_900L,
                deliveryDays = 2,
                sellerRating = 4.8,
                currency = "",
                sourceUrl = "",
                // ключи должны вычисляться из тех же значений, что и brand/model выше
                brandKey = Normalizer.key("Apple"),
                modelKey = Normalizer.key("iPhone 16 Pro"),
                imageUrls = null
            ),
            ProductEntity(
                id = 2L,
                title = "iPhone 16 Pro 1TB Natural Titanium",
                brand = "Apple",
                model = "iPhone 16 Pro",
                categoryCode = "TECH.PHONES",
                priceCents = 1_599_900L,
                deliveryDays = 3,
                sellerRating = 4.9,
                currency = "",
                sourceUrl = "",
                brandKey = Normalizer.key("Apple"),
                modelKey = Normalizer.key("iPhone 16 Pro"),
                imageUrls = null
            ),
            ProductEntity(
                id = 3L,
                title = "iPhone 16 128GB Black",
                brand = "Apple",
                model = "iPhone 16",
                categoryCode = "TECH.PHONES",
                priceCents = 1_299_900L,
                deliveryDays = 4,
                sellerRating = 4.6,
                currency = "",
                sourceUrl = "",
                brandKey = Normalizer.key("Apple"),
                // было ошибочно "iPhone 16 Pro" — исправлено на "iPhone 16"
                modelKey = Normalizer.key("iPhone 16"),
                imageUrls = null
            )
        )
        dao.insertProducts(items)

        val attrs = listOf(
            ProductAttributeEntity(0, 1L, "memory", "256 ГБ"),
            ProductAttributeEntity(0, 1L, "color", "Синий"),

            ProductAttributeEntity(0, 2L, "memory", "1 ТБ"),
            ProductAttributeEntity(0, 2L, "color", "Натуральный титан"),

            ProductAttributeEntity(0, 3L, "memory", "128 ГБ"),
            ProductAttributeEntity(0, 3L, "color", "Чёрный")
        )
        dao.insertAttributes(attrs)
    }
}
