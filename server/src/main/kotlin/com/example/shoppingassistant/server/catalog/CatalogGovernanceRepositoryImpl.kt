package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon
import com.example.shoppingassistant.domain.catalog.CatalogBrandCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecision
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionAction
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionEntityKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceTier
import com.example.shoppingassistant.domain.catalog.CatalogModelCanon
import com.example.shoppingassistant.domain.catalog.CatalogProductFamilyCanon
import com.example.shoppingassistant.domain.catalog.CatalogValueCandidate
import com.example.shoppingassistant.domain.catalog.CatalogValueObservation
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CatalogGovernanceRepositoryImpl : CatalogGovernanceRepository {

    override suspend fun upsertSourceSnapshot(snapshot: CatalogGovernanceSourceSnapshot): CatalogGovernanceSourceSnapshot =
        DatabaseFactory.dbQuery {
            val normalized = snapshot.normalize()
            val existing = findSourceRow(normalized)
            if (existing == null) {
                CatalogGovernanceSourcesTable.insert { stmt ->
                    stmt[sourceCode] = normalized.sourceCode
                    stmt[externalRef] = normalized.externalRef
                    stmt[displayName] = normalized.displayName
                    stmt[tier] = normalized.tier.name
                    stmt[defaultLocale] = normalized.defaultLocale
                    stmt[marketCode] = normalized.marketCode
                    stmt[sourceVersion] = normalized.sourceVersion
                    stmt[sourceUri] = normalized.sourceUri
                    stmt[checksum] = normalized.checksum
                    stmt[metadata] = normalized.metadata
                    stmt[capturedAt] = normalized.capturedAt
                }.resultedValues!!.single().toSourceSnapshot()
            } else {
                CatalogGovernanceSourcesTable.update({ CatalogGovernanceSourcesTable.id eq existing[CatalogGovernanceSourcesTable.id] }) { stmt ->
                    stmt[sourceCode] = normalized.sourceCode
                    stmt[externalRef] = normalized.externalRef
                    stmt[displayName] = normalized.displayName
                    stmt[tier] = normalized.tier.name
                    stmt[defaultLocale] = normalized.defaultLocale
                    stmt[marketCode] = normalized.marketCode
                    stmt[sourceVersion] = normalized.sourceVersion
                    stmt[sourceUri] = normalized.sourceUri
                    stmt[checksum] = normalized.checksum
                    stmt[metadata] = normalized.metadata
                    stmt[capturedAt] = normalized.capturedAt
                }
                CatalogGovernanceSourcesTable.selectAll()
                    .single { it[CatalogGovernanceSourcesTable.id] == existing[CatalogGovernanceSourcesTable.id] }
                    .toSourceSnapshot()
            }
        }

    override suspend fun upsertBrand(brand: CatalogBrandCanon): CatalogBrandCanon = DatabaseFactory.dbQuery {
        val normalized = brand.normalize()
        val updated = CatalogGovernanceBrandsTable.update({ CatalogGovernanceBrandsTable.code eq normalized.code }) { stmt ->
            stmt[code] = normalized.code
            stmt[labels] = normalized.labels.nullIfBlank()
            stmt[normalizedKey] = normalized.normalizedKey
            stmt[status] = normalized.status.name
            stmt[primaryCategoryCode] = normalized.primaryCategoryCode
            stmt[primarySegment] = normalized.primarySegment?.name
            stmt[metadata] = normalized.metadata
            stmt[createdAt] = normalized.createdAt
            stmt[updatedAt] = normalized.updatedAt
        }
        if (updated == 0) {
            CatalogGovernanceBrandsTable.insert { stmt ->
                stmt[code] = normalized.code
                stmt[labels] = normalized.labels.nullIfBlank()
                stmt[normalizedKey] = normalized.normalizedKey
                stmt[status] = normalized.status.name
                stmt[primaryCategoryCode] = normalized.primaryCategoryCode
                stmt[primarySegment] = normalized.primarySegment?.name
                stmt[metadata] = normalized.metadata
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }
        }
        CatalogGovernanceBrandsTable.selectAll()
            .single { it[CatalogGovernanceBrandsTable.code] == normalized.code }
            .toBrandCanon()
    }

    override suspend fun upsertProductFamily(family: CatalogProductFamilyCanon): CatalogProductFamilyCanon = DatabaseFactory.dbQuery {
        val normalized = family.normalize()
        val updated = CatalogGovernanceProductFamiliesTable.update({
            CatalogGovernanceProductFamiliesTable.code eq normalized.code
        }) { stmt ->
            stmt[code] = normalized.code
            stmt[brandCode] = normalized.brandCode
            stmt[labels] = normalized.labels.nullIfBlank()
            stmt[normalizedKey] = normalized.normalizedKey
            stmt[prettyModelPrefix] = normalized.prettyModelPrefix
            stmt[variantTokens] = normalized.variantTokens
            stmt[accessoryBlockers] = normalized.accessoryBlockers
            stmt[defaultCategoryCode] = normalized.defaultCategoryCode
            stmt[status] = normalized.status.name
            stmt[metadata] = normalized.metadata
            stmt[createdAt] = normalized.createdAt
            stmt[updatedAt] = normalized.updatedAt
        }
        if (updated == 0) {
            CatalogGovernanceProductFamiliesTable.insert { stmt ->
                stmt[code] = normalized.code
                stmt[brandCode] = normalized.brandCode
                stmt[labels] = normalized.labels.nullIfBlank()
                stmt[normalizedKey] = normalized.normalizedKey
                stmt[prettyModelPrefix] = normalized.prettyModelPrefix
                stmt[variantTokens] = normalized.variantTokens
                stmt[accessoryBlockers] = normalized.accessoryBlockers
                stmt[defaultCategoryCode] = normalized.defaultCategoryCode
                stmt[status] = normalized.status.name
                stmt[metadata] = normalized.metadata
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }
        }
        CatalogGovernanceProductFamiliesTable.selectAll()
            .single { it[CatalogGovernanceProductFamiliesTable.code] == normalized.code }
            .toProductFamilyCanon()
    }

    override suspend fun upsertModel(model: CatalogModelCanon): CatalogModelCanon = DatabaseFactory.dbQuery {
        val normalized = model.normalize()
        val updated = CatalogGovernanceModelsTable.update({ CatalogGovernanceModelsTable.code eq normalized.code }) { stmt ->
            stmt[code] = normalized.code
            stmt[brandCode] = normalized.brandCode
            stmt[familyCode] = normalized.familyCode
            stmt[labels] = normalized.labels.nullIfBlank()
            stmt[normalizedKey] = normalized.normalizedKey
            stmt[defaultCategoryCode] = normalized.defaultCategoryCode
            stmt[releaseYear] = normalized.releaseYear
            stmt[status] = normalized.status.name
            stmt[metadata] = normalized.metadata
            stmt[createdAt] = normalized.createdAt
            stmt[updatedAt] = normalized.updatedAt
        }
        if (updated == 0) {
            CatalogGovernanceModelsTable.insert { stmt ->
                stmt[code] = normalized.code
                stmt[brandCode] = normalized.brandCode
                stmt[familyCode] = normalized.familyCode
                stmt[labels] = normalized.labels.nullIfBlank()
                stmt[normalizedKey] = normalized.normalizedKey
                stmt[defaultCategoryCode] = normalized.defaultCategoryCode
                stmt[releaseYear] = normalized.releaseYear
                stmt[status] = normalized.status.name
                stmt[metadata] = normalized.metadata
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }
        }
        CatalogGovernanceModelsTable.selectAll()
            .single { it[CatalogGovernanceModelsTable.code] == normalized.code }
            .toModelCanon()
    }

    override suspend fun upsertAttributeValueCanon(value: CatalogAttributeValueCanon): CatalogAttributeValueCanon =
        DatabaseFactory.dbQuery {
            val normalized = value.normalize()
            val existing = findValueCanonRow(normalized)
            if (existing == null) {
                CatalogGovernanceValueCanonTable.insert { stmt ->
                    stmt[attributeCode] = normalized.attributeCode
                    stmt[canonicalCode] = normalized.canonicalCode
                    stmt[canonicalValue] = normalized.canonicalValue
                    stmt[labels] = normalized.labels.nullIfBlank()
                    stmt[canonicalLocale] = normalized.canonicalLocale
                    stmt[normalizedValue] = normalized.normalizedValue
                    stmt[categoryCode] = normalized.scope.categoryCode
                    stmt[brandCode] = normalized.scope.brandCode
                    stmt[familyCode] = normalized.scope.familyCode
                    stmt[modelCode] = normalized.scope.modelCode
                    stmt[status] = normalized.status.name
                    stmt[metadata] = normalized.metadata
                    stmt[createdAt] = normalized.createdAt
                    stmt[updatedAt] = normalized.updatedAt
                }.resultedValues!!.single().toAttributeValueCanon()
            } else {
                CatalogGovernanceValueCanonTable.update({
                    CatalogGovernanceValueCanonTable.id eq existing[CatalogGovernanceValueCanonTable.id]
                }) { stmt ->
                    stmt[attributeCode] = normalized.attributeCode
                    stmt[canonicalCode] = normalized.canonicalCode
                    stmt[canonicalValue] = normalized.canonicalValue
                    stmt[labels] = normalized.labels.nullIfBlank()
                    stmt[canonicalLocale] = normalized.canonicalLocale
                    stmt[normalizedValue] = normalized.normalizedValue
                    stmt[categoryCode] = normalized.scope.categoryCode
                    stmt[brandCode] = normalized.scope.brandCode
                    stmt[familyCode] = normalized.scope.familyCode
                    stmt[modelCode] = normalized.scope.modelCode
                    stmt[status] = normalized.status.name
                    stmt[metadata] = normalized.metadata
                    stmt[createdAt] = normalized.createdAt
                    stmt[updatedAt] = normalized.updatedAt
                }
                CatalogGovernanceValueCanonTable.selectAll()
                    .single { it[CatalogGovernanceValueCanonTable.id] == existing[CatalogGovernanceValueCanonTable.id] }
                    .toAttributeValueCanon()
            }
        }

    override suspend fun upsertAlias(alias: CatalogAliasCanon): CatalogAliasCanon = DatabaseFactory.dbQuery {
        val normalized = alias.normalize()
        val existing = findAliasRow(normalized)
        if (existing == null) {
            CatalogGovernanceAliasesTable.insert { stmt ->
                stmt[locale] = normalized.locale
                stmt[marketCode] = normalized.marketCode
                stmt[aliasText] = normalized.aliasText
                stmt[normalizedAlias] = normalized.normalizedAlias
                stmt[targetKind] = normalized.targetKind.name
                stmt[targetCode] = normalized.targetCode
                stmt[attributeCode] = normalized.attributeCode
                stmt[categoryCode] = normalized.scope.categoryCode
                stmt[brandCode] = normalized.scope.brandCode
                stmt[familyCode] = normalized.scope.familyCode
                stmt[modelCode] = normalized.scope.modelCode
                stmt[sourceSnapshotId] = normalized.sourceSnapshotId
                stmt[confidence] = normalized.confidence
                stmt[status] = normalized.status.name
                stmt[metadata] = normalized.metadata
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }.resultedValues!!.single().toAliasCanon()
        } else {
            CatalogGovernanceAliasesTable.update({
                CatalogGovernanceAliasesTable.id eq existing[CatalogGovernanceAliasesTable.id]
            }) { stmt ->
                stmt[locale] = normalized.locale
                stmt[marketCode] = normalized.marketCode
                stmt[aliasText] = normalized.aliasText
                stmt[normalizedAlias] = normalized.normalizedAlias
                stmt[targetKind] = normalized.targetKind.name
                stmt[targetCode] = normalized.targetCode
                stmt[attributeCode] = normalized.attributeCode
                stmt[categoryCode] = normalized.scope.categoryCode
                stmt[brandCode] = normalized.scope.brandCode
                stmt[familyCode] = normalized.scope.familyCode
                stmt[modelCode] = normalized.scope.modelCode
                stmt[sourceSnapshotId] = normalized.sourceSnapshotId
                stmt[confidence] = normalized.confidence
                stmt[status] = normalized.status.name
                stmt[metadata] = normalized.metadata
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }
            CatalogGovernanceAliasesTable.selectAll()
                .single { it[CatalogGovernanceAliasesTable.id] == existing[CatalogGovernanceAliasesTable.id] }
                .toAliasCanon()
        }
    }

    override suspend fun recordObservation(observation: CatalogValueObservation): CatalogValueObservation =
        DatabaseFactory.dbQuery {
            val normalized = observation.normalize()
            val existing = findObservationRow(normalized)
            if (existing == null) {
                CatalogGovernanceValueObservationsTable.insert { stmt ->
                    stmt[attributeCode] = normalized.attributeCode
                    stmt[locale] = normalized.locale
                    stmt[marketCode] = normalized.marketCode
                    stmt[rawValue] = normalized.rawValue
                    stmt[normalizedValue] = normalized.normalizedValue
                    stmt[categoryCode] = normalized.scope.categoryCode
                    stmt[brandCode] = normalized.scope.brandCode
                    stmt[familyCode] = normalized.scope.familyCode
                    stmt[modelCode] = normalized.scope.modelCode
                    stmt[sourceSnapshotId] = normalized.sourceSnapshotId
                    stmt[observedCount] = normalized.observedCount
                    stmt[sampleRefs] = normalized.sampleRefs
                    stmt[metadata] = normalized.metadata
                    stmt[firstSeenAt] = normalized.firstSeenAt
                    stmt[lastSeenAt] = normalized.lastSeenAt
                }.resultedValues!!.single().toValueObservation()
            } else {
                val mergedSampleRefs = (existing[CatalogGovernanceValueObservationsTable.sampleRefs] + normalized.sampleRefs)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                val mergedMetadata = existing[CatalogGovernanceValueObservationsTable.metadata] + normalized.metadata
                CatalogGovernanceValueObservationsTable.update({
                    CatalogGovernanceValueObservationsTable.id eq existing[CatalogGovernanceValueObservationsTable.id]
                }) { stmt ->
                    stmt[attributeCode] = normalized.attributeCode
                    stmt[locale] = normalized.locale
                    stmt[marketCode] = normalized.marketCode
                    stmt[rawValue] = normalized.rawValue
                    stmt[normalizedValue] = normalized.normalizedValue
                    stmt[categoryCode] = normalized.scope.categoryCode
                    stmt[brandCode] = normalized.scope.brandCode
                    stmt[familyCode] = normalized.scope.familyCode
                    stmt[modelCode] = normalized.scope.modelCode
                    stmt[sourceSnapshotId] = normalized.sourceSnapshotId
                    stmt[observedCount] = max(0, existing[CatalogGovernanceValueObservationsTable.observedCount] + normalized.observedCount)
                    stmt[sampleRefs] = mergedSampleRefs
                    stmt[metadata] = mergedMetadata
                    stmt[firstSeenAt] = min(existing[CatalogGovernanceValueObservationsTable.firstSeenAt], normalized.firstSeenAt)
                    stmt[lastSeenAt] = max(existing[CatalogGovernanceValueObservationsTable.lastSeenAt], normalized.lastSeenAt)
                }
                CatalogGovernanceValueObservationsTable.selectAll()
                    .single { it[CatalogGovernanceValueObservationsTable.id] == existing[CatalogGovernanceValueObservationsTable.id] }
                    .toValueObservation()
            }
        }

    override suspend fun submitCandidate(candidate: CatalogValueCandidate): CatalogValueCandidate = DatabaseFactory.dbQuery {
        val normalized = candidate.normalize()
        val existing = findCandidateRow(normalized)
        if (existing == null) {
            CatalogGovernanceValueCandidatesTable.insert { stmt ->
                stmt[attributeCode] = normalized.attributeCode
                stmt[locale] = normalized.locale
                stmt[marketCode] = normalized.marketCode
                stmt[rawValue] = normalized.rawValue
                stmt[normalizedValue] = normalized.normalizedValue
                stmt[proposedCanonicalCode] = normalized.proposedCanonicalCode
                stmt[proposedCanonicalValue] = normalized.proposedCanonicalValue
                stmt[proposedLabels] = normalized.proposedLabels.nullIfBlank()
                stmt[proposedCanonicalLocale] = normalized.proposedCanonicalLocale
                stmt[categoryCode] = normalized.scope.categoryCode
                stmt[brandCode] = normalized.scope.brandCode
                stmt[familyCode] = normalized.scope.familyCode
                stmt[modelCode] = normalized.scope.modelCode
                stmt[sourceSnapshotId] = normalized.sourceSnapshotId
                stmt[candidateStatus] = normalized.status.name
                stmt[autoConfidence] = normalized.autoConfidence
                stmt[evidenceCount] = normalized.evidenceCount
                stmt[metadata] = normalized.metadata
                stmt[createdAt] = normalized.createdAt
                stmt[updatedAt] = normalized.updatedAt
            }.resultedValues!!.single().toValueCandidate()
        } else {
            val mergedMetadata = existing[CatalogGovernanceValueCandidatesTable.metadata] + normalized.metadata
            CatalogGovernanceValueCandidatesTable.update({
                CatalogGovernanceValueCandidatesTable.id eq existing[CatalogGovernanceValueCandidatesTable.id]
            }) { stmt ->
                stmt[attributeCode] = normalized.attributeCode
                stmt[locale] = normalized.locale
                stmt[marketCode] = normalized.marketCode
                stmt[rawValue] = normalized.rawValue
                stmt[normalizedValue] = normalized.normalizedValue
                stmt[proposedCanonicalCode] = normalized.proposedCanonicalCode
                stmt[proposedCanonicalValue] = normalized.proposedCanonicalValue
                stmt[proposedLabels] = normalized.proposedLabels.nullIfBlank()
                stmt[proposedCanonicalLocale] = normalized.proposedCanonicalLocale
                stmt[categoryCode] = normalized.scope.categoryCode
                stmt[brandCode] = normalized.scope.brandCode
                stmt[familyCode] = normalized.scope.familyCode
                stmt[modelCode] = normalized.scope.modelCode
                stmt[sourceSnapshotId] = normalized.sourceSnapshotId
                stmt[candidateStatus] = normalized.status.name
                stmt[autoConfidence] = max(existing[CatalogGovernanceValueCandidatesTable.autoConfidence], normalized.autoConfidence)
                stmt[evidenceCount] = max(existing[CatalogGovernanceValueCandidatesTable.evidenceCount], normalized.evidenceCount)
                stmt[metadata] = mergedMetadata
                stmt[createdAt] = min(existing[CatalogGovernanceValueCandidatesTable.createdAt], normalized.createdAt)
                stmt[updatedAt] = max(existing[CatalogGovernanceValueCandidatesTable.updatedAt], normalized.updatedAt)
            }
            CatalogGovernanceValueCandidatesTable.selectAll()
                .single { it[CatalogGovernanceValueCandidatesTable.id] == existing[CatalogGovernanceValueCandidatesTable.id] }
                .toValueCandidate()
        }
    }

    override suspend fun recordDecision(decision: CatalogGovernanceDecision): CatalogGovernanceDecision = DatabaseFactory.dbQuery {
        val normalized = decision.normalize()
        CatalogGovernanceDecisionsTable.insert { stmt ->
            stmt[entityKind] = normalized.entityKind.name
            stmt[entityRef] = normalized.entityRef
            stmt[action] = normalized.action.name
            stmt[reasonCode] = normalized.reasonCode
            stmt[actor] = normalized.actor
            stmt[payload] = normalized.payload
            stmt[createdAt] = normalized.createdAt
        }.resultedValues!!.single().toDecision()
    }

    override suspend fun listSourceSnapshots(sourceCode: String?): List<CatalogGovernanceSourceSnapshot> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceSourcesTable.selectAll()
        sourceCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceSourcesTable.sourceCode eq it } }
        query.map { it.toSourceSnapshot() }
            .sortedWith(compareBy<CatalogGovernanceSourceSnapshot> { it.sourceCode }.thenByDescending { it.capturedAt })
    }

    override suspend fun listBrands(): List<CatalogBrandCanon> = DatabaseFactory.dbQuery {
        CatalogGovernanceBrandsTable.selectAll()
            .map { it.toBrandCanon() }
            .sortedBy { it.code }
    }

    override suspend fun listProductFamilies(brandCode: String?): List<CatalogProductFamilyCanon> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceProductFamiliesTable.selectAll()
        brandCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceProductFamiliesTable.brandCode eq it } }
        query.map { it.toProductFamilyCanon() }
            .sortedWith(compareBy<CatalogProductFamilyCanon> { it.brandCode }.thenBy { it.code })
    }

    override suspend fun listModels(
        brandCode: String?,
        familyCode: String?,
    ): List<CatalogModelCanon> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceModelsTable.selectAll()
        brandCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceModelsTable.brandCode eq it } }
        familyCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceModelsTable.familyCode eq it } }
        query.map { it.toModelCanon() }
            .sortedWith(compareBy<CatalogModelCanon> { it.brandCode }.thenBy { it.familyCode ?: "" }.thenBy { it.code })
    }

    override suspend fun listCanonicalValues(
        attributeCode: String,
        scope: CatalogGovernanceScope,
    ): List<CatalogAttributeValueCanon> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceValueCanonTable.selectAll()
        query.andWhere { CatalogGovernanceValueCanonTable.attributeCode eq attributeCode.trim() }
        query.andWhereScope(
            ScopeColumnSet(
                categoryCode = CatalogGovernanceValueCanonTable.categoryCode,
                brandCode = CatalogGovernanceValueCanonTable.brandCode,
                familyCode = CatalogGovernanceValueCanonTable.familyCode,
                modelCode = CatalogGovernanceValueCanonTable.modelCode,
            ),
            scope.normalize(),
        )
        query.map { it.toAttributeValueCanon() }
            .sortedWith(compareBy<CatalogAttributeValueCanon> { it.attributeCode }.thenBy { it.canonicalCode })
    }

    override suspend fun listCanonicalValuesAnyScope(
        attributeCode: String,
    ): List<CatalogAttributeValueCanon> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceValueCanonTable.selectAll()
        query.andWhere { CatalogGovernanceValueCanonTable.attributeCode eq attributeCode.trim() }
        query.map { it.toAttributeValueCanon() }
            .sortedWith(
                compareBy<CatalogAttributeValueCanon> { it.attributeCode }
                    .thenBy { it.canonicalCode }
                    .thenBy { it.scope.categoryCode ?: "" }
                    .thenBy { it.scope.brandCode ?: "" }
                    .thenBy { it.scope.familyCode ?: "" }
                    .thenBy { it.scope.modelCode ?: "" },
            )
    }

    override suspend fun listCanonicalValueAttributeCodes(): Set<String> = DatabaseFactory.dbQuery {
        CatalogGovernanceValueCanonTable.selectAll()
            .map { row -> row[CatalogGovernanceValueCanonTable.attributeCode] }
            .toSet()
    }

    override suspend fun listAliases(
        locale: String?,
        targetKind: CatalogGovernanceAliasTargetKind?,
        attributeCode: String?,
    ): List<CatalogAliasCanon> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceAliasesTable.selectAll()
        locale.normalizedLocaleOrNull()?.let { query.andWhere { CatalogGovernanceAliasesTable.locale eq it } }
        targetKind?.let { query.andWhere { CatalogGovernanceAliasesTable.targetKind eq it.name } }
        attributeCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceAliasesTable.attributeCode eq it } }
        query.map { it.toAliasCanon() }
            .sortedWith(
                compareBy<CatalogAliasCanon> { it.locale }
                    .thenBy { it.marketCode ?: "" }
                    .thenBy { it.targetKind.name }
                    .thenBy { it.normalizedAlias },
            )
    }

    override suspend fun listObservations(
        attributeCode: String?,
        scope: CatalogGovernanceScope,
        locale: String?,
        marketCode: String?,
    ): List<CatalogValueObservation> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceValueObservationsTable.selectAll()
        attributeCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceValueObservationsTable.attributeCode eq it } }
        locale.normalizedLocaleOrNull()?.let { query.andWhere { CatalogGovernanceValueObservationsTable.locale eq it } }
        marketCode.normalizedMarketOrNull()?.let { query.andWhere { CatalogGovernanceValueObservationsTable.marketCode eq it } }
        query.andWhereScope(
            ScopeColumnSet(
                categoryCode = CatalogGovernanceValueObservationsTable.categoryCode,
                brandCode = CatalogGovernanceValueObservationsTable.brandCode,
                familyCode = CatalogGovernanceValueObservationsTable.familyCode,
                modelCode = CatalogGovernanceValueObservationsTable.modelCode,
            ),
            scope.normalize(),
        )
        query.map { it.toValueObservation() }
            .sortedWith(compareBy<CatalogValueObservation> { it.attributeCode }.thenByDescending { it.lastSeenAt })
    }

    override suspend fun listCandidates(
        attributeCode: String?,
        status: CatalogGovernanceCandidateStatus?,
    ): List<CatalogValueCandidate> = DatabaseFactory.dbQuery {
        val query = CatalogGovernanceValueCandidatesTable.selectAll()
        attributeCode.normalizedOrNull()?.let { query.andWhere { CatalogGovernanceValueCandidatesTable.attributeCode eq it } }
        status?.let { query.andWhere { CatalogGovernanceValueCandidatesTable.candidateStatus eq it.name } }
        query.map { it.toValueCandidate() }
            .sortedWith(compareByDescending<CatalogValueCandidate> { it.updatedAt }.thenBy { it.id ?: Long.MAX_VALUE })
    }

    private fun findSourceRow(snapshot: CatalogGovernanceSourceSnapshot): ResultRow? {
        snapshot.id?.let { id ->
            return CatalogGovernanceSourcesTable.selectAll().singleOrNull { row -> row[CatalogGovernanceSourcesTable.id] == id }
        }
        val query = CatalogGovernanceSourcesTable.selectAll()
        query.andWhere { CatalogGovernanceSourcesTable.sourceCode eq snapshot.sourceCode }
        query.andWhereNullable(CatalogGovernanceSourcesTable.externalRef, snapshot.externalRef)
        query.andWhereNullable(CatalogGovernanceSourcesTable.defaultLocale, snapshot.defaultLocale)
        query.andWhereNullable(CatalogGovernanceSourcesTable.marketCode, snapshot.marketCode)
        query.andWhereNullable(CatalogGovernanceSourcesTable.sourceVersion, snapshot.sourceVersion)
        return query.singleOrNull()
    }

    private fun findValueCanonRow(value: CatalogAttributeValueCanon): ResultRow? {
        value.id?.let { id ->
            return CatalogGovernanceValueCanonTable.selectAll().singleOrNull { row -> row[CatalogGovernanceValueCanonTable.id] == id }
        }
        val query = CatalogGovernanceValueCanonTable.selectAll()
        query.andWhere { CatalogGovernanceValueCanonTable.attributeCode eq value.attributeCode }
        query.andWhere { CatalogGovernanceValueCanonTable.canonicalCode eq value.canonicalCode }
        query.andWhereScope(
            ScopeColumnSet(
                categoryCode = CatalogGovernanceValueCanonTable.categoryCode,
                brandCode = CatalogGovernanceValueCanonTable.brandCode,
                familyCode = CatalogGovernanceValueCanonTable.familyCode,
                modelCode = CatalogGovernanceValueCanonTable.modelCode,
            ),
            value.scope,
        )
        return query.singleOrNull()
    }

    private fun findAliasRow(alias: CatalogAliasCanon): ResultRow? {
        alias.id?.let { id ->
            return CatalogGovernanceAliasesTable.selectAll().singleOrNull { row -> row[CatalogGovernanceAliasesTable.id] == id }
        }
        val query = CatalogGovernanceAliasesTable.selectAll()
        query.andWhere { CatalogGovernanceAliasesTable.locale eq alias.locale }
        query.andWhereNullable(CatalogGovernanceAliasesTable.marketCode, alias.marketCode)
        query.andWhere { CatalogGovernanceAliasesTable.normalizedAlias eq alias.normalizedAlias }
        query.andWhere { CatalogGovernanceAliasesTable.targetKind eq alias.targetKind.name }
        query.andWhere { CatalogGovernanceAliasesTable.targetCode eq alias.targetCode }
        query.andWhereNullable(CatalogGovernanceAliasesTable.attributeCode, alias.attributeCode)
        query.andWhereScope(
            ScopeColumnSet(
                categoryCode = CatalogGovernanceAliasesTable.categoryCode,
                brandCode = CatalogGovernanceAliasesTable.brandCode,
                familyCode = CatalogGovernanceAliasesTable.familyCode,
                modelCode = CatalogGovernanceAliasesTable.modelCode,
            ),
            alias.scope,
        )
        return query.singleOrNull()
    }

    private fun findObservationRow(observation: CatalogValueObservation): ResultRow? {
        observation.id?.let { id ->
            return CatalogGovernanceValueObservationsTable.selectAll().singleOrNull { row ->
                row[CatalogGovernanceValueObservationsTable.id] == id
            }
        }
        val query = CatalogGovernanceValueObservationsTable.selectAll()
        query.andWhere { CatalogGovernanceValueObservationsTable.attributeCode eq observation.attributeCode }
        query.andWhereNullable(CatalogGovernanceValueObservationsTable.locale, observation.locale)
        query.andWhereNullable(CatalogGovernanceValueObservationsTable.marketCode, observation.marketCode)
        query.andWhere { CatalogGovernanceValueObservationsTable.normalizedValue eq observation.normalizedValue }
        query.andWhereNullable(CatalogGovernanceValueObservationsTable.sourceSnapshotId, observation.sourceSnapshotId)
        query.andWhereScope(
            ScopeColumnSet(
                categoryCode = CatalogGovernanceValueObservationsTable.categoryCode,
                brandCode = CatalogGovernanceValueObservationsTable.brandCode,
                familyCode = CatalogGovernanceValueObservationsTable.familyCode,
                modelCode = CatalogGovernanceValueObservationsTable.modelCode,
            ),
            observation.scope,
        )
        return query.singleOrNull()
    }

    private fun findCandidateRow(candidate: CatalogValueCandidate): ResultRow? {
        candidate.id?.let { id ->
            return CatalogGovernanceValueCandidatesTable.selectAll().singleOrNull { row ->
                row[CatalogGovernanceValueCandidatesTable.id] == id
            }
        }
        val query = CatalogGovernanceValueCandidatesTable.selectAll()
        query.andWhere { CatalogGovernanceValueCandidatesTable.attributeCode eq candidate.attributeCode }
        query.andWhereNullable(CatalogGovernanceValueCandidatesTable.locale, candidate.locale)
        query.andWhereNullable(CatalogGovernanceValueCandidatesTable.marketCode, candidate.marketCode)
        query.andWhere { CatalogGovernanceValueCandidatesTable.normalizedValue eq candidate.normalizedValue }
        query.andWhereNullable(CatalogGovernanceValueCandidatesTable.proposedCanonicalCode, candidate.proposedCanonicalCode)
        query.andWhereNullable(CatalogGovernanceValueCandidatesTable.sourceSnapshotId, candidate.sourceSnapshotId)
        query.andWhereScope(
            ScopeColumnSet(
                categoryCode = CatalogGovernanceValueCandidatesTable.categoryCode,
                brandCode = CatalogGovernanceValueCandidatesTable.brandCode,
                familyCode = CatalogGovernanceValueCandidatesTable.familyCode,
                modelCode = CatalogGovernanceValueCandidatesTable.modelCode,
            ),
            candidate.scope,
        )
        return query.singleOrNull()
    }
}

