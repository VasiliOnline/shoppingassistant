package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon
import com.example.shoppingassistant.domain.catalog.CatalogBrandCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateDisposition
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecision
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSignalIngestionResult
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceTier
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceClusterRecommendation
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceValueSignal
import com.example.shoppingassistant.domain.catalog.CatalogModelCanon
import com.example.shoppingassistant.domain.catalog.CatalogProductFamilyCanon
import com.example.shoppingassistant.domain.catalog.CatalogValueCandidate
import com.example.shoppingassistant.domain.catalog.CatalogValueObservation
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogGovernanceWorkflowServiceTest {

    @Test
    fun ingestSignal_forOpenAttribute_keepsObservationOnly() = runBlocking {
        val repository = InMemoryCatalogGovernanceRepository()
        val service = CatalogGovernanceWorkflowService(repository = repository)
        val now = 1_710_000_000_000L

        val result = service.ingestSignal(
            CatalogGovernanceValueSignal(
                attributeCode = "brand",
                rawValue = "Acme",
                normalizedValue = "acme",
                locale = "en-US",
                marketCode = "us",
                observedCount = 1,
                createdAt = now,
            ),
        )

        assertEquals(CatalogGovernanceCandidateDisposition.OBSERVED_ONLY, result.disposition)
        assertNull(result.candidate)
        assertEquals(1, repository.observations.size)
        assertEquals("OPEN", result.policy.mode.name)
    }

    @Test
    fun ingestSignal_forClosedAttribute_createsAliasCandidate_whenNearCanonicalExists() = runBlocking {
        val repository = InMemoryCatalogGovernanceRepository()
        val service = CatalogGovernanceWorkflowService(repository = repository)
        val now = 1_710_000_000_000L

        repository.values += CatalogAttributeValueCanon(
            id = 1L,
            attributeCode = "color",
            canonicalCode = "BLACK",
            canonicalValue = "Black",
            labels = localizedTextOf("en" to "Black"),
            canonicalLocale = "en",
            normalizedValue = "black",
            status = CatalogGovernanceEntityStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )

        val result = service.ingestSignal(
            CatalogGovernanceValueSignal(
                attributeCode = "color",
                rawValue = "Blackk",
                normalizedValue = "blackk",
                locale = "en-US",
                marketCode = "us",
                observedCount = 2,
                createdAt = now,
            ),
        )

        assertEquals(CatalogGovernanceCandidateDisposition.ALIAS_CANDIDATE, result.disposition)
        assertNotNull(result.candidate)
        assertEquals("BLACK", result.candidate!!.proposedCanonicalCode)
        assertEquals("BLACK", result.existingMatch?.canonicalCode)
        assertEquals(1, repository.candidates.size)
    }

    @Test
    fun listCandidateClusters_aggregatesEvidence_and_recommendsReviewCanonical() = runBlocking {
        val repository = InMemoryCatalogGovernanceRepository()
        val service = CatalogGovernanceWorkflowService(repository = repository)
        val now = 1_710_000_000_000L

        val sourceA = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                id = null,
                sourceCode = "seller-feed-a",
                displayName = "Seller A",
                tier = CatalogGovernanceSourceTier.MARKETPLACE,
                defaultLocale = "ru-RU",
                marketCode = "ru",
                capturedAt = now,
            ),
        )
        val sourceB = repository.upsertSourceSnapshot(
            CatalogGovernanceSourceSnapshot(
                id = null,
                sourceCode = "seller-feed-b",
                displayName = "Seller B",
                tier = CatalogGovernanceSourceTier.PARTNER_STRUCTURED,
                defaultLocale = "ru-RU",
                marketCode = "ru",
                capturedAt = now,
            ),
        )

        repository.observations += CatalogValueObservation(
            id = 1L,
            attributeCode = "color",
            locale = "ru-ru",
            marketCode = "RU",
            rawValue = "титаново-оранжевый",
            normalizedValue = "orange titanium",
            scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
            sourceSnapshotId = sourceA.id,
                observedCount = 4,
                metadata = mapOf(
                    "clusterKey" to "color|VALUE_CODE|ru-ru|RU|TECH.PHONES|*|*|*|orange titanium",
                    "seller_count" to "2",
                    "query_count" to "11",
                ),
            firstSeenAt = now - 7 * 24 * 60 * 60 * 1000L,
            lastSeenAt = now,
        )
        repository.observations += CatalogValueObservation(
            id = 2L,
            attributeCode = "color",
            locale = "ru-ru",
            marketCode = "RU",
            rawValue = "orange titanium",
            normalizedValue = "orange titanium",
            scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
            sourceSnapshotId = sourceB.id,
                observedCount = 3,
                metadata = mapOf(
                    "clusterKey" to "color|VALUE_CODE|ru-ru|RU|TECH.PHONES|*|*|*|orange titanium",
                    "seller_count" to "1",
                    "query_count" to "6",
                ),
            firstSeenAt = now - 3 * 24 * 60 * 60 * 1000L,
            lastSeenAt = now,
        )
        repository.candidates += CatalogValueCandidate(
            id = 1L,
            attributeCode = "color",
            locale = "ru-ru",
            marketCode = "RU",
            rawValue = "титаново-оранжевый",
            normalizedValue = "orange titanium",
            proposedCanonicalCode = "ORANGE_TITANIUM",
            proposedCanonicalValue = "Orange Titanium",
            proposedLabels = localizedTextOf("en" to "Orange Titanium", "ru" to "Титаново-оранжевый"),
            proposedCanonicalLocale = "en",
            scope = CatalogGovernanceScope(categoryCode = "TECH.PHONES"),
            sourceSnapshotId = sourceB.id,
            status = CatalogGovernanceCandidateStatus.REVIEWING,
            autoConfidence = 0.71,
            evidenceCount = 5,
            metadata = mapOf(
                "clusterKey" to "color|VALUE_CODE|ru-ru|RU|TECH.PHONES|*|*|*|orange titanium",
            ),
            createdAt = now - 2 * 24 * 60 * 60 * 1000L,
            updatedAt = now,
        )

        val cluster = service.listCandidateClusters(attributeCode = "color").single()

        assertEquals("orange titanium", cluster.normalizedValue)
        assertTrue(cluster.evidenceScore.observedCount >= 7)
        assertTrue(cluster.evidenceScore.distinctSourceCount >= 2)
        assertEquals(CatalogGovernanceCandidateDisposition.NEW_CANONICAL_CANDIDATE, cluster.recommendedDisposition)
        assertEquals(CatalogGovernanceClusterRecommendation.REVIEW_CANONICAL, cluster.recommendation)
    }
}

