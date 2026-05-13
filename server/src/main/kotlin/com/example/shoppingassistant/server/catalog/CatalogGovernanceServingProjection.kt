package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasKind
import com.example.shoppingassistant.domain.catalog.AliasMatchKind
import com.example.shoppingassistant.domain.catalog.AliasSource
import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.AttributeValueDictEntry
import com.example.shoppingassistant.domain.catalog.CatalogAliasCanon
import com.example.shoppingassistant.domain.catalog.CatalogAttributeValueCanon
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyEntry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistryDocument
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasTargetKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateDisposition
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateStatus
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecision
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionAction
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceDecisionEntityKind
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceExistingValueMatch
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceRepository
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceSourceTier
import com.example.shoppingassistant.domain.catalog.CatalogProductFamilyCanon
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CatalogValueCandidate
import com.example.shoppingassistant.domain.catalog.CatalogValueObservation
import com.example.shoppingassistant.domain.catalog.SeedCatalogCanonicalProductFamilyRegistryProvider
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.server.db.DatabaseFactory
import java.time.Instant
import java.util.Locale
import kotlin.math.max

data class CatalogGovernanceSourceReliability(
    val sourceSnapshotId: Long?,
    val tier: CatalogGovernanceSourceTier?,
    val score: Double,
    val projectionEligible: Boolean,
    val autoPromotionEligible: Boolean,
)

data class CatalogGovernancePromotionAssessment(
    val candidate: CatalogValueCandidate,
    val sourceReliability: CatalogGovernanceSourceReliability,
    val observedCount: Int,
    val cluster: com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateCluster? = null,
    val recommendedDisposition: CatalogGovernanceCandidateDisposition? = null,
    val existingMatch: CatalogGovernanceExistingValueMatch? = null,
    val regressionIssues: List<String> = emptyList(),
    val eligible: Boolean,
    val reasons: List<String>,
)

data class CatalogGovernancePromotionResult(
    val candidate: CatalogValueCandidate,
    val canonicalValue: CatalogAttributeValueCanon,
    val alias: CatalogAliasCanon?,
    val decision: CatalogGovernanceDecision,
    val assessment: CatalogGovernancePromotionAssessment,
)

data class CatalogGovernanceServingProjectionReport(
    val projectedBrandAliases: Int,
    val projectedAttributeHints: Int,
    val projectedAttributes: Int,
    val projectedProductFamilies: Int,
    val skippedAliases: Int,
)

data class CatalogGovernanceServingArtifacts(
    val aliasEntries: List<AliasEntry>,
    val valueDictionaries: List<AttributeValueDict>,
    val productFamilies: CatalogCanonicalProductFamilyRegistryDocument,
    val report: CatalogGovernanceServingProjectionReport,
)