private data class ScopeColumnSet(
    val categoryCode: Column<String?>,
    val brandCode: Column<String?>,
    val familyCode: Column<String?>,
    val modelCode: Column<String?>,
)

private fun Query.andWhereScope(
    columns: ScopeColumnSet,
    scope: CatalogGovernanceScope,
) {
    andWhereNullable(columns.categoryCode, scope.categoryCode)
    andWhereNullable(columns.brandCode, scope.brandCode)
    andWhereNullable(columns.familyCode, scope.familyCode)
    andWhereNullable(columns.modelCode, scope.modelCode)
}

private fun <T> Query.andWhereNullable(
    column: Column<T?>,
    value: T?,
) {
    if (value == null) {
        andWhere { column.isNull() }
    } else {
        andWhere { column eq value }
    }
}

private fun ResultRow.toSourceSnapshot(): CatalogGovernanceSourceSnapshot =
    CatalogGovernanceSourceSnapshot(
        id = this[CatalogGovernanceSourcesTable.id],
        sourceCode = this[CatalogGovernanceSourcesTable.sourceCode],
        externalRef = this[CatalogGovernanceSourcesTable.externalRef],
        displayName = this[CatalogGovernanceSourcesTable.displayName],
        tier = runCatching { CatalogGovernanceSourceTier.valueOf(this[CatalogGovernanceSourcesTable.tier]) }
            .getOrDefault(CatalogGovernanceSourceTier.SYSTEM_GENERATED),
        defaultLocale = this[CatalogGovernanceSourcesTable.defaultLocale],
        marketCode = this[CatalogGovernanceSourcesTable.marketCode],
        sourceVersion = this[CatalogGovernanceSourcesTable.sourceVersion],
        sourceUri = this[CatalogGovernanceSourcesTable.sourceUri],
        checksum = this[CatalogGovernanceSourcesTable.checksum],
        metadata = this[CatalogGovernanceSourcesTable.metadata],
        capturedAt = this[CatalogGovernanceSourcesTable.capturedAt],
    )

