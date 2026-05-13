package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeedPack
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecision
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionAction
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionEntityKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSources
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshParserType
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshValueSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateStatus
import com.example.shoppingassistant.domain.catalog.CatalogBrandCanon
import com.example.shoppingassistant.domain.catalog.CatalogProductFamilyCanon
import com.example.shoppingassistant.domain.catalog.CatalogModelCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceTier
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogValueCandidate
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.Locale

const val CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE: String = "TECH.PHONES"

enum class CatalogGovernanceRefreshConnectorType {
    CURATED_SEED_PACK,
    OFFICIAL_PHONE_WEB_SOURCE,
}

enum class CatalogGovernanceRefreshRunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}

enum class CatalogGovernancePublishStatus {
    PUBLISHED,
    FAILED,
    SKIPPED,
}

enum class CatalogGovernanceReviewAction {
    APPROVE,
    REJECT,
    PROMOTE,
}

data class CatalogGovernanceSourceRegistryEntry(
    val registryCode: String,
    val categoryCode: String,
    val connectorType: CatalogGovernanceRefreshConnectorType,
    val sourceCode: String,
    val externalRef: String? = null,
    val displayName: String,
    val tier: CatalogGovernanceSourceTier,
    val defaultLocale: String? = null,
    val marketCode: String? = null,
    val sourceUri: String? = null,
    val enabled: Boolean,
    val autoPublish: Boolean,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
)

data class CatalogGovernanceRefreshRun(
    val id: Long,
    val registryCode: String,
    val categoryCode: String,
    val trigger: String,
    val status: CatalogGovernanceRefreshRunStatus,
    val sourceSnapshotId: Long? = null,
    val brandsSynced: Int = 0,
    val familiesSynced: Int = 0,
    val modelsSynced: Int = 0,
    val canonicalValuesSynced: Int = 0,
    val aliasesSynced: Int = 0,
    val candidatesDetected: Int = 0,
    val reviewQueueSize: Int = 0,
    val publishStatus: CatalogGovernancePublishStatus? = null,
    val errorMessage: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val startedAt: Long,
    val finishedAt: Long? = null,
)

data class CatalogGovernancePublishEvent(
    val id: Long,
    val refreshRunId: Long? = null,
    val categoryCode: String? = null,
    val eventType: String,
    val artifactType: String,
    val entityRef: String? = null,
    val status: CatalogGovernancePublishStatus,
    val details: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
)

interface CatalogGovernanceRefreshRepository {
    suspend fun upsertSourceRegistryEntry(entry: CatalogGovernanceSourceRegistryEntry): CatalogGovernanceSourceRegistryEntry
    suspend fun listSourceRegistryEntries(
        categoryCode: String? = null,
        enabledOnly: Boolean? = null,
    ): List<CatalogGovernanceSourceRegistryEntry>

    suspend fun startRefreshRun(
        registryCode: String,
        categoryCode: String,
        trigger: String,
        metadata: Map<String, String> = emptyMap(),
    ): CatalogGovernanceRefreshRun

    suspend fun completeRefreshRun(
        runId: Long,
        sourceSnapshotId: Long? = null,
        brandsSynced: Int = 0,
        familiesSynced: Int = 0,
        modelsSynced: Int = 0,
        canonicalValuesSynced: Int = 0,
        aliasesSynced: Int = 0,
        candidatesDetected: Int = 0,
        reviewQueueSize: Int = 0,
        publishStatus: CatalogGovernancePublishStatus? = null,
        metadata: Map<String, String> = emptyMap(),
        finishedAt: Long = System.currentTimeMillis(),
    ): CatalogGovernanceRefreshRun

    suspend fun failRefreshRun(
        runId: Long,
        errorMessage: String,
        publishStatus: CatalogGovernancePublishStatus = CatalogGovernancePublishStatus.FAILED,
        metadata: Map<String, String> = emptyMap(),
        finishedAt: Long = System.currentTimeMillis(),
    ): CatalogGovernanceRefreshRun

    suspend fun listRefreshRuns(
        categoryCode: String? = null,
        limit: Int = 20,
    ): List<CatalogGovernanceRefreshRun>

    suspend fun recordPublishEvent(event: CatalogGovernancePublishEvent): CatalogGovernancePublishEvent

    suspend fun listPublishEvents(
        categoryCode: String? = null,
        limit: Int = 20,
    ): List<CatalogGovernancePublishEvent>
}

class DatabaseCatalogGovernanceRefreshRepository : CatalogGovernanceRefreshRepository {

    override suspend fun upsertSourceRegistryEntry(
        entry: CatalogGovernanceSourceRegistryEntry,
    ): CatalogGovernanceSourceRegistryEntry = DatabaseFactory.dbQuery {
        val existing = CatalogGovernanceSourceRegistryTable.selectAll()
            .singleOrNull { row ->
                row[CatalogGovernanceSourceRegistryTable.registryCode] == entry.registryCode
            }
        if (existing == null) {
            CatalogGovernanceSourceRegistryTable.insert { stmt ->
                stmt[registryCode] = entry.registryCode
                stmt[categoryCode] = entry.categoryCode
                stmt[connectorType] = entry.connectorType.name
                stmt[sourceCode] = entry.sourceCode
                stmt[externalRef] = entry.externalRef
                stmt[displayName] = entry.displayName
                stmt[tier] = entry.tier.name
                stmt[defaultLocale] = entry.defaultLocale
                stmt[marketCode] = entry.marketCode
                stmt[sourceUri] = entry.sourceUri
                stmt[enabled] = entry.enabled
                stmt[autoPublish] = entry.autoPublish
                stmt[metadata] = entry.metadata
                stmt[createdAt] = entry.createdAt
                stmt[updatedAt] = entry.updatedAt
            }
        } else {
            CatalogGovernanceSourceRegistryTable.update({
                CatalogGovernanceSourceRegistryTable.registryCode eq entry.registryCode
            }) { stmt ->
                stmt[categoryCode] = entry.categoryCode
                stmt[connectorType] = entry.connectorType.name
                stmt[sourceCode] = entry.sourceCode
                stmt[externalRef] = entry.externalRef
                stmt[displayName] = entry.displayName
                stmt[tier] = entry.tier.name
                stmt[defaultLocale] = entry.defaultLocale
                stmt[marketCode] = entry.marketCode
                stmt[sourceUri] = entry.sourceUri
                stmt[enabled] = entry.enabled
                stmt[autoPublish] = entry.autoPublish
                stmt[metadata] = entry.metadata
                stmt[createdAt] = entry.createdAt
                stmt[updatedAt] = entry.updatedAt
            }
        }
        CatalogGovernanceSourceRegistryTable.selectAll()
            .single { row -> row[CatalogGovernanceSourceRegistryTable.registryCode] == entry.registryCode }
            .toSourceRegistryEntry()
    }

