package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceLoader
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogSchemaVersion
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

internal data class CatalogReadinessSnapshotRunResult(
    val captured: Boolean,
    val weeklySummaryCaptured: Boolean,
    val reason: String,
    val snapshotDate: String,
    val capturedCategories: Int = 0,
)

internal class CatalogReadinessSnapshotRunner(
    private val inventoryService: CatalogReadinessInventoryService,
    private val snapshotRepository: CatalogReadinessSnapshotRepository,
    private val leaseRepository: CatalogAutomationLeaseRepository,
    private val governanceReportRepository: CatalogGovernanceReportRepository,
    private val hookExecutor: CatalogGovernanceHookExecutor,
    private val leaseOwnerId: String,
    private val leaseTtlMs: Long,
    private val leaseHeartbeatIntervalMs: Long,
) {
    private val scheduledDailyTimeUtc: LocalTime by lazy {
        parseDailyTimeUtc(
            CatalogGovernanceLoader.loadSnapshot().readinessPolicy.runCadence.dailyOperationalTimeUtc,
        )
    }
    private val weeklySummaryDay: DayOfWeek by lazy {
        DayOfWeek.valueOf(
            CatalogGovernanceLoader.loadSnapshot().readinessPolicy.runCadence.weeklySummaryDay.trim().uppercase(),
        )
    }
    private val weeklySummaryTimeUtc: LocalTime by lazy {
        parseDailyTimeUtc(
            CatalogGovernanceLoader.loadSnapshot().readinessPolicy.runCadence.weeklySummaryTimeUtc,
        )
    }

    suspend fun runIfDue(
        nowUtc: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
    ): CatalogReadinessSnapshotRunResult {
        val inventoryHolder = InventoryHolder()
        val snapshotResult = captureSnapshotIfDue(nowUtc, inventoryHolder)
        snapshotResult.event?.let { event -> hookExecutor.dispatch(event) }
        val weeklySummaryResult = captureWeeklySummaryIfDue(nowUtc, inventoryHolder)
        weeklySummaryResult.event?.let { event -> hookExecutor.dispatch(event) }
        val weeklySummaryCaptured = weeklySummaryResult.captured
        val reason = when {
            snapshotResult.captured && weeklySummaryCaptured -> "captured+weekly_summary"
            snapshotResult.captured -> snapshotResult.reason
            weeklySummaryCaptured -> "weekly_summary"
            snapshotResult.reason != "already_captured" -> snapshotResult.reason
            weeklySummaryResult.reason == "before_weekly_schedule" -> snapshotResult.reason
            else -> weeklySummaryResult.reason
        }
        return snapshotResult.copy(
            weeklySummaryCaptured = weeklySummaryCaptured,
            reason = reason,
        ).toRunResult()
    }

    private suspend fun captureSnapshotIfDue(
        nowUtc: LocalDateTime,
        inventoryHolder: InventoryHolder,
    ): SnapshotCaptureOutcome {
        if (nowUtc.time < scheduledDailyTimeUtc) {
            return SnapshotCaptureOutcome(
                captured = false,
                weeklySummaryCaptured = false,
                reason = "before_schedule",
                snapshotDate = nowUtc.date.toString(),
            )
        }
        if (snapshotRepository.hasSnapshotForDate(nowUtc.date)) {
            return SnapshotCaptureOutcome(
                captured = false,
                weeklySummaryCaptured = false,
                reason = "already_captured",
                snapshotDate = nowUtc.date.toString(),
            )
        }

        val leaseKey = "catalog_readiness_snapshot:${nowUtc.date}"
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val lease = leaseRepository.tryAcquireLease(
            leaseKey = leaseKey,
            ownerId = leaseOwnerId,
            nowMs = nowMs,
            leaseExpiresAt = nowMs + leaseTtlMs,
        )
        if (lease == null) {
            return SnapshotCaptureOutcome(
                captured = false,
                weeklySummaryCaptured = false,
                reason = "lease_held",
                snapshotDate = nowUtc.date.toString(),
            )
        }

        return withLease(lease) { leaseState ->
            if (snapshotRepository.hasSnapshotForDate(nowUtc.date)) {
                return@withLease SnapshotCaptureOutcome(
                    captured = false,
                    weeklySummaryCaptured = false,
                    reason = "already_captured",
                    snapshotDate = nowUtc.date.toString(),
                )
            }
            val inventory = inventoryHolder.inventory ?: inventoryService.getInventory().also { inventoryHolder.inventory = it }
            if (!leaseState.isValid()) {
                return@withLease SnapshotCaptureOutcome(
                    captured = false,
                    weeklySummaryCaptured = false,
                    reason = "lease_lost",
                    snapshotDate = nowUtc.date.toString(),
                )
            }
            if (inventory.isEmpty()) {
                return@withLease SnapshotCaptureOutcome(
                    captured = false,
                    weeklySummaryCaptured = false,
                    reason = "empty_inventory",
                    snapshotDate = nowUtc.date.toString(),
                )
            }
            snapshotRepository.recordInventory(
                snapshotDate = nowUtc.date,
                inventory = inventory,
            )
            SnapshotCaptureOutcome(
                captured = true,
                weeklySummaryCaptured = false,
                reason = "captured",
                snapshotDate = nowUtc.date.toString(),
                capturedCategories = inventory.size,
                event = inventory.toGovernanceEvent(
                    trigger = GOVERNANCE_TRIGGER_DAILY_SNAPSHOT_CAPTURED,
                    snapshotDate = nowUtc.date.toString(),
                ),
            )
        }
    }

    private suspend fun captureWeeklySummaryIfDue(
        nowUtc: LocalDateTime,
        inventoryHolder: InventoryHolder,
    ): WeeklySummaryCaptureOutcome {
        if (nowUtc.date.dayOfWeek != weeklySummaryDay || nowUtc.time < weeklySummaryTimeUtc) {
            return WeeklySummaryCaptureOutcome(captured = false, reason = "before_weekly_schedule")
        }
        val reportType = WEEKLY_SUMMARY_REPORT_TYPE
        if (governanceReportRepository.hasReport(reportType = reportType, reportDate = nowUtc.date)) {
            return WeeklySummaryCaptureOutcome(captured = false, reason = "already_reported")
        }

        val leaseKey = "catalog_governance_weekly_summary:${nowUtc.date}"
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val lease = leaseRepository.tryAcquireLease(
            leaseKey = leaseKey,
            ownerId = leaseOwnerId,
            nowMs = nowMs,
            leaseExpiresAt = nowMs + leaseTtlMs,
        )
        if (lease == null) {
            return WeeklySummaryCaptureOutcome(captured = false, reason = "lease_held")
        }

        return withLease(lease) { leaseState ->
            if (governanceReportRepository.hasReport(reportType = reportType, reportDate = nowUtc.date)) {
                return@withLease WeeklySummaryCaptureOutcome(captured = false, reason = "already_reported")
            }
            val inventory = inventoryHolder.inventory ?: inventoryService.getInventory().also { inventoryHolder.inventory = it }
            if (!leaseState.isValid()) {
                return@withLease WeeklySummaryCaptureOutcome(captured = false, reason = "lease_lost")
            }
            if (inventory.isEmpty()) return@withLease WeeklySummaryCaptureOutcome(captured = false, reason = "empty_inventory")

            val report = CatalogGovernanceReportResponse(
                reportType = reportType,
                reportDate = nowUtc.date.toString(),
                generatedAt = Clock.System.now().toString(),
                windowStartDate = nowUtc.date.minus(DatePeriod(days = 6)).toString(),
                windowEndDate = nowUtc.date.toString(),
                totalCategories = inventory.size,
                readyCategories = inventory.count { it.readiness.name == "READY" },
                betaCategories = inventory.count { it.readiness.name == "BETA" },
                internalCategories = inventory.count { it.readiness.name == "INTERNAL" },
                categoriesWithBlockingIssues = inventory
                    .filter { it.blockingIssues.isNotEmpty() }
                    .map { it.category.code }
                    .sorted(),
                dataVersion = CatalogDataVersion.current,
                schemaVersion = CatalogSchemaVersion.current,
            )
            governanceReportRepository.upsertReport(report)
            WeeklySummaryCaptureOutcome(
                captured = true,
                reason = "weekly_summary",
                event = inventory.toGovernanceEvent(
                    trigger = GOVERNANCE_TRIGGER_WEEKLY_SUMMARY_CAPTURED,
                    snapshotDate = nowUtc.date.toString(),
                    report = report,
                ),
            )
        }
    }

    private suspend fun <T> withLease(
        lease: CatalogAutomationLease,
        block: suspend (LeaseState) -> T,
    ): T = coroutineScope {
        val leaseRef = AtomicReference(lease)
        val leaseValid = AtomicBoolean(true)
        val heartbeatJob = launch {
            while (isActive && leaseValid.get()) {
                delay(leaseHeartbeatIntervalMs)
                val currentLease = leaseRef.get()
                val nowMs = Clock.System.now().toEpochMilliseconds()
                val renewed = leaseRepository.renewLease(
                    lease = currentLease,
                    nowMs = nowMs,
                    leaseExpiresAt = nowMs + leaseTtlMs,
                )
                if (renewed == null) {
                    leaseValid.set(false)
                    break
                }
                leaseRef.set(renewed)
            }
        }
        try {
            block(LeaseState { leaseValid.get() })
        } finally {
            heartbeatJob.cancelAndJoin()
            leaseRepository.releaseLease(leaseRef.get())
        }
    }
}

