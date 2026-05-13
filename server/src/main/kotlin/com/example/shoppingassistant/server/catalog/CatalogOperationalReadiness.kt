package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.server.db.DatabaseFactory
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.util.Locale

internal data class CatalogOperationalReadinessReport(
    val sampleCount: Int,
    val normalizedCount: Int,
    val droppedCount: Int,
    val unknownAttributeCount: Int,
    val requiredForCategoryMissingCount: Int,
    val categoryConfidenceLowCount: Int,
    val droppedRate: Double,
    val unknownAttributeRate: Double,
    val requiredMissingRate: Double,
    val lowConfidenceRate: Double,
    val blockingIssues: List<String>,
)

internal data class CatalogOperationalReadinessHistoryEntry(
    val windowEndDate: LocalDate,
    val report: CatalogOperationalReadinessReport,
)

private data class CatalogOperationalMetricRow(
    val metricDate: LocalDate,
    val normalizedCount: Int,
    val droppedCount: Int,
    val unknownAttributeCount: Int,
    val requiredForCategoryMissingCount: Int,
    val categoryConfidenceLowCount: Int,
)

internal suspend fun loadOperationalReadinessReport(
    categoryCode: String,
): CatalogOperationalReadinessReport {
    val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
    if (normalizedCategoryCode.isEmpty() || !isOperationalLeafCategory(normalizedCategoryCode)) {
        return emptyOperationalReadinessReport()
    }

    val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    val windowStart = today.minus(DatePeriod(days = OPERATIONAL_WINDOW_DAYS - 1))
    val rows = loadOperationalMetricRows(
        categoryCode = normalizedCategoryCode,
        fromDate = windowStart,
        toDate = today,
    )
    return computeOperationalReadinessReport(rows)
}

internal suspend fun loadOperationalReadinessHistory(
    categoryCode: String,
    days: Int,
): List<CatalogOperationalReadinessHistoryEntry> {
    val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
    if (normalizedCategoryCode.isEmpty() || !isOperationalLeafCategory(normalizedCategoryCode)) {
        return emptyList()
    }

    val normalizedDays = days.coerceIn(1, MAX_OPERATIONAL_HISTORY_DAYS)
    val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    val historyStart = today.minus(DatePeriod(days = normalizedDays - 1))
    val metricsStart = historyStart.minus(DatePeriod(days = OPERATIONAL_WINDOW_DAYS - 1))
    val rows = loadOperationalMetricRows(
        categoryCode = normalizedCategoryCode,
        fromDate = metricsStart,
        toDate = today,
    )

    return generateSequence(historyStart) { current ->
        current.takeIf { it < today }?.plus(DatePeriod(days = 1))
    }
        .take(normalizedDays)
        .map { windowEndDate ->
            val windowStart = windowEndDate.minus(DatePeriod(days = OPERATIONAL_WINDOW_DAYS - 1))
            val windowRows = rows.filter { row ->
                row.metricDate >= windowStart && row.metricDate <= windowEndDate
            }
            CatalogOperationalReadinessHistoryEntry(
                windowEndDate = windowEndDate,
                report = computeOperationalReadinessReport(windowRows),
            )
        }
        .toList()
}

internal fun applyOperationalReadiness(
    spec: CatalogCategoryEffectiveSpec,
    report: CatalogOperationalReadinessReport,
): CatalogCategoryEffectiveSpec {
    val editorialReadiness = spec.meta.editorialReadiness
    val operationalReadiness = when {
        editorialReadiness == CatalogCategoryReadiness.INTERNAL -> CatalogCategoryReadiness.INTERNAL
        report.blockingIssues.isEmpty() -> CatalogCategoryReadiness.READY
        else -> CatalogCategoryReadiness.BETA
    }
    val mergedIssues = (spec.meta.editorialBlockingIssues + report.blockingIssues).distinct()
    val finalReadiness = combineReadiness(
        editorialReadiness = editorialReadiness,
        operationalReadiness = operationalReadiness,
    )

    return spec.copy(
        readiness = finalReadiness,
        meta = spec.meta.copy(
            operationalReadiness = operationalReadiness,
            operationalBlockingIssues = report.blockingIssues,
            operationalSampleCount = report.sampleCount,
            operationalDroppedRate = report.droppedRate,
            operationalUnknownAttributeRate = report.unknownAttributeRate,
            operationalRequiredMissingRate = report.requiredMissingRate,
            operationalLowConfidenceRate = report.lowConfidenceRate,
            readinessBlockingIssues = mergedIssues,
        ),
    )
}

internal fun combineReadiness(
    editorialReadiness: CatalogCategoryReadiness,
    operationalReadiness: CatalogCategoryReadiness,
): CatalogCategoryReadiness = when {
    editorialReadiness == CatalogCategoryReadiness.INTERNAL ||
        operationalReadiness == CatalogCategoryReadiness.INTERNAL ->
        CatalogCategoryReadiness.INTERNAL

    editorialReadiness == CatalogCategoryReadiness.BETA ||
        operationalReadiness == CatalogCategoryReadiness.BETA ->
        CatalogCategoryReadiness.BETA

    else -> CatalogCategoryReadiness.READY
}

private fun isOperationalLeafCategory(categoryCode: String): Boolean =
    CatalogSeed.categories.none { category ->
        category.parentCode?.trim()?.uppercase(Locale.ROOT) == categoryCode
    }

