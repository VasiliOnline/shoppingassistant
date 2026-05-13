package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogArtifactPaths
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceLoader
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import kotlinx.datetime.LocalDate
import java.nio.charset.StandardCharsets

internal interface CatalogReadinessInventoryService {
    suspend fun getInventory(): List<CatalogReadinessInventoryItem>
}

internal interface CatalogReadinessHistoryService {
    suspend fun getCategoryHistory(
        categoryCode: String,
        days: Int,
    ): CatalogReadinessHistoryResponse?
}

internal interface CatalogReadinessGovernanceService {
    fun getGovernance(): CatalogReadinessGovernanceResponse
}

internal interface CatalogRuntimeCompatibilityService {
    fun getRuntimeCompatibility(): CatalogRuntimeCompatibilityResponse
}

internal interface CatalogGovernanceReportsService {
    suspend fun listReports(limit: Int): List<CatalogGovernanceReportResponse>
}

internal interface CatalogOpenApiService {
    fun getDocument(): String
}

internal interface CatalogReadinessSnapshotRepository {
    suspend fun recordInventory(
        snapshotDate: LocalDate,
        inventory: List<CatalogReadinessInventoryItem>,
    )

    suspend fun hasSnapshotForDate(snapshotDate: LocalDate): Boolean

    suspend fun loadHistory(
        categoryCode: String,
        days: Int,
    ): List<CatalogPersistedReadinessSnapshot>
}

internal data class CatalogAutomationLease(
    val leaseKey: String,
    val ownerId: String,
    val fencingToken: Long,
    val leaseExpiresAt: Long,
)

internal interface CatalogAutomationLeaseRepository {
    suspend fun tryAcquireLease(
        leaseKey: String,
        ownerId: String,
        nowMs: Long,
        leaseExpiresAt: Long,
    ): CatalogAutomationLease?

    suspend fun renewLease(
        lease: CatalogAutomationLease,
        nowMs: Long,
        leaseExpiresAt: Long,
    ): CatalogAutomationLease?

    suspend fun releaseLease(
        lease: CatalogAutomationLease,
    )
}

internal interface CatalogGovernanceReportRepository {
    suspend fun hasReport(
        reportType: String,
        reportDate: LocalDate,
    ): Boolean

    suspend fun upsertReport(report: CatalogGovernanceReportResponse)

    suspend fun listReports(limit: Int): List<CatalogGovernanceReportResponse>
}

internal interface CatalogGovernanceHookDeliveryRepository {
    suspend fun recordDelivery(delivery: CatalogGovernanceHookDeliveryResponse)
}

internal class CatalogReadinessInventoryServiceImpl(
    private val repository: CatalogReadRepository,
    private val taxonomyRepository: CatalogTaxonomyRepository,
) : CatalogReadinessInventoryService {
    override suspend fun getInventory(): List<CatalogReadinessInventoryItem> {
        return buildList {
            taxonomyRepository.listCategories()
                .filter { category -> category.status != CategoryStatus.HIDDEN }
                .sortedBy { category -> category.code }
                .forEach { category ->
                    val spec = repository.getCategoryEffectiveSpec(category.code) ?: return@forEach
                    add(spec.toInventoryItem())
                }
        }
    }
}

internal class CatalogReadinessHistoryServiceImpl(
    private val repository: CatalogReadRepository,
    private val snapshotRepository: CatalogReadinessSnapshotRepository,
) : CatalogReadinessHistoryService {
    override suspend fun getCategoryHistory(
        categoryCode: String,
        days: Int,
    ): CatalogReadinessHistoryResponse? {
        val normalizedCategoryCode = categoryCode.trim()
        if (normalizedCategoryCode.isEmpty()) return null

        val spec = repository.getCategoryEffectiveSpec(normalizedCategoryCode) ?: return null
        val persistedHistory = snapshotRepository.loadHistory(
            categoryCode = spec.category.code,
            days = days,
        )
        if (persistedHistory.isNotEmpty()) {
            return CatalogReadinessHistoryResponse(
                category = spec.category,
                requestedDays = days.coerceIn(1, MAX_OPERATIONAL_HISTORY_DAYS),
                operationalWindowDays = OPERATIONAL_WINDOW_DAYS,
                points = persistedHistory.map { snapshot ->
                    CatalogReadinessHistoryPoint(
                        windowEndDate = snapshot.snapshotDate.toString(),
                        readiness = snapshot.readiness,
                        editorialReadiness = snapshot.editorialReadiness,
                        operationalReadiness = snapshot.operationalReadiness,
                        blockingIssues = snapshot.blockingIssues,
                        editorialBlockingIssues = snapshot.editorialBlockingIssues,
                        operationalBlockingIssues = snapshot.operationalBlockingIssues,
                        operationalSampleCount = snapshot.operationalSampleCount,
                        operationalDroppedRate = snapshot.operationalDroppedRate,
                        operationalUnknownAttributeRate = snapshot.operationalUnknownAttributeRate,
                        operationalRequiredMissingRate = snapshot.operationalRequiredMissingRate,
                        operationalLowConfidenceRate = snapshot.operationalLowConfidenceRate,
                    )
                },
            )
        }
        val history = loadOperationalReadinessHistory(
            categoryCode = spec.category.code,
            days = days,
        )
        return CatalogReadinessHistoryResponse(
            category = spec.category,
            requestedDays = days.coerceIn(1, MAX_OPERATIONAL_HISTORY_DAYS),
            operationalWindowDays = OPERATIONAL_WINDOW_DAYS,
            points = history.map { entry ->
                val operationalReadiness = when {
                    spec.meta.editorialReadiness == CatalogCategoryReadiness.INTERNAL ->
                        CatalogCategoryReadiness.INTERNAL

                    entry.report.blockingIssues.isEmpty() ->
                        CatalogCategoryReadiness.READY

                    else ->
                        CatalogCategoryReadiness.BETA
                }
                val finalReadiness = combineReadiness(
                    editorialReadiness = spec.meta.editorialReadiness,
                    operationalReadiness = operationalReadiness,
                )
                CatalogReadinessHistoryPoint(
                    windowEndDate = entry.windowEndDate.toString(),
                    readiness = finalReadiness,
                    editorialReadiness = spec.meta.editorialReadiness,
                    operationalReadiness = operationalReadiness,
                    blockingIssues = (spec.meta.editorialBlockingIssues + entry.report.blockingIssues).distinct(),
                    editorialBlockingIssues = spec.meta.editorialBlockingIssues,
                    operationalBlockingIssues = entry.report.blockingIssues,
                    operationalSampleCount = entry.report.sampleCount,
                    operationalDroppedRate = entry.report.droppedRate,
                    operationalUnknownAttributeRate = entry.report.unknownAttributeRate,
                    operationalRequiredMissingRate = entry.report.requiredMissingRate,
                    operationalLowConfidenceRate = entry.report.lowConfidenceRate,
                )
            },
        )
    }
}

