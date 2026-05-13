package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import com.example.shoppingassistant.domain.catalog.Category
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.Serializable

const val CATALOG_DATA_VERSION_HEADER: String = "X-Catalog-Data-Version"
const val CATALOG_SCHEMA_VERSION_HEADER: String = "X-Catalog-Schema-Version"
const val CATALOG_MIN_SUPPORTED_CLIENT_SCHEMA_VERSION_HEADER: String = "X-Catalog-Min-Supported-Client-Schema-Version"
const val CATALOG_CATEGORY_REQUESTED_CODE_HEADER: String = "X-Catalog-Category-Requested-Code"
const val CATALOG_CATEGORY_RESOLVED_CODE_HEADER: String = "X-Catalog-Category-Resolved-Code"
const val CATALOG_CATEGORY_REDIRECTED_HEADER: String = "X-Catalog-Category-Redirected"

@Serializable
data class CatalogVersionResponse(
    val schemaVersion: String,
    val dataVersion: String,
    val minSupportedClientSchemaVersion: String,
)

@Serializable
data class CatalogCategoryResolveResponse(
    val requestedCode: String,
    val resolvedCode: String,
    val redirected: Boolean,
    val redirectChain: List<String> = emptyList(),
)

@Serializable
data class CatalogReadinessInventoryItem(
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

@Serializable
data class CatalogReadinessHistoryPoint(
    val windowEndDate: String,
    val readiness: CatalogCategoryReadiness,
    val editorialReadiness: CatalogCategoryReadiness,
    val operationalReadiness: CatalogCategoryReadiness,
    val blockingIssues: List<String> = emptyList(),
    val editorialBlockingIssues: List<String> = emptyList(),
    val operationalBlockingIssues: List<String> = emptyList(),
    val operationalSampleCount: Int = 0,
    val operationalDroppedRate: Double = 0.0,
    val operationalUnknownAttributeRate: Double = 0.0,
    val operationalRequiredMissingRate: Double = 0.0,
    val operationalLowConfidenceRate: Double = 0.0,
)

@Serializable
data class CatalogReadinessHistoryResponse(
    val category: Category,
    val requestedDays: Int,
    val operationalWindowDays: Int,
    val points: List<CatalogReadinessHistoryPoint>,
)

@Serializable
data class CatalogReadinessGovernanceArtifactStatusResponse(
    val declaredPath: String,
    val resourcePath: String,
    val exists: Boolean,
)

@Serializable
data class CatalogDataReleaseEntryResponse(
    val version: String,
    val releaseDate: String,
    val changes: List<String>,
)

@Serializable
data class CatalogReleasePolicyResponse(
    val releaseCadence: String,
    val owner: String,
    val announceBeforeDays: Int,
    val removeAfterDays: Int,
    val requiredArtifactStatuses: List<CatalogReadinessGovernanceArtifactStatusResponse>,
)

@Serializable
data class CatalogReadinessRunCadenceResponse(
    val dailyOperationalTimeUtc: String,
    val weeklySummaryDay: String,
    val weeklySummaryTimeUtc: String,
)

@Serializable
data class CatalogGovernanceHookResponse(
    val code: String,
    val trigger: String,
    val transport: String,
    val targetEnvVar: String,
    val description: String,
)

@Serializable
data class CatalogReadinessPolicyResponse(
    val schemaVersion: String,
    val runCadence: CatalogReadinessRunCadenceResponse,
    val sqlChecks: List<String>,
    val automationScripts: List<String>,
    val runbooks: List<String>,
    val monthlyReleaseArtifacts: List<String>,
    val slaDaysByIssueType: Map<String, Int>,
    val governanceHooks: List<CatalogGovernanceHookResponse>,
)

@Serializable
data class CatalogRuntimeCompatibilityPolicyResponse(
    val schemaVersioningMode: String,
    val minSupportedClientStrategy: String,
    val dataVersionMode: String,
    val embeddedParityRequiredWhen: List<String>,
    val negotiatedDataVersionMustStayStable: Boolean,
    val requiredVersionHeaders: List<String>,
    val versionEndpoint: String,
)

@Serializable
data class CatalogReadinessGovernanceResponse(
    val schemaVersion: String,
    val dataVersion: String,
    val generatedAt: String,
    val releasePolicy: CatalogReleasePolicyResponse,
    val readinessPolicy: CatalogReadinessPolicyResponse,
    val compatibilityPolicy: CatalogRuntimeCompatibilityPolicyResponse,
    val releaseHistory: List<CatalogDataReleaseEntryResponse>,
)

@Serializable
data class CatalogRuntimeCompatibilityResponse(
    val schemaVersion: String,
    val currentSchemaVersion: String,
    val minSupportedClientSchemaVersion: String,
    val currentDataVersion: String,
    val policy: CatalogRuntimeCompatibilityPolicyResponse,
)

@Serializable
data class CatalogGovernanceReportResponse(
    val reportType: String,
    val reportDate: String,
    val generatedAt: String,
    val windowStartDate: String,
    val windowEndDate: String,
    val totalCategories: Int,
    val readyCategories: Int,
    val betaCategories: Int,
    val internalCategories: Int,
    val categoriesWithBlockingIssues: List<String>,
    val dataVersion: String,
    val schemaVersion: String,
)

@Serializable
data class CatalogGovernanceHookDeliveryResponse(
    val hookCode: String,
    val trigger: String,
    val transport: String,
    val target: String?,
    val status: String,
    val attemptedAt: String,
    val responseStatus: Int? = null,
    val details: String? = null,
)

@Serializable
data class CatalogGovernanceSourceRegistryEntryResponse(
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
data class CatalogGovernanceRefreshStatusResponse(
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
data class CatalogGovernanceRefreshRunResponse(
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
data class CatalogGovernanceModelEnrichmentCandidateResponse(
    val id: Long,
    val candidateKey: String,
    val categoryCode: String,
    val brandRaw: String,
    val brandCode: String? = null,
    val familyRaw: String? = null,
    val familyCode: String? = null,
    val canonicalModelCode: String? = null,
    val modelRaw: String,
    val modelNormalized: String,
    val officialSourceCode: String? = null,
    val officialEndpointCode: String? = null,
    val status: String,
    val observedCount: Int,
    val distinctSellerCount: Int,
    val sellerRefs: List<String> = emptyList(),
    val sampleOfferRefs: List<String> = emptyList(),
    val maxConfidence: Double,
    val reasonCodes: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val suggestedSourceUri: String? = null,
    val suggestedParserType: String? = null,
    val sourceUriSuggestionConfidence: String? = null,
    val sourceUriSuggestionReason: String? = null,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CatalogGovernanceModelEnrichmentPromotionRequest(
    val candidateId: Long,
    val actor: String = "manual_operator",
    val sourceUri: String? = null,
    val parserType: String = "GENERIC_PHONE_SPECS_PAGE",
    val endpointCode: String? = null,
    val familyCode: String? = null,
    val modelCode: String? = null,
    val modelLabel: String? = null,
    val modelLine: String? = null,
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val fixedValues: Map<String, String> = emptyMap(),
    val triggerRefresh: Boolean = true,
)

@Serializable
data class CatalogGovernanceModelEnrichmentPromotionResponse(
    val categoryCode: String,
    val candidateId: Long,
    val action: String,
    val candidateStatus: String,
    val sourceCode: String,
    val registryCode: String,
    val endpointCode: String,
    val modelCode: String,
    val familyCode: String,
    val parserType: String,
    val triggerRefresh: Boolean,
    val runs: List<CatalogGovernanceRefreshRunResponse> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogGovernancePublishEventResponse(
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
data class CatalogGovernanceReviewQueueItemResponse(
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
data class CatalogGovernanceReviewActionResponse(
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
data class CatalogGovernanceRefreshTriggerResponse(
    val categoryCode: String,
    val requestedRegistryCodes: List<String> = emptyList(),
    val runs: List<CatalogGovernanceRefreshRunResponse>,
)

internal fun ApplicationCall.attachCatalogVersionHeader() {
    response.headers.append(CATALOG_SCHEMA_VERSION_HEADER, CatalogSchemaVersion.current)
    response.headers.append(CATALOG_DATA_VERSION_HEADER, CatalogDataVersion.current)
    response.headers.append(
        CATALOG_MIN_SUPPORTED_CLIENT_SCHEMA_VERSION_HEADER,
        CatalogSchemaVersion.minSupportedClient,
    )
}
