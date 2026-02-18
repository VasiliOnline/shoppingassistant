package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.data.db.CategoryOfferCountsDao
import com.example.shoppingassistant.domain.catalog.CategoryOfferCountsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CategoryOfferCountsRepositoryImpl(
    private val dao: CategoryOfferCountsDao,
) : CategoryOfferCountsRepository {

    private data class CacheEntry(
        val storedAtMillis: Long,
        val count: Int,
    )

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, CacheEntry>()
    private val ttlMillis = 2 * 60 * 1000L

    override suspend fun getCounts(categoryCodes: List<String>): Map<String, Int> {
        val codes = categoryCodes.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (codes.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val cached = LinkedHashMap<String, Int>()
        val missing = ArrayList<String>()

        mutex.withLock {
            codes.forEach { code ->
                val entry = cache[code]
                if (entry != null && now - entry.storedAtMillis <= ttlMillis) {
                    cached[code] = entry.count
                } else {
                    missing.add(code)
                }
            }
        }

        if (missing.isNotEmpty()) {
            val fresh = dao.getCounts(missing).associate { it.categoryCode to it.offersCount }
            mutex.withLock {
                fresh.forEach { (code, count) ->
                    cache[code] = CacheEntry(now, count)
                }
                missing.filterNot { fresh.containsKey(it) }.forEach { code ->
                    cache[code] = CacheEntry(now, 0)
                }
            }
            return codes.associateWith { code ->
                fresh[code] ?: cached[code] ?: 0
            }
        }

        return codes.associateWith { code -> cached[code] ?: 0 }
    }
}
