package com.example.shoppingassistant.server.catalog

data class CatalogGovernanceRuntimeInvalidationEvent(
    val reason: String,
    val entityRef: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
)

interface CatalogGovernanceRuntimeInvalidator {
    fun invalidate(event: CatalogGovernanceRuntimeInvalidationEvent)
}

object NoOpCatalogGovernanceRuntimeInvalidator : CatalogGovernanceRuntimeInvalidator {
    override fun invalidate(event: CatalogGovernanceRuntimeInvalidationEvent) = Unit
}

class GovernanceCatalogRuntimeRegistryInvalidator(
    private val familyRegistryProvider: GovernanceCatalogCanonicalProductFamilyRegistryProvider,
    private val modelRegistryProvider: GovernanceCatalogCanonicalModelRegistryProvider,
) : CatalogGovernanceRuntimeInvalidator {
    override fun invalidate(event: CatalogGovernanceRuntimeInvalidationEvent) {
        familyRegistryProvider.refresh(force = true)
        modelRegistryProvider.refresh(force = true)
    }
}
