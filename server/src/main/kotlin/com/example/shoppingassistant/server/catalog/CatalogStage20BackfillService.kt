package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory

data class CatalogStage20BackfillStartupConfig(
    val enabled: Boolean,
    val autoRepairEnabled: Boolean,
    val syncMode: CatalogSeedSyncMode,
    val ensureReferencedCategories: Boolean,
    val exitAfterRun: Boolean,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): CatalogStage20BackfillStartupConfig {
            val enabled = env.flag("CATALOG_STAGE20_BACKFILL_ON_STARTUP")
            val autoRepairEnabled = env.flag("CATALOG_STAGE20_AUTO_REPAIR_ON_STARTUP", default = true)
            val syncMode = CatalogSeedSyncMode.fromEnv(env["CATALOG_STAGE20_BACKFILL_SYNC_MODE"])
            val ensureReferencedCategories = env.flag("CATALOG_STAGE20_BACKFILL_ENSURE_REFERENCED_CATEGORIES")
            val exitAfterRun = env.flag("CATALOG_STAGE20_BACKFILL_EXIT_AFTER_RUN")
            return CatalogStage20BackfillStartupConfig(
                enabled = enabled,
                autoRepairEnabled = autoRepairEnabled,
                syncMode = syncMode,
                ensureReferencedCategories = ensureReferencedCategories,
                exitAfterRun = exitAfterRun,
            )
        }
    }
}

interface CatalogStage20BackfillService {
    suspend fun runBackfill(
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
        ensureReferencedCategories: Boolean = false,
    ): CatalogStage20BackfillReport

    suspend fun runAutoRepairIfNeeded(): CatalogStage20BackfillReport?
}

class CatalogStage20BackfillServiceImpl : CatalogStage20BackfillService {
    override suspend fun runBackfill(
        syncMode: CatalogSeedSyncMode,
        ensureReferencedCategories: Boolean,
    ): CatalogStage20BackfillReport = DatabaseFactory.dbQuery {
        val report = CatalogSeeder.seedStage20Taxonomy(
            syncMode = syncMode,
            ensureReferencedCategories = ensureReferencedCategories,
        )
        logger.info(
            "catalog.stage20.backfill.completed syncMode={} ensureReferencedCategories={} categoriesEnsured={} aliases={} browseNodes={} aliasEntries={} googleMappings={}",
            report.syncMode,
            report.ensureReferencedCategories,
            report.categoriesEnsured,
            report.categoryAliasesTotal,
            report.browseNodesTotal,
            report.aliasEntriesTotal,
            report.googleMappingsTotal,
        )
        report
    }

    override suspend fun runAutoRepairIfNeeded(): CatalogStage20BackfillReport? = DatabaseFactory.dbQuery {
        val observed = CatalogStage20SeedCoverage(
            categoryAliasesTotal = CategoryAliasesTable.selectAll().count(),
            browseNodesTotal = BrowseNodesTable.selectAll().count(),
            aliasEntriesTotal = AliasEntriesTable.selectAll().count(),
            googleMappingsTotal = GoogleTaxonomyMappingsTable.selectAll().count(),
        )
        val expected = CatalogStage20SeedCoverage.expectedFromSeed()
        if (!observed.isIncompleteAgainst(expected)) {
            logger.info(
                "catalog.stage20.auto_repair.skipped aliases={} browseNodes={} aliasEntries={} googleMappings={}",
                observed.categoryAliasesTotal,
                observed.browseNodesTotal,
                observed.aliasEntriesTotal,
                observed.googleMappingsTotal,
            )
            return@dbQuery null
        }

        logger.warn(
            "catalog.stage20.auto_repair.triggered observedAliases={} expectedAliases={} observedBrowseNodes={} expectedBrowseNodes={} observedAliasEntries={} expectedAliasEntries={} observedGoogleMappings={} expectedGoogleMappings={}",
            observed.categoryAliasesTotal,
            expected.categoryAliasesTotal,
            observed.browseNodesTotal,
            expected.browseNodesTotal,
            observed.aliasEntriesTotal,
            expected.aliasEntriesTotal,
            observed.googleMappingsTotal,
            expected.googleMappingsTotal,
        )
        val report = CatalogSeeder.seedStage20Taxonomy(
            syncMode = CatalogSeedSyncMode.UPSERT_ONLY,
            ensureReferencedCategories = true,
        )
        logger.info(
            "catalog.stage20.auto_repair.completed categoriesEnsured={} aliases={} browseNodes={} aliasEntries={} googleMappings={}",
            report.categoriesEnsured,
            report.categoryAliasesTotal,
            report.browseNodesTotal,
            report.aliasEntriesTotal,
            report.googleMappingsTotal,
        )
        report
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(CatalogStage20BackfillServiceImpl::class.java)
    }
}

internal data class CatalogStage20SeedCoverage(
    val categoryAliasesTotal: Long,
    val browseNodesTotal: Long,
    val aliasEntriesTotal: Long,
    val googleMappingsTotal: Long,
) {
    fun isIncompleteAgainst(expected: CatalogStage20SeedCoverage): Boolean =
        categoryAliasesTotal < expected.categoryAliasesTotal ||
            browseNodesTotal < expected.browseNodesTotal ||
            aliasEntriesTotal < expected.aliasEntriesTotal ||
            googleMappingsTotal < expected.googleMappingsTotal

    companion object {
        fun expectedFromSeed(): CatalogStage20SeedCoverage = CatalogStage20SeedCoverage(
            categoryAliasesTotal = CatalogSeed.categoryAliases.size.toLong(),
            browseNodesTotal = CatalogSeed.browseNodes.size.toLong(),
            aliasEntriesTotal = CatalogSeed.aliasEntries.size.toLong(),
            googleMappingsTotal = CatalogSeed.googleMappings.size.toLong(),
        )
    }
}

private fun Map<String, String>.flag(name: String, default: Boolean = false): Boolean =
    (this[name] ?: default.toString()).equals("true", ignoreCase = true)
