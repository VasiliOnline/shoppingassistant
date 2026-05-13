package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelEntry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistryProvider
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus
import com.example.shoppingassistant.domain.catalog.SeedCatalogCanonicalModelRegistryProvider
import kotlinx.coroutines.runBlocking

class GovernanceCatalogCanonicalModelRegistryProvider(
    private val repository: com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository,
    private val fallbackProvider: CatalogCanonicalModelRegistryProvider =
        SeedCatalogCanonicalModelRegistryProvider,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : CatalogCanonicalModelRegistryProvider {

    @Volatile
    private var cachedModels: CachedModels? = null

    private val lock = Any()

    override fun models(): List<CatalogCanonicalModelEntry> {
        val snapshot = cachedModels
        val now = nowMs()
        if (snapshot != null && now - snapshot.loadedAtMs <= cacheTtlMs) {
            return snapshot.models
        }
        return refresh()
    }

    fun refresh(force: Boolean = false): List<CatalogCanonicalModelEntry> =
        synchronized(lock) {
            val snapshot = cachedModels
            val now = nowMs()
            if (!force && snapshot != null && now - snapshot.loadedAtMs <= cacheTtlMs) {
                return@synchronized snapshot.models
            }

            val models = runCatching {
                runBlocking {
                    val brandsByCode = repository.listBrands()
                        .filter { it.status == CatalogGovernanceEntityStatus.ACTIVE }
                        .associateBy { it.code }
                    val familiesByCode = repository.listProductFamilies()
                        .filter { it.status == CatalogGovernanceEntityStatus.ACTIVE }
                        .associateBy { it.code }
                    val aliases = repository.listAliases()
                        .filter { it.targetKind == CatalogGovernanceAliasTargetKind.MODEL }
                    repository.listModels()
                        .filter { it.status == CatalogGovernanceEntityStatus.ACTIVE }
                        .mapNotNull { model ->
                            val brand = brandsByCode[model.brandCode] ?: return@mapNotNull null
                            val family = model.familyCode?.let(familiesByCode::get)
                            CatalogCanonicalModelEntry(
                                modelCode = model.code,
                                defaultCategoryCode = model.defaultCategoryCode
                                    ?: family?.defaultCategoryCode
                                    ?: brand.primaryCategoryCode
                                    ?: "TECH.PHONES",
                                brandCanonical = brand.displayLabel(),
                                familyCode = family?.code,
                                canonicalModel = model.displayLabel(),
                                modelAliases = (
                                    aliases.filter { it.targetCode == model.code }.map { it.aliasText } +
                                        model.labels.values +
                                        listOf(model.displayLabel())
                                    ).distinct(),
                                accessoryBlockers = family?.accessoryBlockers.orEmpty(),
                            )
                        }
                }
            }.getOrElse {
                fallbackProvider.models()
            }

            val effectiveModels = if (models.isNotEmpty()) models else fallbackProvider.models()
            cachedModels = CachedModels(
                loadedAtMs = now,
                models = effectiveModels,
            )
            effectiveModels
        }

    fun installIntoRuntime(): List<CatalogCanonicalModelEntry> {
        CatalogCanonicalModelRegistry.installProvider(this)
        return refresh(force = true)
    }

    private data class CachedModels(
        val loadedAtMs: Long,
        val models: List<CatalogCanonicalModelEntry>,
    )

    private companion object {
        private const val DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000L
    }
}

private fun com.example.shoppingassistant.domain.catalog.CatalogBrandCanon.displayLabel(): String =
    labels.resolve(locale = "en", fallback = code) ?: code

private fun com.example.shoppingassistant.domain.catalog.CatalogModelCanon.displayLabel(): String =
    labels.resolve(locale = "en", fallback = code) ?: code
