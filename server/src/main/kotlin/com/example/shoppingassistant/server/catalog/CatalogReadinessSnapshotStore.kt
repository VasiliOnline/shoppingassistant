package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.count
import java.util.Locale

internal data class CatalogPersistedReadinessSnapshot(
    val snapshotDate: LocalDate,
    val category: Category,
    val readiness: CatalogCategoryReadiness,
    val editorialReadiness: CatalogCategoryReadiness,
    val operationalReadiness: CatalogCategoryReadiness,
    val completenessGatePassed: Boolean,
    val blockingIssues: List<String>,
    val editorialBlockingIssues: List<String>,
    val operationalBlockingIssues: List<String>,
    val operationalSampleCount: Int,
    val operationalDroppedRate: Double,
    val operationalUnknownAttributeRate: Double,
    val operationalRequiredMissingRate: Double,
    val operationalLowConfidenceRate: Double,
)

internal suspend fun persistReadinessInventorySnapshots(
    snapshotDate: LocalDate,
    inventory: List<CatalogReadinessInventoryItem>,
) {
    if (inventory.isEmpty()) return
    val capturedAt = System.currentTimeMillis()
    DatabaseFactory.dbQuery {
        inventory.forEach { item ->
            val updated = CatalogReadinessSnapshotsTable.update({
                (CatalogReadinessSnapshotsTable.snapshotDate eq snapshotDate) and
                    (CatalogReadinessSnapshotsTable.categoryCode eq item.category.code)
            }) { stmt ->
                bindReadinessSnapshot(stmt, item, capturedAt)
            }
            if (updated == 0) {
                CatalogReadinessSnapshotsTable.insert { stmt ->
                    stmt[CatalogReadinessSnapshotsTable.snapshotDate] = snapshotDate
                    stmt[CatalogReadinessSnapshotsTable.categoryCode] = item.category.code
                    bindReadinessSnapshot(stmt, item, capturedAt)
                }
            }
        }
    }
}

internal suspend fun loadPersistedReadinessHistory(
    categoryCode: String,
    days: Int,
): List<CatalogPersistedReadinessSnapshot> {
    val normalizedCategoryCode = categoryCode.trim()
    if (normalizedCategoryCode.isEmpty()) return emptyList()
    val normalizedDays = days.coerceAtLeast(1)
    val windowStart = Clock.System.now()
        .toLocalDateTime(TimeZone.UTC)
        .date
        .minus(DatePeriod(days = normalizedDays - 1))
    return DatabaseFactory.dbQuery {
        val query = CatalogReadinessSnapshotsTable
            .innerJoin(
                CategoriesTable,
                { CatalogReadinessSnapshotsTable.categoryCode },
                { CategoriesTable.code },
            )
            .selectAll()
        query.andWhere { CatalogReadinessSnapshotsTable.categoryCode eq normalizedCategoryCode }
        query.andWhere { CatalogReadinessSnapshotsTable.snapshotDate greaterEq windowStart }
        query.orderBy(CatalogReadinessSnapshotsTable.snapshotDate to SortOrder.ASC)
        query.map { row -> row.toPersistedReadinessSnapshot() }
    }
}

internal suspend fun hasPersistedReadinessSnapshot(snapshotDate: LocalDate): Boolean =
    DatabaseFactory.dbQuery {
        val query = CatalogReadinessSnapshotsTable
            .selectAll()
        query.andWhere { CatalogReadinessSnapshotsTable.snapshotDate eq snapshotDate }
        query.count() > 0
    }