private fun ResultRow.toBrandCanon(): CatalogBrandCanon =
    CatalogBrandCanon(
        code = this[CatalogGovernanceBrandsTable.code],
        labels = this[CatalogGovernanceBrandsTable.labels] ?: LocalizedText.Empty,
        normalizedKey = this[CatalogGovernanceBrandsTable.normalizedKey],
        status = runCatching { CatalogGovernanceEntityStatus.valueOf(this[CatalogGovernanceBrandsTable.status]) }
            .getOrDefault(CatalogGovernanceEntityStatus.ACTIVE),
        primaryCategoryCode = this[CatalogGovernanceBrandsTable.primaryCategoryCode],
        primarySegment = this[CatalogGovernanceBrandsTable.primarySegment]
            ?.let { runCatching { CategorySegment.valueOf(it) }.getOrNull() },
        metadata = this[CatalogGovernanceBrandsTable.metadata],
        createdAt = this[CatalogGovernanceBrandsTable.createdAt],
        updatedAt = this[CatalogGovernanceBrandsTable.updatedAt],
    )

private fun ResultRow.toProductFamilyCanon(): CatalogProductFamilyCanon =
    CatalogProductFamilyCanon(
        code = this[CatalogGovernanceProductFamiliesTable.code],
        brandCode = this[CatalogGovernanceProductFamiliesTable.brandCode],
        labels = this[CatalogGovernanceProductFamiliesTable.labels] ?: LocalizedText.Empty,
        normalizedKey = this[CatalogGovernanceProductFamiliesTable.normalizedKey],
        prettyModelPrefix = this[CatalogGovernanceProductFamiliesTable.prettyModelPrefix],
        variantTokens = this[CatalogGovernanceProductFamiliesTable.variantTokens],
        accessoryBlockers = this[CatalogGovernanceProductFamiliesTable.accessoryBlockers],
        defaultCategoryCode = this[CatalogGovernanceProductFamiliesTable.defaultCategoryCode],
        status = runCatching { CatalogGovernanceEntityStatus.valueOf(this[CatalogGovernanceProductFamiliesTable.status]) }
            .getOrDefault(CatalogGovernanceEntityStatus.ACTIVE),
        metadata = this[CatalogGovernanceProductFamiliesTable.metadata],
        createdAt = this[CatalogGovernanceProductFamiliesTable.createdAt],
        updatedAt = this[CatalogGovernanceProductFamiliesTable.updatedAt],
    )