internal class CatalogReadinessGovernanceServiceImpl : CatalogReadinessGovernanceService {
    override fun getGovernance(): CatalogReadinessGovernanceResponse {
        val snapshot = CatalogGovernanceLoader.loadSnapshot()
        return CatalogReadinessGovernanceResponse(
            schemaVersion = snapshot.schemaVersion,
            dataVersion = snapshot.dataVersion,
            generatedAt = snapshot.generatedAt,
            releasePolicy = CatalogReleasePolicyResponse(
                releaseCadence = snapshot.releasePolicy.releaseCadence,
                owner = snapshot.releasePolicy.owner,
                announceBeforeDays = snapshot.releasePolicy.deprecation.announceBeforeDays,
                removeAfterDays = snapshot.releasePolicy.deprecation.removeAfterDays,
                requiredArtifactStatuses = snapshot.requiredArtifactStatuses.map { artifact ->
                    CatalogReadinessGovernanceArtifactStatusResponse(
                        declaredPath = artifact.declaredPath,
                        resourcePath = artifact.resourcePath,
                        exists = artifact.exists,
                    )
                },
            ),
            readinessPolicy = CatalogReadinessPolicyResponse(
                schemaVersion = snapshot.readinessPolicy.schemaVersion,
                runCadence = CatalogReadinessRunCadenceResponse(
                    dailyOperationalTimeUtc = snapshot.readinessPolicy.runCadence.dailyOperationalTimeUtc,
                    weeklySummaryDay = snapshot.readinessPolicy.runCadence.weeklySummaryDay,
                    weeklySummaryTimeUtc = snapshot.readinessPolicy.runCadence.weeklySummaryTimeUtc,
                ),
                sqlChecks = snapshot.readinessPolicy.sqlChecks,
                automationScripts = snapshot.readinessPolicy.automationScripts,
                runbooks = snapshot.readinessPolicy.runbooks,
                monthlyReleaseArtifacts = snapshot.readinessPolicy.monthlyReleaseArtifacts,
                slaDaysByIssueType = snapshot.readinessPolicy.slaDaysByIssueType,
                governanceHooks = snapshot.readinessPolicy.governanceHooks.map { hook ->
                    CatalogGovernanceHookResponse(
                        code = hook.code,
                        trigger = hook.trigger,
                        transport = hook.transport,
                        targetEnvVar = hook.targetEnvVar,
                        description = hook.description,
                    )
                },
            ),
            compatibilityPolicy = snapshot.compatibilityPolicy.toResponse(),
            releaseHistory = snapshot.releaseHistory.map { entry ->
                CatalogDataReleaseEntryResponse(
                    version = entry.version,
                    releaseDate = entry.releaseDate,
                    changes = entry.changes,
                )
            },
        )
    }
}

internal class CatalogRuntimeCompatibilityServiceImpl : CatalogRuntimeCompatibilityService {
    override fun getRuntimeCompatibility(): CatalogRuntimeCompatibilityResponse {
        val snapshot = CatalogGovernanceLoader.loadSnapshot()
        return CatalogRuntimeCompatibilityResponse(
            schemaVersion = snapshot.schemaVersion,
            currentSchemaVersion = CatalogSchemaVersion.current,
            minSupportedClientSchemaVersion = CatalogSchemaVersion.minSupportedClient,
            currentDataVersion = CatalogDataVersion.current,
            policy = snapshot.compatibilityPolicy.toResponse(),
        )
    }
}

