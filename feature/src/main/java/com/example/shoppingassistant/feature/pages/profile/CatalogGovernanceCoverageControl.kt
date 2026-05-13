package com.example.shoppingassistant.feature.pages.profile

import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminReadinessItem
import com.example.shoppingassistant.core.data.catalog.CatalogGovernanceAdminRefreshStatus
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import java.util.concurrent.TimeUnit

data class CatalogGovernanceCoverageSummary(
    val totalCategories: Int,
    val categoriesNeedingCoverage: Int,
    val categoriesNeedingRefresh: Int,
    val automatedRefreshCategories: Int,
    val items: List<CatalogGovernanceCoverageItem>,
)

data class CatalogGovernanceCoverageItem(
    val categoryCode: String,
    val categoryTitle: String,
    val readiness: CatalogCategoryReadiness,
    val completenessGatePassed: Boolean,
    val refreshMode: String,
    val latestRefreshStatus: String? = null,
    val latestRefreshPublishStatus: String? = null,
    val latestRefreshFinishedAt: Long? = null,
    val needsCoverage: Boolean,
    val needsRefresh: Boolean,
    val attentionLevel: String,
    val attentionReasons: List<String> = emptyList(),
)

fun buildCatalogGovernanceCoverageSummary(
    readinessInventory: List<CatalogGovernanceAdminReadinessItem>,
    refreshStatus: CatalogGovernanceAdminRefreshStatus?,
    nowMs: Long = System.currentTimeMillis(),
): CatalogGovernanceCoverageSummary {
    val automatedCategoryCode = refreshStatus?.effectiveCategoryCode
    val refreshNeedsAttention = refreshStatus?.let { status ->
        val latestFinishedAt = status.latestRunFinishedAt
        val staleThresholdMs = maxOf(
            status.pollIntervalMs * 2,
            TimeUnit.HOURS.toMillis(2),
        )
        when {
            !status.enabled -> true
            status.matchedSourceCount <= 0 -> true
            status.latestRunStatus == null -> true
            status.latestRunStatus != "COMPLETED" -> true
            status.latestRunPublishStatus == "FAILED" -> true
            latestFinishedAt == null -> true
            nowMs - latestFinishedAt > staleThresholdMs -> true
            else -> false
        }
    } ?: false
    val items = readinessInventory
        .map { item ->
            val isAutomatedCategory = automatedCategoryCode != null &&
                item.category.code.equals(automatedCategoryCode, ignoreCase = true)
            val needsCoverage = !item.completenessGatePassed ||
                item.readiness != CatalogCategoryReadiness.READY ||
                item.blockingIssues.isNotEmpty()
            val needsRefresh = isAutomatedCategory && refreshNeedsAttention
            val reasons = buildList {
                if (!item.completenessGatePassed) add("completeness gate не пройден")
                if (item.readiness != CatalogCategoryReadiness.READY) {
                    add("readiness=${item.readiness.name}")
                }
                if (item.blockingIssues.isNotEmpty()) {
                    add("blocking=${item.blockingIssues.take(2).joinToString()}")
                }
                if (isAutomatedCategory) {
                    when {
                        !refreshStatus.enabled -> add("scheduler выключен")
                        refreshStatus.matchedSourceCount <= 0 -> add("нет подключённых sources")
                        refreshStatus.latestRunStatus == null -> add("refresh ещё не запускался")
                        refreshStatus.latestRunStatus != "COMPLETED" ->
                            add("последний refresh=${refreshStatus.latestRunStatus}")
                        refreshStatus.latestRunPublishStatus == "FAILED" -> add("publish завершился ошибкой")
                        refreshStatus.latestRunFinishedAt == null -> add("нет времени завершения refresh")
                        else -> {
                            val latestFinishedAt = refreshStatus.latestRunFinishedAt ?: 0L
                            val staleThresholdMs = maxOf(
                                refreshStatus.pollIntervalMs * 2,
                                TimeUnit.HOURS.toMillis(2),
                            )
                            if (nowMs - latestFinishedAt > staleThresholdMs) {
                                add("refresh устарел")
                            }
                        }
                    }
                }
            }
            val attentionLevel = when {
                needsRefresh -> "CRITICAL"
                needsCoverage -> "WARNING"
                else -> "OK"
            }
            CatalogGovernanceCoverageItem(
                categoryCode = item.category.code,
                categoryTitle = item.category.title.resolve(locale = "ru", fallback = item.category.code)
                    ?: item.category.code,
                readiness = item.readiness,
                completenessGatePassed = item.completenessGatePassed,
                refreshMode = if (isAutomatedCategory) "AUTOMATED" else "MANUAL",
                latestRefreshStatus = if (isAutomatedCategory) refreshStatus?.latestRunStatus else null,
                latestRefreshPublishStatus = if (isAutomatedCategory) refreshStatus?.latestRunPublishStatus else null,
                latestRefreshFinishedAt = if (isAutomatedCategory) refreshStatus?.latestRunFinishedAt else null,
                needsCoverage = needsCoverage,
                needsRefresh = needsRefresh,
                attentionLevel = attentionLevel,
                attentionReasons = reasons,
            )
        }
        .sortedWith(
            compareByDescending<CatalogGovernanceCoverageItem> { it.attentionLevel != "OK" }
                .thenByDescending { it.needsRefresh }
                .thenBy { it.categoryCode },
        )
    return CatalogGovernanceCoverageSummary(
        totalCategories = items.size,
        categoriesNeedingCoverage = items.count { it.needsCoverage },
        categoriesNeedingRefresh = items.count { it.needsRefresh },
        automatedRefreshCategories = items.count { it.refreshMode == "AUTOMATED" },
        items = items,
    )
}