private fun ResultRow.toModelCanon(): CatalogModelCanon =
    CatalogModelCanon(
        code = this[CatalogGovernanceModelsTable.code],
        brandCode = this[CatalogGovernanceModelsTable.brandCode],
        familyCode = this[CatalogGovernanceModelsTable.familyCode],
        labels = this[CatalogGovernanceModelsTable.labels] ?: LocalizedText.Empty,
        normalizedKey = this[CatalogGovernanceModelsTable.normalizedKey],
        defaultCategoryCode = this[CatalogGovernanceModelsTable.defaultCategoryCode],
        releaseYear = this[CatalogGovernanceModelsTable.releaseYear],
        status = runCatching { CatalogGovernanceEntityStatus.valueOf(this[CatalogGovernanceModelsTable.status]) }
            .getOrDefault(CatalogGovernanceEntityStatus.ACTIVE),
        metadata = this[CatalogGovernanceModelsTable.metadata],
        createdAt = this[CatalogGovernanceModelsTable.createdAt],
        updatedAt = this[CatalogGovernanceModelsTable.updatedAt],
    )

private fun ResultRow.toAttributeValueCanon(): CatalogAttributeValueCanon =
    CatalogAttributeValueCanon(
        id = this[CatalogGovernanceValueCanonTable.id],
        attributeCode = this[CatalogGovernanceValueCanonTable.attributeCode],
        canonicalCode = this[CatalogGovernanceValueCanonTable.canonicalCode],
        canonicalValue = this[CatalogGovernanceValueCanonTable.canonicalValue],
        labels = this[CatalogGovernanceValueCanonTable.labels] ?: LocalizedText.Empty,
        canonicalLocale = this[CatalogGovernanceValueCanonTable.canonicalLocale],
        normalizedValue = this[CatalogGovernanceValueCanonTable.normalizedValue],
        scope = CatalogGovernanceScope(
            categoryCode = this[CatalogGovernanceValueCanonTable.categoryCode],
            brandCode = this[CatalogGovernanceValueCanonTable.brandCode],
            familyCode = this[CatalogGovernanceValueCanonTable.familyCode],
            modelCode = this[CatalogGovernanceValueCanonTable.modelCode],
        ),
        status = runCatching { CatalogGovernanceEntityStatus.valueOf(this[CatalogGovernanceValueCanonTable.status]) }
            .getOrDefault(CatalogGovernanceEntityStatus.ACTIVE),
        metadata = this[CatalogGovernanceValueCanonTable.metadata],
        createdAt = this[CatalogGovernanceValueCanonTable.createdAt],
        updatedAt = this[CatalogGovernanceValueCanonTable.updatedAt],
    )