private class InventoryHolder(
    var inventory: List<CatalogReadinessInventoryItem>? = null,
)

private fun List<CatalogReadinessInventoryItem>.toGovernanceSummary(): CatalogGovernanceInventorySummary =
    CatalogGovernanceInventorySummary(
        totalCategories = size,
        readyCategories = count { it.readiness == CatalogCategoryReadiness.READY },
        betaCategories = count { it.readiness == CatalogCategoryReadiness.BETA },
        internalCategories = count { it.readiness == CatalogCategoryReadiness.INTERNAL },
        categoriesWithBlockingIssues = filter { it.blockingIssues.isNotEmpty() }
            .map { it.category.code }
            .sorted(),
    )

private fun List<CatalogReadinessInventoryItem>.toGovernanceEvent(
    trigger: String,
    snapshotDate: String,
    report: CatalogGovernanceReportResponse? = null,
): CatalogGovernanceHookEvent =
    CatalogGovernanceHookEvent(
        trigger = trigger,
        emittedAt = Clock.System.now().toString(),
        snapshotDate = snapshotDate,
        dataVersion = CatalogDataVersion.current,
        schemaVersion = CatalogSchemaVersion.current,
        inventory = toGovernanceSummary(),
        report = report,
    )

private fun interface LeaseState {
    fun isValid(): Boolean
}

