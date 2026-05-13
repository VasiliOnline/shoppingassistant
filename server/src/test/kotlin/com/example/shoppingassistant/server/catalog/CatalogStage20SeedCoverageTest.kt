package com.example.shoppingassistant.server.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStage20SeedCoverageTest {
    @Test
    fun isIncompleteAgainst_returnsTrue_whenAnyStage20SourceTableIsBelowSeedCoverage() {
        val expected = CatalogStage20SeedCoverage(
            categoryAliasesTotal = 100,
            browseNodesTotal = 300,
            aliasEntriesTotal = 1700,
            googleMappingsTotal = 66,
        )
        val observed = CatalogStage20SeedCoverage(
            categoryAliasesTotal = 100,
            browseNodesTotal = 300,
            aliasEntriesTotal = 1700,
            googleMappingsTotal = 0,
        )

        assertTrue(observed.isIncompleteAgainst(expected))
    }

    @Test
    fun isIncompleteAgainst_returnsFalse_whenObservedCoverageMeetsOrExceedsSeed() {
        val expected = CatalogStage20SeedCoverage(
            categoryAliasesTotal = 100,
            browseNodesTotal = 300,
            aliasEntriesTotal = 1700,
            googleMappingsTotal = 66,
        )
        val observed = CatalogStage20SeedCoverage(
            categoryAliasesTotal = 101,
            browseNodesTotal = 300,
            aliasEntriesTotal = 1701,
            googleMappingsTotal = 66,
        )

        assertFalse(observed.isIncompleteAgainst(expected))
    }
}
