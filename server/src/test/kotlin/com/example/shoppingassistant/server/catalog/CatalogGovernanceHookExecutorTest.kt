package com.example.shoppingassistant.server.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CatalogGovernanceHookExecutorTest {
    @Test
    fun dispatch_appends_daily_snapshot_event_to_jsonl_archive() = runBlocking {
        val archive = Files.createTempFile("catalog-governance-daily", ".jsonl")
        val repository = FakeCatalogGovernanceHookDeliveryRepository()
        val executor = CatalogGovernanceHookExecutorImpl(
            deliveryRepository = repository,
            targetResolver = { key ->
                if (key == "CATALOG_GOVERNANCE_DAILY_ARCHIVE_PATH") archive.toString() else null
            },
        )

        executor.dispatch(sampleEvent(trigger = GOVERNANCE_TRIGGER_DAILY_SNAPSHOT_CAPTURED))

        val payload = Files.readString(archive)
        assertTrue(payload.contains("\"trigger\":\"$GOVERNANCE_TRIGGER_DAILY_SNAPSHOT_CAPTURED\""))
        assertEquals(1, repository.deliveries.size)
        assertEquals("daily_snapshot_archive", repository.deliveries.single().hookCode)
        assertEquals("DELIVERED", repository.deliveries.single().status)
    }

    @Test
    fun dispatch_marks_weekly_hooks_as_skipped_when_targets_are_not_configured() = runBlocking {
        val repository = FakeCatalogGovernanceHookDeliveryRepository()
        val executor = CatalogGovernanceHookExecutorImpl(
            deliveryRepository = repository,
            targetResolver = { null },
        )

        executor.dispatch(sampleEvent(trigger = GOVERNANCE_TRIGGER_WEEKLY_SUMMARY_CAPTURED))

        assertEquals(2, repository.deliveries.size)
        assertTrue(repository.deliveries.all { delivery -> delivery.status == "SKIPPED_UNCONFIGURED" })
        assertTrue(repository.deliveries.any { delivery -> delivery.hookCode == "weekly_summary_webhook" })
        assertTrue(repository.deliveries.any { delivery -> delivery.hookCode == "weekly_summary_archive" })
    }

    private fun sampleEvent(trigger: String): CatalogGovernanceHookEvent =
        CatalogGovernanceHookEvent(
            trigger = trigger,
            emittedAt = "2026-03-10T09:31:00Z",
            snapshotDate = "2026-03-10",
            dataVersion = "2.2.0",
            schemaVersion = "1.0.0",
            inventory = CatalogGovernanceInventorySummary(
                totalCategories = 2,
                readyCategories = 1,
                betaCategories = 1,
                internalCategories = 0,
                categoriesWithBlockingIssues = listOf("APPL.SMALL"),
            ),
            report = null,
        )
}

private class FakeCatalogGovernanceHookDeliveryRepository : CatalogGovernanceHookDeliveryRepository {
    val deliveries = mutableListOf<CatalogGovernanceHookDeliveryResponse>()

    override suspend fun recordDelivery(delivery: CatalogGovernanceHookDeliveryResponse) {
        deliveries += delivery
    }
}