private fun ResultRow.toAliasCanon(): CatalogAliasCanon =
    CatalogAliasCanon(
        id = this[CatalogGovernanceAliasesTable.id],
        locale = this[CatalogGovernanceAliasesTable.locale],
        marketCode = this[CatalogGovernanceAliasesTable.marketCode],
        aliasText = this[CatalogGovernanceAliasesTable.aliasText],
        normalizedAlias = this[CatalogGovernanceAliasesTable.normalizedAlias],
        targetKind = runCatching { CatalogGovernanceAliasTargetKind.valueOf(this[CatalogGovernanceAliasesTable.targetKind]) }
            .getOrDefault(CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE),
        targetCode = this[CatalogGovernanceAliasesTable.targetCode],
        attributeCode = this[CatalogGovernanceAliasesTable.attributeCode],
        scope = CatalogGovernanceScope(
            categoryCode = this[CatalogGovernanceAliasesTable.categoryCode],
            brandCode = this[CatalogGovernanceAliasesTable.brandCode],
            familyCode = this[CatalogGovernanceAliasesTable.familyCode],
            modelCode = this[CatalogGovernanceAliasesTable.modelCode],
        ),
        sourceSnapshotId = this[CatalogGovernanceAliasesTable.sourceSnapshotId],
        confidence = this[CatalogGovernanceAliasesTable.confidence],
        status = runCatching { CatalogGovernanceAliasStatus.valueOf(this[CatalogGovernanceAliasesTable.status]) }
            .getOrDefault(CatalogGovernanceAliasStatus.ACTIVE),
        metadata = this[CatalogGovernanceAliasesTable.metadata],
        createdAt = this[CatalogGovernanceAliasesTable.createdAt],
        updatedAt = this[CatalogGovernanceAliasesTable.updatedAt],
    )