internal suspend fun tryAcquireCatalogAutomationLease(
    leaseKey: String,
    ownerId: String,
    nowMs: Long,
    leaseExpiresAt: Long,
): CatalogAutomationLease? = DatabaseFactory.dbQuery {
    val normalizedLeaseKey = leaseKey.trim()
    if (normalizedLeaseKey.isEmpty()) return@dbQuery null

    val existingQuery = CatalogReadinessAutomationLeasesTable.selectAll()
    existingQuery.andWhere { CatalogReadinessAutomationLeasesTable.leaseKey eq normalizedLeaseKey }
    val existing = existingQuery.singleOrNull()
    if (existing != null) {
        val existingOwnerId = existing[CatalogReadinessAutomationLeasesTable.ownerId]
        val existingToken = existing[CatalogReadinessAutomationLeasesTable.acquiredAt]
        val existingLeaseExpiresAt = existing[CatalogReadinessAutomationLeasesTable.leaseExpiresAt]
        if (existingOwnerId == ownerId && existingLeaseExpiresAt > nowMs) {
            CatalogReadinessAutomationLeasesTable.update({
                (CatalogReadinessAutomationLeasesTable.leaseKey eq normalizedLeaseKey) and
                    (CatalogReadinessAutomationLeasesTable.ownerId eq ownerId) and
                    (CatalogReadinessAutomationLeasesTable.acquiredAt eq existingToken)
            }) { stmt ->
                stmt[CatalogReadinessAutomationLeasesTable.lastHeartbeatAt] = nowMs
                stmt[CatalogReadinessAutomationLeasesTable.leaseExpiresAt] = leaseExpiresAt
            }
            return@dbQuery CatalogAutomationLease(
                leaseKey = normalizedLeaseKey,
                ownerId = ownerId,
                fencingToken = existingToken,
                leaseExpiresAt = leaseExpiresAt,
            )
        }
        if (existingLeaseExpiresAt <= nowMs) {
            val updated = CatalogReadinessAutomationLeasesTable.update({
                (CatalogReadinessAutomationLeasesTable.leaseKey eq normalizedLeaseKey) and
                    (CatalogReadinessAutomationLeasesTable.acquiredAt eq existingToken) and
                    (CatalogReadinessAutomationLeasesTable.leaseExpiresAt eq existingLeaseExpiresAt)
            }) { stmt ->
                stmt[CatalogReadinessAutomationLeasesTable.ownerId] = ownerId
                stmt[CatalogReadinessAutomationLeasesTable.acquiredAt] = nowMs
                stmt[CatalogReadinessAutomationLeasesTable.lastHeartbeatAt] = nowMs
                stmt[CatalogReadinessAutomationLeasesTable.leaseExpiresAt] = leaseExpiresAt
            }
            if (updated > 0) {
                return@dbQuery CatalogAutomationLease(
                    leaseKey = normalizedLeaseKey,
                    ownerId = ownerId,
                    fencingToken = nowMs,
                    leaseExpiresAt = leaseExpiresAt,
                )
            }
        }
        return@dbQuery null
    }

    val inserted = CatalogReadinessAutomationLeasesTable.insertIgnore { stmt ->
        stmt[CatalogReadinessAutomationLeasesTable.leaseKey] = normalizedLeaseKey
        stmt[CatalogReadinessAutomationLeasesTable.ownerId] = ownerId
        stmt[CatalogReadinessAutomationLeasesTable.acquiredAt] = nowMs
        stmt[CatalogReadinessAutomationLeasesTable.lastHeartbeatAt] = nowMs
        stmt[CatalogReadinessAutomationLeasesTable.leaseExpiresAt] = leaseExpiresAt
    }
    if (inserted.insertedCount > 0) {
        CatalogAutomationLease(
            leaseKey = normalizedLeaseKey,
            ownerId = ownerId,
            fencingToken = nowMs,
            leaseExpiresAt = leaseExpiresAt,
        )
    } else {
        null
    }
}

internal suspend fun renewCatalogAutomationLease(
    lease: CatalogAutomationLease,
    nowMs: Long,
    leaseExpiresAt: Long,
): CatalogAutomationLease? = DatabaseFactory.dbQuery {
    val updated = CatalogReadinessAutomationLeasesTable.update({
        (CatalogReadinessAutomationLeasesTable.leaseKey eq lease.leaseKey) and
            (CatalogReadinessAutomationLeasesTable.ownerId eq lease.ownerId) and
            (CatalogReadinessAutomationLeasesTable.acquiredAt eq lease.fencingToken) and
            (CatalogReadinessAutomationLeasesTable.leaseExpiresAt greaterEq nowMs)
    }) { stmt ->
        stmt[CatalogReadinessAutomationLeasesTable.lastHeartbeatAt] = nowMs
        stmt[CatalogReadinessAutomationLeasesTable.leaseExpiresAt] = leaseExpiresAt
    }
    if (updated == 0) null else lease.copy(leaseExpiresAt = leaseExpiresAt)
}

internal suspend fun releaseCatalogAutomationLease(
    lease: CatalogAutomationLease,
) {
    DatabaseFactory.dbQuery {
        CatalogReadinessAutomationLeasesTable.deleteWhere {
            (CatalogReadinessAutomationLeasesTable.leaseKey eq lease.leaseKey) and
                (CatalogReadinessAutomationLeasesTable.ownerId eq lease.ownerId) and
                (CatalogReadinessAutomationLeasesTable.acquiredAt eq lease.fencingToken)
        }
    }
}

internal suspend fun hasCatalogGovernanceReport(
    reportType: String,
    reportDate: LocalDate,
): Boolean = DatabaseFactory.dbQuery {
    val query = CatalogGovernanceReportsTable.selectAll()
    query.andWhere { CatalogGovernanceReportsTable.reportType eq reportType.trim() }
    query.andWhere { CatalogGovernanceReportsTable.reportDate eq reportDate }
    query.count() > 0
}