private data class SnapshotCaptureOutcome(
    val captured: Boolean,
    val weeklySummaryCaptured: Boolean,
    val reason: String,
    val snapshotDate: String,
    val capturedCategories: Int = 0,
    val event: CatalogGovernanceHookEvent? = null,
)

private fun SnapshotCaptureOutcome.toRunResult(): CatalogReadinessSnapshotRunResult =
    CatalogReadinessSnapshotRunResult(
        captured = captured,
        weeklySummaryCaptured = weeklySummaryCaptured,
        reason = reason,
        snapshotDate = snapshotDate,
        capturedCategories = capturedCategories,
    )

private data class WeeklySummaryCaptureOutcome(
    val captured: Boolean,
    val reason: String,
    val event: CatalogGovernanceHookEvent? = null,
)

private fun parseDailyTimeUtc(value: String): LocalTime {
    val parts = value.trim().split(':')
    require(parts.size == 2) { "Expected HH:mm UTC time, got '$value'" }
    val hour = parts[0].toIntOrNull() ?: error("Expected HH:mm UTC time, got '$value'")
    val minute = parts[1].toIntOrNull() ?: error("Expected HH:mm UTC time, got '$value'")
    require(hour in 0..23 && minute in 0..59) { "Expected HH:mm UTC time, got '$value'" }
    return LocalTime(hour, minute)
}

private const val WEEKLY_SUMMARY_REPORT_TYPE = "WEEKLY_SUMMARY"
