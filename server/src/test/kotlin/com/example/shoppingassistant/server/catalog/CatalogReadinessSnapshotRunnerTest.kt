package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogReadinessSnapshotRunnerTest {
    @Test
    fun runIfDue_skips_before_governance_schedule() = runBlocking {
        val inventoryService = FakeCatalogReadinessInventoryService(
            inventory = listOf(sampleInventoryItem("TECH.PHONES")),
        )
        val snapshotRepository = FakeCatalogReadinessSnapshotRepository()
        val leaseRepository = FakeCatalogAutomationLeaseRepository()
        val governanceReportRepository = FakeCatalogGovernanceReportRepository()
        val runner = CatalogReadinessSnapshotRunner(
            inventoryService = inventoryService,
            snapshotRepository = snapshotRepository,
            leaseRepository = leaseRepository,
            governanceReportRepository = governanceReportRepository,
            hookExecutor = FakeCatalogGovernanceHookExecutor(),
            leaseOwnerId = "test-node",
            leaseTtlMs = 60_000L,
            leaseHeartbeatIntervalMs = 10_000L,
        )

        val result = runner.runIfDue(LocalDateTime(2026, 3, 10, 8, 59))

        assertFalse(result.captured)
        assertFalse(result.weeklySummaryCaptured)
        assertEquals("before_schedule", result.reason)
        assertTrue(snapshotRepository.recordedInventory.isEmpty())
    }

    @Test
    fun runIfDue_captures_inventory_once_per_day() = runBlocking {
        val inventory = listOf(
            sampleInventoryItem("TECH.PHONES"),
            sampleInventoryItem("APPL.SMALL"),
        )
        val inventoryService = FakeCatalogReadinessInventoryService(inventory = inventory)
        val snapshotRepository = FakeCatalogReadinessSnapshotRepository()
        val leaseRepository = FakeCatalogAutomationLeaseRepository()
        val governanceReportRepository = FakeCatalogGovernanceReportRepository()
        val runner = CatalogReadinessSnapshotRunner(
            inventoryService = inventoryService,
            snapshotRepository = snapshotRepository,
            leaseRepository = leaseRepository,
            governanceReportRepository = governanceReportRepository,
            hookExecutor = FakeCatalogGovernanceHookExecutor(),
            leaseOwnerId = "test-node",
            leaseTtlMs = 60_000L,
            leaseHeartbeatIntervalMs = 10_000L,
        )

        val firstRun = runner.runIfDue(LocalDateTime(2026, 3, 10, 9, 1))
        val secondRun = runner.runIfDue(LocalDateTime(2026, 3, 10, 15, 30))

        assertTrue(firstRun.captured)
        assertEquals(2, firstRun.capturedCategories)
        assertEquals(inventory, snapshotRepository.recordedInventory)
        assertFalse(secondRun.captured)
        assertEquals("already_captured", secondRun.reason)
        assertEquals(1, inventoryService.calls)
    }

    @Test
    fun runIfDue_persists_weekly_summary_after_weekly_governance_cadence() = runBlocking {
        val inventory = listOf(
            sampleInventoryItem("TECH.PHONES"),
            sampleInventoryItem("APPL.SMALL").copy(
                readiness = CatalogCategoryReadiness.BETA,
                editorialReadiness = CatalogCategoryReadiness.BETA,
                operationalReadiness = CatalogCategoryReadiness.BETA,
                blockingIssues = listOf("operational_samples_below_min:2"),
            ),
        )
        val inventoryService = FakeCatalogReadinessInventoryService(inventory = inventory)
        val snapshotRepository = FakeCatalogReadinessSnapshotRepository()
        val leaseRepository = FakeCatalogAutomationLeaseRepository()
        val governanceReportRepository = FakeCatalogGovernanceReportRepository()
        val runner = CatalogReadinessSnapshotRunner(
            inventoryService = inventoryService,
            snapshotRepository = snapshotRepository,
            leaseRepository = leaseRepository,
            governanceReportRepository = governanceReportRepository,
            hookExecutor = FakeCatalogGovernanceHookExecutor(),
            leaseOwnerId = "test-node",
            leaseTtlMs = 60_000L,
            leaseHeartbeatIntervalMs = 10_000L,
        )

        val result = runner.runIfDue(LocalDateTime(2026, 3, 9, 9, 31))

        assertTrue(result.captured)
        assertTrue(result.weeklySummaryCaptured)
        assertEquals("captured+weekly_summary", result.reason)
        assertEquals(1, governanceReportRepository.reports.size)
        assertEquals("WEEKLY_SUMMARY", governanceReportRepository.reports.single().reportType)
        assertEquals(listOf("APPL.SMALL"), governanceReportRepository.reports.single().categoriesWithBlockingIssues)
    }
}