internal suspend fun upsertCatalogGovernanceReport(
    report: CatalogGovernanceReportResponse,
) {
    DatabaseFactory.dbQuery {
        val reportDate = LocalDate.parse(report.reportDate)
        val windowStartDate = LocalDate.parse(report.windowStartDate)
        val windowEndDate = LocalDate.parse(report.windowEndDate)
        val createdAt = System.currentTimeMillis()
        val updated = CatalogGovernanceReportsTable.update({
            (CatalogGovernanceReportsTable.reportType eq report.reportType) and
                (CatalogGovernanceReportsTable.reportDate eq reportDate)
        }) { stmt ->
            bindGovernanceReport(stmt, report, windowStartDate, windowEndDate, createdAt)
        }
        if (updated == 0) {
            CatalogGovernanceReportsTable.insert { stmt ->
                stmt[CatalogGovernanceReportsTable.reportType] = report.reportType
                stmt[CatalogGovernanceReportsTable.reportDate] = reportDate
                bindGovernanceReport(stmt, report, windowStartDate, windowEndDate, createdAt)
            }
        }
    }
}

internal suspend fun loadCatalogGovernanceReports(limit: Int): List<CatalogGovernanceReportResponse> =
    DatabaseFactory.dbQuery {
        CatalogGovernanceReportsTable
            .selectAll()
            .orderBy(CatalogGovernanceReportsTable.reportDate to SortOrder.DESC)
            .limit(limit.coerceIn(1, 100))
            .map { row ->
                CatalogGovernanceReportResponse(
                    reportType = row[CatalogGovernanceReportsTable.reportType],
                    reportDate = row[CatalogGovernanceReportsTable.reportDate].toString(),
                    generatedAt = row[CatalogGovernanceReportsTable.generatedAt],
                    windowStartDate = row[CatalogGovernanceReportsTable.windowStartDate].toString(),
                    windowEndDate = row[CatalogGovernanceReportsTable.windowEndDate].toString(),
                    totalCategories = row[CatalogGovernanceReportsTable.totalCategories],
                    readyCategories = row[CatalogGovernanceReportsTable.readyCategories],
                    betaCategories = row[CatalogGovernanceReportsTable.betaCategories],
                    internalCategories = row[CatalogGovernanceReportsTable.internalCategories],
                    categoriesWithBlockingIssues = row[CatalogGovernanceReportsTable.categoriesWithBlockingIssues],
                    dataVersion = row[CatalogGovernanceReportsTable.dataVersion],
                    schemaVersion = row[CatalogGovernanceReportsTable.schemaVersion],
                )
            }
    }

internal suspend fun persistCatalogGovernanceHookDelivery(
    delivery: CatalogGovernanceHookDeliveryResponse,
) {
    DatabaseFactory.dbQuery {
        CatalogGovernanceHookDeliveriesTable.insert { stmt ->
            stmt[CatalogGovernanceHookDeliveriesTable.hookCode] = delivery.hookCode
            stmt[CatalogGovernanceHookDeliveriesTable.trigger] = delivery.trigger
            stmt[CatalogGovernanceHookDeliveriesTable.transport] = delivery.transport
            stmt[CatalogGovernanceHookDeliveriesTable.target] = delivery.target
            stmt[CatalogGovernanceHookDeliveriesTable.status] = delivery.status
            stmt[CatalogGovernanceHookDeliveriesTable.attemptedAt] = delivery.attemptedAt
            stmt[CatalogGovernanceHookDeliveriesTable.responseStatus] = delivery.responseStatus
            stmt[CatalogGovernanceHookDeliveriesTable.details] = delivery.details
            stmt[CatalogGovernanceHookDeliveriesTable.createdAt] = System.currentTimeMillis()
        }
    }
}

private fun bindReadinessSnapshot(
    stmt: UpdateBuilder<*>,
    item: CatalogReadinessInventoryItem,
    capturedAt: Long,
) {
    stmt[CatalogReadinessSnapshotsTable.readiness] = item.readiness.name
    stmt[CatalogReadinessSnapshotsTable.editorialReadiness] = item.editorialReadiness.name
    stmt[CatalogReadinessSnapshotsTable.operationalReadiness] = item.operationalReadiness.name
    stmt[CatalogReadinessSnapshotsTable.completenessGatePassed] = item.completenessGatePassed
    stmt[CatalogReadinessSnapshotsTable.blockingIssues] = item.blockingIssues
    stmt[CatalogReadinessSnapshotsTable.editorialBlockingIssues] = item.editorialBlockingIssues
    stmt[CatalogReadinessSnapshotsTable.operationalBlockingIssues] = item.operationalBlockingIssues
    stmt[CatalogReadinessSnapshotsTable.operationalSampleCount] = item.operationalSampleCount
    stmt[CatalogReadinessSnapshotsTable.operationalDroppedRate] = item.operationalDroppedRate
    stmt[CatalogReadinessSnapshotsTable.operationalUnknownAttributeRate] = item.operationalUnknownAttributeRate
    stmt[CatalogReadinessSnapshotsTable.operationalRequiredMissingRate] = item.operationalRequiredMissingRate
    stmt[CatalogReadinessSnapshotsTable.operationalLowConfidenceRate] = item.operationalLowConfidenceRate
    stmt[CatalogReadinessSnapshotsTable.dataVersion] = CatalogDataVersion.current
    stmt[CatalogReadinessSnapshotsTable.schemaVersion] = CatalogSchemaVersion.current
    stmt[CatalogReadinessSnapshotsTable.capturedAt] = capturedAt
}

