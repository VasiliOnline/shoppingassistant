package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.i18n.LocalizedText
import kotlinx.serialization.Serializable

@Serializable
enum class CatalogGovernanceSourceTier {
    AUTHORITATIVE,
    PARTNER_STRUCTURED,
    MARKETPLACE,
    USER_SIGNAL,
    MANUAL_EDITORIAL,
    SYSTEM_GENERATED,
}

@Serializable
enum class CatalogGovernanceEntityStatus {
    ACTIVE,
    DEPRECATED,
    MERGED,
    REJECTED,
}

@Serializable
enum class CatalogGovernanceAliasStatus {
    ACTIVE,
    SHADOWED,
    REJECTED,
}

@Serializable
enum class CatalogGovernanceCandidateStatus {
    NEW,
    REVIEWING,
    APPROVED,
    REJECTED,
    PROMOTED,
}

@Serializable
enum class CatalogGovernanceAliasTargetKind {
    BRAND,
    PRODUCT_FAMILY,
    MODEL,
    ATTRIBUTE_VALUE,
}

@Serializable
enum class CatalogGovernanceDecisionEntityKind {
    SOURCE_SNAPSHOT,
    BRAND,
    PRODUCT_FAMILY,
    MODEL,
    ATTRIBUTE_VALUE,
    ALIAS,
    VALUE_CANDIDATE,
}

@Serializable
enum class CatalogGovernanceDecisionAction {
    APPROVE,
    REJECT,
    PROMOTE,
    MERGE,
    DEPRECATE,
    SHADOW,
}

@Serializable
data class CatalogGovernanceScope(
    val categoryCode: String? = null,
    val brandCode: String? = null,
    val familyCode: String? = null,
    val modelCode: String? = null,
)

@Serializable
data class CatalogGovernanceSourceSnapshot(
    val id: Long? = null,
    val sourceCode: String,
    val externalRef: String? = null,
    val displayName: String,
    val tier: CatalogGovernanceSourceTier,
    val defaultLocale: String? = null,
    val marketCode: String? = null,
    val sourceVersion: String? = null,
    val sourceUri: String? = null,
    val checksum: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val capturedAt: Long,
)

@Serializable
data class CatalogBrandCanon(
    val code: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val normalizedKey: String,
    val status: CatalogGovernanceEntityStatus = CatalogGovernanceEntityStatus.ACTIVE,
    val primaryCategoryCode: String? = null,
    val primarySegment: CategorySegment? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class CatalogProductFamilyCanon(
    val code: String,
    val brandCode: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val normalizedKey: String,
    val prettyModelPrefix: String,
    val variantTokens: List<String> = emptyList(),
    val accessoryBlockers: List<String> = emptyList(),
    val defaultCategoryCode: String? = null,
    val status: CatalogGovernanceEntityStatus = CatalogGovernanceEntityStatus.ACTIVE,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class CatalogModelCanon(
    val code: String,
    val brandCode: String,
    val familyCode: String? = null,
    val labels: LocalizedText = LocalizedText.Empty,
    val normalizedKey: String,
    val defaultCategoryCode: String? = null,
    val releaseYear: Int? = null,
    val status: CatalogGovernanceEntityStatus = CatalogGovernanceEntityStatus.ACTIVE,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class CatalogAttributeValueCanon(
    val id: Long? = null,
    val attributeCode: String,
    val canonicalCode: String,
    val canonicalValue: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val canonicalLocale: String? = null,
    val normalizedValue: String,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val status: CatalogGovernanceEntityStatus = CatalogGovernanceEntityStatus.ACTIVE,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class CatalogAliasCanon(
    val id: Long? = null,
    val locale: String,
    val marketCode: String? = null,
    val aliasText: String,
    val normalizedAlias: String,
    val targetKind: CatalogGovernanceAliasTargetKind,
    val targetCode: String,
    val attributeCode: String? = null,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val sourceSnapshotId: Long? = null,
    val confidence: Double = 1.0,
    val status: CatalogGovernanceAliasStatus = CatalogGovernanceAliasStatus.ACTIVE,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class CatalogValueObservation(
    val id: Long? = null,
    val attributeCode: String,
    val locale: String? = null,
    val marketCode: String? = null,
    val rawValue: String,
    val normalizedValue: String,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val sourceSnapshotId: Long? = null,
    val observedCount: Int = 1,
    val sampleRefs: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

@Serializable
data class CatalogValueCandidate(
    val id: Long? = null,
    val attributeCode: String,
    val locale: String? = null,
    val marketCode: String? = null,
    val rawValue: String,
    val normalizedValue: String,
    val proposedCanonicalCode: String? = null,
    val proposedCanonicalValue: String? = null,
    val proposedLabels: LocalizedText = LocalizedText.Empty,
    val proposedCanonicalLocale: String? = null,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val sourceSnapshotId: Long? = null,
    val status: CatalogGovernanceCandidateStatus = CatalogGovernanceCandidateStatus.NEW,
    val autoConfidence: Double = 0.0,
    val evidenceCount: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class CatalogGovernanceDecision(
    val id: Long? = null,
    val entityKind: CatalogGovernanceDecisionEntityKind,
    val entityRef: String,
    val action: CatalogGovernanceDecisionAction,
    val reasonCode: String,
    val actor: String,
    val payload: Map<String, String> = emptyMap(),
    val createdAt: Long,
)

interface CatalogGovernanceRepository {
    suspend fun upsertSourceSnapshot(snapshot: CatalogGovernanceSourceSnapshot): CatalogGovernanceSourceSnapshot

    suspend fun upsertBrand(brand: CatalogBrandCanon): CatalogBrandCanon

    suspend fun upsertProductFamily(family: CatalogProductFamilyCanon): CatalogProductFamilyCanon

    suspend fun upsertModel(model: CatalogModelCanon): CatalogModelCanon

    suspend fun upsertAttributeValueCanon(value: CatalogAttributeValueCanon): CatalogAttributeValueCanon

    suspend fun upsertAlias(alias: CatalogAliasCanon): CatalogAliasCanon

    suspend fun recordObservation(observation: CatalogValueObservation): CatalogValueObservation

    suspend fun submitCandidate(candidate: CatalogValueCandidate): CatalogValueCandidate

    suspend fun recordDecision(decision: CatalogGovernanceDecision): CatalogGovernanceDecision

    suspend fun listSourceSnapshots(sourceCode: String? = null): List<CatalogGovernanceSourceSnapshot>

    suspend fun listBrands(): List<CatalogBrandCanon>

    suspend fun listProductFamilies(brandCode: String? = null): List<CatalogProductFamilyCanon>

    suspend fun listModels(
        brandCode: String? = null,
        familyCode: String? = null,
    ): List<CatalogModelCanon>

    suspend fun listCanonicalValues(
        attributeCode: String,
        scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    ): List<CatalogAttributeValueCanon>

    suspend fun listCanonicalValuesAnyScope(
        attributeCode: String,
    ): List<CatalogAttributeValueCanon>

    suspend fun listCanonicalValueAttributeCodes(): Set<String>

    suspend fun listAliases(
        locale: String? = null,
        targetKind: CatalogGovernanceAliasTargetKind? = null,
        attributeCode: String? = null,
    ): List<CatalogAliasCanon>

    suspend fun listObservations(
        attributeCode: String? = null,
        scope: CatalogGovernanceScope = CatalogGovernanceScope(),
        locale: String? = null,
        marketCode: String? = null,
    ): List<CatalogValueObservation>

    suspend fun listCandidates(
        attributeCode: String? = null,
        status: CatalogGovernanceCandidateStatus? = null,
    ): List<CatalogValueCandidate>
}
