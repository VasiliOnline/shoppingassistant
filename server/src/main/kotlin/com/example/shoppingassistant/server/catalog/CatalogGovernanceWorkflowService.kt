package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAttributePolicy
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAttributePolicyMode
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateCluster
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateDisposition
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceClusterRecommendation
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceExistingValueMatch
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSignalIngestionResult
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceValueSignal
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CatalogValueCandidate
import com.example.shoppingassistant.domain.catalog.CatalogValueObservation
import com.example.shoppingassistant.domain.catalog.Stage22ValueSetType
import com.example.shoppingassistant.domain.catalog.Stage22ValueType
import com.example.shoppingassistant.domain.catalog.Stage40DedupTokenMode
import com.example.shoppingassistant.domain.catalog.Stage40ImmutableAttribute
import com.example.shoppingassistant.domain.catalog.Stage40NormalizationRule
import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CatalogGovernanceAttributePolicyResolver(
    immutableAttributes: List<Stage40ImmutableAttribute> = CatalogSeed.stage40ImmutableSchema.attributes,
    normalizationRules: List<Stage40NormalizationRule> = CatalogSeed.stage40NormalizationContract.rules,
) {
    private val immutableByAttribute = immutableAttributes.associateBy { it.attributeCode.trim() }
    private val rulesByAttribute = normalizationRules.associateBy { it.attributeCode.trim() }

    fun resolve(attributeCode: String): CatalogGovernanceAttributePolicy {
        val normalizedAttributeCode = attributeCode.trim()
        val immutable = immutableByAttribute[normalizedAttributeCode]
        val rule = rulesByAttribute[normalizedAttributeCode]
        val valueSetType = rule?.valueSetType ?: immutable?.valueSetType ?: Stage22ValueSetType.OPEN
        val mode = when (valueSetType) {
            Stage22ValueSetType.CLOSED -> CatalogGovernanceAttributePolicyMode.CLOSED
            Stage22ValueSetType.SEMI_CLOSED -> CatalogGovernanceAttributePolicyMode.HYBRID
            Stage22ValueSetType.OPEN -> CatalogGovernanceAttributePolicyMode.OPEN
        }
        val valueType = immutable?.valueType ?: Stage22ValueType.STRING
        val dictionaryBacked = rule?.dictionaryBacked ?: immutable?.dictionaryRequired ?: false
        val acceptsFreeText = rule?.acceptsFreeText ?: (mode != CatalogGovernanceAttributePolicyMode.CLOSED)
        val isFacet = immutable?.isFacet == true
        val isIdentity = immutable?.isIdentity == true
        val dictionaryRequired = immutable?.dictionaryRequired ?: dictionaryBacked
        return CatalogGovernanceAttributePolicy(
            attributeCode = normalizedAttributeCode,
            mode = mode,
            valueType = valueType,
            isFacet = isFacet,
            isIdentity = isIdentity,
            dictionaryBacked = dictionaryBacked,
            dictionaryRequired = dictionaryRequired,
            acceptsFreeText = acceptsFreeText,
            canonicalSource = rule?.canonicalSource?.trim().orEmpty().ifBlank { "inline" },
            dedupTokenMode = rule?.dedupTokenMode ?: Stage40DedupTokenMode.NORMALIZED_TEXT,
            allowUnknownOfferValue = mode != CatalogGovernanceAttributePolicyMode.CLOSED || acceptsFreeText,
            allowObservedOnly = mode != CatalogGovernanceAttributePolicyMode.CLOSED || acceptsFreeText,
            allowCandidateCreation = mode != CatalogGovernanceAttributePolicyMode.OPEN && (valueType == Stage22ValueType.ENUM || isFacet || isIdentity),
            allowLiveFacetValue = isFacet && mode == CatalogGovernanceAttributePolicyMode.HYBRID,
            allowAutoPromotion = mode == CatalogGovernanceAttributePolicyMode.HYBRID && dictionaryBacked,
        )
    }
}

