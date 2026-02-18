package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.TaxonomyValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxonomyGateTest {
    @Test
    fun validateOrThrow_passes_for_seeded_catalog() = runBlocking {
        val gate = TaxonomyGate(
            catalogRepository = CatalogRepositoryImpl(SeededCatalogDataSource()),
            categoryAliasRepository = CategoryAliasRepositoryImpl(),
            browseNodeRepository = BrowseNodeRepositoryImpl(),
            aliasEntryRepository = AliasEntryRepositoryImpl(),
            googleTaxonomyMappingRepository = GoogleTaxonomyMappingRepositoryImpl(),
            validator = TaxonomyValidator(),
        )

        gate.validateOrThrow()
    }

    @Test
    fun validate_returnsStatsForDebugDiagnostics() = runBlocking {
        val gate = TaxonomyGate(
            catalogRepository = CatalogRepositoryImpl(SeededCatalogDataSource()),
            categoryAliasRepository = CategoryAliasRepositoryImpl(),
            browseNodeRepository = BrowseNodeRepositoryImpl(),
            aliasEntryRepository = AliasEntryRepositoryImpl(),
            googleTaxonomyMappingRepository = GoogleTaxonomyMappingRepositoryImpl(),
            validator = TaxonomyValidator(),
        )

        val report = gate.validate()
        assertTrue(report.stats.categoriesCount > 0)
        assertTrue(report.stats.browseNodesCount > 0)
        assertTrue(report.stats.aliasEntriesCount > 0)
    }
}