    override suspend fun listSourceRegistryEntries(
        categoryCode: String?,
        enabledOnly: Boolean?,
    ): List<CatalogGovernanceSourceRegistryEntry> = DatabaseFactory.dbQuery {
        CatalogGovernanceSourceRegistryTable.selectAll()
            .apply {
                categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedCategoryCode ->
                    andWhere { CatalogGovernanceSourceRegistryTable.categoryCode eq normalizedCategoryCode }
                }
                enabledOnly?.let { onlyEnabled ->
                    andWhere { CatalogGovernanceSourceRegistryTable.enabled eq onlyEnabled }
                }
            }
            .orderBy(CatalogGovernanceSourceRegistryTable.categoryCode to SortOrder.ASC)
            .orderBy(CatalogGovernanceSourceRegistryTable.displayName to SortOrder.ASC)
            .map { it.toSourceRegistryEntry() }
    }

    override suspend fun startRefreshRun(
        registryCode: String,
        categoryCode: String,
        trigger: String,
        metadata: Map<String, String>,
    ): CatalogGovernanceRefreshRun = DatabaseFactory.dbQuery {
        CatalogGovernanceRefreshRunsTable.insert { stmt ->
            stmt[CatalogGovernanceRefreshRunsTable.registryCode] = registryCode
            stmt[CatalogGovernanceRefreshRunsTable.categoryCode] = categoryCode
            stmt[CatalogGovernanceRefreshRunsTable.trigger] = trigger.trim().ifBlank { "MANUAL" }
            stmt[status] = CatalogGovernanceRefreshRunStatus.RUNNING.name
            stmt[CatalogGovernanceRefreshRunsTable.metadata] = metadata
            stmt[startedAt] = System.currentTimeMillis()
        }.resultedValues!!.single().toRefreshRun()
    }

    override suspend fun completeRefreshRun(
        runId: Long,
        sourceSnapshotId: Long?,
        brandsSynced: Int,
        familiesSynced: Int,
        modelsSynced: Int,
        canonicalValuesSynced: Int,
        aliasesSynced: Int,
        candidatesDetected: Int,
        reviewQueueSize: Int,
        publishStatus: CatalogGovernancePublishStatus?,
        metadata: Map<String, String>,
        finishedAt: Long,
    ): CatalogGovernanceRefreshRun = DatabaseFactory.dbQuery {
        CatalogGovernanceRefreshRunsTable.update({ CatalogGovernanceRefreshRunsTable.id eq runId }) { stmt ->
            stmt[status] = CatalogGovernanceRefreshRunStatus.COMPLETED.name
            stmt[CatalogGovernanceRefreshRunsTable.sourceSnapshotId] = sourceSnapshotId
            stmt[CatalogGovernanceRefreshRunsTable.brandsSynced] = brandsSynced
            stmt[CatalogGovernanceRefreshRunsTable.familiesSynced] = familiesSynced
            stmt[CatalogGovernanceRefreshRunsTable.modelsSynced] = modelsSynced
            stmt[CatalogGovernanceRefreshRunsTable.canonicalValuesSynced] = canonicalValuesSynced
            stmt[CatalogGovernanceRefreshRunsTable.aliasesSynced] = aliasesSynced
            stmt[CatalogGovernanceRefreshRunsTable.candidatesDetected] = candidatesDetected
            stmt[CatalogGovernanceRefreshRunsTable.reviewQueueSize] = reviewQueueSize
            stmt[CatalogGovernanceRefreshRunsTable.publishStatus] = publishStatus?.name
            stmt[CatalogGovernanceRefreshRunsTable.metadata] = metadata
            stmt[CatalogGovernanceRefreshRunsTable.finishedAt] = finishedAt
        }
        CatalogGovernanceRefreshRunsTable.selectAll()
            .single { row -> row[CatalogGovernanceRefreshRunsTable.id] == runId }
            .toRefreshRun()
    }

    override suspend fun failRefreshRun(
        runId: Long,
        errorMessage: String,
        publishStatus: CatalogGovernancePublishStatus,
        metadata: Map<String, String>,
        finishedAt: Long,
    ): CatalogGovernanceRefreshRun = DatabaseFactory.dbQuery {
        CatalogGovernanceRefreshRunsTable.update({ CatalogGovernanceRefreshRunsTable.id eq runId }) { stmt ->
            stmt[status] = CatalogGovernanceRefreshRunStatus.FAILED.name
            stmt[CatalogGovernanceRefreshRunsTable.publishStatus] = publishStatus.name
            stmt[CatalogGovernanceRefreshRunsTable.errorMessage] = errorMessage.take(2000)
            stmt[CatalogGovernanceRefreshRunsTable.metadata] = metadata
            stmt[CatalogGovernanceRefreshRunsTable.finishedAt] = finishedAt
        }
        CatalogGovernanceRefreshRunsTable.selectAll()
            .single { row -> row[CatalogGovernanceRefreshRunsTable.id] == runId }
            .toRefreshRun()
    }

    override suspend fun listRefreshRuns(
        categoryCode: String?,
        limit: Int,
    ): List<CatalogGovernanceRefreshRun> = DatabaseFactory.dbQuery {
        CatalogGovernanceRefreshRunsTable.selectAll()
            .apply {
                categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedCategoryCode ->
                    andWhere { CatalogGovernanceRefreshRunsTable.categoryCode eq normalizedCategoryCode }
                }
            }
            .orderBy(CatalogGovernanceRefreshRunsTable.startedAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map { it.toRefreshRun() }
    }

    override suspend fun recordPublishEvent(
        event: CatalogGovernancePublishEvent,
    ): CatalogGovernancePublishEvent = DatabaseFactory.dbQuery {
        CatalogGovernancePublishEventsTable.insert { stmt ->
            stmt[refreshRunId] = event.refreshRunId
            stmt[categoryCode] = event.categoryCode
            stmt[eventType] = event.eventType
            stmt[artifactType] = event.artifactType
            stmt[entityRef] = event.entityRef
            stmt[status] = event.status.name
            stmt[details] = event.details
            stmt[metadata] = event.metadata
            stmt[createdAt] = event.createdAt
        }.resultedValues!!.single().toPublishEvent()
    }

    override suspend fun listPublishEvents(
        categoryCode: String?,
        limit: Int,
    ): List<CatalogGovernancePublishEvent> = DatabaseFactory.dbQuery {
        CatalogGovernancePublishEventsTable.selectAll()
            .apply {
                categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { normalizedCategoryCode ->
                    andWhere { CatalogGovernancePublishEventsTable.categoryCode eq normalizedCategoryCode }
                }
            }
            .orderBy(CatalogGovernancePublishEventsTable.createdAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .map { it.toPublishEvent() }
    }

    private fun ResultRow.toSourceRegistryEntry(): CatalogGovernanceSourceRegistryEntry =
        CatalogGovernanceSourceRegistryEntry(
            registryCode = this[CatalogGovernanceSourceRegistryTable.registryCode],
            categoryCode = this[CatalogGovernanceSourceRegistryTable.categoryCode],
            connectorType = CatalogGovernanceRefreshConnectorType.valueOf(
                this[CatalogGovernanceSourceRegistryTable.connectorType],
            ),
            sourceCode = this[CatalogGovernanceSourceRegistryTable.sourceCode],
            externalRef = this[CatalogGovernanceSourceRegistryTable.externalRef],
            displayName = this[CatalogGovernanceSourceRegistryTable.displayName],
            tier = CatalogGovernanceSourceTier.valueOf(this[CatalogGovernanceSourceRegistryTable.tier]),
            defaultLocale = this[CatalogGovernanceSourceRegistryTable.defaultLocale],
            marketCode = this[CatalogGovernanceSourceRegistryTable.marketCode],
            sourceUri = this[CatalogGovernanceSourceRegistryTable.sourceUri],
            enabled = this[CatalogGovernanceSourceRegistryTable.enabled],
            autoPublish = this[CatalogGovernanceSourceRegistryTable.autoPublish],
            metadata = this[CatalogGovernanceSourceRegistryTable.metadata],
            createdAt = this[CatalogGovernanceSourceRegistryTable.createdAt],
            updatedAt = this[CatalogGovernanceSourceRegistryTable.updatedAt],
        )

    private fun ResultRow.toRefreshRun(): CatalogGovernanceRefreshRun =
        CatalogGovernanceRefreshRun(
            id = this[CatalogGovernanceRefreshRunsTable.id],
            registryCode = this[CatalogGovernanceRefreshRunsTable.registryCode],
            categoryCode = this[CatalogGovernanceRefreshRunsTable.categoryCode],
            trigger = this[CatalogGovernanceRefreshRunsTable.trigger],
            status = CatalogGovernanceRefreshRunStatus.valueOf(this[CatalogGovernanceRefreshRunsTable.status]),
            sourceSnapshotId = this[CatalogGovernanceRefreshRunsTable.sourceSnapshotId],
            brandsSynced = this[CatalogGovernanceRefreshRunsTable.brandsSynced],
            familiesSynced = this[CatalogGovernanceRefreshRunsTable.familiesSynced],
            modelsSynced = this[CatalogGovernanceRefreshRunsTable.modelsSynced],
            canonicalValuesSynced = this[CatalogGovernanceRefreshRunsTable.canonicalValuesSynced],
            aliasesSynced = this[CatalogGovernanceRefreshRunsTable.aliasesSynced],
            candidatesDetected = this[CatalogGovernanceRefreshRunsTable.candidatesDetected],
            reviewQueueSize = this[CatalogGovernanceRefreshRunsTable.reviewQueueSize],
            publishStatus = this[CatalogGovernanceRefreshRunsTable.publishStatus]
                ?.let(CatalogGovernancePublishStatus::valueOf),
            errorMessage = this[CatalogGovernanceRefreshRunsTable.errorMessage],
            metadata = this[CatalogGovernanceRefreshRunsTable.metadata],
            startedAt = this[CatalogGovernanceRefreshRunsTable.startedAt],
            finishedAt = this[CatalogGovernanceRefreshRunsTable.finishedAt],
        )

    private fun ResultRow.toPublishEvent(): CatalogGovernancePublishEvent =
        CatalogGovernancePublishEvent(
            id = this[CatalogGovernancePublishEventsTable.id],
            refreshRunId = this[CatalogGovernancePublishEventsTable.refreshRunId],
            categoryCode = this[CatalogGovernancePublishEventsTable.categoryCode],
            eventType = this[CatalogGovernancePublishEventsTable.eventType],
            artifactType = this[CatalogGovernancePublishEventsTable.artifactType],
            entityRef = this[CatalogGovernancePublishEventsTable.entityRef],
            status = CatalogGovernancePublishStatus.valueOf(this[CatalogGovernancePublishEventsTable.status]),
            details = this[CatalogGovernancePublishEventsTable.details],
            metadata = this[CatalogGovernancePublishEventsTable.metadata],
            createdAt = this[CatalogGovernancePublishEventsTable.createdAt],
        )
}