private fun ResultRow.toValueObservation(): CatalogValueObservation =
    CatalogValueObservation(
        id = this[CatalogGovernanceValueObservationsTable.id],
        attributeCode = this[CatalogGovernanceValueObservationsTable.attributeCode],
        locale = this[CatalogGovernanceValueObservationsTable.locale],
        marketCode = this[CatalogGovernanceValueObservationsTable.marketCode],
        rawValue = this[CatalogGovernanceValueObservationsTable.rawValue],
        normalizedValue = this[CatalogGovernanceValueObservationsTable.normalizedValue],
        scope = CatalogGovernanceScope(
            categoryCode = this[CatalogGovernanceValueObservationsTable.categoryCode],
            brandCode = this[CatalogGovernanceValueObservationsTable.brandCode],
            familyCode = this[CatalogGovernanceValueObservationsTable.familyCode],
            modelCode = this[CatalogGovernanceValueObservationsTable.modelCode],
        ),
        sourceSnapshotId = this[CatalogGovernanceValueObservationsTable.sourceSnapshotId],
        observedCount = this[CatalogGovernanceValueObservationsTable.observedCount],
        sampleRefs = this[CatalogGovernanceValueObservationsTable.sampleRefs],
        metadata = this[CatalogGovernanceValueObservationsTable.metadata],
        firstSeenAt = this[CatalogGovernanceValueObservationsTable.firstSeenAt],
        lastSeenAt = this[CatalogGovernanceValueObservationsTable.lastSeenAt],
    )