class CatalogGovernanceProjectionPolicy(
    val minProjectionReliabilityScore: Double = 0.75,
    val minAliasConfidenceForProjection: Double = 0.70,
    val minAutoConfidenceForPromotion: Double = 0.70,
    val minObservedCountForPromotion: Int = 3,
    val minCandidateEvidenceCountForPromotion: Int = 3,
    val minAutoPromotionReliabilityScore: Double = 0.88,
) {
    fun evaluateSource(snapshot: CatalogGovernanceSourceSnapshot?): CatalogGovernanceSourceReliability {
        val baseScore = when (snapshot?.tier) {
            CatalogGovernanceSourceTier.AUTHORITATIVE -> 1.0
            CatalogGovernanceSourceTier.MANUAL_EDITORIAL -> 0.98
            CatalogGovernanceSourceTier.PARTNER_STRUCTURED -> 0.90
            CatalogGovernanceSourceTier.SYSTEM_GENERATED -> 0.82
            CatalogGovernanceSourceTier.MARKETPLACE -> 0.62
            CatalogGovernanceSourceTier.USER_SIGNAL -> 0.45
            null -> 0.80
        }
        return CatalogGovernanceSourceReliability(
            sourceSnapshotId = snapshot?.id,
            tier = snapshot?.tier,
            score = baseScore,
            projectionEligible = baseScore >= minProjectionReliabilityScore,
            autoPromotionEligible = baseScore >= minAutoPromotionReliabilityScore,
        )
    }

    fun shouldProjectAlias(
        alias: CatalogAliasCanon,
        reliability: CatalogGovernanceSourceReliability,
    ): Boolean =
        alias.status == com.example.shoppingassistant.domain.catalog.CatalogGovernanceAliasStatus.ACTIVE &&
            alias.confidence >= minAliasConfidenceForProjection &&
            reliability.projectionEligible

    fun assessCandidate(
        candidate: CatalogValueCandidate,
        reliability: CatalogGovernanceSourceReliability,
        observedCount: Int,
    ): CatalogGovernancePromotionAssessment {
        val reasons = mutableListOf<String>()
        if (candidate.status == CatalogGovernanceCandidateStatus.PROMOTED) {
            reasons += "already_promoted"
        }
        if (candidate.status == CatalogGovernanceCandidateStatus.REJECTED) {
            reasons += "candidate_rejected"
        }
        if (candidate.proposedCanonicalValue.isNullOrBlank() && candidate.proposedLabels.isBlank()) {
            reasons += "missing_canonical_value"
        }
        if (candidate.status != CatalogGovernanceCandidateStatus.APPROVED &&
            candidate.autoConfidence < minAutoConfidenceForPromotion
        ) {
            reasons += "auto_confidence_too_low"
        }
        if (max(candidate.evidenceCount, observedCount) < minCandidateEvidenceCountForPromotion &&
            observedCount < minObservedCountForPromotion
        ) {
            reasons += "insufficient_evidence"
        }
        val forceApproved = candidate.status == CatalogGovernanceCandidateStatus.APPROVED
        if (!forceApproved && !reliability.autoPromotionEligible) {
            reasons += "source_reliability_too_low"
        }
        return CatalogGovernancePromotionAssessment(
            candidate = candidate,
            sourceReliability = reliability,
            observedCount = observedCount,
            eligible = reasons.isEmpty(),
            reasons = reasons,
        )
    }
}

