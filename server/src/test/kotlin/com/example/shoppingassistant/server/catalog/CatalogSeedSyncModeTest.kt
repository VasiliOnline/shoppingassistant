package com.example.shoppingassistant.server.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CatalogSeedSyncModeTest {
    @Test
    fun fromEnv_defaults_to_upsert_only() {
        assertEquals(CatalogSeedSyncMode.UPSERT_ONLY, CatalogSeedSyncMode.fromEnv(null))
        assertEquals(CatalogSeedSyncMode.UPSERT_ONLY, CatalogSeedSyncMode.fromEnv(""))
        assertEquals(CatalogSeedSyncMode.UPSERT_ONLY, CatalogSeedSyncMode.fromEnv("safe"))
    }

    @Test
    fun fromEnv_supports_full_sync_aliases() {
        assertEquals(CatalogSeedSyncMode.FULL_SYNC, CatalogSeedSyncMode.fromEnv("full_sync"))
        assertEquals(CatalogSeedSyncMode.FULL_SYNC, CatalogSeedSyncMode.fromEnv("destructive"))
    }

    @Test
    fun fromEnv_rejects_unknown_values() {
        try {
            CatalogSeedSyncMode.fromEnv("unknown")
            fail("Expected IllegalStateException for unknown sync mode.")
        } catch (_: IllegalStateException) {
            // Expected path.
        }
    }
}
