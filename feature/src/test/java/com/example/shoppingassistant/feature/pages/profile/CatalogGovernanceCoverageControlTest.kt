package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminReadinessItem
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminRefreshStatus
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGovernanceCoverageControlTest {

    @Test
    fun buildCoverageSummary_marks_non_ready_branches_as_needing_coverage() {
        val summary = buildCatalogGovernanceCoverageSummary(
            readinessInventory = listOf(
                readinessItem(
                    code = "FOOD.READY_MEALS",
                    title = "Готовая еда",
                    readiness = CatalogCategoryReadiness.BETA,
                    completenessGatePassed = false,
                    blockingIssues = listOf("missing_restaurant_values"),
                ),
            ),
            refreshStatus = null,
            nowMs = 1_000L,
        )

        assertEquals(1, summary.categoriesNeedingCoverage)
        assertEquals(0, summary.categoriesNeedingRefresh)
        assertEquals("WARNING", summary.items.single().attentionLevel)
        assertTrue(summary.items.single().attentionReasons.any { it.contains("completeness gate") })
    }

    @Test
    fun buildCoverageSummary_marks_phones_refresh_as_needing_attention_when_stale() {
        val summary = buildCatalogGovernanceCoverageSummary(
            readinessInventory = listOf(
                readinessItem(
                    code = "TECH.PHONES",
                    title = "Смартфоны",
                    readiness = CatalogCategoryReadiness.READY,
                    completenessGatePassed = true,
                ),
            ),
            refreshStatus = CatalogGovernanceAdminRefreshStatus(
                enabled = true,
                configuredCategoryCode = "TECH.PHONES",
                effectiveCategoryCode = "TECH.PHONES",
                pollIntervalMs = 60_000L,
                trigger = "SCHEDULED",
                connectorTypes = listOf("OFFICIAL_PHONE_WEB_SOURCE"),
                availableSourceCount = 3,
                matchedSourceCount = 3,
                matchedRegistryCodes = listOf("APPLE", "SAMSUNG", "GOOGLE"),
                latestRunId = 7L,
                latestRunStatus = "COMPLETED",
                latestRunPublishStatus = "PUBLISHED",
                latestRunStartedAt = 1_000L,
                latestRunFinishedAt = 2_000L,
            ),
            nowMs = 10_000_000L,
        )

        assertEquals(1, summary.categoriesNeedingRefresh)
        assertEquals("AUTOMATED", summary.items.single().refreshMode)
        assertEquals("CRITICAL", summary.items.single().attentionLevel)
        assertTrue(summary.items.single().attentionReasons.any { it.contains("устарел") })
    }

    private fun readinessItem(
        code: String,
        title: String,
        readiness: CatalogCategoryReadiness,
        completenessGatePassed: Boolean,
        blockingIssues: List<String> = emptyList(),
    ): CatalogGovernanceAdminReadinessItem =
        CatalogGovernanceAdminReadinessItem(
            category = Category(
                code = code,
                segment = CategorySegment.valueOf(code.substringBefore('.')),
                title = localizedTextOf("ru" to title),
            ),
            readiness = readiness,
            editorialReadiness = readiness,
            operationalReadiness = readiness,
            completenessGatePassed = completenessGatePassed,
            blockingIssues = blockingIssues,
        )
}