class CatalogGovernanceServingProjectionService(
    private val repository: CatalogGovernanceRepository,
    private val policy: CatalogGovernanceProjectionPolicy = CatalogGovernanceProjectionPolicy(),
    private val workflowService: CatalogGovernanceWorkflowService = CatalogGovernanceWorkflowService(
        repository = repository,
        projectionPolicy = policy,
    ),
    private val runtimeInvalidatorProvider: () -> CatalogGovernanceRuntimeInvalidator = {
        NoOpCatalogGovernanceRuntimeInvalidator
    },
) {
    suspend fun buildServingArtifacts(): CatalogGovernanceServingArtifacts =
        buildServingArtifacts(extraCanonicalValues = emptyList(), extraAliases = emptyList())

    private suspend fun buildServingArtifacts(
        extraCanonicalValues: List<CatalogAttributeValueCanon>,
        extraAliases: List<CatalogAliasCanon>,
    ): CatalogGovernanceServingArtifacts {
        val sourcesById = repository.listSourceSnapshots().associateBy { it.id }
        val governanceAliases = (repository.listAliases() + extraAliases)
            .distinctBy { alias ->
                listOf(
                    alias.locale,
                    alias.marketCode.orEmpty(),
                    alias.normalizedAlias,
                    alias.targetKind.name,
                    alias.targetCode,
                    alias.attributeCode.orEmpty(),
                    alias.scope.categoryCode.orEmpty(),
                    alias.scope.brandCode.orEmpty(),
                    alias.scope.familyCode.orEmpty(),
                    alias.scope.modelCode.orEmpty(),
                ).joinToString("|")
            }
        val projectedBrandAliases = governanceAliases
            .filter { it.targetKind == CatalogGovernanceAliasTargetKind.BRAND }
            .filter { alias -> policy.shouldProjectAlias(alias, policy.evaluateSource(sourcesById[alias.sourceSnapshotId])) }
            .map { alias -> alias.toBrandAliasEntry() }

        val projectedAttributeHints = governanceAliases
            .filter { it.targetKind == CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE && !it.attributeCode.isNullOrBlank() }
            .filter { alias -> policy.shouldProjectAlias(alias, policy.evaluateSource(sourcesById[alias.sourceSnapshotId])) }
            .filter(::isProjectableAttributeHintAlias)
            .map { alias -> alias.toAttributeHintAliasEntry() }

        val aliasEntries = mergeAliasEntries(
            baseEntries = CatalogSeed.aliasEntries,
            governanceEntries = projectedBrandAliases + projectedAttributeHints,
        )

        val valueDictionaryProjection = buildValueDictionaries(
            aliases = governanceAliases,
            sourcesById = sourcesById,
            extraCanonicalValues = extraCanonicalValues,
        )

        val productFamilyProjection = buildProductFamilyDocument(
            aliases = governanceAliases,
            sourcesById = sourcesById,
        )

        return CatalogGovernanceServingArtifacts(
            aliasEntries = aliasEntries,
            valueDictionaries = valueDictionaryProjection.dictionaries,
            productFamilies = productFamilyProjection.document,
            report = CatalogGovernanceServingProjectionReport(
                projectedBrandAliases = projectedBrandAliases.size,
                projectedAttributeHints = projectedAttributeHints.size,
                projectedAttributes = valueDictionaryProjection.projectedAttributeCodes.size,
                projectedProductFamilies = productFamilyProjection.projectedFamilyCodes.size,
                skippedAliases = governanceAliases.size - projectedBrandAliases.size - projectedAttributeHints.size,
            ),
        )
    }

    suspend fun syncDatabaseServingArtifacts(
        syncMode: CatalogSeedSyncMode = CatalogSeedSyncMode.UPSERT_ONLY,
    ): CatalogGovernanceServingArtifacts {
        val artifacts = buildServingArtifacts()
        val regressionIssues = validateServingArtifacts(artifacts)
        check(regressionIssues.isEmpty()) {
            "Serving artifact validation failed: ${regressionIssues.joinToString(",")}"
        }
        DatabaseFactory.dbQuery {
            CatalogSeeder.syncServingAliasEntries(artifacts.aliasEntries, syncMode = syncMode)
            CatalogSeeder.syncServingAttributeValueDict(artifacts.valueDictionaries, syncMode = syncMode)
        }
        runtimeInvalidatorProvider().invalidate(
            CatalogGovernanceRuntimeInvalidationEvent(
                reason = "serving_artifacts_synced",
                metadata = mapOf(
                    "projectedAttributes" to artifacts.report.projectedAttributes.toString(),
                    "projectedProductFamilies" to artifacts.report.projectedProductFamilies.toString(),
                ),
            ),
        )
        return artifacts
    }

    suspend fun assessPromotionCandidates(
        attributeCode: String? = null,
    ): List<CatalogGovernancePromotionAssessment> {
        val sourcesById = repository.listSourceSnapshots().associateBy { it.id }
        val candidates = repository.listCandidates(attributeCode = attributeCode)
        return candidates.map { candidate ->
            val reliability = policy.evaluateSource(sourcesById[candidate.sourceSnapshotId])
            val cluster = workflowService.findClusterForCandidate(candidate)
            val observedCount = cluster?.evidenceScore?.observedCount ?: repository.listObservations(
                attributeCode = candidate.attributeCode,
                scope = candidate.scope,
                locale = candidate.locale,
                marketCode = candidate.marketCode,
            ).filter { observation ->
                observation.normalizedValue == candidate.normalizedValue
            }.sumOf { it.observedCount }
            val baseAssessment = policy.assessCandidate(
                candidate = candidate,
                reliability = reliability,
                observedCount = observedCount,
            )
            val regressionIssues = if (baseAssessment.reasons.isEmpty()) {
                simulatePromotionRegressionIssues(candidate, cluster)
            } else {
                emptyList()
            }
            val reasons = buildList {
                addAll(baseAssessment.reasons)
                cluster?.let { currentCluster ->
                    if (currentCluster.recommendedDisposition == CatalogGovernanceCandidateDisposition.REJECT_NOISE) {
                        add("cluster_rejected")
                    }
                }
                addAll(regressionIssues)
            }
            baseAssessment.copy(
                observedCount = observedCount,
                cluster = cluster,
                recommendedDisposition = cluster?.recommendedDisposition,
                existingMatch = cluster?.existingMatch,
                regressionIssues = regressionIssues,
                eligible = reasons.isEmpty(),
                reasons = reasons,
            )
        }
    }

    suspend fun promoteCandidate(
        candidateId: Long,
        actor: String,
        reasonCode: String = "governance_projection_promotion",
    ): CatalogGovernancePromotionResult {
        val assessment = assessPromotionCandidates()
            .firstOrNull { it.candidate.id == candidateId }
            ?: error("Candidate id=$candidateId not found.")
        check(assessment.eligible) {
            "Candidate id=$candidateId is not eligible for promotion: ${assessment.reasons.joinToString(",")}"
        }

        val candidate = assessment.candidate
        val now = System.currentTimeMillis()
        val canonicalValue = ensureCanonicalValue(
            candidate = candidate,
            assessment = assessment,
            now = now,
        )
        val alias = maybeCreateAliasForCandidate(candidate, canonicalValue, assessment, now)
        val prePromotionArtifacts = buildServingArtifacts(
            extraCanonicalValues = listOf(canonicalValue),
            extraAliases = listOfNotNull(alias),
        )
        val regressionIssues = validateServingArtifacts(prePromotionArtifacts)
        check(regressionIssues.isEmpty()) {
            "Candidate id=$candidateId failed regression validation: ${regressionIssues.joinToString(",")}"
        }
        val promotedCandidate = repository.submitCandidate(
            candidate.copy(
                status = CatalogGovernanceCandidateStatus.PROMOTED,
                updatedAt = now,
            ),
        )
        val decision = repository.recordDecision(
            CatalogGovernanceDecision(
                entityKind = CatalogGovernanceDecisionEntityKind.VALUE_CANDIDATE,
                entityRef = promotedCandidate.id?.toString() ?: candidateId.toString(),
                action = CatalogGovernanceDecisionAction.PROMOTE,
                reasonCode = reasonCode,
                actor = actor,
                payload = mapOf(
                    "canonicalCode" to canonicalValue.canonicalCode,
                    "attributeCode" to canonicalValue.attributeCode,
                ),
                createdAt = now,
            ),
        )
        syncDatabaseServingArtifacts(syncMode = CatalogSeedSyncMode.UPSERT_ONLY)
        return CatalogGovernancePromotionResult(
            candidate = promotedCandidate,
            canonicalValue = canonicalValue,
            alias = alias,
            decision = decision,
            assessment = assessment,
        )
    }

    private suspend fun buildValueDictionaries(
        aliases: List<CatalogAliasCanon>,
        sourcesById: Map<Long?, CatalogGovernanceSourceSnapshot>,
        extraCanonicalValues: List<CatalogAttributeValueCanon>,
    ): ValueDictionaryProjection {
        val baseByAttribute = CatalogSeed.valueDictionaries.associateBy { it.attributeCode }.toMutableMap()
        val projectedByAttribute = LinkedHashMap<String, MutableMap<String, ProjectedValueEntry>>()
        val governanceAttributeCodes = repository.listCanonicalValueAttributeCodes() + extraCanonicalValues.map { it.attributeCode }
        governanceAttributeCodes.forEach { attributeCode ->
            (repository.listCanonicalValuesAnyScope(attributeCode) + extraCanonicalValues.filter { it.attributeCode == attributeCode })
                .distinctBy { value ->
                    listOf(
                        value.attributeCode,
                        value.canonicalCode,
                        value.scope.categoryCode.orEmpty(),
                        value.scope.brandCode.orEmpty(),
                        value.scope.familyCode.orEmpty(),
                        value.scope.modelCode.orEmpty(),
                    ).joinToString("|")
                }
                .filter { it.status == com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus.ACTIVE }
                .forEach { canonicalValue ->
                    val eligibleAliases = aliases
                        .asSequence()
                        .filter { it.targetKind == CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE }
                        .filter { it.targetCode == canonicalValue.canonicalCode }
                        .filter { it.attributeCode == canonicalValue.attributeCode }
                        .filter { alias -> policy.shouldProjectAlias(alias, policy.evaluateSource(sourcesById[alias.sourceSnapshotId])) }
                        .map { it.aliasText }
                        .distinct()
                        .sorted()
                        .toList()
                    projectedByAttribute
                        .getOrPut(attributeCode) { LinkedHashMap() }[canonicalValue.canonicalCode] = ProjectedValueEntry(
                        entry = AttributeValueDictEntry(
                            canonicalCode = canonicalValue.canonicalCode,
                            canonicalValue = canonicalValue.displayValue(),
                            synonyms = eligibleAliases,
                            rank = 0,
                        ),
                        priority = canonicalValue.scope.servingProjectionPriority(),
                    )
                }
        }

        projectedByAttribute.forEach { (attributeCode, entriesByCode) ->
            val base = baseByAttribute[attributeCode]
            val mergedEntries = LinkedHashMap<String, AttributeValueDictEntry>()
            val entryPriorities = LinkedHashMap<String, Int>()
            base?.entries?.forEach { entry -> mergedEntries[entry.canonicalCode] = entry }
            base?.entries?.forEach { entry -> entryPriorities[entry.canonicalCode] = 0 }
            entriesByCode.values.forEach { projected ->
                val existing = mergedEntries[projected.entry.canonicalCode]
                val existingPriority = entryPriorities[projected.entry.canonicalCode]
                mergedEntries[projected.entry.canonicalCode] = if (existing == null) {
                    projected.entry
                } else {
                    existing.copy(
                        canonicalValue = projected.entry.canonicalValue.ifBlank { existing.canonicalValue },
                        synonyms = (existing.synonyms + projected.entry.synonyms).distinct().sorted(),
                        rank = max(existing.rank, projected.entry.rank),
                    )
                }
                entryPriorities[projected.entry.canonicalCode] = minOf(existingPriority ?: projected.priority, projected.priority)
            }
            val sanitizedEntries = sanitizeValueDictionaryEntries(
                entries = mergedEntries.values.toList(),
                prioritiesByCanonicalCode = entryPriorities,
            )
            baseByAttribute[attributeCode] = AttributeValueDict(
                attributeCode = attributeCode,
                code = base?.code ?: attributeCode,
                entries = sanitizedEntries.sortedBy { it.canonicalCode },
            )
        }
        return ValueDictionaryProjection(
            dictionaries = baseByAttribute.values.sortedBy { it.attributeCode },
            projectedAttributeCodes = projectedByAttribute.keys,
        )
    }

    private suspend fun buildProductFamilyDocument(
        aliases: List<CatalogAliasCanon>,
        sourcesById: Map<Long?, CatalogGovernanceSourceSnapshot>,
    ): ProductFamilyProjection {
        val baseByFamilyCode = SeedCatalogCanonicalProductFamilyRegistryProvider.families()
            .associateBy { it.familyCode }
            .toMutableMap()
        val brandsByCode = repository.listBrands().associateBy { it.code }
        val governanceFamilies = repository.listProductFamilies()
            .filter { it.status == com.example.shoppingassistant.domain.catalog.CatalogGovernanceEntityStatus.ACTIVE }
        governanceFamilies.forEach { family ->
                val brand = brandsByCode[family.brandCode] ?: return@forEach
                val brandAliases = aliases
                    .asSequence()
                    .filter { it.targetKind == CatalogGovernanceAliasTargetKind.BRAND }
                    .filter { it.targetCode == brand.code }
                    .filter { alias -> policy.shouldProjectAlias(alias, policy.evaluateSource(sourcesById[alias.sourceSnapshotId])) }
                    .map { it.aliasText }
                    .distinct()
                    .sorted()
                val familyAliases = aliases
                    .asSequence()
                    .filter { it.targetKind == CatalogGovernanceAliasTargetKind.PRODUCT_FAMILY }
                    .filter { it.targetCode == family.code }
                    .filter { alias -> policy.shouldProjectAlias(alias, policy.evaluateSource(sourcesById[alias.sourceSnapshotId])) }
                    .map { it.aliasText }
                    .distinct()
                    .sorted()

                val existing = baseByFamilyCode[family.code]
                baseByFamilyCode[family.code] = CatalogCanonicalProductFamilyEntry(
                    familyCode = family.code,
                    defaultCategoryCode = family.defaultCategoryCode ?: existing?.defaultCategoryCode ?: "TECH.PHONES",
                    brandCanonical = brand.displayLabel(),
                    brandAliases = (existing?.brandAliases.orEmpty() + brandAliases).distinct(),
                    familyCanonical = family.displayLabel(),
                    familyAliases = (existing?.familyAliases.orEmpty() + familyAliases).distinct(),
                    prettyModelPrefix = family.prettyModelPrefix,
                    variantTokens = if (family.variantTokens.isNotEmpty()) family.variantTokens else existing?.variantTokens.orEmpty(),
                    accessoryBlockers = if (family.accessoryBlockers.isNotEmpty()) family.accessoryBlockers else existing?.accessoryBlockers.orEmpty(),
                )
            }

        return ProductFamilyProjection(
            document = CatalogCanonicalProductFamilyRegistryDocument(
                schemaVersion = "1.0.0",
                families = baseByFamilyCode.values.sortedBy { it.familyCode },
            ),
            projectedFamilyCodes = governanceFamilies.map { it.code }.toSet(),
        )
    }

    private fun mergeAliasEntries(
        baseEntries: List<AliasEntry>,
        governanceEntries: List<AliasEntry>,
    ): List<AliasEntry> {
        val merged = LinkedHashMap<String, AliasEntry>()
        (baseEntries + governanceEntries)
            .sortedWith(
                compareByDescending<AliasEntry> { it.weight }
                    .thenBy { it.kind.name }
                    .thenBy { it.locale }
                    .thenBy { it.normalizedTerm },
            )
            .forEach { entry ->
                val key = "${entry.locale}|${entry.normalizedTerm}|${entry.kind.name}|${entry.targetCode}"
                val existing = merged[key]
                merged[key] = if (existing == null) {
                    entry
                } else {
                    existing.copy(
                        weight = max(existing.weight, entry.weight),
                        isBlocked = existing.isBlocked || entry.isBlocked,
                        notes = listOfNotNull(existing.notes, entry.notes).distinct().joinToString(" | ").ifBlank { null },
                    )
                }
            }
        return merged.values.toList()
    }

    private suspend fun ensureCanonicalValue(
        candidate: CatalogValueCandidate,
        assessment: CatalogGovernancePromotionAssessment,
        now: Long,
    ): CatalogAttributeValueCanon {
        val existingMatch = assessment.existingMatch
        if (existingMatch?.canonicalCode != null) {
            repository.listCanonicalValuesAnyScope(candidate.attributeCode)
                .firstOrNull { value -> value.canonicalCode == existingMatch.canonicalCode }
                ?.let { return it }
        }
        val existing = repository.listCanonicalValues(
            attributeCode = candidate.attributeCode,
            scope = candidate.scope,
        ).firstOrNull { value ->
            value.canonicalCode == candidate.proposedCanonicalCode ||
                value.normalizedValue == candidate.normalizedValue
        }
        if (existing != null) return existing

        val canonicalLabels = if (!candidate.proposedLabels.isBlank()) {
            candidate.proposedLabels
        } else {
            localizedTextOf((candidate.locale ?: "und") to candidate.rawValue)
        }
        return repository.upsertAttributeValueCanon(
            CatalogAttributeValueCanon(
                attributeCode = candidate.attributeCode,
                canonicalCode = candidate.proposedCanonicalCode ?: generatedCanonicalCode(candidate),
                canonicalValue = candidate.proposedCanonicalValue
                    ?: canonicalLabels.resolve(locale = candidate.proposedCanonicalLocale ?: "en", fallback = candidate.rawValue)
                    ?: candidate.rawValue,
                labels = canonicalLabels,
                canonicalLocale = candidate.proposedCanonicalLocale ?: candidate.locale,
                normalizedValue = candidate.normalizedValue,
                scope = candidate.scope,
                metadata = candidate.metadata + mapOf("promotedFromCandidateId" to (candidate.id?.toString() ?: "")),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun maybeCreateAliasForCandidate(
        candidate: CatalogValueCandidate,
        canonicalValue: CatalogAttributeValueCanon,
        assessment: CatalogGovernancePromotionAssessment,
        now: Long,
    ): CatalogAliasCanon? {
        if (assessment.existingMatch?.reasonCode == "alias_exact") return null
        val locale = candidate.locale ?: return null
        val rawValue = candidate.rawValue.trim()
        if (rawValue.isEmpty()) return null
        val canonicalDisplay = canonicalValue.displayValue()
        if (rawValue.equals(canonicalDisplay, ignoreCase = true)) return null
        return repository.upsertAlias(
            CatalogAliasCanon(
                locale = locale,
                marketCode = candidate.marketCode,
                aliasText = rawValue,
                normalizedAlias = candidate.normalizedValue,
                targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
                targetCode = canonicalValue.canonicalCode,
                attributeCode = candidate.attributeCode,
                scope = candidate.scope,
                sourceSnapshotId = candidate.sourceSnapshotId,
                confidence = assessment.sourceReliability.score.coerceAtMost(1.0),
                metadata = mapOf("promotedAt" to Instant.ofEpochMilli(now).toString()),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun CatalogAliasCanon.toBrandAliasEntry(): AliasEntry =
        AliasEntry(
            locale = locale,
            term = aliasText,
            normalizedTerm = normalizedAlias,
            kind = AliasKind.BRAND,
            targetCode = targetCode,
            weight = (confidence * 100).toInt().coerceIn(50, 100),
            matchKind = AliasMatchKind.EXACT,
            isBlocked = false,
            source = AliasSource.LEARNED,
            notes = "governance.brand",
        )

    private fun CatalogAliasCanon.toAttributeHintAliasEntry(): AliasEntry =
        AliasEntry(
            locale = locale,
            term = aliasText,
            normalizedTerm = normalizedAlias,
            kind = AliasKind.ATTRIBUTE_HINT,
            targetCode = attributeCode ?: targetCode,
            weight = (confidence * 100).toInt().coerceIn(40, 95),
            matchKind = AliasMatchKind.TOKEN,
            isBlocked = false,
            source = AliasSource.LEARNED,
            notes = "governance.attribute_value:$targetCode",
        )

    private fun CatalogAttributeValueCanon.displayValue(): String =
        labels.resolve(locale = canonicalLocale ?: "en", fallback = canonicalValue) ?: canonicalValue

    private fun com.example.shoppingassistant.domain.catalog.CatalogBrandCanon.displayLabel(): String =
        labels.resolve(locale = "en", fallback = code) ?: code

    private fun CatalogProductFamilyCanon.displayLabel(): String =
        labels.resolve(locale = "en", fallback = prettyModelPrefix) ?: prettyModelPrefix

    private fun generatedCanonicalCode(candidate: CatalogValueCandidate): String =
        candidate.normalizedValue
            .uppercase(Locale.ROOT)
            .replace("[^A-Z0-9]+".toRegex(), "_")
            .trim('_')
            .ifEmpty { "VALUE_${candidate.attributeCode.uppercase(Locale.ROOT)}" }

    private suspend fun simulatePromotionRegressionIssues(
        candidate: CatalogValueCandidate,
        cluster: com.example.shoppingassistant.domain.catalog.CatalogGovernanceCandidateCluster?,
    ): List<String> {
        val provisionalCanonical = if (cluster?.existingMatch?.canonicalCode == null) {
            CatalogAttributeValueCanon(
                attributeCode = candidate.attributeCode,
                canonicalCode = candidate.proposedCanonicalCode ?: generatedCanonicalCode(candidate),
                canonicalValue = candidate.proposedCanonicalValue ?: candidate.rawValue,
                labels = candidate.proposedLabels.takeUnless { it.isBlank() } ?: localizedTextOf((candidate.locale ?: "und") to candidate.rawValue),
                canonicalLocale = candidate.proposedCanonicalLocale ?: candidate.locale,
                normalizedValue = candidate.normalizedValue,
                scope = candidate.scope,
                metadata = candidate.metadata,
                createdAt = candidate.createdAt,
                updatedAt = candidate.updatedAt,
            )
        } else {
            null
        }
        val provisionalAlias = if (
            cluster?.recommendedDisposition == CatalogGovernanceCandidateDisposition.ALIAS_CANDIDATE ||
            cluster?.existingMatch?.canonicalCode != null
        ) {
            CatalogAliasCanon(
                locale = candidate.locale ?: "und",
                marketCode = candidate.marketCode,
                aliasText = candidate.rawValue,
                normalizedAlias = candidate.normalizedValue,
                targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
                targetCode = cluster?.existingMatch?.canonicalCode ?: candidate.proposedCanonicalCode.orEmpty(),
                attributeCode = candidate.attributeCode,
                scope = candidate.scope,
                sourceSnapshotId = candidate.sourceSnapshotId,
                confidence = candidate.autoConfidence.coerceIn(0.0, 1.0),
                metadata = candidate.metadata,
                createdAt = candidate.createdAt,
                updatedAt = candidate.updatedAt,
            ).takeIf { it.targetCode.isNotBlank() }
        } else {
            null
        }
        val simulatedArtifacts = buildServingArtifacts(
            extraCanonicalValues = listOfNotNull(provisionalCanonical),
            extraAliases = listOfNotNull(provisionalAlias),
        )
        return validateServingArtifacts(simulatedArtifacts)
    }

    private fun validateServingArtifacts(
        artifacts: CatalogGovernanceServingArtifacts,
    ): List<String> {
        val issues = mutableListOf<String>()
        val aliasCollisions = mutableMapOf<String, MutableSet<String>>()
        artifacts.aliasEntries.forEach { alias ->
            val key = listOf(alias.locale, alias.kind.name, alias.normalizedTerm).joinToString("|")
            aliasCollisions.getOrPut(key) { linkedSetOf() }.add(alias.targetCode)
        }
        aliasCollisions
            .filterValues { it.size > 1 }
            .forEach { (key, targets) ->
                issues += "alias_collision:$key->${targets.sorted().joinToString("/")}"
            }

        artifacts.valueDictionaries.forEach { dictionary ->
            val synonymTargets = mutableMapOf<String, MutableSet<String>>()
            dictionary.entries.forEach { entry ->
                buildList {
                    add(entry.canonicalValue)
                    addAll(entry.synonyms)
                }.map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }
                    .forEach { normalized ->
                        synonymTargets.getOrPut(normalized) { linkedSetOf() }.add(entry.canonicalCode)
                    }
            }
            synonymTargets
                .filterValues { it.size > 1 }
                .forEach { (synonym, targets) ->
                    issues += "value_dict_collision:${dictionary.attributeCode}:$synonym->${targets.sorted().joinToString("/")}"
                }
        }
        return issues
    }

    private fun sanitizeValueDictionaryEntries(
        entries: List<AttributeValueDictEntry>,
        prioritiesByCanonicalCode: Map<String, Int>,
    ): List<AttributeValueDictEntry> {
        data class TermOwner(
            val canonicalCode: String,
            val isCanonicalValue: Boolean,
            val priority: Int,
        )

        fun normalizedTerm(value: String): String = value.trim().lowercase(Locale.ROOT)

        val ownersByNormalizedTerm = LinkedHashMap<String, MutableList<TermOwner>>()
        entries.forEach { entry ->
            val priority = prioritiesByCanonicalCode[entry.canonicalCode] ?: Int.MAX_VALUE
            normalizedTerm(entry.canonicalValue)
                .takeIf { it.isNotEmpty() }
                ?.let { normalized ->
                    ownersByNormalizedTerm.getOrPut(normalized) { mutableListOf() }
                        .add(TermOwner(entry.canonicalCode, isCanonicalValue = true, priority = priority))
                }
            entry.synonyms.forEach { synonym ->
                normalizedTerm(synonym)
                    .takeIf { it.isNotEmpty() }
                    ?.let { normalized ->
                        ownersByNormalizedTerm.getOrPut(normalized) { mutableListOf() }
                            .add(TermOwner(entry.canonicalCode, isCanonicalValue = false, priority = priority))
                    }
            }
        }

        val winningOwnerByTerm = ownersByNormalizedTerm.mapValues { (_, owners) ->
            owners.sortedWith(
                compareBy<TermOwner> { it.priority }
                    .thenByDescending { it.isCanonicalValue }
                    .thenBy { it.canonicalCode },
            ).first()
        }

        return entries.mapNotNull { entry ->
            val canonicalOwner = winningOwnerByTerm[normalizedTerm(entry.canonicalValue)]
            if (canonicalOwner != null && canonicalOwner.canonicalCode != entry.canonicalCode) {
                null
            } else {
                entry.copy(
                    synonyms = entry.synonyms
                        .filter { synonym ->
                            winningOwnerByTerm[normalizedTerm(synonym)]?.canonicalCode == entry.canonicalCode
                        }
                        .distinct()
                        .sorted(),
                )
            }
        }
    }

    private fun CatalogGovernanceScope.servingProjectionPriority(): Int =
        when {
            !modelCode.isNullOrBlank() -> 4
            !familyCode.isNullOrBlank() -> 3
            !brandCode.isNullOrBlank() -> 2
            !categoryCode.isNullOrBlank() -> 1
            else -> 0
        }

    private data class ValueDictionaryProjection(
        val dictionaries: List<AttributeValueDict>,
        val projectedAttributeCodes: Set<String>,
    )

    private data class ProductFamilyProjection(
        val document: CatalogCanonicalProductFamilyRegistryDocument,
        val projectedFamilyCodes: Set<String>,
    )

    private data class ProjectedValueEntry(
        val entry: AttributeValueDictEntry,
        val priority: Int,
    )
}

internal fun isProjectableAttributeHintAlias(alias: CatalogAliasCanon): Boolean {
    val normalizedAlias = alias.normalizedAlias.trim()
    if (normalizedAlias.isEmpty()) return false
    val tokens = normalizedAlias
        .split(Regex("""\s+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return false
    return tokens.any { token -> token.any(Char::isLetter) }
}
