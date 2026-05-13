package com.example.shoppingassistant.server.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CatalogStage20BackfillStartupConfigTest {
    @Test
    fun fromEnv_defaults_to_disabled_safe_mode() {
        val config = CatalogStage20BackfillStartupConfig.fromEnv(emptyMap())

        assertFalse(config.enabled)
        assertTrue(config.autoRepairEnabled)
        assertEquals(CatalogSeedSyncMode.UPSERT_ONLY, config.syncMode)
        assertFalse(config.ensureReferencedCategories)
        assertFalse(config.exitAfterRun)
    }

    @Test
    fun fromEnv_parses_flags_and_sync_mode() {
        val config = CatalogStage20BackfillStartupConfig.fromEnv(
            mapOf(
                "CATALOG_STAGE20_BACKFILL_ON_STARTUP" to "TRUE",
                "CATALOG_STAGE20_AUTO_REPAIR_ON_STARTUP" to "false",
                "CATALOG_STAGE20_BACKFILL_SYNC_MODE" to "destructive",
                "CATALOG_STAGE20_BACKFILL_ENSURE_REFERENCED_CATEGORIES" to "true",
                "CATALOG_STAGE20_BACKFILL_EXIT_AFTER_RUN" to "true",
            ),
        )

        assertTrue(config.enabled)
        assertFalse(config.autoRepairEnabled)
        assertEquals(CatalogSeedSyncMode.FULL_SYNC, config.syncMode)
        assertTrue(config.ensureReferencedCategories)
        assertTrue(config.exitAfterRun)
    }

    @Test
    fun fromEnv_rejects_unknown_sync_mode() {
        try {
            CatalogStage20BackfillStartupConfig.fromEnv(
                mapOf("CATALOG_STAGE20_BACKFILL_SYNC_MODE" to "unknown"),
            )
            fail("Expected IllegalStateException for unknown sync mode.")
        } catch (_: IllegalStateException) {
            // Expected path.
        }
    }
}
