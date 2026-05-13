package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21ApplPackageLoaderTest {

    @Test
    fun coverage_leaf_nodes_exist_in_browse_nodes() {
        val browseCodes = Stage21ApplPackageLoader.browseNodes.map { it.browseCode }.toSet()
        val missing = Stage21ApplPackageLoader.coverageGate.expectedLeafNodes.filterNot { it in browseCodes }
        assertTrue("Missing leaf nodes in browse package: $missing", missing.isEmpty())
    }

    @Test
    fun coverage_minimums_met_by_aliases_and_golden_queries() {
        val coverage = Stage21ApplPackageLoader.coverageGate

        val aliasesByNode = Stage21ApplPackageLoader.aliasSeedRows
            .asSequence()
            .filter { it.targetType == ApplAliasTargetType.NODE }
            .groupingBy { it.targetId }
            .eachCount()

        val goldenByNode = Stage21ApplPackageLoader.goldenQueries
            .asSequence()
            .filter { it.expectedTargetKind == GoldenTargetKind.BROWSE_NODE }
            .groupingBy { it.expectedCode }
            .eachCount()

        val lowAlias = coverage.expectedLeafNodes.filter {
            aliasesByNode.getOrDefault(it, 0) < coverage.minAliasesPerLeaf
        }
        val lowGolden = coverage.expectedLeafNodes.filter {
            goldenByNode.getOrDefault(it, 0) < coverage.minGoldenPerLeaf
        }

        assertTrue("Leaf nodes below min alias threshold: $lowAlias", lowAlias.isEmpty())
        assertTrue("Leaf nodes below min golden threshold: $lowGolden", lowGolden.isEmpty())
    }

    @Test
    fun required_ambiguous_queries_present_in_golden_set() {
        val goldenQueries = Stage21ApplPackageLoader.goldenQueries
            .map { it.queryText.trim().lowercase() }
            .toSet()
        val missing = Stage21ApplPackageLoader.coverageGate.requiredAmbiguousTests.filterNot {
            it.trim().lowercase() in goldenQueries
        }

        assertTrue("Missing required ambiguous tests in golden set: $missing", missing.isEmpty())
    }

    @Test
    fun appl_alias_seed_rows_define_explicit_runtime_semantics() {
        val conditioner = Stage21ApplPackageLoader.aliasSeedRows.first { it.query == "кондиционер" }
        val hairConditioner = Stage21ApplPackageLoader.aliasSeedRows.first { it.query == "кондиционер для волос" }

        assertEquals(AliasMatchKind.TOKEN, conditioner.matchKind)
        assertEquals(listOf("волос", "белья"), conditioner.negativeTokens)
        assertEquals(76, conditioner.weight)
        assertEquals(AliasSource.SEED, conditioner.source)

        assertEquals(AliasMatchKind.PREFIX, hairConditioner.matchKind)
        assertEquals(90, hairConditioner.weight)
        assertTrue(hairConditioner.negativeTokens.isEmpty())
    }
}