data class CatalogGovernanceConnectorPayload(
    val packs: List<CatalogGovernanceCuratedSeedPack>,
    val metadata: Map<String, String> = emptyMap(),
)

interface CatalogGovernanceRefreshConnector {
    val connectorType: CatalogGovernanceRefreshConnectorType
    suspend fun loadPayload(entry: CatalogGovernanceSourceRegistryEntry): CatalogGovernanceConnectorPayload
}

class CatalogGovernanceCuratedSeedPackConnector : CatalogGovernanceRefreshConnector {
    override val connectorType: CatalogGovernanceRefreshConnectorType =
        CatalogGovernanceRefreshConnectorType.CURATED_SEED_PACK

    override suspend fun loadPayload(
        entry: CatalogGovernanceSourceRegistryEntry,
    ): CatalogGovernanceConnectorPayload {
        val packCode = entry.metadata["seedPackCode"]
            ?: entry.externalRef
            ?: error("Curated seed connector requires seedPackCode for '${entry.registryCode}'.")
        val pack = CatalogGovernanceCuratedSeed.snapshot.packs
            .firstOrNull { candidate -> candidate.packCode.equals(packCode, ignoreCase = true) }
            ?: error("Curated governance seed pack '$packCode' not found for '${entry.registryCode}'.")
        return CatalogGovernanceConnectorPayload(
            packs = listOf(pack),
            metadata = mapOf("seedPackCode" to pack.packCode),
        )
    }
}

interface CatalogGovernanceRefreshSurfaceService {
    suspend fun ensurePhonesSourceRegistry(): List<CatalogGovernanceSourceRegistryEntryResponse>
    suspend fun listSources(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
    ): List<CatalogGovernanceSourceRegistryEntryResponse>

    suspend fun listModelEnrichmentQueue(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        statuses: Set<CatalogPhoneModelEnrichmentStatus> = emptySet(),
        limit: Int = 50,
    ): List<CatalogGovernanceModelEnrichmentCandidateResponse>

    suspend fun promoteModelEnrichmentCandidate(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        request: CatalogGovernanceModelEnrichmentPromotionRequest,
    ): CatalogGovernanceModelEnrichmentPromotionResponse

    suspend fun getRefreshStatus(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
    ): CatalogGovernanceRefreshStatusResponse

    suspend fun listRefreshRuns(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        limit: Int = 20,
    ): List<CatalogGovernanceRefreshRunResponse>

    suspend fun listReviewQueue(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        limit: Int = 20,
    ): List<CatalogGovernanceReviewQueueItemResponse>

    suspend fun listPublishEvents(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        limit: Int = 20,
    ): List<CatalogGovernancePublishEventResponse>

    suspend fun submitReviewAction(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        candidateId: Long,
        action: CatalogGovernanceReviewAction,
        actor: String,
        reasonCode: String? = null,
    ): CatalogGovernanceReviewActionResponse

    suspend fun triggerRefresh(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        registryCodes: List<String> = emptyList(),
        trigger: String = "MANUAL",
    ): CatalogGovernanceRefreshTriggerResponse

    suspend fun triggerRebuild(
        categoryCode: String = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
        reason: String = "MANUAL_REBUILD",
    ): CatalogGovernancePublishEventResponse
}