private fun ResultRow.toValueCandidate(): CatalogValueCandidate =
    CatalogValueCandidate(
        id = this[CatalogGovernanceValueCandidatesTable.id],
        attributeCode = this[CatalogGovernanceValueCandidatesTable.attributeCode],
        locale = this[CatalogGovernanceValueCandidatesTable.locale],
        marketCode = this[CatalogGovernanceValueCandidatesTable.marketCode],
        rawValue = this[CatalogGovernanceValueCandidatesTable.rawValue],
        normalizedValue = this[CatalogGovernanceValueCandidatesTable.normalizedValue],
        proposedCanonicalCode = this[CatalogGovernanceValueCandidatesTable.proposedCanonicalCode],
        proposedCanonicalValue = this[CatalogGovernanceValueCandidatesTable.proposedCanonicalValue],
        proposedLabels = this[CatalogGovernanceValueCandidatesTable.proposedLabels] ?: LocalizedText.Empty,
        proposedCanonicalLocale = this[CatalogGovernanceValueCandidatesTable.proposedCanonicalLocale],
        scope = CatalogGovernanceScope(
            categoryCode = this[CatalogGovernanceValueCandidatesTable.categoryCode],
            brandCode = this[CatalogGovernanceValueCandidatesTable.brandCode],
            familyCode = this[CatalogGovernanceValueCandidatesTable.familyCode],
            modelCode = this[CatalogGovernanceValueCandidatesTable.modelCode],
        ),
        sourceSnapshotId = this[CatalogGovernanceValueCandidatesTable.sourceSnapshotId],
        status = runCatching { CatalogGovernanceCandidateStatus.valueOf(this[CatalogGovernanceValueCandidatesTable.candidateStatus]) }
            .getOrDefault(CatalogGovernanceCandidateStatus.NEW),
        autoConfidence = this[CatalogGovernanceValueCandidatesTable.autoConfidence],
        evidenceCount = this[CatalogGovernanceValueCandidatesTable.evidenceCount],
        metadata = this[CatalogGovernanceValueCandidatesTable.metadata],
        createdAt = this[CatalogGovernanceValueCandidatesTable.createdAt],
        updatedAt = this[CatalogGovernanceValueCandidatesTable.updatedAt],
    )