private fun sampleInventoryItem(categoryCode: String): CatalogReadinessInventoryItem =
    CatalogReadinessInventoryItem(
        category = Category(
            code = categoryCode,
            segment = CategorySegment.TECH,
        ),
        readiness = CatalogCategoryReadiness.READY,
        editorialReadiness = CatalogCategoryReadiness.READY,
        operationalReadiness = CatalogCategoryReadiness.READY,
        completenessGatePassed = true,
    )

private class FakeCatalogReadinessInventoryService(
    private val inventory: List<CatalogReadinessInventoryItem>,
) : CatalogReadinessInventoryService {
    var calls: Int = 0
        private set

    override suspend fun getInventory(): List<CatalogReadinessInventoryItem> {
        calls += 1
        return inventory
    }
}

private class FakeCatalogReadinessSnapshotRepository : CatalogReadinessSnapshotRepository {
    var recordedInventory: List<CatalogReadinessInventoryItem> = emptyList()
        private set

    private val capturedDates = linkedSetOf<LocalDate>()

    override suspend fun recordInventory(
        snapshotDate: LocalDate,
        inventory: List<CatalogReadinessInventoryItem>,
    ) {
        recordedInventory = inventory
        capturedDates += snapshotDate
    }

    override suspend fun hasSnapshotForDate(snapshotDate: LocalDate): Boolean =
        snapshotDate in capturedDates

    override suspend fun loadHistory(
        categoryCode: String,
        days: Int,
    ): List<CatalogPersistedReadinessSnapshot> = emptyList()
}

private class FakeCatalogAutomationLeaseRepository : CatalogAutomationLeaseRepository {
    private val heldLeases = linkedMapOf<String, CatalogAutomationLease>()

    override suspend fun tryAcquireLease(
        leaseKey: String,
        ownerId: String,
        nowMs: Long,
        leaseExpiresAt: Long,
    ): CatalogAutomationLease? {
        val current = heldLeases[leaseKey]
        if (current != null && current.leaseExpiresAt > nowMs && current.ownerId != ownerId) {
            return null
        }
        return CatalogAutomationLease(
            leaseKey = leaseKey,
            ownerId = ownerId,
            fencingToken = current?.fencingToken ?: nowMs,
            leaseExpiresAt = leaseExpiresAt,
        ).also { lease -> heldLeases[leaseKey] = lease }
    }

    override suspend fun renewLease(
        lease: CatalogAutomationLease,
        nowMs: Long,
        leaseExpiresAt: Long,
    ): CatalogAutomationLease? {
        val current = heldLeases[lease.leaseKey] ?: return null
        if (current.fencingToken != lease.fencingToken || current.ownerId != lease.ownerId) return null
        return lease.copy(leaseExpiresAt = leaseExpiresAt).also { renewed ->
            heldLeases[lease.leaseKey] = renewed
        }
    }

    override suspend fun releaseLease(
        lease: CatalogAutomationLease,
    ) {
        val current = heldLeases[lease.leaseKey]
        if (current?.fencingToken == lease.fencingToken && current.ownerId == lease.ownerId) {
            heldLeases.remove(lease.leaseKey)
        }
    }
}

private class FakeCatalogGovernanceReportRepository : CatalogGovernanceReportRepository {
    val reports = mutableListOf<CatalogGovernanceReportResponse>()

    override suspend fun hasReport(
        reportType: String,
        reportDate: LocalDate,
    ): Boolean = reports.any { it.reportType == reportType && it.reportDate == reportDate.toString() }

    override suspend fun upsertReport(report: CatalogGovernanceReportResponse) {
        reports.removeAll { it.reportType == report.reportType && it.reportDate == report.reportDate }
        reports += report
    }

    override suspend fun listReports(limit: Int): List<CatalogGovernanceReportResponse> =
        reports.take(limit)
}

private class FakeCatalogGovernanceHookExecutor : CatalogGovernanceHookExecutor {
    override suspend fun dispatch(event: CatalogGovernanceHookEvent) = Unit
}
