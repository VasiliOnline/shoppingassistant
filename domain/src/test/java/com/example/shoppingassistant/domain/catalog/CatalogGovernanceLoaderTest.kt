package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceLoaderTest {
    @Test
    fun governance_snapshot_exposes_release_and_readiness_policy() {
        val snapshot = CatalogGovernanceLoader.loadSnapshot()

        assertEquals(Stage22RegistryLoader.loadSnapshot().meta.dataVersion, snapshot.dataVersion)
        assertTrue(snapshot.releasePolicy.owner.isNotBlank())
        assertTrue(snapshot.readinessPolicy.sqlChecks.isNotEmpty())
        assertEquals(
            "SERVER_AUTHORITATIVE_NEGOTIATED_STABILITY",
            snapshot.compatibilityPolicy.dataVersionMode,
        )
        assertTrue(snapshot.compatibilityPolicy.requiredVersionHeaders.contains("X-Catalog-Data-Version"))
        assertTrue(snapshot.readinessPolicy.slaDaysByIssueType["ZERO_RESULTS"] == 3)
        assertTrue(snapshot.readinessPolicy.governanceHooks.any { it.code == "weekly_summary_webhook" })
        assertTrue(snapshot.readinessPolicy.governanceHooks.any { it.transport == "JSONL_FILE" })
        assertTrue(
            snapshot.requiredArtifactStatuses.any { artifact ->
                artifact.declaredPath == "readiness_governance_policy.json" && artifact.exists
            },
        )
        assertTrue(
            snapshot.requiredArtifactStatuses.any { artifact ->
                artifact.declaredPath == "runtime_compatibility_policy.json" && artifact.exists
            },
        )
    }
}
