package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig

data class CatalogGovernanceRefreshRunnerResult(
    val categoryCode: String,
    val sourceCount: Int,
    val scheduledRegistryCodes: List<String>,
    val createdRunIds: List<Long>,
    val completedRuns: Int,
    val failedRuns: Int,
    val skippedReason: String? = null,
)

data class CatalogGovernanceRefreshSelection(
    val availableSourceCount: Int,
    val matchedSources: List<CatalogGovernanceSourceRegistryEntryResponse>,
    val skippedReason: String? = null,
)

internal fun CatalogGovernanceRefreshConfig.selectScheduledSources(
    sources: List<CatalogGovernanceSourceRegistryEntryResponse>,
): CatalogGovernanceRefreshSelection {
    val matchedSources = sources
        .asSequence()
        .filter { source -> source.enabled }
        .filter { source ->
            connectorTypes.any { connectorType ->
                connectorType.name.equals(source.connectorType, ignoreCase = true)
            }
        }
        .filter { source ->
            registryCodeAllowlist.isEmpty() || source.registryCode in registryCodeAllowlist
        }
        .distinctBy { it.registryCode }
        .toList()
    return CatalogGovernanceRefreshSelection(
        availableSourceCount = sources.size,
        matchedSources = matchedSources,
        skippedReason = if (matchedSources.isEmpty()) "no_matching_sources" else null,
    )
}

class CatalogGovernanceRefreshRunner(
    private val refreshSurfaceService: CatalogGovernanceRefreshSurfaceService,
    private val config: CatalogGovernanceRefreshConfig,
) {
    suspend fun runOnce(): CatalogGovernanceRefreshRunnerResult {
        if (config.categoryCode.equals(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE, ignoreCase = true)) {
            refreshSurfaceService.ensurePhonesSourceRegistry()
        }
        val sources = refreshSurfaceService.listSources(categoryCode = config.categoryCode)
        val selection = config.selectScheduledSources(sources)
        val selected = selection.matchedSources.map { it.registryCode }
        if (selected.isEmpty()) {
            return CatalogGovernanceRefreshRunnerResult(
                categoryCode = config.categoryCode,
                sourceCount = 0,
                scheduledRegistryCodes = emptyList(),
                createdRunIds = emptyList(),
                completedRuns = 0,
                failedRuns = 0,
                skippedReason = selection.skippedReason,
            )
        }
        val response = refreshSurfaceService.triggerRefresh(
            categoryCode = config.categoryCode,
            registryCodes = selected,
            trigger = config.trigger,
        )
        return CatalogGovernanceRefreshRunnerResult(
            categoryCode = response.categoryCode,
            sourceCount = selected.size,
            scheduledRegistryCodes = selected,
            createdRunIds = response.runs.map { it.id },
            completedRuns = response.runs.count { it.status == CatalogGovernanceRefreshRunStatus.COMPLETED.name },
            failedRuns = response.runs.count { it.status == CatalogGovernanceRefreshRunStatus.FAILED.name },
        )
    }
}
