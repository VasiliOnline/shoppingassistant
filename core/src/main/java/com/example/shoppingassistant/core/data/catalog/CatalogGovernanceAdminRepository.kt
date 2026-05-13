package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.core.config.BackendConfig
import com.example.shoppingassistant.core.network.BackendClient
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogHttpContractPaths
import com.example.shoppingassistant.domain.catalog.Category
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

const val DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE: String = "TECH.PHONES"

enum class CatalogGovernanceAdminReviewAction {
    APPROVE,
    REJECT,
    PROMOTE,
}

@Serializable
data class CatalogGovernanceAdminSourceRegistryEntry(
    val registryCode: String,
    val categoryCode: String,
    val connectorType: String,
    val sourceCode: String,
    val externalRef: String? = null,
    val displayName: String,
    val tier: String,
    val defaultLocale: String? = null,
    val marketCode: String? = null,
    val sourceUri: String? = null,
    val enabled: Boolean,
    val autoPublish: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CatalogGovernanceAdminRefreshStatus(
    val enabled: Boolean,
    val configuredCategoryCode: String,
    val effectiveCategoryCode: String,
    val pollIntervalMs: Long,
    val trigger: String,
    val connectorTypes: List<String> = emptyList(),
    val registryCodeAllowlist: List<String> = emptyList(),
    val availableSourceCount: Int,
    val matchedSourceCount: Int,
    val matchedRegistryCodes: List<String> = emptyList(),
    val skippedReason: String? = null,
    val latestRunId: Long? = null,
    val latestRunRegistryCode: String? = null,
    val latestRunStatus: String? = null,
    val latestRunPublishStatus: String? = null,
    val latestRunStartedAt: Long? = null,
    val latestRunFinishedAt: Long? = null,
)

@Serializable
data class CatalogGovernanceAdminRefreshRun(
    val id: Long,
    val registryCode: String,
    val categoryCode: String,
    val trigger: String,
    val status: String,
    val sourceSnapshotId: Long? = null,
    val brandsSynced: Int = 0,
    val familiesSynced: Int = 0,
    val modelsSynced: Int = 0,
    val canonicalValuesSynced: Int = 0,
    val aliasesSynced: Int = 0,
    val candidatesDetected: Int = 0,
    val reviewQueueSize: Int = 0,
    val publishStatus: String? = null,
    val errorMessage: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val startedAt: Long,
    val finishedAt: Long? = null,
)

@Serializable
data class CatalogGovernanceAdminPublishEvent(
    val id: Long,
    val refreshRunId: Long? = null,
    val categoryCode: String? = null,
    val eventType: String,
    val artifactType: String,
    val entityRef: String? = null,
    val status: String,
    val details: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
)

@Serializable
data class CatalogGovernanceAdminReviewQueueItem(
    val clusterKey: String,
    val categoryCode: String? = null,
    val attributeCode: String,
    val locale: String? = null,
    val marketCode: String? = null,
    val normalizedValue: String,
    val primaryCandidateId: Long? = null,
    val recommendation: String,
    val recommendedDisposition: String,
    val existingTargetCode: String? = null,
    val totalScore: Double,
    val observedCount: Int,
    val candidateCount: Int,
    val candidateIds: List<Long> = emptyList(),
    val candidateStatuses: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
)

@Serializable
data class CatalogGovernanceAdminReviewActionResult(
    val categoryCode: String? = null,
    val candidateId: Long,
    val attributeCode: String,
    val action: String,
    val candidateStatus: String,
    val decisionId: Long,
    val decisionAction: String,
    val reasonCode: String,
    val actor: String,
    val publishStatus: String? = null,
    val canonicalCode: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogGovernanceAdminRefreshTriggerResult(
    val categoryCode: String,
    val requestedRegistryCodes: List<String> = emptyList(),
    val runs: List<CatalogGovernanceAdminRefreshRun>,
)

@Serializable
data class CatalogGovernanceAdminReadinessItem(
    val category: Category,
    val readiness: CatalogCategoryReadiness,
    val editorialReadiness: CatalogCategoryReadiness,
    val operationalReadiness: CatalogCategoryReadiness,
    val completenessGatePassed: Boolean,
    val blockingIssues: List<String> = emptyList(),
    val editorialBlockingIssues: List<String> = emptyList(),
    val operationalBlockingIssues: List<String> = emptyList(),
    val operationalSampleCount: Int = 0,
    val operationalDroppedRate: Double = 0.0,
    val operationalUnknownAttributeRate: Double = 0.0,
    val operationalRequiredMissingRate: Double = 0.0,
    val operationalLowConfidenceRate: Double = 0.0,
)

interface CatalogGovernanceAdminRepository {
    suspend fun listReadinessInventory(): List<CatalogGovernanceAdminReadinessItem>

    suspend fun getRefreshStatus(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
    ): CatalogGovernanceAdminRefreshStatus

    suspend fun listSources(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
    ): List<CatalogGovernanceAdminSourceRegistryEntry>

    suspend fun listRefreshRuns(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
        limit: Int = 20,
    ): List<CatalogGovernanceAdminRefreshRun>

    suspend fun listReviewQueue(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
        limit: Int = 20,
    ): List<CatalogGovernanceAdminReviewQueueItem>

    suspend fun listPublishEvents(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
        limit: Int = 20,
    ): List<CatalogGovernanceAdminPublishEvent>

    suspend fun submitReviewAction(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
        candidateId: Long,
        action: CatalogGovernanceAdminReviewAction,
        actor: String,
        reasonCode: String? = null,
    ): CatalogGovernanceAdminReviewActionResult

    suspend fun triggerRefresh(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
        registryCodes: List<String> = emptyList(),
        trigger: String = "MANUAL",
    ): CatalogGovernanceAdminRefreshTriggerResult

    suspend fun triggerRebuild(
        categoryCode: String = DEFAULT_CATALOG_GOVERNANCE_CATEGORY_CODE,
        reason: String = "MANUAL_REBUILD",
    ): CatalogGovernanceAdminPublishEvent
}

class CatalogGovernanceAdminApiRepository(
    private val backendClient: BackendClient,
    private val baseUrl: String = BackendConfig.BASE_URL,
) : CatalogGovernanceAdminRepository {

    override suspend fun listReadinessInventory(): List<CatalogGovernanceAdminReadinessItem> {
        val response = backendClient.client.get("$baseUrl${CatalogHttpContractPaths.readiness}")
        require(response.status.isSuccess()) {
            "Catalog readiness inventory API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun getRefreshStatus(
        categoryCode: String,
    ): CatalogGovernanceAdminRefreshStatus {
        val response = backendClient.client.get("$baseUrl${CatalogHttpContractPaths.governanceRefreshStatus}") {
            parameter("categoryCode", categoryCode)
        }
        require(response.status.isSuccess()) {
            "Catalog governance refresh-status API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun listSources(
        categoryCode: String,
    ): List<CatalogGovernanceAdminSourceRegistryEntry> {
        val response = backendClient.client.get("$baseUrl${CatalogHttpContractPaths.governanceSources}") {
            parameter("categoryCode", categoryCode)
        }
        require(response.status.isSuccess()) {
            "Catalog governance sources API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun listRefreshRuns(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceAdminRefreshRun> {
        val response = backendClient.client.get("$baseUrl${CatalogHttpContractPaths.governanceRefreshRuns}") {
            parameter("categoryCode", categoryCode)
            parameter("limit", limit)
        }
        require(response.status.isSuccess()) {
            "Catalog governance refresh-runs API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun listReviewQueue(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceAdminReviewQueueItem> {
        val response = backendClient.client.get("$baseUrl${CatalogHttpContractPaths.governanceReviewQueue}") {
            parameter("categoryCode", categoryCode)
            parameter("limit", limit)
        }
        require(response.status.isSuccess()) {
            "Catalog governance review-queue API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun listPublishEvents(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceAdminPublishEvent> {
        val response = backendClient.client.get("$baseUrl${CatalogHttpContractPaths.governancePublishEvents}") {
            parameter("categoryCode", categoryCode)
            parameter("limit", limit)
        }
        require(response.status.isSuccess()) {
            "Catalog governance publish-events API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun submitReviewAction(
        categoryCode: String,
        candidateId: Long,
        action: CatalogGovernanceAdminReviewAction,
        actor: String,
        reasonCode: String?,
    ): CatalogGovernanceAdminReviewActionResult {
        val response = backendClient.client.post("$baseUrl${CatalogHttpContractPaths.governanceReviewAction}") {
            parameter("categoryCode", categoryCode)
            parameter("candidateId", candidateId)
            parameter("action", action.name)
            parameter("actor", actor)
            reasonCode?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("reasonCode", it) }
        }
        require(response.status.isSuccess()) {
            "Catalog governance review-action API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun triggerRefresh(
        categoryCode: String,
        registryCodes: List<String>,
        trigger: String,
    ): CatalogGovernanceAdminRefreshTriggerResult {
        val response = backendClient.client.post("$baseUrl${CatalogHttpContractPaths.governanceRefresh}") {
            parameter("categoryCode", categoryCode)
            registryCodes.forEach { registryCode ->
                registryCode.trim().takeIf { it.isNotEmpty() }?.let { parameter("registryCode", it) }
            }
            parameter("trigger", trigger)
        }
        require(response.status.isSuccess()) {
            "Catalog governance refresh API status=${response.status}"
        }
        return response.body()
    }

    override suspend fun triggerRebuild(
        categoryCode: String,
        reason: String,
    ): CatalogGovernanceAdminPublishEvent {
        val response = backendClient.client.post("$baseUrl${CatalogHttpContractPaths.governanceRebuild}") {
            parameter("categoryCode", categoryCode)
            parameter("reason", reason)
        }
        require(response.status.isSuccess()) {
            "Catalog governance rebuild API status=${response.status}"
        }
        return response.body()
    }
}