private suspend fun loadOperationalMetricRows(
    categoryCode: String,
    fromDate: LocalDate,
    toDate: LocalDate,
): List<CatalogOperationalMetricRow> = DatabaseFactory.dbQuery {
    val query = CatalogStage4ExecutionMetricsTable.selectAll()
    query.andWhere { CatalogStage4ExecutionMetricsTable.stream eq Stage4ExecutionStream.OFFERS_INGEST.code }
    query.andWhere { CatalogStage4ExecutionMetricsTable.metricDate greaterEq fromDate }
    query.andWhere { CatalogStage4ExecutionMetricsTable.metricDate lessEq toDate }
    query.toList()
}.mapNotNull { row ->
    val metadata = row[CatalogStage4ExecutionMetricsTable.metadata]
    if (metadata["categoryCode"]?.trim()?.uppercase(Locale.ROOT) != categoryCode) {
        return@mapNotNull null
    }
    CatalogOperationalMetricRow(
        metricDate = row[CatalogStage4ExecutionMetricsTable.metricDate],
        normalizedCount = row[CatalogStage4ExecutionMetricsTable.normalizedCount].coerceAtLeast(0),
        droppedCount = row[CatalogStage4ExecutionMetricsTable.droppedCount].coerceAtLeast(0),
        unknownAttributeCount = row[CatalogStage4ExecutionMetricsTable.unknownAttributeCount].coerceAtLeast(0),
        requiredForCategoryMissingCount = metadata["requiredForCategoryMissingCount"]
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0,
        categoryConfidenceLowCount = metadata["categoryConfidenceLowCount"]
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0,
    )
}

private fun computeOperationalReadinessReport(
    rows: List<CatalogOperationalMetricRow>,
): CatalogOperationalReadinessReport {
    val sampleCount = rows.size
    val normalizedCount = rows.sumOf { row -> row.normalizedCount }
    val droppedCount = rows.sumOf { row -> row.droppedCount }
    val unknownAttributeCount = rows.sumOf { row -> row.unknownAttributeCount }
    val requiredForCategoryMissingCount = rows.sumOf { row -> row.requiredForCategoryMissingCount }
    val categoryConfidenceLowCount = rows.sumOf { row -> row.categoryConfidenceLowCount }

    val issues = mutableListOf<String>()
    if (sampleCount < MIN_OPERATIONAL_SAMPLE_COUNT) {
        issues += "operational_samples_below_min:$sampleCount"
    }

    val normalizationBase = (normalizedCount + droppedCount).coerceAtLeast(1)
    val droppedRate = droppedCount.toDouble() / normalizationBase
    val unknownAttributeRate = unknownAttributeCount.toDouble() / normalizationBase
    val requiredMissingRate = if (sampleCount == 0) 1.0 else requiredForCategoryMissingCount.toDouble() / sampleCount
    val lowConfidenceRate = if (sampleCount == 0) 1.0 else categoryConfidenceLowCount.toDouble() / sampleCount

    if (droppedRate > MAX_DROPPED_RATE) {
        issues += "operational_dropped_rate_exceeded:${formatRate(droppedRate)}"
    }
    if (unknownAttributeRate > MAX_UNKNOWN_ATTRIBUTE_RATE) {
        issues += "operational_unknown_attribute_rate_exceeded:${formatRate(unknownAttributeRate)}"
    }
    if (requiredMissingRate > MAX_REQUIRED_MISSING_RATE) {
        issues += "operational_required_missing_rate_exceeded:${formatRate(requiredMissingRate)}"
    }
    if (categoryConfidenceLowCount > 0 && lowConfidenceRate > MAX_LOW_CONFIDENCE_RATE) {
        issues += "operational_low_confidence_rate_exceeded:${formatRate(lowConfidenceRate)}"
    }

    return CatalogOperationalReadinessReport(
        sampleCount = sampleCount,
        normalizedCount = normalizedCount,
        droppedCount = droppedCount,
        unknownAttributeCount = unknownAttributeCount,
        requiredForCategoryMissingCount = requiredForCategoryMissingCount,
        categoryConfidenceLowCount = categoryConfidenceLowCount,
        droppedRate = droppedRate,
        unknownAttributeRate = unknownAttributeRate,
        requiredMissingRate = requiredMissingRate,
        lowConfidenceRate = lowConfidenceRate,
        blockingIssues = issues.distinct(),
    )
}

private fun emptyOperationalReadinessReport(): CatalogOperationalReadinessReport =
    CatalogOperationalReadinessReport(
        sampleCount = 0,
        normalizedCount = 0,
        droppedCount = 0,
        unknownAttributeCount = 0,
        requiredForCategoryMissingCount = 0,
        categoryConfidenceLowCount = 0,
        droppedRate = 0.0,
        unknownAttributeRate = 0.0,
        requiredMissingRate = 0.0,
        lowConfidenceRate = 0.0,
        blockingIssues = emptyList(),
    )

private fun formatRate(rate: Double): String = "%.3f".format(Locale.US, rate)

internal const val OPERATIONAL_WINDOW_DAYS = 14
private const val MIN_OPERATIONAL_SAMPLE_COUNT = 3
internal const val MAX_OPERATIONAL_HISTORY_DAYS = 180
private const val MAX_DROPPED_RATE = 0.05
private const val MAX_UNKNOWN_ATTRIBUTE_RATE = 0.05
private const val MAX_REQUIRED_MISSING_RATE = 0.10
private const val MAX_LOW_CONFIDENCE_RATE = 0.10
