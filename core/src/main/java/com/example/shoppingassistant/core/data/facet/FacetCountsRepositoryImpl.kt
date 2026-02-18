package com.example.shoppingassistant.core.data.facet

import com.example.shoppingassistant.core.data.db.ProductDao
import com.example.shoppingassistant.domain.facet.FacetCountMode
import com.example.shoppingassistant.domain.facet.FacetCountsQuery
import com.example.shoppingassistant.domain.facet.FacetCountsRepository
import com.example.shoppingassistant.domain.facet.FacetValueCount
import java.util.Locale
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FacetCountsRepositoryImpl(
    private val dao: ProductDao,
) : FacetCountsRepository {

    private data class CacheEntry(
        val storedAtMillis: Long,
        val counts: Map<String, Int>, // ключ = displayValue
    )

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, CacheEntry>()
    private val inFlight = mutableMapOf<String, Deferred<Map<String, Int>>>()

    private val ttlMillis = 2 * 60 * 1000L
    private val maxCacheEntries = 200

    override suspend fun getFacetCounts(query: FacetCountsQuery): List<FacetValueCount> = coroutineScope {
        val targetKey = query.targetFacetKey.trim()
        if (targetKey.isBlank()) return@coroutineScope emptyList()

        val brand = query.brand?.trim()?.takeIf { it.isNotBlank() }
        val model = query.model?.trim()?.takeIf { it.isNotBlank() }
        val filters = normalizeFilters(query.selectedFilters)
        val cacheKey = buildCacheKey(query, brand, model, filters)
        val now = System.currentTimeMillis()

        mutex.withLock {
            cache[cacheKey]
                ?.takeIf { now - it.storedAtMillis <= ttlMillis }
                ?.let { entry ->
                    return@coroutineScope entry.counts.map { (value, count) ->
                        FacetValueCount(value = value, count = count)
                    }
                }
        }

        val existing = mutex.withLock { inFlight[cacheKey] }
        if (existing != null) {
            return@coroutineScope existing.await().map { (value, count) ->
                FacetValueCount(value = value, count = count)
            }
        }

        val deferred = async {
            computeCounts(
                query = query,
                brand = brand,
                model = model,
                filters = filters,
            )
        }

        mutex.withLock { inFlight[cacheKey] = deferred }

        try {
            val counts = deferred.await()
            mutex.withLock {
                cache[cacheKey] = CacheEntry(now, counts)
                inFlight.remove(cacheKey)
                trimCache(now)
            }
            counts.map { (value, count) -> FacetValueCount(value = value, count = count) }
        } catch (e: Throwable) {
            mutex.withLock { inFlight.remove(cacheKey) }
            throw e
        }
    }

    private suspend fun computeCounts(
        query: FacetCountsQuery,
        brand: String?,
        model: String?,
        filters: Map<String, List<String>>,
    ): Map<String, Int> {
        val facetKey = query.targetFacetKey.trim()
        if (facetKey.isBlank()) return emptyMap()

        // normValue -> displayValue (каноничное/как в БД/каталоге)
        val labelByNorm = linkedMapOf<String, String>()

        val filtersWithoutFacet = if (query.excludeTargetFacet) {
            filters.filterKeys { !it.equals(facetKey, ignoreCase = true) }
        } else {
            filters
        }

        val pairs = filtersWithoutFacet.flatMap { (key, values) ->
            values.map { value -> "${key.trim()}=${value.trim()}" }
        }
        val keysCount = filtersWithoutFacet.keys.size

        val baseCounts = if (keysCount == 0) {
            dao.facetCountsWithoutPairs(brand, model, facetKey)
        } else {
            dao.facetCountsWithPairs(brand, model, facetKey, pairs, keysCount)
        }

        val baseMapNorm = LinkedHashMap<String, Int>()
        baseCounts.forEach { row ->
            val raw = row.value.trim()
            val norm = normalizeValueKey(raw)
            if (!labelByNorm.containsKey(norm)) labelByNorm[norm] = raw
            baseMapNorm[norm] = (baseMapNorm[norm] ?: 0) + row.count
        }

        if (query.mode != FacetCountMode.ADD) {
            return toLabelMap(baseMapNorm, labelByNorm)
        }

        val selectedValues = query.selectedFilters.entries
            .firstOrNull { it.key.equals(facetKey, ignoreCase = true) }
            ?.value
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (selectedValues.isEmpty()) {
            return toLabelMap(baseMapNorm, labelByNorm)
        }

        // фиксируем лейблы и для выбранных значений (если в baseCounts их вдруг нет)
        selectedValues.forEach { raw ->
            val norm = normalizeValueKey(raw)
            if (!labelByNorm.containsKey(norm)) labelByNorm[norm] = raw
        }

        val selectedKeys = selectedValues.map(::normalizeValueKey).toSet()

        val selectedCount = if (keysCount == 0) {
            dao.facetSelectedCountWithoutPairs(brand, model, facetKey, selectedValues)
        } else {
            dao.facetSelectedCountWithPairs(brand, model, facetKey, pairs, keysCount, selectedValues)
        }

        val countsWithoutSelected = if (keysCount == 0) {
            dao.facetCountsWithoutSelectedValues(brand, model, facetKey, selectedValues)
        } else {
            dao.facetCountsWithPairsWithoutSelectedValues(brand, model, facetKey, pairs, keysCount, selectedValues)
        }

        val withoutMapNorm = LinkedHashMap<String, Int>()
        countsWithoutSelected.forEach { row ->
            val raw = row.value.trim()
            val norm = normalizeValueKey(raw)
            if (!labelByNorm.containsKey(norm)) labelByNorm[norm] = raw
            withoutMapNorm[norm] = (withoutMapNorm[norm] ?: 0) + row.count
        }

        val allKeys = LinkedHashSet<String>().apply {
            addAll(baseMapNorm.keys)
            addAll(withoutMapNorm.keys)
            addAll(selectedKeys)
        }

        val outNorm = LinkedHashMap<String, Int>()
        allKeys.forEach { normKey ->
            val extra = if (normKey in selectedKeys) 0 else withoutMapNorm[normKey].orZero()
            val count = if (normKey in selectedKeys) selectedCount else selectedCount + extra
            outNorm[normKey] = count
        }

        return toLabelMap(outNorm, labelByNorm)
    }

    private fun toLabelMap(
        normCounts: LinkedHashMap<String, Int>,
        labelByNorm: Map<String, String>,
    ): Map<String, Int> {
        val out = LinkedHashMap<String, Int>(normCounts.size)
        normCounts.forEach { (norm, count) ->
            out[labelByNorm[norm] ?: norm] = count
        }
        return out
    }

    private fun normalizeFilters(
        filters: Map<String, List<String>>,
    ): Map<String, List<String>> {
        if (filters.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, List<String>>()
        filters.forEach { (key, values) ->
            val cleanedKey = key.trim()
            if (cleanedKey.isBlank()) return@forEach
            val cleanedValues = values.mapNotNull { v ->
                v.trim().takeIf { it.isNotBlank() }
            }.distinct()
            if (cleanedValues.isNotEmpty()) {
                out[cleanedKey] = cleanedValues
            }
        }
        return out
    }

    private fun buildCacheKey(
        query: FacetCountsQuery,
        brand: String?,
        model: String?,
        filters: Map<String, List<String>>,
    ): String {
        val sb = StringBuilder()
        sb.append("cat=").append(query.categoryCode.trim().lowercase(Locale.ROOT))
        sb.append("|facet=").append(query.targetFacetKey.trim().lowercase(Locale.ROOT))
        sb.append("|mode=").append(query.mode.name)
        sb.append("|exclude=").append(query.excludeTargetFacet)
        sb.append("|brand=").append(brand?.lowercase(Locale.ROOT).orEmpty())
        sb.append("|model=").append(model?.lowercase(Locale.ROOT).orEmpty())

        filters.entries.sortedBy { it.key.lowercase(Locale.ROOT) }.forEach { (key, values) ->
            sb.append("|f=").append(key.trim().lowercase(Locale.ROOT)).append("=")
            sb.append(values.map(::normalizeValueKey).sorted().joinToString(","))
        }
        return sb.toString()
    }

    private fun normalizeValueKey(value: String): String =
        value.trim().lowercase(Locale.ROOT)

    private fun trimCache(nowMillis: Long) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value.storedAtMillis > ttlMillis) {
                iterator.remove()
            }
        }
        if (cache.size <= maxCacheEntries) return

        val overflow = cache.size - maxCacheEntries
        val dropIterator = cache.entries.iterator()
        repeat(overflow) {
            if (dropIterator.hasNext()) {
                dropIterator.next()
                dropIterator.remove()
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