private fun ResultRow.toDecision(): CatalogGovernanceDecision =
    CatalogGovernanceDecision(
        id = this[CatalogGovernanceDecisionsTable.id],
        entityKind = runCatching { CatalogGovernanceDecisionEntityKind.valueOf(this[CatalogGovernanceDecisionsTable.entityKind]) }
            .getOrDefault(CatalogGovernanceDecisionEntityKind.VALUE_CANDIDATE),
        entityRef = this[CatalogGovernanceDecisionsTable.entityRef],
        action = runCatching { CatalogGovernanceDecisionAction.valueOf(this[CatalogGovernanceDecisionsTable.action]) }
            .getOrDefault(CatalogGovernanceDecisionAction.APPROVE),
        reasonCode = this[CatalogGovernanceDecisionsTable.reasonCode],
        actor = this[CatalogGovernanceDecisionsTable.actor],
        payload = this[CatalogGovernanceDecisionsTable.payload],
        createdAt = this[CatalogGovernanceDecisionsTable.createdAt],
    )

private fun CatalogGovernanceSourceSnapshot.normalize(): CatalogGovernanceSourceSnapshot =
    copy(
        sourceCode = sourceCode.trim(),
        externalRef = externalRef.normalizedOrNull(),
        displayName = displayName.trim(),
        defaultLocale = defaultLocale.normalizedLocaleOrNull(),
        marketCode = marketCode.normalizedMarketOrNull(),
        sourceVersion = sourceVersion.normalizedOrNull(),
        sourceUri = sourceUri.normalizedOrNull(),
        checksum = checksum.normalizedOrNull(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogBrandCanon.normalize(): CatalogBrandCanon =
    copy(
        code = code.trim(),
        labels = labels.normalizedLocalized(),
        normalizedKey = normalizedKey.trim(),
        primaryCategoryCode = primaryCategoryCode.normalizedOrNull(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogProductFamilyCanon.normalize(): CatalogProductFamilyCanon =
    copy(
        code = code.trim(),
        brandCode = brandCode.trim(),
        labels = labels.normalizedLocalized(),
        normalizedKey = normalizedKey.trim(),
        prettyModelPrefix = prettyModelPrefix.trim(),
        variantTokens = variantTokens
            .mapNotNull { it.normalizedTokenOrNull() }
            .distinct(),
        accessoryBlockers = accessoryBlockers
            .mapNotNull { it.normalizedTokenOrNull() }
            .distinct(),
        defaultCategoryCode = defaultCategoryCode.normalizedOrNull(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogModelCanon.normalize(): CatalogModelCanon =
    copy(
        code = code.trim(),
        brandCode = brandCode.trim(),
        familyCode = familyCode.normalizedOrNull(),
        labels = labels.normalizedLocalized(),
        normalizedKey = normalizedKey.trim(),
        defaultCategoryCode = defaultCategoryCode.normalizedOrNull(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogAttributeValueCanon.normalize(): CatalogAttributeValueCanon =
    copy(
        attributeCode = attributeCode.trim(),
        canonicalCode = canonicalCode.trim(),
        canonicalValue = canonicalValue.trim(),
        labels = labels.normalizedLocalized(),
        canonicalLocale = canonicalLocale.normalizedLocaleOrNull(),
        normalizedValue = normalizedValue.trim(),
        scope = scope.normalize(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogAliasCanon.normalize(): CatalogAliasCanon =
    copy(
        locale = locale.normalizedLocaleOrNull() ?: "und",
        marketCode = marketCode.normalizedMarketOrNull(),
        aliasText = aliasText.trim(),
        normalizedAlias = normalizedAlias.trim(),
        targetCode = targetCode.trim(),
        attributeCode = attributeCode.normalizedOrNull(),
        scope = scope.normalize(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogValueObservation.normalize(): CatalogValueObservation =
    copy(
        attributeCode = attributeCode.trim(),
        locale = locale.normalizedLocaleOrNull(),
        marketCode = marketCode.normalizedMarketOrNull(),
        rawValue = rawValue.trim(),
        normalizedValue = normalizedValue.trim(),
        scope = scope.normalize(),
        sampleRefs = sampleRefs.mapNotNull { it.normalizedOrNull() }.distinct(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogValueCandidate.normalize(): CatalogValueCandidate =
    copy(
        attributeCode = attributeCode.trim(),
        locale = locale.normalizedLocaleOrNull(),
        marketCode = marketCode.normalizedMarketOrNull(),
        rawValue = rawValue.trim(),
        normalizedValue = normalizedValue.trim(),
        proposedCanonicalCode = proposedCanonicalCode.normalizedOrNull(),
        proposedCanonicalValue = proposedCanonicalValue.normalizedOrNull(),
        proposedLabels = proposedLabels.normalizedLocalized(),
        proposedCanonicalLocale = proposedCanonicalLocale.normalizedLocaleOrNull(),
        scope = scope.normalize(),
        metadata = metadata.normalizedMetadata(),
    )

private fun CatalogGovernanceDecision.normalize(): CatalogGovernanceDecision =
    copy(
        entityRef = entityRef.trim(),
        reasonCode = reasonCode.trim(),
        actor = actor.trim(),
        payload = payload.normalizedMetadata(),
    )

private fun CatalogGovernanceScope.normalize(): CatalogGovernanceScope =
    CatalogGovernanceScope(
        categoryCode = categoryCode.normalizedOrNull(),
        brandCode = brandCode.normalizedOrNull(),
        familyCode = familyCode.normalizedOrNull(),
        modelCode = modelCode.normalizedOrNull(),
    )

private fun LocalizedText.normalizedLocalized(): LocalizedText =
    asMap().entries
        .mapNotNull { (locale, value) ->
            val normalizedLocale = locale.normalizedLocaleOrNull()
            val normalizedValue = value.trim()
            if (normalizedLocale == null || normalizedValue.isEmpty()) {
                null
            } else {
                normalizedLocale to normalizedValue
            }
        }
        .toMap()
        .let(::LocalizedText)

private fun LocalizedText.nullIfBlank(): LocalizedText? =
    takeUnless { it.isBlank() }

private fun String?.normalizedOrNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedLocaleOrNull(): String? =
    this?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

private fun String?.normalizedMarketOrNull(): String? =
    this?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

private fun String?.normalizedTokenOrNull(): String? =
    this?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

private fun Map<String, String>.normalizedMetadata(): Map<String, String> =
    entries
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            val normalizedValue = value.trim()
            if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
                null
            } else {
                normalizedKey to normalizedValue
            }
        }
        .toMap()
