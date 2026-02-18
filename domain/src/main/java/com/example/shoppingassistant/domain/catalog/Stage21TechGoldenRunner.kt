package com.example.shoppingassistant.domain.catalog

import kotlin.math.roundToInt

class Stage21TechGoldenRunner(
    private val queryRouter: Stage21TechQueryRouter = Stage21TechQueryRouter(),
) {
    private val goldenQueries: List<GoldenQuery> = CatalogSeed.stage21TechGoldenQueries
    private val coverageGate: Stage21CoverageGate = CatalogSeed.stage21TechCoverageGate

    suspend fun run(): Stage21TechGoldenReport {
        val maxRank = goldenQueries.maxOfOrNull { it.mustRankTopN }?.coerceAtLeast(3) ?: 3

        val items = goldenQueries.map { query ->
            val debug = queryRouter.routeWithCandidates(
                query = query.queryText,
                locale = query.locale,
                topN = maxRank,
            )

            val matchedRank = findMatchedRank(query, debug)
            val hitAt1 = matchedRank != null && matchedRank <= 1
            val hitAt3 = matchedRank != null && matchedRank <= 3
            val hitByRequirement = matchedRank != null && matchedRank <= query.mustRankTopN

            Stage21GoldenItemResult(
                queryId = query.queryId,
                locale = query.locale,
                queryText = query.queryText,
                expectedCode = query.expectedCode,
                expectedTargetKind = query.expectedTargetKind,
                mustRankTopN = query.mustRankTopN,
                matchedRank = matchedRank,
                topCandidates = debug.topCandidates,
                result = debug.result,
                hitAt1 = hitAt1,
                hitAt3 = hitAt3,
                hitByRequirement = hitByRequirement,
                wasAmbiguous = debug.wasAmbiguous,
                notes = query.notes,
            )
        }

        val total = items.size.coerceAtLeast(1)
        val hitsAt1 = items.count { it.hitAt1 }
        val hitsAt3 = items.count { it.hitAt3 }
        val hitsByRequirement = items.count { it.hitByRequirement }
        val ambiguousShare = items.count { it.wasAmbiguous } / total.toDouble()

        val perLeaf = items
            .asSequence()
            .filter { it.expectedTargetKind == GoldenTargetKind.CATEGORY_LEAF }
            .groupBy { it.expectedCode }
            .mapValues { (_, leafItems) ->
                val leafTotal = leafItems.size.coerceAtLeast(1)
                Stage21LeafRecall(
                    total = leafItems.size,
                    recallAt1 = leafItems.count { it.hitAt1 } / leafTotal.toDouble(),
                    recallAt3 = leafItems.count { it.hitAt3 } / leafTotal.toDouble(),
                )
            }

        val thresholds = Stage21GoldenThresholds(
            goldenMinQueries = coverageGate.thresholds.goldenMinQueries,
            goldenRecallTop1 = coverageGate.thresholds.goldenRecallTop1,
            goldenRecallTop3 = coverageGate.thresholds.goldenRecallTop3,
            ambiguousQueriesShareWarn = coverageGate.thresholds.ambiguousQueriesShareWarn,
        )

        val recallAt1 = hitsAt1 / total.toDouble()
        val recallAt3 = hitsAt3 / total.toDouble()
        val recallByRequirement = hitsByRequirement / total.toDouble()

        val meetsGoldenMinQueries = items.size >= thresholds.goldenMinQueries
        val meetsRecallTop1 = recallAt1 >= thresholds.goldenRecallTop1
        val meetsRecallTop3 = recallAt3 >= thresholds.goldenRecallTop3
        val ambiguousWarnExceeded = ambiguousShare > thresholds.ambiguousQueriesShareWarn

        return Stage21TechGoldenReport(
            total = items.size,
            hitsAt1 = hitsAt1,
            hitsAt3 = hitsAt3,
            hitsByRequirement = hitsByRequirement,
            recallAt1 = recallAt1,
            recallAt3 = recallAt3,
            recallByRequirement = recallByRequirement,
            ambiguousShare = ambiguousShare,
            thresholds = thresholds,
            meetsGoldenMinQueries = meetsGoldenMinQueries,
            meetsRecallTop1 = meetsRecallTop1,
            meetsRecallTop3 = meetsRecallTop3,
            ambiguousWarnExceeded = ambiguousWarnExceeded,
            perLeaf = perLeaf,
            failedItems = items.filterNot { it.hitByRequirement },
            allItems = items,
        )
    }

    private fun findMatchedRank(
        query: GoldenQuery,
        debug: QueryRoutingDebugResult,
    ): Int? {
        val fromCandidates = debug.topCandidates.indexOfFirst { candidate ->
            when (query.expectedTargetKind) {
                GoldenTargetKind.CATEGORY_LEAF ->
                    candidate.routeType == QueryRouteType.OPEN_CATEGORY && candidate.targetCode == query.expectedCode

                GoldenTargetKind.BROWSE_NODE ->
                    candidate.routeType == QueryRouteType.OPEN_BROWSE && candidate.targetCode == query.expectedCode
            }
        }.takeIf { it >= 0 }?.plus(1)
        if (fromCandidates != null) return fromCandidates

        return when (query.expectedTargetKind) {
            GoldenTargetKind.CATEGORY_LEAF -> {
                if (debug.result.routeType == QueryRouteType.OPEN_CATEGORY && debug.result.primaryTargetCode == query.expectedCode) {
                    1
                } else {
                    null
                }
            }

            GoldenTargetKind.BROWSE_NODE -> {
                if (debug.result.routeType == QueryRouteType.OPEN_BROWSE && debug.result.primaryTargetCode == query.expectedCode) {
                    1
                } else {
                    null
                }
            }
        }
    }
}