class CatalogGovernanceRefreshSurfaceServiceImpl(
    private val refreshRepository: CatalogGovernanceRefreshRepository,
    private val governanceRepository: CatalogGovernanceRepository,
    private val workflowService: CatalogGovernanceWorkflowService,
    private val projectionService: CatalogGovernanceServingProjectionService,
    private val curatedSeedSyncService: CatalogGovernanceCuratedSeedSyncService,
    private val phoneModelEnrichmentService: CatalogPhoneModelEnrichmentService,
    private val endpointOverlayRepository: CatalogGovernanceOfficialPhoneEndpointOverlayRepository =
        NoopCatalogGovernanceOfficialPhoneEndpointOverlayRepository,
    private val refreshConfig: CatalogGovernanceRefreshConfig,
    connectors: List<CatalogGovernanceRefreshConnector>,
) : CatalogGovernanceRefreshSurfaceService {
    private val connectorsByType = connectors.associateBy { it.connectorType }

    override suspend fun ensurePhonesSourceRegistry(): List<CatalogGovernanceSourceRegistryEntryResponse> {
        val now = System.currentTimeMillis()
        val curatedEntries = CatalogGovernanceCuratedSeed.snapshot.packs
            .filter { pack -> pack.appliesToCategory(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE) }
            .map { pack ->
                refreshRepository.upsertSourceRegistryEntry(
                    CatalogGovernanceSourceRegistryEntry(
                        registryCode = buildRegistryCode(
                            categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                            connectorType = CatalogGovernanceRefreshConnectorType.CURATED_SEED_PACK,
                            externalRef = pack.packCode,
                        ),
                        categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                        connectorType = CatalogGovernanceRefreshConnectorType.CURATED_SEED_PACK,
                        sourceCode = pack.sourceCode,
                        externalRef = pack.packCode,
                        displayName = pack.displayName,
                        tier = pack.tier,
                        defaultLocale = pack.defaultLocale,
                        marketCode = pack.marketCode,
                        sourceUri = pack.sourceUri,
                        enabled = true,
                        autoPublish = true,
                        metadata = pack.metadata + mapOf(
                            "seedPackCode" to pack.packCode,
                            "bootstrapCategoryCode" to CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                        ),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
        }
        val officialEntries = buildList {
            CatalogGovernanceOfficialRefreshSources.resolve(
                CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
            ).forEach { source ->
                val overlayEndpointCount = endpointOverlayRepository.listEndpoints(
                    categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                    sourceCode = source.sourceCode,
                ).size
                add(
                    refreshRepository.upsertSourceRegistryEntry(
                        CatalogGovernanceSourceRegistryEntry(
                            registryCode = buildRegistryCode(
                                categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                                connectorType = CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE,
                                externalRef = source.sourceCode,
                            ),
                            categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                            connectorType = CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE,
                            sourceCode = source.sourceCode,
                            externalRef = source.sourceCode,
                            displayName = source.displayName,
                            tier = source.tier,
                            defaultLocale = source.defaultLocale,
                            marketCode = source.marketCode,
                            sourceUri = source.sourceUri,
                            enabled = true,
                            autoPublish = true,
                            metadata = source.metadata + mapOf(
                                "officialSourceCode" to source.sourceCode,
                                "officialBrandCode" to source.brandCode,
                                "declaredEndpointCount" to source.endpoints.size.toString(),
                                "overlayEndpointCount" to overlayEndpointCount.toString(),
                                "endpointCount" to (source.endpoints.size + overlayEndpointCount).toString(),
                                "bootstrapCategoryCode" to CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
                            ),
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                )
            }
        }
        return (curatedEntries + officialEntries)
            .distinctBy { it.registryCode }
            .sortedBy { it.displayName }
            .map { it.toResponse() }
    }

    override suspend fun listSources(
        categoryCode: String,
    ): List<CatalogGovernanceSourceRegistryEntryResponse> {
        maybeEnsurePhonesRegistry(categoryCode)
        return refreshRepository.listSourceRegistryEntries(categoryCode = categoryCode)
            .map { it.toResponse() }
    }

    override suspend fun listModelEnrichmentQueue(
        categoryCode: String,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogGovernanceModelEnrichmentCandidateResponse> =
        phoneModelEnrichmentService.listCandidates(
            categoryCode = categoryCode,
            statuses = statuses,
            limit = limit,
        ).map { candidate ->
            val promotionHints = CatalogPhoneModelEnrichmentPromotionHintsResolver.resolve(candidate)
            CatalogGovernanceModelEnrichmentCandidateResponse(
                id = candidate.id ?: 0L,
                candidateKey = candidate.candidateKey,
                categoryCode = candidate.categoryCode,
                brandRaw = candidate.brandRaw,
                brandCode = candidate.brandCode,
                familyRaw = candidate.familyRaw,
                familyCode = candidate.familyCode,
                canonicalModelCode = candidate.canonicalModelCode,
                modelRaw = candidate.modelRaw,
                modelNormalized = candidate.modelNormalized,
                officialSourceCode = candidate.officialSourceCode,
                officialEndpointCode = candidate.officialEndpointCode,
                status = candidate.status.name,
                observedCount = candidate.observedCount,
                distinctSellerCount = candidate.distinctSellerCount,
                sellerRefs = candidate.sellerRefs,
                sampleOfferRefs = candidate.sampleOfferRefs,
                maxConfidence = candidate.maxConfidence,
                reasonCodes = candidate.reasonCodes,
                metadata = candidate.metadata,
                suggestedSourceUri = promotionHints.suggestedSourceUri,
                suggestedParserType = promotionHints.suggestedParserType?.name,
                sourceUriSuggestionConfidence = promotionHints.sourceUriSuggestionConfidence,
                sourceUriSuggestionReason = promotionHints.sourceUriSuggestionReason,
                firstSeenAt = candidate.firstSeenAt,
                lastSeenAt = candidate.lastSeenAt,
                createdAt = candidate.createdAt,
                updatedAt = candidate.updatedAt,
            )
        }

    override suspend fun promoteModelEnrichmentCandidate(
        categoryCode: String,
        request: CatalogGovernanceModelEnrichmentPromotionRequest,
    ): CatalogGovernanceModelEnrichmentPromotionResponse {
        val candidateId = request.candidateId
        require(candidateId > 0L) { "candidateId must be a positive integer." }
        val candidate = phoneModelEnrichmentService.getCandidate(candidateId)
            ?: error("Model enrichment candidate id=$candidateId not found.")
        check(candidate.categoryCode.equals(categoryCode, ignoreCase = true)) {
            "Model enrichment candidate id=$candidateId does not belong to category '$categoryCode'."
        }
        check(candidate.status == CatalogPhoneModelEnrichmentStatus.READY_FOR_OFFICIAL_ENRICHMENT) {
            "Only READY_FOR_OFFICIAL_ENRICHMENT candidates can be promoted."
        }
        val sourceCode = candidate.officialSourceCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Candidate id=$candidateId has no official source.")
        val brandCode = candidate.brandCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Candidate id=$candidateId has unresolved brand.")
        val actor = request.actor.trim().ifBlank { "manual_operator" }
        val promotionHints = CatalogPhoneModelEnrichmentPromotionHintsResolver.resolve(candidate)
        val sourceUri = request.sourceUri
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: promotionHints.suggestedSourceUri
                ?.takeIf { promotionHints.sourceUriSuggestionConfidence == "HIGH" }
            ?: error("sourceUri is required for model enrichment promotion.")
        val requestedParserType = runCatching {
            CatalogGovernanceOfficialRefreshParserType.valueOf(
                request.parserType.trim().uppercase(Locale.ROOT),
            )
        }.getOrElse {
            error("parserType must be a valid CatalogGovernanceOfficialRefreshParserType value.")
        }
        val parserType = when {
            promotionHints.suggestedParserType == null -> requestedParserType
            requestedParserType == CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE ->
                promotionHints.suggestedParserType
            else -> requestedParserType
        }
        val familyLabel = request.modelLine?.trim()?.takeIf { it.isNotEmpty() }
            ?: candidate.familyRaw?.trim()?.takeIf { it.isNotEmpty() }
            ?: candidate.modelRaw
        val familyCode = request.familyCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: candidate.familyCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: generateFamilyCode(brandCode = brandCode, label = familyLabel)
        val modelLabel = request.modelLabel?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.modelRaw
        val modelCode = request.modelCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: candidate.canonicalModelCode?.trim()?.takeIf { it.isNotEmpty() }
            ?: generateModelCode(brandCode = brandCode, label = modelLabel)
        val endpointCode = request.endpointCode?.trim()?.takeIf { it.isNotEmpty() } ?: modelCode
        val releaseYear = request.releaseYear ?: request.releaseDate
            ?.takeIf { it.length >= 4 }
            ?.substring(0, 4)
            ?.toIntOrNull()
        upsertPromotionSupportEntities(
            categoryCode = candidate.categoryCode,
            brandCode = brandCode,
            brandLabel = candidate.brandRaw,
            familyCode = familyCode,
            familyLabel = familyLabel,
            modelCode = modelCode,
            modelLabel = modelLabel,
            releaseYear = releaseYear,
            actor = actor,
        )
        val overlay = endpointOverlayRepository.upsertEndpoint(
            CatalogGovernanceOfficialPhoneEndpointOverlay(
                candidateId = candidate.id,
                categoryCode = candidate.categoryCode,
                sourceCode = sourceCode,
                brandCode = brandCode,
                endpointCode = endpointCode,
                parserType = parserType,
                sourceUri = sourceUri,
                familyCode = familyCode,
                modelCode = modelCode,
                modelLabel = modelLabel,
                releaseYear = releaseYear,
                releaseDate = request.releaseDate?.trim()?.ifEmpty { null },
                aliases = buildEndpointAliases(candidate = candidate, modelLabel = modelLabel),
                fixedValues = request.fixedValues.entries
                    .mapNotNull { (attributeCode, rawValue) ->
                        val normalizedAttributeCode = attributeCode.trim().lowercase(Locale.ROOT)
                        val normalizedRawValue = rawValue.trim()
                        if (normalizedAttributeCode.isBlank() || normalizedRawValue.isBlank()) {
                            null
                        } else {
                            CatalogGovernanceOfficialRefreshValueSeed(
                                attributeCode = normalizedAttributeCode,
                                rawValue = normalizedRawValue,
                            )
                        }
                    },
                metadata = buildMap {
                    put("seededBy", actor)
                    put("seededFromCandidateId", candidateId.toString())
                    put("seededFromStatus", candidate.status.name)
                    put("seededAt", System.currentTimeMillis().toString())
                    if (familyLabel.isNotBlank()) put("modelLine", familyLabel)
                },
                createdBy = actor,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        phoneModelEnrichmentService.markOfficiallySeeded(
            candidateId = candidateId,
            officialSourceCode = sourceCode,
            officialEndpointCode = overlay.endpointCode,
            metadata = mapOf(
                "promotedSourceUri" to overlay.sourceUri,
                "promotedBy" to actor,
                "promotedEndpointCode" to overlay.endpointCode,
            ),
        )
        val registryCode = buildRegistryCode(
            categoryCode = candidate.categoryCode,
            connectorType = CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE,
            externalRef = sourceCode,
        )
        refreshRepository.recordPublishEvent(
            CatalogGovernancePublishEvent(
                id = 0,
                categoryCode = candidate.categoryCode,
                eventType = "MODEL_ENRICHMENT_PROMOTED",
                artifactType = "OFFICIAL_REFRESH_ENDPOINT",
                entityRef = overlay.endpointCode,
                status = CatalogGovernancePublishStatus.PUBLISHED,
                details = "source=$sourceCode,endpoint=${overlay.endpointCode},model=${overlay.modelCode}",
                metadata = mapOf(
                    "candidateId" to candidateId.toString(),
                    "registryCode" to registryCode,
                    "sourceCode" to sourceCode,
                    "endpointCode" to overlay.endpointCode,
                    "modelCode" to overlay.modelCode,
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val refreshResult = if (request.triggerRefresh) {
            triggerRefresh(
                categoryCode = candidate.categoryCode,
                registryCodes = listOf(registryCode),
                trigger = "MODEL_ENRICHMENT_PROMOTION",
            )
        } else {
            CatalogGovernanceRefreshTriggerResponse(
                categoryCode = candidate.categoryCode,
                requestedRegistryCodes = listOf(registryCode),
                runs = emptyList(),
            )
        }
        return CatalogGovernanceModelEnrichmentPromotionResponse(
            categoryCode = candidate.categoryCode,
            candidateId = candidateId,
            action = "PROMOTE_TO_OFFICIAL_SOURCE",
            candidateStatus = CatalogPhoneModelEnrichmentStatus.ENRICHED.name,
            sourceCode = sourceCode,
            registryCode = registryCode,
            endpointCode = overlay.endpointCode,
            modelCode = overlay.modelCode,
            familyCode = overlay.familyCode,
            parserType = overlay.parserType.name,
            triggerRefresh = request.triggerRefresh,
            runs = refreshResult.runs,
            metadata = overlay.metadata + mapOf(
                "sourceUri" to overlay.sourceUri,
                "brandCode" to overlay.brandCode,
                "sourceUriSuggestionConfidence" to (promotionHints.sourceUriSuggestionConfidence ?: ""),
            ),
        )
    }

    override suspend fun getRefreshStatus(
        categoryCode: String,
    ): CatalogGovernanceRefreshStatusResponse {
        val effectiveCategoryCode = categoryCode.trim().ifBlank { refreshConfig.categoryCode }
        val sources = listSources(categoryCode = effectiveCategoryCode)
        val selection = refreshConfig.selectScheduledSources(sources)
        val latestRun = refreshRepository.listRefreshRuns(
            categoryCode = effectiveCategoryCode,
            limit = 1,
        ).firstOrNull()
        return CatalogGovernanceRefreshStatusResponse(
            enabled = refreshConfig.enabled,
            configuredCategoryCode = refreshConfig.categoryCode,
            effectiveCategoryCode = effectiveCategoryCode,
            pollIntervalMs = refreshConfig.pollIntervalMs,
            trigger = refreshConfig.trigger,
            connectorTypes = refreshConfig.connectorTypes.map { it.name }.sorted(),
            registryCodeAllowlist = refreshConfig.registryCodeAllowlist.sorted(),
            availableSourceCount = selection.availableSourceCount,
            matchedSourceCount = selection.matchedSources.size,
            matchedRegistryCodes = selection.matchedSources.map { it.registryCode }.sorted(),
            skippedReason = selection.skippedReason,
            latestRunId = latestRun?.id,
            latestRunRegistryCode = latestRun?.registryCode,
            latestRunStatus = latestRun?.status?.name,
            latestRunPublishStatus = latestRun?.publishStatus?.name,
            latestRunStartedAt = latestRun?.startedAt,
            latestRunFinishedAt = latestRun?.finishedAt,
        )
    }

    override suspend fun listRefreshRuns(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceRefreshRunResponse> =
        refreshRepository.listRefreshRuns(categoryCode = categoryCode, limit = limit)
            .map { it.toResponse() }

    override suspend fun listReviewQueue(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceReviewQueueItemResponse> =
        workflowService.listCandidateClusters()
            .asSequence()
            .filter { cluster -> cluster.scope.categoryCode.equals(categoryCode, ignoreCase = true) }
            .filter { cluster ->
                cluster.recommendation == com.example.shoppingassistant.domain.catalog.CatalogGovernanceClusterRecommendation.REVIEW_ALIAS ||
                    cluster.recommendation == com.example.shoppingassistant.domain.catalog.CatalogGovernanceClusterRecommendation.REVIEW_CANONICAL ||
                    cluster.recommendation == com.example.shoppingassistant.domain.catalog.CatalogGovernanceClusterRecommendation.REJECT
            }
            .take(limit.coerceIn(1, 200))
            .map { cluster ->
                CatalogGovernanceReviewQueueItemResponse(
                    clusterKey = cluster.clusterKey,
                    categoryCode = cluster.scope.categoryCode,
                    attributeCode = cluster.attributeCode,
                    locale = cluster.locale,
                    marketCode = cluster.marketCode,
                    normalizedValue = cluster.normalizedValue,
                    primaryCandidateId = cluster.candidateIds.firstOrNull(),
                    recommendation = cluster.recommendation.name,
                    recommendedDisposition = cluster.recommendedDisposition.name,
                    existingTargetCode = cluster.existingMatch?.targetCode,
                    totalScore = cluster.evidenceScore.totalScore,
                    observedCount = cluster.evidenceScore.observedCount,
                    candidateCount = cluster.candidateIds.size,
                    candidateIds = cluster.candidateIds,
                    candidateStatuses = cluster.candidateStatuses.map { it.name },
                    reasons = cluster.reasons,
                )
            }
            .toList()

    override suspend fun listPublishEvents(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernancePublishEventResponse> =
        refreshRepository.listPublishEvents(categoryCode = categoryCode, limit = limit)
            .map { it.toResponse() }

    override suspend fun submitReviewAction(
        categoryCode: String,
        candidateId: Long,
        action: CatalogGovernanceReviewAction,
        actor: String,
        reasonCode: String?,
    ): CatalogGovernanceReviewActionResponse {
        val candidate = findCandidateById(candidateId)
            ?: error("Candidate id=$candidateId not found.")
        check(
            categoryCode.equals(
                candidate.scope.categoryCode ?: categoryCode,
                ignoreCase = true,
            ),
        ) {
            "Candidate id=$candidateId does not belong to category '$categoryCode'."
        }
        val normalizedReasonCode = reasonCode?.trim()?.ifBlank { null }
        return when (action) {
            CatalogGovernanceReviewAction.PROMOTE -> {
                val result = projectionService.promoteCandidate(
                    candidateId = candidateId,
                    actor = actor.trim().ifBlank { "system" },
                    reasonCode = normalizedReasonCode ?: "review_promote",
                )
                refreshRepository.recordPublishEvent(
                    CatalogGovernancePublishEvent(
                        id = 0,
                        categoryCode = categoryCode,
                        eventType = "REVIEW_PROMOTE",
                        artifactType = "SERVING_ARTIFACTS",
                        entityRef = candidateId.toString(),
                        status = CatalogGovernancePublishStatus.PUBLISHED,
                        details = "candidate_promoted",
                        metadata = mapOf(
                            "candidateId" to candidateId.toString(),
                            "canonicalCode" to result.canonicalValue.canonicalCode,
                        ),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                CatalogGovernanceReviewActionResponse(
                    categoryCode = result.candidate.scope.categoryCode,
                    candidateId = candidateId,
                    attributeCode = result.candidate.attributeCode,
                    action = action.name,
                    candidateStatus = result.candidate.status.name,
                    decisionId = result.decision.id ?: 0L,
                    decisionAction = result.decision.action.name,
                    reasonCode = result.decision.reasonCode,
                    actor = result.decision.actor,
                    publishStatus = CatalogGovernancePublishStatus.PUBLISHED.name,
                    canonicalCode = result.canonicalValue.canonicalCode,
                    metadata = mapOf(
                        "decisionEntityRef" to result.decision.entityRef,
                        "aliasCreated" to (result.alias != null).toString(),
                    ),
                )
            }

            CatalogGovernanceReviewAction.APPROVE,
            CatalogGovernanceReviewAction.REJECT,
            -> updateCandidateDecision(
                candidate = candidate,
                action = action,
                actor = actor,
                reasonCode = normalizedReasonCode,
            )
        }
    }

    override suspend fun triggerRefresh(
        categoryCode: String,
        registryCodes: List<String>,
        trigger: String,
    ): CatalogGovernanceRefreshTriggerResponse {
        maybeEnsurePhonesRegistry(categoryCode)
        val availableEntries = refreshRepository.listSourceRegistryEntries(
            categoryCode = categoryCode,
            enabledOnly = true,
        )
        val selectedEntries = if (registryCodes.isEmpty()) {
            availableEntries
        } else {
            val requested = registryCodes.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            availableEntries.filter { it.registryCode in requested }
        }
        val runs = selectedEntries.map { entry ->
            runRefresh(entry = entry, trigger = trigger)
        }
        return CatalogGovernanceRefreshTriggerResponse(
            categoryCode = categoryCode,
            requestedRegistryCodes = registryCodes,
            runs = runs.map { it.toResponse() },
        )
    }

    override suspend fun triggerRebuild(
        categoryCode: String,
        reason: String,
    ): CatalogGovernancePublishEventResponse {
        return try {
            DatabaseFactory.dbQuery {
                CatalogSeeder.syncRuntimeContracts(syncMode = CatalogSeedSyncMode.UPSERT_ONLY)
            }
            val artifacts = projectionService.syncDatabaseServingArtifacts(
                syncMode = CatalogSeedSyncMode.UPSERT_ONLY,
            )
            refreshRepository.recordPublishEvent(
                CatalogGovernancePublishEvent(
                    id = 0,
                    categoryCode = categoryCode,
                    eventType = reason.ifBlank { "MANUAL_REBUILD" },
                    artifactType = "SERVING_ARTIFACTS",
                    entityRef = categoryCode,
                    status = CatalogGovernancePublishStatus.PUBLISHED,
                    details = "attributes=${artifacts.report.projectedAttributes},families=${artifacts.report.projectedProductFamilies}",
                    metadata = mapOf(
                        "projectedAttributes" to artifacts.report.projectedAttributes.toString(),
                        "projectedProductFamilies" to artifacts.report.projectedProductFamilies.toString(),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            ).toResponse()
        } catch (throwable: Throwable) {
            refreshRepository.recordPublishEvent(
                CatalogGovernancePublishEvent(
                    id = 0,
                    categoryCode = categoryCode,
                    eventType = reason.ifBlank { "MANUAL_REBUILD" },
                    artifactType = "SERVING_ARTIFACTS",
                    entityRef = categoryCode,
                    status = CatalogGovernancePublishStatus.FAILED,
                    details = throwable.message?.take(2000),
                    createdAt = System.currentTimeMillis(),
                ),
            ).toResponse()
        }
    }

    private suspend fun runRefresh(
        entry: CatalogGovernanceSourceRegistryEntry,
        trigger: String,
    ): CatalogGovernanceRefreshRun {
        val run = refreshRepository.startRefreshRun(
            registryCode = entry.registryCode,
            categoryCode = entry.categoryCode,
            trigger = trigger.ifBlank { "MANUAL" },
            metadata = mapOf(
                "connectorType" to entry.connectorType.name,
                "autoPublish" to entry.autoPublish.toString(),
            ),
        )
        return try {
            val connector = connectorsByType[entry.connectorType]
                ?: error("Connector '${entry.connectorType}' is not registered.")
            val payload = connector.loadPayload(entry)
            val report = curatedSeedSyncService.syncPacks(
                packs = payload.packs,
                syncMode = CatalogSeedSyncMode.UPSERT_ONLY,
                publishArtifacts = entry.autoPublish,
            )
            val reviewQueueSize = listReviewQueue(
                categoryCode = entry.categoryCode,
                limit = 500,
            ).size
            val publishStatus = if (entry.autoPublish) {
                CatalogGovernancePublishStatus.PUBLISHED
            } else {
                CatalogGovernancePublishStatus.SKIPPED
            }
            val completed = refreshRepository.completeRefreshRun(
                runId = run.id,
                sourceSnapshotId = report.sourceSnapshotIds.lastOrNull(),
                brandsSynced = report.brandsSynced,
                familiesSynced = report.familiesSynced,
                modelsSynced = report.modelsSynced,
                canonicalValuesSynced = report.canonicalValuesSynced,
                aliasesSynced = report.aliasesSynced,
                candidatesDetected = 0,
                reviewQueueSize = reviewQueueSize,
                publishStatus = publishStatus,
                metadata = payload.metadata + mapOf(
                    "packCodes" to report.packCodes.joinToString(","),
                    "packsSynced" to report.packsSynced.toString(),
                ),
            )
            refreshRepository.recordPublishEvent(
                CatalogGovernancePublishEvent(
                    id = 0,
                    refreshRunId = completed.id,
                    categoryCode = entry.categoryCode,
                    eventType = "REFRESH_SYNC",
                    artifactType = if (entry.autoPublish) "SERVING_ARTIFACTS" else "GOVERNANCE_CORE",
                    entityRef = entry.registryCode,
                    status = publishStatus,
                    details = "packs=${report.packCodes.joinToString(",")}",
                    metadata = mapOf(
                        "registryCode" to entry.registryCode,
                        "packs" to report.packCodes.joinToString(","),
                    ),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (entry.autoPublish) {
                refreshRepository.recordPublishEvent(
                    CatalogGovernancePublishEvent(
                        id = 0,
                        refreshRunId = completed.id,
                        categoryCode = entry.categoryCode,
                        eventType = "REFRESH_RUNTIME_INVALIDATION",
                        artifactType = "RUNTIME_INVALIDATION",
                        entityRef = entry.registryCode,
                        status = CatalogGovernancePublishStatus.PUBLISHED,
                        metadata = mapOf("registryCode" to entry.registryCode),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            completed
        } catch (throwable: Throwable) {
            val failed = refreshRepository.failRefreshRun(
                runId = run.id,
                errorMessage = throwable.message ?: throwable::class.simpleName.orEmpty(),
                metadata = mapOf("registryCode" to entry.registryCode),
            )
            refreshRepository.recordPublishEvent(
                CatalogGovernancePublishEvent(
                    id = 0,
                    refreshRunId = failed.id,
                    categoryCode = entry.categoryCode,
                    eventType = "REFRESH_SYNC",
                    artifactType = "SERVING_ARTIFACTS",
                    entityRef = entry.registryCode,
                    status = CatalogGovernancePublishStatus.FAILED,
                    details = throwable.message?.take(2000),
                    metadata = mapOf("registryCode" to entry.registryCode),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            failed
        }
    }

    private suspend fun updateCandidateDecision(
        candidate: CatalogValueCandidate,
        action: CatalogGovernanceReviewAction,
        actor: String,
        reasonCode: String?,
    ): CatalogGovernanceReviewActionResponse {
        val now = System.currentTimeMillis()
        val targetStatus = when (action) {
            CatalogGovernanceReviewAction.APPROVE -> CatalogGovernanceCandidateStatus.APPROVED
            CatalogGovernanceReviewAction.REJECT -> CatalogGovernanceCandidateStatus.REJECTED
            CatalogGovernanceReviewAction.PROMOTE -> error("PROMOTE is handled separately.")
        }
        val updatedCandidate = governanceRepository.submitCandidate(
            candidate.copy(
                status = targetStatus,
                updatedAt = now,
            ),
        )
        val decision = governanceRepository.recordDecision(
            CatalogGovernanceDecision(
                entityKind = CatalogGovernanceDecisionEntityKind.VALUE_CANDIDATE,
                entityRef = updatedCandidate.id?.toString() ?: candidate.id?.toString().orEmpty(),
                action = when (action) {
                    CatalogGovernanceReviewAction.APPROVE -> CatalogGovernanceDecisionAction.APPROVE
                    CatalogGovernanceReviewAction.REJECT -> CatalogGovernanceDecisionAction.REJECT
                    CatalogGovernanceReviewAction.PROMOTE -> CatalogGovernanceDecisionAction.PROMOTE
                },
                reasonCode = reasonCode ?: "review_${action.name.lowercase()}",
                actor = actor.trim().ifBlank { "system" },
                payload = mapOf(
                    "attributeCode" to updatedCandidate.attributeCode,
                    "normalizedValue" to updatedCandidate.normalizedValue,
                ),
                createdAt = now,
            ),
        )
        val publishStatus = CatalogGovernancePublishStatus.SKIPPED
        refreshRepository.recordPublishEvent(
            CatalogGovernancePublishEvent(
                id = 0,
                categoryCode = updatedCandidate.scope.categoryCode,
                eventType = "REVIEW_${action.name}",
                artifactType = "REVIEW_QUEUE",
                entityRef = updatedCandidate.id?.toString(),
                status = publishStatus,
                details = updatedCandidate.status.name,
                metadata = mapOf(
                    "candidateId" to (updatedCandidate.id?.toString() ?: ""),
                    "attributeCode" to updatedCandidate.attributeCode,
                ),
                createdAt = now,
            ),
        )
        return CatalogGovernanceReviewActionResponse(
            categoryCode = updatedCandidate.scope.categoryCode,
            candidateId = updatedCandidate.id ?: candidate.id ?: 0L,
            attributeCode = updatedCandidate.attributeCode,
            action = action.name,
            candidateStatus = updatedCandidate.status.name,
            decisionId = decision.id ?: 0L,
            decisionAction = decision.action.name,
            reasonCode = decision.reasonCode,
            actor = decision.actor,
            publishStatus = publishStatus.name,
            metadata = mapOf("normalizedValue" to updatedCandidate.normalizedValue),
        )
    }

    private suspend fun findCandidateById(candidateId: Long): CatalogValueCandidate? =
        governanceRepository.listCandidates(attributeCode = null, status = null)
            .firstOrNull { candidate -> candidate.id == candidateId }

    private suspend fun maybeEnsurePhonesRegistry(categoryCode: String) {
        if (categoryCode.equals(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE, ignoreCase = true)) {
            ensurePhonesSourceRegistry()
        }
    }

    private suspend fun upsertPromotionSupportEntities(
        categoryCode: String,
        brandCode: String,
        brandLabel: String,
        familyCode: String,
        familyLabel: String,
        modelCode: String,
        modelLabel: String,
        releaseYear: Int?,
        actor: String,
    ) {
        val now = System.currentTimeMillis()
        governanceRepository.upsertBrand(
            CatalogBrandCanon(
                code = brandCode,
                labels = localizedTextOf("en" to brandLabel),
                normalizedKey = normalizeGovernanceKey(brandLabel),
                status = CatalogGovernanceEntityStatus.ACTIVE,
                primaryCategoryCode = categoryCode,
                metadata = mapOf(
                    "createdBy" to actor,
                    "source" to "model_enrichment_promotion",
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )
        governanceRepository.upsertProductFamily(
            CatalogProductFamilyCanon(
                code = familyCode,
                brandCode = brandCode,
                labels = localizedTextOf("en" to familyLabel),
                normalizedKey = normalizeGovernanceKey(familyLabel),
                prettyModelPrefix = familyLabel,
                defaultCategoryCode = categoryCode,
                status = CatalogGovernanceEntityStatus.ACTIVE,
                metadata = mapOf(
                    "createdBy" to actor,
                    "source" to "model_enrichment_promotion",
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )
        governanceRepository.upsertModel(
            CatalogModelCanon(
                code = modelCode,
                brandCode = brandCode,
                familyCode = familyCode,
                labels = localizedTextOf("en" to modelLabel),
                normalizedKey = normalizeGovernanceKey(modelLabel),
                defaultCategoryCode = categoryCode,
                releaseYear = releaseYear,
                status = CatalogGovernanceEntityStatus.ACTIVE,
                metadata = mapOf(
                    "createdBy" to actor,
                    "source" to "model_enrichment_promotion",
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun buildEndpointAliases(
        candidate: CatalogPhoneModelEnrichmentCandidate,
        modelLabel: String,
    ): Map<String, List<String>> =
        mapOf(
            "en" to listOfNotNull(
                candidate.modelRaw.trim().takeIf { it.isNotEmpty() },
                modelLabel.trim().takeIf { it.isNotEmpty() },
                candidate.metadata["sampleTitle"]?.trim()?.takeIf { it.isNotEmpty() },
            ).distinct(),
        ).filterValues { values -> values.isNotEmpty() }

    private fun generateFamilyCode(brandCode: String, label: String): String =
        listOf(brandCode.trim().uppercase(Locale.ROOT), normalizeGovernanceCodeToken(label))
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .take(64)

    private fun generateModelCode(brandCode: String, label: String): String =
        listOf(brandCode.trim().uppercase(Locale.ROOT), normalizeGovernanceCodeToken(label))
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .take(96)

    private fun normalizeGovernanceKey(raw: String): String =
        SearchTextNormalizer.normalize(raw)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun normalizeGovernanceCodeToken(raw: String): String =
        SearchTextNormalizer.normalize(raw)
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifEmpty { "UNKNOWN" }

    private fun buildRegistryCode(
        categoryCode: String,
        connectorType: CatalogGovernanceRefreshConnectorType,
        externalRef: String?,
    ): String = listOf(
        categoryCode.trim().uppercase(),
        connectorType.name,
        externalRef?.trim()?.uppercase().orEmpty(),
    ).filter { it.isNotEmpty() }
        .joinToString("|")
        .take(96)

    private fun CatalogGovernanceSourceRegistryEntry.toResponse(): CatalogGovernanceSourceRegistryEntryResponse =
        CatalogGovernanceSourceRegistryEntryResponse(
            registryCode = registryCode,
            categoryCode = categoryCode,
            connectorType = connectorType.name,
            sourceCode = sourceCode,
            externalRef = externalRef,
            displayName = displayName,
            tier = tier.name,
            defaultLocale = defaultLocale,
            marketCode = marketCode,
            sourceUri = sourceUri,
            enabled = enabled,
            autoPublish = autoPublish,
            metadata = metadata,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun CatalogGovernanceRefreshRun.toResponse(): CatalogGovernanceRefreshRunResponse =
        CatalogGovernanceRefreshRunResponse(
            id = id,
            registryCode = registryCode,
            categoryCode = categoryCode,
            trigger = trigger,
            status = status.name,
            sourceSnapshotId = sourceSnapshotId,
            brandsSynced = brandsSynced,
            familiesSynced = familiesSynced,
            modelsSynced = modelsSynced,
            canonicalValuesSynced = canonicalValuesSynced,
            aliasesSynced = aliasesSynced,
            candidatesDetected = candidatesDetected,
            reviewQueueSize = reviewQueueSize,
            publishStatus = publishStatus?.name,
            errorMessage = errorMessage,
            metadata = metadata,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )

    private fun CatalogGovernancePublishEvent.toResponse(): CatalogGovernancePublishEventResponse =
        CatalogGovernancePublishEventResponse(
            id = id,
            refreshRunId = refreshRunId,
            categoryCode = categoryCode,
            eventType = eventType,
            artifactType = artifactType,
            entityRef = entityRef,
            status = status.name,
            details = details,
            metadata = metadata,
            createdAt = createdAt,
        )

    private fun CatalogGovernanceCuratedSeedPack.appliesToCategory(categoryCode: String): Boolean {
        val normalizedCategoryCode = categoryCode.trim().uppercase()
        val brandCodes = brands
            .filter { seed -> seed.primaryCategoryCode == normalizedCategoryCode }
            .map { seed -> seed.code }
            .toSet()
        val familyCodes = families
            .filter { seed ->
                seed.defaultCategoryCode == normalizedCategoryCode || seed.brandCode in brandCodes
            }
            .map { seed -> seed.code }
            .toSet()
        val modelCodes = models
            .filter { seed ->
                seed.defaultCategoryCode == normalizedCategoryCode ||
                    seed.brandCode in brandCodes ||
                    seed.familyCode in familyCodes
            }
            .map { seed -> seed.code }
            .toSet()
        return brandCodes.isNotEmpty() ||
            familyCodes.isNotEmpty() ||
            modelCodes.isNotEmpty() ||
            canonicalValues.any { value ->
                value.categoryCode == normalizedCategoryCode ||
                    value.brandCode in brandCodes ||
                    value.familyCode in familyCodes ||
                    value.modelCode in modelCodes
            }
    }
}