class CatalogGovernanceWorkflowService(
    private val repository: CatalogGovernanceRepository,
    private val projectionPolicy: CatalogGovernanceProjectionPolicy = CatalogGovernanceProjectionPolicy(),
    private val policyResolver: CatalogGovernanceAttributePolicyResolver = CatalogGovernanceAttributePolicyResolver(),
) {
    suspend fun ingestSignal(
        signal: CatalogGovernanceValueSignal,
    ): CatalogGovernanceSignalIngestionResult {
        val normalizedSignal = signal.normalize()
        val policy = policyResolver.resolve(normalizedSignal.attributeCode)
        val observation = repository.recordObservation(
            CatalogValueObservation(
                attributeCode = normalizedSignal.attributeCode,
                locale = normalizedSignal.locale,
                marketCode = normalizedSignal.marketCode,
                rawValue = normalizedSignal.rawValue,
                normalizedValue = normalizedSignal.normalizedValue,
                scope = normalizedSignal.scope,
                sourceSnapshotId = normalizedSignal.sourceSnapshotId,
                observedCount = normalizedSignal.observedCount,
                sampleRefs = normalizedSignal.sampleRefs,
                metadata = normalizedSignal.metadata + mapOf(
                    CLUSTER_KEY_METADATA to buildClusterKey(
                        attributeCode = normalizedSignal.attributeCode,
                        normalizedValue = normalizedSignal.normalizedValue,
                        locale = normalizedSignal.locale,
                        marketCode = normalizedSignal.marketCode,
                        scope = normalizedSignal.scope,
                        policy = policy,
                    ),
                    POLICY_MODE_METADATA to policy.mode.name,
                ),
                firstSeenAt = normalizedSignal.createdAt,
                lastSeenAt = normalizedSignal.createdAt,
            ),
        )

        val exactMatch = findExactExistingMatch(
            attributeCode = normalizedSignal.attributeCode,
            normalizedValue = normalizedSignal.normalizedValue,
            locale = normalizedSignal.locale,
            marketCode = normalizedSignal.marketCode,
            scope = normalizedSignal.scope,
        )
        if (exactMatch != null) {
            return CatalogGovernanceSignalIngestionResult(
                signal = normalizedSignal,
                policy = policy,
                disposition = when (exactMatch.reasonCode) {
                    "alias_exact" -> CatalogGovernanceCandidateDisposition.EXISTING_ALIAS
                    else -> CatalogGovernanceCandidateDisposition.EXISTING_CANONICAL
                },
                observation = observation,
                existingMatch = exactMatch,
                cluster = findClusterForNormalizedValue(
                    attributeCode = normalizedSignal.attributeCode,
                    normalizedValue = normalizedSignal.normalizedValue,
                    locale = normalizedSignal.locale,
                    marketCode = normalizedSignal.marketCode,
                    scope = normalizedSignal.scope,
                ),
                reasons = listOf(exactMatch.reasonCode),
            )
        }

        val noiseReasons = detectNoise(normalizedSignal, policy)
        if (noiseReasons.isNotEmpty() && !policy.allowCandidateCreation) {
            return CatalogGovernanceSignalIngestionResult(
                signal = normalizedSignal,
                policy = policy,
                disposition = CatalogGovernanceCandidateDisposition.REJECT_NOISE,
                observation = observation,
                cluster = findClusterForNormalizedValue(
                    attributeCode = normalizedSignal.attributeCode,
                    normalizedValue = normalizedSignal.normalizedValue,
                    locale = normalizedSignal.locale,
                    marketCode = normalizedSignal.marketCode,
                    scope = normalizedSignal.scope,
                ),
                reasons = noiseReasons,
            )
        }

        if (!policy.allowCandidateCreation) {
            return CatalogGovernanceSignalIngestionResult(
                signal = normalizedSignal,
                policy = policy,
                disposition = CatalogGovernanceCandidateDisposition.OBSERVED_ONLY,
                observation = observation,
                cluster = findClusterForNormalizedValue(
                    attributeCode = normalizedSignal.attributeCode,
                    normalizedValue = normalizedSignal.normalizedValue,
                    locale = normalizedSignal.locale,
                    marketCode = normalizedSignal.marketCode,
                    scope = normalizedSignal.scope,
                ),
                reasons = listOf("policy_observed_only"),
            )
        }

        val aliasMatch = findStrongAliasCandidateMatch(
            attributeCode = normalizedSignal.attributeCode,
            normalizedValue = normalizedSignal.normalizedValue,
            locale = normalizedSignal.locale,
            marketCode = normalizedSignal.marketCode,
            scope = normalizedSignal.scope,
        )
        val candidate = repository.submitCandidate(
            buildCandidate(
                signal = normalizedSignal,
                policy = policy,
                aliasMatch = aliasMatch,
            ),
        )
        val cluster = findClusterForCandidate(candidate)
        return CatalogGovernanceSignalIngestionResult(
            signal = normalizedSignal,
            policy = policy,
            disposition = aliasMatch?.let { CatalogGovernanceCandidateDisposition.ALIAS_CANDIDATE }
                ?: CatalogGovernanceCandidateDisposition.NEW_CANONICAL_CANDIDATE,
            observation = observation,
            candidate = candidate,
            existingMatch = aliasMatch,
            cluster = cluster,
            reasons = buildList {
                if (aliasMatch != null) add("strong_alias_match")
                addAll(noiseReasons)
            },
        )
    }

    suspend fun findClusterForCandidate(candidate: CatalogValueCandidate): CatalogGovernanceCandidateCluster? =
        listCandidateClusters(attributeCode = candidate.attributeCode)
            .firstOrNull { cluster -> candidate.id in cluster.candidateIds }

    suspend fun findClusterForNormalizedValue(
        attributeCode: String,
        normalizedValue: String,
        locale: String?,
        marketCode: String?,
        scope: CatalogGovernanceScope,
    ): CatalogGovernanceCandidateCluster? {
        val policy = policyResolver.resolve(attributeCode)
        val clusterKey = buildClusterKey(
            attributeCode = attributeCode,
            normalizedValue = normalizedValue,
            locale = locale,
            marketCode = marketCode,
            scope = scope,
            policy = policy,
        )
        return listCandidateClusters(attributeCode = attributeCode).firstOrNull { it.clusterKey == clusterKey }
    }

    suspend fun listCandidateClusters(
        attributeCode: String? = null,
    ): List<CatalogGovernanceCandidateCluster> {
        val candidates = repository.listCandidates(attributeCode = attributeCode)
        val observations = repository.listObservations(attributeCode = attributeCode)
        val sourceReliabilityById = repository.listSourceSnapshots().associate { snapshot ->
            snapshot.id to projectionPolicy.evaluateSource(snapshot)
        }
        val grouped = linkedMapOf<String, MutableClusterMembers>()
        observations.forEach { observation ->
            val policy = policyResolver.resolve(observation.attributeCode)
            val clusterKey = observation.metadata[CLUSTER_KEY_METADATA]
                ?: buildClusterKey(
                    attributeCode = observation.attributeCode,
                    normalizedValue = observation.normalizedValue,
                    locale = observation.locale,
                    marketCode = observation.marketCode,
                    scope = observation.scope,
                    policy = policy,
                )
            grouped.getOrPut(clusterKey) {
                MutableClusterMembers(
                    clusterKey = clusterKey,
                    attributeCode = observation.attributeCode,
                    locale = observation.locale,
                    marketCode = observation.marketCode,
                    normalizedValue = observation.normalizedValue,
                    scope = observation.scope,
                    policy = policy,
                )
            }.observations += observation
        }
        candidates.forEach { candidate ->
            val policy = policyResolver.resolve(candidate.attributeCode)
            val clusterKey = candidate.metadata[CLUSTER_KEY_METADATA]
                ?: buildClusterKey(
                    attributeCode = candidate.attributeCode,
                    normalizedValue = candidate.normalizedValue,
                    locale = candidate.locale,
                    marketCode = candidate.marketCode,
                    scope = candidate.scope,
                    policy = policy,
                )
            grouped.getOrPut(clusterKey) {
                MutableClusterMembers(
                    clusterKey = clusterKey,
                    attributeCode = candidate.attributeCode,
                    locale = candidate.locale,
                    marketCode = candidate.marketCode,
                    normalizedValue = candidate.normalizedValue,
                    scope = candidate.scope,
                    policy = policy,
                )
            }.candidates += candidate
        }
        return grouped.values
            .map { members ->
                val existingMatch = findBestExistingMatch(
                    attributeCode = members.attributeCode,
                    normalizedValue = members.normalizedValue,
                    locale = members.locale,
                    marketCode = members.marketCode,
                    scope = members.scope,
                )
                val evidenceScore = scoreEvidence(
                    members = members,
                    sourceReliabilityById = sourceReliabilityById,
                    semanticCollisionCount = semanticCollisionCount(
                        attributeCode = members.attributeCode,
                        normalizedValue = members.normalizedValue,
                        scope = members.scope,
                    ),
                )
                toCluster(
                    members = members,
                    evidenceScore = evidenceScore,
                    existingMatch = existingMatch,
                )
            }
            .sortedWith(
                compareByDescending<CatalogGovernanceCandidateCluster> { it.evidenceScore.totalScore }
                    .thenByDescending { it.evidenceScore.observedCount }
                    .thenBy { it.clusterKey }
            )
    }

    fun resolvePolicy(attributeCode: String): CatalogGovernanceAttributePolicy =
        policyResolver.resolve(attributeCode)

    fun buildClusterKey(
        attributeCode: String,
        normalizedValue: String,
        locale: String?,
        marketCode: String?,
        scope: CatalogGovernanceScope,
        policy: CatalogGovernanceAttributePolicy,
    ): String = listOf(
        attributeCode.trim(),
        policy.dedupTokenMode.name,
        normalizeLocale(locale) ?: "und",
        normalizeMarket(marketCode) ?: "GLOBAL",
        scope.categoryCode ?: "*",
        scope.brandCode ?: "*",
        scope.familyCode ?: "*",
        scope.modelCode ?: "*",
        normalizedValue.trim(),
    ).joinToString("|")

    suspend fun findBestExistingMatch(
        attributeCode: String,
        normalizedValue: String,
        locale: String?,
        marketCode: String?,
        scope: CatalogGovernanceScope,
    ): CatalogGovernanceExistingValueMatch? {
        return findExactExistingMatch(attributeCode, normalizedValue, locale, marketCode, scope)
            ?: findStrongAliasCandidateMatch(attributeCode, normalizedValue, locale, marketCode, scope)
    }

    private suspend fun findExactExistingMatch(
        attributeCode: String,
        normalizedValue: String,
        locale: String?,
        marketCode: String?,
        scope: CatalogGovernanceScope,
    ): CatalogGovernanceExistingValueMatch? {
        val normalized = normalizedValue.trim()
        val exactAlias = repository.listAliases(
            locale = locale,
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            attributeCode = attributeCode,
        )
            .asSequence()
            .filter { it.normalizedAlias == normalized }
            .filter { alias -> isScopeCompatible(query = scope, candidate = alias.scope) }
            .filter { alias -> isLocaleCompatible(locale, alias.locale) }
            .filter { alias -> isMarketCompatible(marketCode, alias.marketCode) }
            .maxByOrNull { alias -> scopeScore(query = scope, candidate = alias.scope) + alias.confidence }
        if (exactAlias != null) {
            return CatalogGovernanceExistingValueMatch(
                targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
                targetCode = exactAlias.targetCode,
                attributeCode = exactAlias.attributeCode,
                canonicalCode = exactAlias.targetCode,
                scope = exactAlias.scope,
                confidence = exactAlias.confidence.coerceIn(0.0, 1.0),
                reasonCode = "alias_exact",
            )
        }

        val canonicalMatches = repository.listCanonicalValuesAnyScope(attributeCode)
            .asSequence()
            .filter { value -> isScopeCompatible(query = scope, candidate = value.scope) }
            .map { value -> value to canonicalMatchScore(value, normalized, locale) }
            .filter { (_, score) -> score >= 0.995 }
            .toList()
        val exactCanonical = canonicalMatches.maxByOrNull { (value, score) ->
            score + scopeScore(query = scope, candidate = value.scope)
        } ?: return null
        val value = exactCanonical.first
        return CatalogGovernanceExistingValueMatch(
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            targetCode = value.canonicalCode,
            attributeCode = value.attributeCode,
            canonicalCode = value.canonicalCode,
            canonicalValueId = value.id,
            scope = value.scope,
            confidence = exactCanonical.second.coerceIn(0.0, 1.0),
            reasonCode = "canonical_exact",
        )
    }

    private suspend fun findStrongAliasCandidateMatch(
        attributeCode: String,
        normalizedValue: String,
        locale: String?,
        marketCode: String?,
        scope: CatalogGovernanceScope,
    ): CatalogGovernanceExistingValueMatch? {
        val candidates = repository.listCanonicalValuesAnyScope(attributeCode)
            .asSequence()
            .filter { value -> isScopeCompatible(query = scope, candidate = value.scope) }
            .map { value ->
                MatchCandidate(
                    canonical = value,
                    score = canonicalMatchScore(value, normalizedValue, locale) + scopeScore(query = scope, candidate = value.scope),
                )
            }
            .filter { it.score >= STRONG_ALIAS_MATCH_THRESHOLD }
            .sortedByDescending { it.score }
            .toList()
        if (candidates.isEmpty()) return null
        if (candidates.size > 1 && candidates[0].score - candidates[1].score < COLLISION_MARGIN) return null
        val best = candidates.first()
        return CatalogGovernanceExistingValueMatch(
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            targetCode = best.canonical.canonicalCode,
            attributeCode = best.canonical.attributeCode,
            canonicalCode = best.canonical.canonicalCode,
            canonicalValueId = best.canonical.id,
            scope = best.canonical.scope,
            confidence = best.score.coerceIn(0.0, 1.0),
            reasonCode = "canonical_near_match",
        )
    }

    private fun buildCandidate(
        signal: CatalogGovernanceValueSignal,
        policy: CatalogGovernanceAttributePolicy,
        aliasMatch: CatalogGovernanceExistingValueMatch?,
    ): CatalogValueCandidate {
        val clusterKey = buildClusterKey(
            attributeCode = signal.attributeCode,
            normalizedValue = signal.normalizedValue,
            locale = signal.locale,
            marketCode = signal.marketCode,
            scope = signal.scope,
            policy = policy,
        )
        val metadata = signal.metadata + buildMap {
            put(CLUSTER_KEY_METADATA, clusterKey)
            put(POLICY_MODE_METADATA, policy.mode.name)
            put(
                DISPOSITION_METADATA,
                if (aliasMatch != null) CatalogGovernanceCandidateDisposition.ALIAS_CANDIDATE.name
                else CatalogGovernanceCandidateDisposition.NEW_CANONICAL_CANDIDATE.name,
            )
            aliasMatch?.canonicalCode?.let { put(MATCHED_CANONICAL_CODE_METADATA, it) }
        }
        return CatalogValueCandidate(
            attributeCode = signal.attributeCode,
            locale = signal.locale,
            marketCode = signal.marketCode,
            rawValue = signal.rawValue,
            normalizedValue = signal.normalizedValue,
            proposedCanonicalCode = aliasMatch?.canonicalCode ?: generatedCanonicalCode(signal),
            proposedCanonicalValue = if (aliasMatch != null) null else signal.rawValue,
            proposedLabels = if (aliasMatch != null) LocalizedText.Empty else localizedTextOf((signal.locale ?: "und") to signal.rawValue),
            proposedCanonicalLocale = if (aliasMatch != null) null else signal.locale,
            scope = signal.scope,
            sourceSnapshotId = signal.sourceSnapshotId,
            status = CatalogGovernanceCandidateStatus.REVIEWING,
            autoConfidence = if (aliasMatch != null) aliasMatch.confidence else DEFAULT_NEW_CANONICAL_CONFIDENCE,
            evidenceCount = signal.observedCount.coerceAtLeast(1),
            metadata = metadata,
            createdAt = signal.createdAt,
            updatedAt = signal.createdAt,
        )
    }

    private suspend fun semanticCollisionCount(
        attributeCode: String,
        normalizedValue: String,
        scope: CatalogGovernanceScope,
    ): Int =
        repository.listCanonicalValuesAnyScope(attributeCode)
            .asSequence()
            .filter { value -> isScopeCompatible(query = scope, candidate = value.scope) }
            .count { value -> canonicalMatchScore(value, normalizedValue, locale = null) >= STRONG_ALIAS_MATCH_THRESHOLD }

    private fun scoreEvidence(
        members: MutableClusterMembers,
        sourceReliabilityById: Map<Long?, CatalogGovernanceSourceReliability>,
        semanticCollisionCount: Int,
    ): com.example.shoppingassistant.domain.catalog.CatalogGovernanceEvidenceScore {
        val observedCount = members.observations.sumOf { it.observedCount }
        val candidateEvidenceCount = members.candidates.sumOf { it.evidenceCount }
        val distinctSourceIds = (members.observations.mapNotNull { it.sourceSnapshotId } + members.candidates.mapNotNull { it.sourceSnapshotId }).toSet()
        val distinctLocales = (members.observations.mapNotNull { it.locale } + members.candidates.mapNotNull { it.locale }).toSet()
        val distinctMarkets = (members.observations.mapNotNull { it.marketCode } + members.candidates.mapNotNull { it.marketCode }).toSet()
        val distinctSellerCount = aggregateMetadataMetric(members.observations, members.candidates, listOf("distinct_seller_count", "seller_count"))
        val userDemandCount = aggregateMetadataMetric(members.observations, members.candidates, listOf("user_demand_count", "query_count", "search_count"))
        val correctionPositiveCount = aggregateMetadataMetric(members.observations, members.candidates, listOf("correction_positive_count", "manual_correction_positive_count"))
        val correctionNegativeCount = aggregateMetadataMetric(members.observations, members.candidates, listOf("correction_negative_count", "manual_correction_negative_count"))
        val firstSeenAt = listOfNotNull(
            members.observations.minOfOrNull { it.firstSeenAt },
            members.candidates.minOfOrNull { it.createdAt },
        ).minOrNull() ?: 0L
        val lastSeenAt = listOfNotNull(
            members.observations.maxOfOrNull { it.lastSeenAt },
            members.candidates.maxOfOrNull { it.updatedAt },
        ).maxOrNull() ?: firstSeenAt
        val persistenceDays = max(1, (((lastSeenAt - firstSeenAt).coerceAtLeast(0L)) / MILLIS_PER_DAY).toInt() + 1)
        val sourceReliabilityScore = if (distinctSourceIds.isEmpty()) {
            projectionPolicy.evaluateSource(snapshot = null).score
        } else {
            distinctSourceIds.map { id -> sourceReliabilityById[id]?.score ?: projectionPolicy.evaluateSource(snapshot = null).score }
                .average()
        }
        val correctionBalance = if (correctionPositiveCount + correctionNegativeCount == 0) {
            0.5
        } else {
            correctionPositiveCount.toDouble() / (correctionPositiveCount + correctionNegativeCount).toDouble()
        }
        val totalScore = (
            min(1.0, observedCount / 8.0) * 0.24 +
                min(1.0, candidateEvidenceCount / 6.0) * 0.12 +
                min(1.0, distinctSourceIds.size / 3.0) * 0.14 +
                min(1.0, persistenceDays / 14.0) * 0.12 +
                min(1.0, userDemandCount / 20.0) * 0.12 +
                min(1.0, distinctSellerCount / 5.0) * 0.08 +
                min(1.0, distinctLocales.size / 2.0) * 0.04 +
                min(1.0, distinctMarkets.size / 2.0) * 0.04 +
                sourceReliabilityScore * 0.10 +
                correctionBalance * 0.10 -
                min(1.0, semanticCollisionCount / 3.0) * 0.22
            ).coerceIn(0.0, 1.0)
        return com.example.shoppingassistant.domain.catalog.CatalogGovernanceEvidenceScore(
            totalScore = totalScore,
            observedCount = observedCount,
            candidateEvidenceCount = candidateEvidenceCount,
            distinctSourceCount = distinctSourceIds.size,
            distinctLocaleCount = distinctLocales.size,
            distinctMarketCount = distinctMarkets.size,
            distinctSellerCount = distinctSellerCount,
            timePersistenceDays = persistenceDays,
            userDemandCount = userDemandCount,
            correctionPositiveCount = correctionPositiveCount,
            correctionNegativeCount = correctionNegativeCount,
            semanticCollisionCount = semanticCollisionCount,
            sourceReliabilityScore = sourceReliabilityScore,
        )
    }

    private fun toCluster(
        members: MutableClusterMembers,
        evidenceScore: com.example.shoppingassistant.domain.catalog.CatalogGovernanceEvidenceScore,
        existingMatch: CatalogGovernanceExistingValueMatch?,
    ): CatalogGovernanceCandidateCluster {
        val reasons = mutableListOf<String>()
        val (recommendedDisposition, recommendation) = when {
            existingMatch?.reasonCode == "alias_exact" -> {
                reasons += "already_known_alias"
                CatalogGovernanceCandidateDisposition.EXISTING_ALIAS to CatalogGovernanceClusterRecommendation.MONITOR
            }
            existingMatch?.reasonCode == "canonical_exact" -> {
                reasons += "already_known_canonical"
                CatalogGovernanceCandidateDisposition.EXISTING_CANONICAL to CatalogGovernanceClusterRecommendation.MONITOR
            }
            !members.policy.allowCandidateCreation -> {
                reasons += "policy_observed_only"
                CatalogGovernanceCandidateDisposition.OBSERVED_ONLY to CatalogGovernanceClusterRecommendation.MONITOR
            }
            evidenceScore.semanticCollisionCount > 1 && existingMatch == null -> {
                reasons += "semantic_collision"
                CatalogGovernanceCandidateDisposition.REJECT_NOISE to CatalogGovernanceClusterRecommendation.REJECT
            }
            existingMatch != null && evidenceScore.totalScore >= 0.84 && members.policy.allowAutoPromotion -> {
                reasons += "auto_promote_alias_ready"
                CatalogGovernanceCandidateDisposition.ALIAS_CANDIDATE to CatalogGovernanceClusterRecommendation.AUTO_PROMOTE_ALIAS
            }
            existingMatch != null && evidenceScore.totalScore >= 0.60 -> {
                reasons += "review_alias_candidate"
                CatalogGovernanceCandidateDisposition.ALIAS_CANDIDATE to CatalogGovernanceClusterRecommendation.REVIEW_ALIAS
            }
            evidenceScore.totalScore >= 0.86 && members.policy.allowAutoPromotion -> {
                reasons += "auto_promote_canonical_ready"
                CatalogGovernanceCandidateDisposition.NEW_CANONICAL_CANDIDATE to CatalogGovernanceClusterRecommendation.AUTO_PROMOTE_CANONICAL
            }
            evidenceScore.totalScore >= 0.62 -> {
                reasons += "review_canonical_candidate"
                CatalogGovernanceCandidateDisposition.NEW_CANONICAL_CANDIDATE to CatalogGovernanceClusterRecommendation.REVIEW_CANONICAL
            }
            evidenceScore.totalScore <= 0.20 -> {
                reasons += "low_evidence"
                CatalogGovernanceCandidateDisposition.REJECT_NOISE to CatalogGovernanceClusterRecommendation.REJECT
            }
            else -> {
                reasons += "monitor_until_more_evidence"
                CatalogGovernanceCandidateDisposition.NEW_CANONICAL_CANDIDATE to CatalogGovernanceClusterRecommendation.MONITOR
            }
        }
        return CatalogGovernanceCandidateCluster(
            clusterKey = members.clusterKey,
            attributeCode = members.attributeCode,
            locale = members.locale,
            marketCode = members.marketCode,
            normalizedValue = members.normalizedValue,
            scope = members.scope,
            policy = members.policy,
            rawValues = (members.observations.map { it.rawValue } + members.candidates.map { it.rawValue }).distinct().sorted(),
            candidateIds = members.candidates.mapNotNull { it.id },
            candidateStatuses = members.candidates.map { it.status }.distinct(),
            evidenceScore = evidenceScore,
            existingMatch = existingMatch,
            recommendedDisposition = recommendedDisposition,
            recommendation = recommendation,
            reasons = reasons,
        )
    }

    private fun canonicalMatchScore(
        value: CatalogAttributeValueCanon,
        normalizedValue: String,
        locale: String?,
    ): Double {
        val candidates = buildList {
            add(value.normalizedValue)
            add(normalizeFreeText(value.canonicalValue))
            value.labels.asMap().forEach { (labelLocale, label) ->
                val normalizedLabel = normalizeFreeText(label)
                add(normalizedLabel)
                if (locale != null && isLocaleCompatible(locale, labelLocale)) {
                    add(normalizedLabel)
                }
            }
        }.distinct()
        return candidates.maxOfOrNull { candidate -> lexicalSimilarity(candidate, normalizedValue) } ?: 0.0
    }

    private fun detectNoise(
        signal: CatalogGovernanceValueSignal,
        policy: CatalogGovernanceAttributePolicy,
    ): List<String> = buildList {
        val raw = signal.rawValue.trim()
        if (raw.length < 2) add("raw_value_too_short")
        if (raw.none { it.isLetterOrDigit() }) add("raw_value_no_alnum")
        if (policy.mode == CatalogGovernanceAttributePolicyMode.CLOSED && signal.normalizedValue.length < 2) {
            add("closed_value_too_short")
        }
    }

    private fun aggregateMetadataMetric(
        observations: List<CatalogValueObservation>,
        candidates: List<CatalogValueCandidate>,
        keys: List<String>,
    ): Int =
        (observations.map { it.metadata } + candidates.map { it.metadata })
            .sumOf { metadata ->
                keys.firstNotNullOfOrNull { key -> metadata[key]?.toIntOrNull() } ?: 0
            }

    private fun generatedCanonicalCode(signal: CatalogGovernanceValueSignal): String =
        signal.normalizedValue
            .uppercase(Locale.ROOT)
            .replace("[^A-Z0-9]+".toRegex(), "_")
            .trim('_')
            .ifEmpty { "VALUE_${signal.attributeCode.uppercase(Locale.ROOT)}" }

    private fun lexicalSimilarity(
        left: String,
        right: String,
    ): Double {
        val a = normalizeFreeText(left)
        val b = normalizeFreeText(right)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val aTokens = a.split(' ').filter { it.isNotBlank() }.toSet()
        val bTokens = b.split(' ').filter { it.isNotBlank() }.toSet()
        val jaccard = if (aTokens.isEmpty() && bTokens.isEmpty()) 0.0 else {
            aTokens.intersect(bTokens).size.toDouble() / aTokens.union(bTokens).size.toDouble()
        }
        val edit = normalizedEditSimilarity(a, b)
        val prefixBoost = if (a.startsWith(b) || b.startsWith(a)) 0.05 else 0.0
        return max(jaccard, edit).plus(prefixBoost).coerceAtMost(1.0)
    }

    private fun normalizedEditSimilarity(
        left: String,
        right: String,
    ): Double {
        val maxLength = max(left.length, right.length)
        if (maxLength == 0) return 1.0
        return 1.0 - (levenshtein(left, right).toDouble() / maxLength.toDouble())
    }

    private fun levenshtein(
        left: String,
        right: String,
    ): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        left.forEachIndexed { indexLeft, charLeft ->
            current[0] = indexLeft + 1
            right.forEachIndexed { indexRight, charRight ->
                val substitution = if (charLeft == charRight) 0 else 1
                current[indexRight + 1] = min(
                    min(current[indexRight] + 1, previous[indexRight + 1] + 1),
                    previous[indexRight] + substitution,
                )
            }
            current.copyInto(previous)
        }
        return previous[right.length]
    }

    private fun isScopeCompatible(
        query: CatalogGovernanceScope,
        candidate: CatalogGovernanceScope,
    ): Boolean = scopeFieldCompatible(query.categoryCode, candidate.categoryCode) &&
        scopeFieldCompatible(query.brandCode, candidate.brandCode) &&
        scopeFieldCompatible(query.familyCode, candidate.familyCode) &&
        scopeFieldCompatible(query.modelCode, candidate.modelCode)

    private fun scopeFieldCompatible(
        query: String?,
        candidate: String?,
    ): Boolean = query == null || candidate == null || query == candidate

    private fun scopeScore(
        query: CatalogGovernanceScope,
        candidate: CatalogGovernanceScope,
    ): Double = listOf(
        query.categoryCode to candidate.categoryCode,
        query.brandCode to candidate.brandCode,
        query.familyCode to candidate.familyCode,
        query.modelCode to candidate.modelCode,
    ).sumOf { (queryValue, candidateValue) ->
        when {
            queryValue != null && candidateValue != null && queryValue == candidateValue -> 0.10
            candidateValue == null -> 0.03
            else -> 0.0
        }
    }

    private fun isLocaleCompatible(
        preferred: String?,
        actual: String?,
    ): Boolean {
        if (preferred == null || actual == null) return true
        val preferredNormalized = normalizeLocale(preferred) ?: return false
        val actualNormalized = normalizeLocale(actual) ?: return false
        return preferredNormalized == actualNormalized ||
            preferredNormalized.substringBefore('-') == actualNormalized.substringBefore('-')
    }

    private fun isMarketCompatible(
        query: String?,
        candidate: String?,
    ): Boolean = query == null || candidate == null || normalizeMarket(query) == normalizeMarket(candidate)

    private fun normalizeFreeText(value: String?): String =
        value?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('ё', 'е')
            ?.replace("[^\\p{L}\\p{Nd}]+".toRegex(), " ")
            ?.trim()
            .orEmpty()

    private fun normalizeLocale(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    private fun normalizeMarket(value: String?): String? =
        value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    private fun CatalogGovernanceValueSignal.normalize(): CatalogGovernanceValueSignal =
        copy(
            attributeCode = attributeCode.trim(),
            rawValue = rawValue.trim(),
            normalizedValue = normalizedValue.trim().ifBlank { normalizeFreeText(rawValue) },
            locale = normalizeLocale(locale),
            marketCode = normalizeMarket(marketCode),
            scope = CatalogGovernanceScope(
                categoryCode = scope.categoryCode?.trim()?.takeIf { it.isNotEmpty() },
                brandCode = scope.brandCode?.trim()?.takeIf { it.isNotEmpty() },
                familyCode = scope.familyCode?.trim()?.takeIf { it.isNotEmpty() },
                modelCode = scope.modelCode?.trim()?.takeIf { it.isNotEmpty() },
            ),
            sampleRefs = sampleRefs.mapNotNull { it.trim().takeIf { sample -> sample.isNotEmpty() } }.distinct(),
            metadata = metadata.entries.associateNotNull { (key, value) ->
                key.trim().takeIf { it.isNotEmpty() }?.let { normalizedKey ->
                    value.trim().takeIf { it.isNotEmpty() }?.let { normalizedValue ->
                        normalizedKey to normalizedValue
                    }
                }
            },
        )

    private data class MatchCandidate(
        val canonical: CatalogAttributeValueCanon,
        val score: Double,
    )

    private data class MutableClusterMembers(
        val clusterKey: String,
        val attributeCode: String,
        val locale: String?,
        val marketCode: String?,
        val normalizedValue: String,
        val scope: CatalogGovernanceScope,
        val policy: CatalogGovernanceAttributePolicy,
        val observations: MutableList<CatalogValueObservation> = mutableListOf(),
        val candidates: MutableList<CatalogValueCandidate> = mutableListOf(),
    )

    private companion object {
        private const val DEFAULT_NEW_CANONICAL_CONFIDENCE: Double = 0.58
        private const val STRONG_ALIAS_MATCH_THRESHOLD: Double = 0.88
        private const val COLLISION_MARGIN: Double = 0.05
        private const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
        const val CLUSTER_KEY_METADATA: String = "clusterKey"
        const val POLICY_MODE_METADATA: String = "policyMode"
        const val DISPOSITION_METADATA: String = "candidateDisposition"
        const val MATCHED_CANONICAL_CODE_METADATA: String = "matchedCanonicalCode"
    }
}

private inline fun <K, V> Iterable<Map.Entry<K, V>>.associateNotNull(
    transform: (Map.Entry<K, V>) -> Pair<K, V>?,
): Map<K, V> =
    buildMap {
        for (entry in this@associateNotNull) {
            val pair = transform(entry) ?: continue
            put(pair.first, pair.second)
        }
    }