private class InMemoryCatalogGovernanceRepository : CatalogGovernanceRepository {
    val sources = mutableListOf<CatalogGovernanceSourceSnapshot>()
    val brands = mutableListOf<CatalogBrandCanon>()
    val families = mutableListOf<CatalogProductFamilyCanon>()
    val models = mutableListOf<CatalogModelCanon>()
    val values = mutableListOf<CatalogAttributeValueCanon>()
    val aliases = mutableListOf<CatalogAliasCanon>()
    val observations = mutableListOf<CatalogValueObservation>()
    val candidates = mutableListOf<CatalogValueCandidate>()
    val decisions = mutableListOf<CatalogGovernanceDecision>()

    private var sourceSeq = 1L
    private var valueSeq = 1L
    private var aliasSeq = 1L
    private var observationSeq = 1L
    private var candidateSeq = 1L
    private var decisionSeq = 1L

    override suspend fun upsertSourceSnapshot(snapshot: CatalogGovernanceSourceSnapshot): CatalogGovernanceSourceSnapshot {
        val assigned = snapshot.copy(
            id = snapshot.id ?: sourceSeq++,
            defaultLocale = snapshot.defaultLocale?.lowercase(),
            marketCode = snapshot.marketCode?.uppercase(),
        )
        sources.removeAll { it.id == assigned.id }
        sources += assigned
        return assigned
    }

    override suspend fun upsertBrand(brand: CatalogBrandCanon): CatalogBrandCanon {
        brands.removeAll { it.code == brand.code }
        brands += brand
        return brand
    }

