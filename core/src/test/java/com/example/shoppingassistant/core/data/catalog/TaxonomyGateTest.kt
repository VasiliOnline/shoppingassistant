package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.AliasKind
import com.example.shoppingassistant.domain.catalog.CategoryAlias
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.TaxonomyValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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

    @Test
    fun validate_usesTypedAliasEntriesAsAuthoritativeWhenLegacyAliasesConflict() = runBlocking {
        val conflictingLegacyAliases = object : CategoryAliasRepository {
            override suspend fun listAliases(): List<CategoryAlias> = listOf(
                CategoryAlias(alias = "camera", categoryCode = "TECH.CAMERAS"),
                CategoryAlias(alias = "camera", categoryCode = "TECH.CAMERAS_DRONES"),
            )
        }
        val gate = TaxonomyGate(
            catalogRepository = CatalogRepositoryImpl(SeededCatalogDataSource()),
            categoryAliasRepository = conflictingLegacyAliases,
            browseNodeRepository = BrowseNodeRepositoryImpl(),
            aliasEntryRepository = AliasEntryRepositoryImpl(),
            googleTaxonomyMappingRepository = GoogleTaxonomyMappingRepositoryImpl(),
            validator = TaxonomyValidator(),
        )

        val report = gate.validate()

        assertFalse(report.summary(), report.failIssues.any { it.code == "ALIAS_AMBIGUOUS" })
    }

    @Test
    fun validate_normalizesLegacyTypedAliasTargetsBeforeCollisionChecks() = runBlocking {
        val staleRemoteAliasEntries = object : AliasEntryRepository {
            override suspend fun listAliasEntries(locale: String?): List<AliasEntry> = listOf(
                AliasEntry(
                    locale = "en-US",
                    term = "camera",
                    normalizedTerm = "camera",
                    kind = AliasKind.CATEGORY,
                    targetCode = "TECH.CAMERAS",
                    weight = 90,
                ),
                AliasEntry(
                    locale = "en-US",
                    term = "camera",
                    normalizedTerm = "camera",
                    kind = AliasKind.CATEGORY,
                    targetCode = "TECH.CAMERAS_DRONES",
                    weight = 90,
                ),
            )
        }
        val gate = TaxonomyGate(
            catalogRepository = CatalogRepositoryImpl(SeededCatalogDataSource()),
            categoryAliasRepository = CategoryAliasRepositoryImpl(),
            browseNodeRepository = BrowseNodeRepositoryImpl(),
            aliasEntryRepository = staleRemoteAliasEntries,
            googleTaxonomyMappingRepository = GoogleTaxonomyMappingRepositoryImpl(),
            validator = TaxonomyValidator(),
        )

        val report = gate.validate()

        assertFalse(report.summary(), report.failIssues.any { it.code == "ALIAS_ENTRY_COLLISION" })
        assertFalse(report.summary(), report.failIssues.any { it.message.contains("TECH.CAMERAS") })
    }
}
