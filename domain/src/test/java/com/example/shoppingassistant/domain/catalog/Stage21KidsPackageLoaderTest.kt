package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21KidsPackageLoaderTest {

    @Test
    fun coverage_required_leaves_are_reachable_via_browse_tree() {
        val coverage = Stage21KidsPackageLoader.coverageGate
        val browseByCode = Stage21KidsPackageLoader.browseNodes.associateBy { it.browseCode }

        val missingLeafBrowsePaths = coverage.requiredLeafCodes.filter { leafCode ->
            Stage21KidsPackageLoader.browseNodes
                .asSequence()
                .filter { it.targetCategoryCode == leafCode }
                .none { node -> hasPathToRoot(node, browseByCode) }
        }

        assertTrue(
            "Required leaves without browse path to root: $missingLeafBrowsePaths",
            missingLeafBrowsePaths.isEmpty(),
        )
    }

    @Test
    fun coverage_minimums_met_for_aliases_and_golden_queries() {
        val coverage = Stage21KidsPackageLoader.coverageGate
        val requiredLeafs = coverage.requiredLeafCodes.toSet()
        val browseLeafByCode = Stage21KidsPackageLoader.browseNodes
            .asSequence()
            .mapNotNull { node ->
                val canonical = node.targetCategoryCode?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                node.browseCode to canonical
            }
            .toMap()

        val aliasesByLeaf = Stage21KidsPackageLoader.aliasSeedRows
            .asSequence()
            .filter { it.isActive && it.routeKind != HomeAliasRouteKind.BLOCKED }
            .mapNotNull { row ->
                when (row.routeKind) {
                    HomeAliasRouteKind.CANONICAL -> row.targetId.takeIf { it in requiredLeafs }
                    HomeAliasRouteKind.BROWSE_NODE -> browseLeafByCode[row.targetId]?.takeIf { it in requiredLeafs }
                    HomeAliasRouteKind.BLOCKED -> null
                }
            }
            .groupingBy { it }
            .eachCount()

        val goldenByLeaf = Stage21KidsPackageLoader.goldenQueries
            .asSequence()
            .filter { it.expectedTargetKind == GoldenTargetKind.CATEGORY_LEAF }
            .map { it.expectedCode }
            .filter { it in requiredLeafs }
            .groupingBy { it }
            .eachCount()

        val lowAlias = coverage.requiredLeafCodes.filter {
            aliasesByLeaf.getOrDefault(it, 0) < coverage.requirements.minAliasesPerLeaf
        }
        val lowGolden = coverage.requiredLeafCodes.filter {
            goldenByLeaf.getOrDefault(it, 0) < coverage.requirements.minGoldenQueriesPerLeaf
        }

        assertTrue("Leaf codes below min alias threshold: $lowAlias", lowAlias.isEmpty())
        assertTrue("Leaf codes below min golden threshold: $lowGolden", lowGolden.isEmpty())
    }

    @Test
    fun blocked_alias_share_is_within_coverage_limit() {
        val coverage = Stage21KidsPackageLoader.coverageGate
        val rows = Stage21KidsPackageLoader.aliasSeedRows
        val blockedCount = rows.count { it.routeKind == HomeAliasRouteKind.BLOCKED }
        val blockedShare = if (rows.isEmpty()) 0.0 else blockedCount / rows.size.toDouble()

        assertTrue(
            "Blocked aliases share=$blockedShare exceeds ${coverage.requirements.maxBlockedShareOfAliases}",
            blockedShare <= coverage.requirements.maxBlockedShareOfAliases + 1e-9,
        )
    }

    private fun hasPathToRoot(
        node: BrowseNode,
        browseByCode: Map<String, BrowseNode>,
    ): Boolean {
        var current: BrowseNode? = node
        val visited = HashSet<String>()
        while (current != null) {
            if (!visited.add(current.browseCode)) return false
            val parent = current.parentBrowseCode ?: return true
            current = browseByCode[parent] ?: return false
        }
        return false
    }
}
