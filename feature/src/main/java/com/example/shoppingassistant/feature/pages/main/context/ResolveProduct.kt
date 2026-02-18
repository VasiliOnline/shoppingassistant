// Last synced: 2025-12-16 18:07:36
package com.example.shoppingassistant.feature.pages.main.context

import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.feature.pages.model.Product

/**
 * Парсинг из текста (то, что в поле ввода/подсказке) → Product(brand, model).
 *
 * ВАЖНО:
 * - Никаких мини-словарей в feature.
 * - Используем реальную нормализацию из /core (BrandModelRules),
 *   чтобы UI работал с реальными данными (Room/репозитории) без "времянок".
 */
fun resolveProduct(text: String?): Product? {
    if (text.isNullOrBlank()) return null

    val q = BrandModelRules.fromRaw(text.trim())
    val brand = q.brand.trim()
    val model = q.model.trim()

    if (brand.isBlank() || model.isBlank()) return null

    // Product уже использовался в проекте с именованными параметрами brand/model
    // (значит остальные поля имеют дефолты).
    return Product(
        brand = brand,
        model = model,
    )
}
