package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyEntry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistryProvider
import com.example.shoppingassistant.domain.catalog.SeedCatalogCanonicalProductFamilyRegistryProvider
import kotlinx.coroutines.runBlocking

class GovernanceCatalogCanonicalProductFamilyRegistryProvider(
    private val projectionService: CatalogGovernanceServingProjectionService,
    private val fallbackProvider: CatalogCanonicalProductFamilyRegistryProvider =
        SeedCatalogCanonicalProductFamilyRegistryProvider,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : CatalogCanonicalProductFamilyRegistryProvider {

    @Volatile
    private var cachedFamilies: CachedFamilies? = null

    private val lock = Any()

    override fun families(): List<CatalogCanonicalProductFamilyEntry> {
        val snapshot = cachedFamilies
        val now = nowMs()
        if (snapshot != null && now - snapshot.loadedAtMs <= cacheTtlMs) {
            return snapshot.families
        }
        return refresh()
    }

    fun refresh(force: Boolean = false): List<CatalogCanonicalProductFamilyEntry> =
        synchronized(lock) {
            val snapshot = cachedFamilies
            val now = nowMs()
            if (!force && snapshot != null && now - snapshot.loadedAtMs <= cacheTtlMs) {
                return@synchronized snapshot.families
            }

            val families = runCatching {
                runBlocking { projectionService.buildServingArtifacts().productFamilies.families }
            }.getOrElse {
                fallbackProvider.families()
            }

            val effectiveFamilies = if (families.isNotEmpty()) families else fallbackProvider.families()
            cachedFamilies = CachedFamilies(
                loadedAtMs = now,
                families = effectiveFamilies,
            )
            effectiveFamilies
        }

    fun installIntoRuntime(): List<CatalogCanonicalProductFamilyEntry> {
        CatalogCanonicalProductFamilyRegistry.installProvider(this)
        return refresh(force = true)
    }

    private data class CachedFamilies(
        val loadedAtMs: Long,
        val families: List<CatalogCanonicalProductFamilyEntry>,
    )

    private companion object {
        private const val DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
