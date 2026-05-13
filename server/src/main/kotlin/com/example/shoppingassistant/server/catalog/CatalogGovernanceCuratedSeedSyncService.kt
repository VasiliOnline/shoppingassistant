package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeedPack
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed.toBrandCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed.toFamilyCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed.toModelCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed.toAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed.toValueCanon
import com.example.shoppingassistant.server.db.DatabaseFactory

data class CatalogGovernanceCuratedSeedSyncReport(
    val packsSynced: Int,
    val packCodes: List<String> = emptyList(),
    val sourceSnapshotIds: List<Long> = emptyList(),
    val brandsSynced: Int,
    val familiesSynced: Int,
    val modelsSynced: Int,
    val canonicalValuesSynced: Int,
    val aliasesSynced: Int,
    val artifacts: CatalogGovernanceServingArtifacts,
)

class CatalogGovernanceCuratedSeedSyncService(
    private val repository: CatalogGovernanceRepository,
    private val projectionService: CatalogGovernanceServingProjectionService,
) {
    suspend fun syncTopCategoryCoverage(
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
    ): CatalogGovernanceCuratedSeedSyncReport {
        return syncPacks(
            packs = CatalogGovernanceCuratedSeed.snapshot.packs,
            syncMode = syncMode,
            publishArtifacts = true,
        )
    }

    suspend fun syncPacks(
        packs: List<CatalogGovernanceCuratedSeedPack>,
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
        publishArtifacts: Boolean = true,
    ): CatalogGovernanceCuratedSeedSyncReport {
        DatabaseFactory.dbQuery {
            CatalogSeeder.syncRuntimeContracts(syncMode = syncMode)
        }
        val now = System.currentTimeMillis()
        var brandsSynced = 0
        var familiesSynced = 0
        var modelsSynced = 0
        var canonicalValuesSynced = 0
        var aliasesSynced = 0
        val sourceSnapshotIds = mutableListOf<Long>()
        val packCodes = packs.map { it.packCode }

        packs.forEach { pack ->
            val source = repository.upsertSourceSnapshot(pack.toSourceSnapshot(capturedAt = now))
            source.id?.let(sourceSnapshotIds::add)

            pack.brands.forEach { brand ->
                repository.upsertBrand(brand.toBrandCanon(pack = pack, now = now))
                brandsSynced += 1
                aliasesSynced += syncAliases(
                    brand.toAliasCanon(pack = pack, sourceSnapshotId = source.id, now = now),
                )
            }

            pack.families.forEach { family ->
                repository.upsertProductFamily(family.toFamilyCanon(pack = pack, now = now))
                familiesSynced += 1
                aliasesSynced += syncAliases(
                    family.toAliasCanon(pack = pack, sourceSnapshotId = source.id, now = now),
                )
            }

            pack.models.forEach { model ->
                repository.upsertModel(model.toModelCanon(pack = pack, now = now))
                modelsSynced += 1
                aliasesSynced += syncAliases(
                    model.toAliasCanon(pack = pack, sourceSnapshotId = source.id, now = now),
                )
            }

            pack.canonicalValues.forEach { value ->
                repository.upsertAttributeValueCanon(value.toValueCanon(pack = pack, now = now))
                canonicalValuesSynced += 1
                aliasesSynced += syncAliases(
                    value.toAliasCanon(pack = pack, sourceSnapshotId = source.id, now = now),
                )
            }
        }

        val artifacts = if (publishArtifacts) {
            projectionService.syncDatabaseServingArtifacts(syncMode = syncMode)
        } else {
            projectionService.buildServingArtifacts()
        }
        return CatalogGovernanceCuratedSeedSyncReport(
            packsSynced = packs.size,
            packCodes = packCodes,
            sourceSnapshotIds = sourceSnapshotIds.distinct(),
            brandsSynced = brandsSynced,
            familiesSynced = familiesSynced,
            modelsSynced = modelsSynced,
            canonicalValuesSynced = canonicalValuesSynced,
            aliasesSynced = aliasesSynced,
            artifacts = artifacts,
        )
    }

    private suspend fun syncAliases(
        aliases: List<com.example.shoppingassistant.domain.catalog.CatalogAliasCanon>,
    ): Int {
        aliases.forEach { alias ->
            repository.upsertAlias(alias)
        }
        return aliases.size
    }
}
