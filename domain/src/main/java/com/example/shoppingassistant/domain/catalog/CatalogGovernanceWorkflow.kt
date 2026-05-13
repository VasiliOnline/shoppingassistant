package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogGovernanceAttributePolicyMode {
    CLOSED,
    HYBRID,
    OPEN,
}

@Serializable
enum class CatalogGovernanceCandidateDisposition {
    EXISTING_CANONICAL,
    EXISTING_ALIAS,
    OBSERVED_ONLY,
    ALIAS_CANDIDATE,
    NEW_CANONICAL_CANDIDATE,
    REJECT_NOISE,
}

@Serializable
enum class CatalogGovernanceClusterRecommendation {
    REJECT,
    MONITOR,
    REVIEW_ALIAS,
    REVIEW_CANONICAL,
    AUTO_PROMOTE_ALIAS,
    AUTO_PROMOTE_CANONICAL,
}

@Serializable
data class CatalogGovernanceAttributePolicy(
    val attributeCode: String,
    val mode: CatalogGovernanceAttributePolicyMode,
    val valueType: Stage22ValueType,
    val isFacet: Boolean,
    val isIdentity: Boolean,
    val dictionaryBacked: Boolean,
    val dictionaryRequired: Boolean,
    val acceptsFreeText: Boolean,
    val canonicalSource: String,
    val dedupTokenMode: Stage40DedupTokenMode,
    val allowUnknownOfferValue: Boolean,
    val allowObservedOnly: Boolean,
    val allowCandidateCreation: Boolean,
    val allowLiveFacetValue: Boolean,
    val allowAutoPromotion: Boolean,
)

@Serializable
data class CatalogGovernanceExistingValueMatch(
    val targetKind: CatalogGovernanceAliasTargetKind,
    val targetCode: String,
    val attributeCode: String? = null,
    val canonicalCode: String? = null,
    val canonicalValueId: Long? = null,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val confidence: Double,
    val reasonCode: String,
)

@Serializable
data class CatalogGovernanceEvidenceScore(
    val totalScore: Double,
    val observedCount: Int,
    val candidateEvidenceCount: Int,
    val distinctSourceCount: Int,
    val distinctLocaleCount: Int,
    val distinctMarketCount: Int,
    val distinctSellerCount: Int,
    val timePersistenceDays: Int,
    val userDemandCount: Int,
    val correctionPositiveCount: Int,
    val correctionNegativeCount: Int,
    val semanticCollisionCount: Int,
    val sourceReliabilityScore: Double,
)

@Serializable
data class CatalogGovernanceCandidateCluster(
    val clusterKey: String,
    val attributeCode: String,
    val locale: String? = null,
    val marketCode: String? = null,
    val normalizedValue: String,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val policy: CatalogGovernanceAttributePolicy,
    val rawValues: List<String> = emptyList(),
    val candidateIds: List<Long> = emptyList(),
    val candidateStatuses: List<CatalogGovernanceCandidateStatus> = emptyList(),
    val evidenceScore: CatalogGovernanceEvidenceScore,
    val existingMatch: CatalogGovernanceExistingValueMatch? = null,
    val recommendedDisposition: CatalogGovernanceCandidateDisposition,
    val recommendation: CatalogGovernanceClusterRecommendation,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class CatalogGovernanceValueSignal(
    val attributeCode: String,
    val rawValue: String,
    val normalizedValue: String,
    val locale: String? = null,
    val marketCode: String? = null,
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    val sourceSnapshotId: Long? = null,
    val observedCount: Int = 1,
    val sampleRefs: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long,
)

@Serializable
data class CatalogGovernanceSignalIngestionResult(
    val signal: CatalogGovernanceValueSignal,
    val policy: CatalogGovernanceAttributePolicy,
    val disposition: CatalogGovernanceCandidateDisposition,
    val observation: CatalogValueObservation,
    val candidate: CatalogValueCandidate? = null,
    val existingMatch: CatalogGovernanceExistingValueMatch? = null,
    val cluster: CatalogGovernanceCandidateCluster? = null,
    val reasons: List<String> = emptyList(),
)