private fun bindGovernanceReport(
    stmt: UpdateBuilder<*>,
    report: CatalogGovernanceReportResponse,
    windowStartDate: LocalDate,
    windowEndDate: LocalDate,
    createdAt: Long,
) {
    stmt[CatalogGovernanceReportsTable.generatedAt] = report.generatedAt
    stmt[CatalogGovernanceReportsTable.windowStartDate] = windowStartDate
    stmt[CatalogGovernanceReportsTable.windowEndDate] = windowEndDate
    stmt[CatalogGovernanceReportsTable.totalCategories] = report.totalCategories
    stmt[CatalogGovernanceReportsTable.readyCategories] = report.readyCategories
    stmt[CatalogGovernanceReportsTable.betaCategories] = report.betaCategories
    stmt[CatalogGovernanceReportsTable.internalCategories] = report.internalCategories
    stmt[CatalogGovernanceReportsTable.categoriesWithBlockingIssues] = report.categoriesWithBlockingIssues
    stmt[CatalogGovernanceReportsTable.dataVersion] = report.dataVersion
    stmt[CatalogGovernanceReportsTable.schemaVersion] = report.schemaVersion
    stmt[CatalogGovernanceReportsTable.createdAt] = createdAt
}

private fun ResultRow.toPersistedReadinessSnapshot(): CatalogPersistedReadinessSnapshot =
    CatalogPersistedReadinessSnapshot(
        snapshotDate = this[CatalogReadinessSnapshotsTable.snapshotDate],
        category = Category(
            code = this[CategoriesTable.code],
            segment = runCatching {
                com.example.shoppingassistant.domain.catalog.CategorySegment.valueOf(this[CategoriesTable.segment])
            }.getOrDefault(com.example.shoppingassistant.domain.catalog.CategorySegment.OTHER),
            title = localizedTextFromStorage(
                localized = this[CategoriesTable.titleLocalized],
                titleRu = this[CategoriesTable.titleRu],
                titleEn = this[CategoriesTable.titleEn],
            ),
            parentCode = this[CategoriesTable.parentCode],
            description = this[CategoriesTable.description],
            status = runCatching {
                com.example.shoppingassistant.domain.catalog.CategoryStatus.valueOf(this[CategoriesTable.status])
            }.getOrDefault(com.example.shoppingassistant.domain.catalog.CategoryStatus.ACTIVE),
            replacementCode = this[CategoriesTable.replacementCode],
        ),
        readiness = this[CatalogReadinessSnapshotsTable.readiness].toCatalogReadiness(),
        editorialReadiness = this[CatalogReadinessSnapshotsTable.editorialReadiness].toCatalogReadiness(),
        operationalReadiness = this[CatalogReadinessSnapshotsTable.operationalReadiness].toCatalogReadiness(),
        completenessGatePassed = this[CatalogReadinessSnapshotsTable.completenessGatePassed],
        blockingIssues = this[CatalogReadinessSnapshotsTable.blockingIssues],
        editorialBlockingIssues = this[CatalogReadinessSnapshotsTable.editorialBlockingIssues],
        operationalBlockingIssues = this[CatalogReadinessSnapshotsTable.operationalBlockingIssues],
        operationalSampleCount = this[CatalogReadinessSnapshotsTable.operationalSampleCount],
        operationalDroppedRate = this[CatalogReadinessSnapshotsTable.operationalDroppedRate],
        operationalUnknownAttributeRate = this[CatalogReadinessSnapshotsTable.operationalUnknownAttributeRate],
        operationalRequiredMissingRate = this[CatalogReadinessSnapshotsTable.operationalRequiredMissingRate],
        operationalLowConfidenceRate = this[CatalogReadinessSnapshotsTable.operationalLowConfidenceRate],
    )

private fun String.toCatalogReadiness(): CatalogCategoryReadiness =
    runCatching { CatalogCategoryReadiness.valueOf(trim().uppercase(Locale.ROOT)) }
        .getOrDefault(CatalogCategoryReadiness.INTERNAL)
