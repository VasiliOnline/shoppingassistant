package com.example.shoppingassistant.core.data

import com.example.shoppingassistant.core.data.db.ProductDao
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.toRawStringAttributes
import com.example.shoppingassistant.core.rank.RankService

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val rankService: RankService
) : ProductRepository {

    // Преобразуем Map атрибутов в "key=value" для SQL IN()
    override suspend fun offersCount(q: NormalizedQuery): Int {
        val pairs = q.attributes.toRawStringAttributes().entries.map { (k, v) -> "${k.trim()}=${v.trim()}" }
        return if (pairs.isEmpty()) {
            dao.countByBrandModel(q.brand, q.model)
        } else {
            dao.countByBrandModelWithPairs(q.brand, q.model, pairs, pairs.size)
        }
    }


    override suspend fun searchTop(q: NormalizedQuery, limit: Int): List<ProductDto> {
        val pairs = q.attributes.toRawStringAttributes().entries.map { (k, v) -> "${k.trim()}=${v.trim()}" }

        val entities =
            if (pairs.isEmpty()) {
                dao.searchTopOffersSimple(q.brand, q.model, limit)
            } else {
                dao.searchTopOffersWithPairs(q.brand, q.model, pairs, pairs.size, limit)
            }

        val dtos = entities.map { k -> k.toDto()}   // маппер добавлен в шаге 2

        // средняя цена по кандидатам: у ProductDto есть price (Double?)
        val prices = dtos.mapNotNull { it.price }
        val avgPrice = if (prices.isNotEmpty()) prices.average() else 0.0

        // было: ручной score + sort + take
// стало: единая точка ранжирования через RankService
        return rankService
            .topN(dtos, q, n = limit)
            .map { (dto, _) -> dto }

    }

}
