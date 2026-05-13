package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.server.config.CatalogGovernanceRefreshConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceRefreshConfigTest {
    @Test
    fun fromEnv_defaults_to_official_phone_refresh() {
        val config = CatalogGovernanceRefreshConfig.fromEnv(emptyMap())

        assertTrue(config.enabled)
        assertEquals(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE, config.categoryCode)
        assertEquals(
            setOf(CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE),
            config.connectorTypes,
        )
        assertTrue(config.registryCodeAllowlist.isEmpty())
        assertEquals("SCHEDULED", config.trigger)
    }

    @Test
    fun fromEnv_parses_allowlist_and_connector_types() {
        val config = CatalogGovernanceRefreshConfig.fromEnv(
            mapOf(
                "CATALOG_GOVERNANCE_REFRESH_ENABLED" to "false",
                "CATALOG_GOVERNANCE_REFRESH_POLL_MS" to "60000",
                "CATALOG_GOVERNANCE_REFRESH_CATEGORY_CODE" to "TECH.PHONES",
                "CATALOG_GOVERNANCE_REFRESH_CONNECTOR_TYPES" to "official_phone_web_source,curated_seed_pack",
                "CATALOG_GOVERNANCE_REFRESH_REGISTRY_CODES" to "A,B",
                "CATALOG_GOVERNANCE_REFRESH_TRIGGER" to "SCHEDULED_OFFICIAL",
            ),
        )

        assertFalse(config.enabled)
        assertEquals(60_000L, config.pollIntervalMs)
        assertEquals(
            setOf(
                CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE,
                CatalogGovernanceRefreshConnectorType.CURATED_SEED_PACK,
            ),
            config.connectorTypes,
        )
        assertEquals(setOf("A", "B"), config.registryCodeAllowlist)
        assertEquals("SCHEDULED_OFFICIAL", config.trigger)
    }
}