    override suspend fun upsertProductFamily(family: CatalogProductFamilyCanon): CatalogProductFamilyCanon {
        families.removeAll { it.code == family.code }
        families += family
        return family
    }

    override suspend fun upsertModel(model: CatalogModelCanon): CatalogModelCanon {
        models.removeAll { it.code == model.code }
        models += model
        return model
    }

    override suspend fun upsertAttributeValueCanon(value: CatalogAttributeValueCanon): CatalogAttributeValueCanon {
        val assigned = value.copy(id = value.id ?: valueSeq++)
        values.removeAll { it.id == assigned.id || (it.attributeCode == assigned.attributeCode && it.canonicalCode == assigned.canonicalCode) }
        values += assigned
        return assigned
    }

    override suspend fun upsertAlias(alias: CatalogAliasCanon): CatalogAliasCanon {
        val assigned = alias.copy(id = alias.id ?: aliasSeq++)
        aliases.removeAll { it.id == assigned.id }
        aliases += assigned
        return assigned
    }

    override suspend fun recordObservation(observation: CatalogValueObservation): CatalogValueObservation {
        val assigned = observation.copy(id = observation.id ?: observationSeq++)
        observations += assigned
        return assigned
    }

    override suspend fun submitCandidate(candidate: CatalogValueCandidate): CatalogValueCandidate {
        val assigned = candidate.copy(id = candidate.id ?: candidateSeq++)
        candidates.removeAll { it.id == assigned.id }
        candidates += assigned
        return assigned
    }

    override suspend fun recordDecision(decision: CatalogGovernanceDecision): CatalogGovernanceDecision {
        val assigned = decision.copy(id = decision.id ?: decisionSeq++)
        decisions += assigned
        return assigned
    }

    override suspend fun listSourceSnapshots(sourceCode: String?): List<CatalogGovernanceSourceSnapshot> =
        sources.filter { sourceCode == null || it.sourceCode == sourceCode }

    override suspend fun listBrands(): List<CatalogBrandCanon> = brands.toList()

    override suspend fun listProductFamilies(brandCode: String?): List<CatalogProductFamilyCanon> =
        families.filter { brandCode == null || it.brandCode == brandCode }

    override suspend fun listModels(brandCode: String?, familyCode: String?): List<CatalogModelCanon> =
        models.filter { (brandCode == null || it.brandCode == brandCode) && (familyCode == null || it.familyCode == familyCode) }

    override suspend fun listCanonicalValues(
        attributeCode: String,
        scope: CatalogGovernanceScope,
    ): List<CatalogAttributeValueCanon> =
        values.filter { it.attributeCode == attributeCode && it.scope == scope }

    override suspend fun listCanonicalValuesAnyScope(attributeCode: String): List<CatalogAttributeValueCanon> =
        values.filter { it.attributeCode == attributeCode }

    override suspend fun listCanonicalValueAttributeCodes(): Set<String> =
        values.map { it.attributeCode }.toSet()

    override suspend fun listAliases(
        locale: String?,
        targetKind: CatalogGovernanceAliasTargetKind?,
        attributeCode: String?,
    ): List<CatalogAliasCanon> =
        aliases.filter { alias ->
            (locale == null || alias.locale == locale) &&
                (targetKind == null || alias.targetKind == targetKind) &&
                (attributeCode == null || alias.attributeCode == attributeCode)
        }

    override suspend fun listObservations(
        attributeCode: String?,
        scope: CatalogGovernanceScope,
        locale: String?,
        marketCode: String?,
    ): List<CatalogValueObservation> =
        observations.filter { observation ->
            (attributeCode == null || observation.attributeCode == attributeCode) &&
                (locale == null || observation.locale == locale) &&
                (marketCode == null || observation.marketCode == marketCode) &&
                (scope == CatalogGovernanceScope() || observation.scope == scope)
        }

    override suspend fun listCandidates(
        attributeCode: String?,
        status: CatalogGovernanceCandidateStatus?,
    ): List<CatalogValueCandidate> =
        candidates.filter { candidate ->
            (attributeCode == null || candidate.attributeCode == attributeCode) &&
                (status == null || candidate.status == status)
        }
}
