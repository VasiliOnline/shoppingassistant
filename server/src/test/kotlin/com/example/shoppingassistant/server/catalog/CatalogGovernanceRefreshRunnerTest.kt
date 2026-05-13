package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class CatalogGovernanceRefreshRunnerTest {
    @Test
    fun runOnce_refreshes_only_matching_official_sources() = runBlocking {
        val service = FakeCatalogGovernanceRefreshSurfaceService(
            sources = listOf(
                source("CURATED", CatalogGovernanceRefreshConnectorType.CURATED_SEED_PACK),
                source("APPLE", CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                source("SAMSUNG", CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
            ),
        )
        val runner = CatalogGovernanceRefreshRunner(
            refreshSurfaceService = service,
            config = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = TimeUnit.HOURS.toMillis(1),
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = emptySet(),
                trigger = "SCHEDULED",
            ),
        )

        val result = runner.runOnce()

        assertEquals(
            listOf("APPLE", "SAMSUNG"),
            result.scheduledRegistryCodes,
        )
        assertEquals(
            listOf("APPLE", "SAMSUNG"),
            service.triggeredRegistryCodes,
        )
        assertEquals("SCHEDULED", service.lastTrigger)
    }

    @Test
    fun runOnce_skips_when_allowlist_removes_all_matching_sources() = runBlocking {
        val service = FakeCatalogGovernanceRefreshSurfaceService(
            sources = listOf(source("APPLE", CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE)),
        )
        val runner = CatalogGovernanceRefreshRunner(
            refreshSurfaceService = service,
            config = CatalogGovernanceRefreshConfig(
                enabled = true,
                pollIntervalMs = TimeUnit.HOURS.toMillis(1),
                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                connectorTypes = setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
                registryCodeAllowlist = setOf("GOOGLE"),
                trigger = "SCHEDULED",
            ),
        )

        val result = runner.runOnce()

        assertEquals("no_matching_sources", result.skippedReason)
        assertTrue(service.triggeredRegistryCodes.isEmpty())
    }

    private fun source(
        registryCode: String,
        connectorType: CatalogGovernanceRefreshConnectorType,
    ) = CatalogGovernanceSourceRegistryEntryResponse(
        registryCode = registryCode,
        categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        connectorType = connectorType.name,
        sourceCode = registryCode.lowercase(),
        displayName = registryCode,
        tier = "AUTHORITATIVE",
        enabled = true,
        autoPublish = true,
        createdAt = 1L,
        updatedAt = 1L,
    )
}

private class FakeCatalogGovernanceRefreshSurfaceService(
    private val sources: List<CatalogGovernanceSourceRegistryEntryResponse>,
) : CatalogGovernanceRefreshSurfaceService {
    var triggeredRegistryCodes: List<String> = emptyList()
        private set
    var lastTrigger: String? = null
        private set

    override suspend fun ensurePhonesSourceRegistry(): List<CatalogGovernanceSourceRegistryEntryResponse> = sources

    override suspend fun listSources(categoryCode: String): List<CatalogGovernanceSourceRegistryEntryResponse> = sources

    override suspend fun listModelEnrichmentQueue(
        categoryCode: String,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogGovernanceModelEnrichmentCandidateResponse> = emptyList()

    override suspend fun promoteModelEnrichmentCandidate(
        categoryCode: String,
        request: CatalogGovernanceModelEnrichmentPromotionRequest,
    ): CatalogGovernanceModelEnrichmentPromotionResponse {
        error("Not used in test.")
    }

    override suspend fun getRefreshStatus(
        categoryCode: String,
    ): CatalogGovernanceRefreshStatusResponse {
        error("Not used in test.")
    }

    override suspend fun listRefreshRuns(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceRefreshRunResponse> = emptyList()

    override suspend fun listReviewQueue(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceReviewQueueItemResponse> = emptyList()

    override suspend fun listPublishEvents(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernancePublishEventResponse> = emptyList()

    override suspend fun submitReviewAction(
        categoryCode: String,
        candidateId: Long,
        action: CatalogGovernanceReviewAction,
        actor: String,
        reasonCode: String?,
    ): CatalogGovernanceReviewActionResponse {
        error("Not used in test.")
    }

    override suspend fun triggerRefresh(
        categoryCode: String,
        registryCodes: List<String>,
        trigger: String,
    ): CatalogGovernanceRefreshTriggerResponse {
        triggeredRegistryCodes = registryCodes
        lastTrigger = trigger
        return CatalogGovernanceRefreshTriggerResponse(
            categoryCode = categoryCode,
            requestedRegistryCodes = registryCodes,
            runs = registryCodes.mapIndexed { index, registryCode ->
                CatalogGovernanceRefreshRunResponse(
                    id = index + 1L,
                    registryCode = registryCode,
                    categoryCode = categoryCode,
                    trigger = trigger,
                    status = CatalogGovernanceRefreshRunStatus.COMPLETED.name,
                    publishStatus = CatalogGovernancePublishStatus.PUBLISHED.name,
                    startedAt = 1L,
                    finishedAt = 2L,
                )
            },
        )
    }

    override suspend fun triggerRebuild(
        categoryCode: String,
        reason: String,
    ): CatalogGovernancePublishEventResponse {
        error("Not used in test.")
    }
}
