package com.example.shoppingassistant.server.ingest.registry

import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.SourceRegistryEntry
import com.example.shoppingassistant.domain.ingest.SourceType

class InMemorySourceRegistry(
    private val entries: List<SourceRegistryEntry>,
    private val rolloutOverrides: Map<String, Boolean> = emptyMap(),
) : SourceRegistry {

    override fun all(): List<SourceRegistryEntry> =
        entries.mapNotNull { applyOverride(it) }

    override fun findById(id: String): SourceRegistryEntry? =
        applyOverride(entries.firstOrNull { it.id == id })

    override fun findBySourceType(type: SourceType): SourceRegistryEntry? =
        applyOverride(entries.firstOrNull { it.sourceType == type })

    private fun applyOverride(entry: SourceRegistryEntry?): SourceRegistryEntry? {
        if (entry == null) return null
        val override = rolloutOverrides[entry.id] ?: return entry
        return entry.copy(rolloutEnabled = override)
    }
}
