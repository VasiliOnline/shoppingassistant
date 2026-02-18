package com.example.shoppingassistant.core.data.suggest

import com.example.shoppingassistant.core.data.db.ProductDao

class ProductSuggestRepositoryImpl(
    private val dao: ProductDao,
) : ProductSuggestRepository {

    override suspend fun search(query: String, limit: Int): List<ProductSuggestCandidate> {
        val normalized = normalizeSpaces(query)
        if (normalized.isBlank()) return emptyList()

        // Не показываем “мусорные” подсказки на 1 символ (пример: "e")
        if (normalized.length < 2) return emptyList()

        val safeLimit = limit.coerceIn(1, 20)
        val fetchLimit = (safeLimit * 4).coerceAtMost(20)

        val raw = dao.searchProductSuggestions(normalized, fetchLimit)
        if (raw.isEmpty()) return emptyList()

        // Дедуп по (brand+model) либо по title, чтобы не было повторов вроде "Apple iPhone 16" x2
        val distinct = raw.distinctBy { dedupeKey(it) }
        return distinct.take(safeLimit)
    }

    private fun normalizeSpaces(s: String): String =
        s.replace("\\s+".toRegex(), " ").trim()

    private fun dedupeKey(c: ProductSuggestCandidate): String {
        val brand = c.brand?.let(::normalizeSpaces)?.lowercase()
        val model = c.model?.let(::normalizeSpaces)?.lowercase()
        return if (!brand.isNullOrBlank() && !model.isNullOrBlank()) {
            "bm:$brand|$model"
        } else {
            "t:${normalizeSpaces(c.title).lowercase()}"
        }
    }
}