data class Stage21TechGoldenReport(
    val total: Int,
    val hitsAt1: Int,
    val hitsAt3: Int,
    val hitsByRequirement: Int,
    val recallAt1: Double,
    val recallAt3: Double,
    val recallByRequirement: Double,
    val ambiguousShare: Double,
    val thresholds: Stage21GoldenThresholds,
    val meetsGoldenMinQueries: Boolean,
    val meetsRecallTop1: Boolean,
    val meetsRecallTop3: Boolean,
    val ambiguousWarnExceeded: Boolean,
    val perLeaf: Map<String, Stage21LeafRecall>,
    val failedItems: List<Stage21GoldenItemResult>,
    val allItems: List<Stage21GoldenItemResult>,
) {
    val isPass: Boolean
        get() = meetsGoldenMinQueries && meetsRecallTop1 && meetsRecallTop3

    fun summary(maxFailedItems: Int = 20): String {
        val header = buildString {
            append("Golden runner: ")
            append(if (isPass) "PASS" else "FAIL")
            append(", queries=$total")
            append(", recall@1=${percent(recallAt1)}")
            append(", recall@3=${percent(recallAt3)}")
            append(", recall@mustN=${percent(recallByRequirement)}")
            append(", ambiguousShare=${percent(ambiguousShare)}")
        }

        if (failedItems.isEmpty()) return header

        val examples = failedItems
            .take(maxFailedItems.coerceAtLeast(1))
            .joinToString(separator = "\n") { item ->
                val top = item.topCandidates.take(3).joinToString { "${it.targetCode}:${it.score}" }
                "${item.queryId} '${item.queryText}' expected=${item.expectedCode}@${item.mustRankTopN} actualTop=[$top]"
            }
        val suffix = if (failedItems.size > maxFailedItems) "\n... and ${failedItems.size - maxFailedItems} more" else ""
        return "$header\n$examples$suffix"
    }

    private fun percent(value: Double): String =
        "${(value * 10000.0).roundToInt() / 100.0}%"
}

data class Stage21GoldenThresholds(
    val goldenMinQueries: Int,
    val goldenRecallTop1: Double,
    val goldenRecallTop3: Double,
    val ambiguousQueriesShareWarn: Double,
)

data class Stage21LeafRecall(
    val total: Int,
    val recallAt1: Double,
    val recallAt3: Double,
)

data class Stage21GoldenItemResult(
    val queryId: String,
    val locale: String,
    val queryText: String,
    val expectedCode: String,
    val expectedTargetKind: GoldenTargetKind,
    val mustRankTopN: Int,
    val matchedRank: Int?,
    val topCandidates: List<QueryRoutingCandidate>,
    val result: QueryRoutingResult,
    val hitAt1: Boolean,
    val hitAt3: Boolean,
    val hitByRequirement: Boolean,
    val wasAmbiguous: Boolean,
    val notes: String?,
)