internal class CatalogGovernanceReportsServiceImpl(
    private val repository: CatalogGovernanceReportRepository,
) : CatalogGovernanceReportsService {
    override suspend fun listReports(limit: Int): List<CatalogGovernanceReportResponse> =
        repository.listReports(limit)
}

internal class CatalogOpenApiServiceImpl : CatalogOpenApiService {
    private val documentBody: String by lazy {
        javaClass.classLoader
            .getResourceAsStream(CatalogArtifactPaths.openApiDocument)
            ?.use { stream -> stream.readBytes().toString(StandardCharsets.UTF_8) }
            ?: error("Catalog OpenAPI resource '${CatalogArtifactPaths.openApiDocument}' not found.")
    }

    override fun getDocument(): String = documentBody
}

internal class DatabaseCatalogReadinessSnapshotRepository : CatalogReadinessSnapshotRepository {
    override suspend fun recordInventory(
        snapshotDate: LocalDate,
        inventory: List<CatalogReadinessInventoryItem>,
    ) {
        persistReadinessInventorySnapshots(
            snapshotDate = snapshotDate,
            inventory = inventory,
        )
    }

    override suspend fun hasSnapshotForDate(snapshotDate: LocalDate): Boolean =
        hasPersistedReadinessSnapshot(snapshotDate)

    override suspend fun loadHistory(
        categoryCode: String,
        days: Int,
    ): List<CatalogPersistedReadinessSnapshot> =
        loadPersistedReadinessHistory(
            categoryCode = categoryCode,
            days = days,
        )
}

internal class DatabaseCatalogAutomationLeaseRepository : CatalogAutomationLeaseRepository {
    override suspend fun tryAcquireLease(
        leaseKey: String,
        ownerId: String,
        nowMs: Long,
        leaseExpiresAt: Long,
    ): CatalogAutomationLease? =
        tryAcquireCatalogAutomationLease(
            leaseKey = leaseKey,
            ownerId = ownerId,
            nowMs = nowMs,
            leaseExpiresAt = leaseExpiresAt,
        )

    override suspend fun renewLease(
        lease: CatalogAutomationLease,
        nowMs: Long,
        leaseExpiresAt: Long,
    ): CatalogAutomationLease? =
        renewCatalogAutomationLease(
            lease = lease,
            nowMs = nowMs,
            leaseExpiresAt = leaseExpiresAt,
        )

    override suspend fun releaseLease(
        lease: CatalogAutomationLease,
    ) {
        releaseCatalogAutomationLease(
            lease = lease,
        )
    }
}

internal class DatabaseCatalogGovernanceReportRepository : CatalogGovernanceReportRepository {
    override suspend fun hasReport(
        reportType: String,
        reportDate: LocalDate,
    ): Boolean = hasCatalogGovernanceReport(
        reportType = reportType,
        reportDate = reportDate,
    )

    override suspend fun upsertReport(report: CatalogGovernanceReportResponse) {
        upsertCatalogGovernanceReport(report)
    }

    override suspend fun listReports(limit: Int): List<CatalogGovernanceReportResponse> =
        loadCatalogGovernanceReports(limit)
}

internal class DatabaseCatalogGovernanceHookDeliveryRepository : CatalogGovernanceHookDeliveryRepository {
    override suspend fun recordDelivery(delivery: CatalogGovernanceHookDeliveryResponse) {
        persistCatalogGovernanceHookDelivery(delivery)
    }
}

private fun com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec.toInventoryItem():
    CatalogReadinessInventoryItem =
    CatalogReadinessInventoryItem(
        category = category,
        readiness = readiness,
        editorialReadiness = meta.editorialReadiness,
        operationalReadiness = meta.operationalReadiness,
        completenessGatePassed = meta.completenessGatePassed,
        blockingIssues = meta.readinessBlockingIssues,
        editorialBlockingIssues = meta.editorialBlockingIssues,
        operationalBlockingIssues = meta.operationalBlockingIssues,
        operationalSampleCount = meta.operationalSampleCount,
        operationalDroppedRate = meta.operationalDroppedRate,
        operationalUnknownAttributeRate = meta.operationalUnknownAttributeRate,
        operationalRequiredMissingRate = meta.operationalRequiredMissingRate,
        operationalLowConfidenceRate = meta.operationalLowConfidenceRate,
    )

private fun com.example.shoppingassistant.domain.catalog.CatalogRuntimeCompatibilityPolicy.toResponse():
    CatalogRuntimeCompatibilityPolicyResponse =
    CatalogRuntimeCompatibilityPolicyResponse(
        schemaVersioningMode = schemaVersioningMode,
        minSupportedClientStrategy = minSupportedClientStrategy,
        dataVersionMode = dataVersionMode,
        embeddedParityRequiredWhen = embeddedParityRequiredWhen,
        negotiatedDataVersionMustStayStable = negotiatedDataVersionMustStayStable,
        requiredVersionHeaders = requiredVersionHeaders,
        versionEndpoint = versionEndpoint,
    )
