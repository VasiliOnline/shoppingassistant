package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.asBooleanOrNull
import com.example.shoppingassistant.domain.model.asDoubleOrNull
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBinderStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundCandidate
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBoundQuery
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateAttribute
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateProjection
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChip
import com.example.shoppingassistant.domain.visualsearch.VisualSearchChipKind
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEnvelopeStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchErrorCode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchErrorEnvelope
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEventBatchResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizationDraft
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryAction
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryActionType
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlan
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRecoveryPlanResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRouteKind
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRetrievalStrategy
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.server.catalog.CatalogAiCategoryHintResolver
import com.example.shoppingassistant.server.config.VisualSearchConfig
import java.util.Locale
import org.slf4j.LoggerFactory

class VisualSearchServiceImpl(
    private val catalogRepository: CatalogReadRepository,
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val contextStore: VisualSearchContextStore,
    private val config: VisualSearchConfig,
    private val aiDraftNormalizer: VisualSearchAiDraftNormalizer? = null,
) : VisualSearchService {

    private val logger = LoggerFactory.getLogger(VisualSearchServiceImpl::class.java)
    private val categoryHintResolver = CatalogAiCategoryHintResolver(catalogTaxonomyRepository)

    override suspend fun reuseContext(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchContextReuseRequest,
    ): VisualSearchContextReuseResponse {
        if (!config.enabled) {
            return VisualSearchContextReuseResponse(
                status = VisualSearchContextReuseStatus.ERROR,
                error = disabledError(),
            )
        }
        val stored = contextStore.getReusableContext(request.assetFingerprint)
            ?: return VisualSearchContextReuseResponse(status = VisualSearchContextReuseStatus.MISS)
        if (isUnsafeStoredVisualSearchContext(stored.boundQuery)) {
            return VisualSearchContextReuseResponse(status = VisualSearchContextReuseStatus.MISS)
        }
        return VisualSearchContextReuseResponse(
            status = VisualSearchContextReuseStatus.HIT,
            reusedQuery = stored.boundQuery,
            cacheAgeSeconds = stored.ageSeconds(System.currentTimeMillis()),
        )
    }

    override suspend fun normalizeDraft(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
    ): VisualSearchNormalizeDraftResponse {
        if (!config.enabled) {
            return VisualSearchNormalizeDraftResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                error = disabledError(),
            )
        }
        val requestedCategoryCode = request.manualCategoryCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.preflightSignals.exactCategoryCode
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        aiDraftNormalizer?.normalize(
            metadata = metadata,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
        )?.let { draft ->
            logger.info(
                "visualsearch.normalize.ai_draft session={} category={} provider={} confidence={}",
                metadata.visualSessionId,
                draft.projection.categoryCode ?: requestedCategoryCode ?: "none",
                draft.providerName ?: "unknown",
                draft.confidence ?: draft.projection.categoryConfidence,
            )
            return VisualSearchNormalizeDraftResponse(
                status = VisualSearchEnvelopeStatus.OK,
                draft = draft,
            )
        }
        return buildCheapDraftResponse(
            metadata = metadata,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
        )
    }

    private suspend fun buildCheapDraftResponse(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
    ): VisualSearchNormalizeDraftResponse {
        val resolvedCategoryCode = requestedCategoryCode?.let { resolveCategoryCode(it) }
        val inferredTitle = request.preflightSignals.objectLabel
            ?.trim()
            ?.takeIf(::isSafeVisualSearchObjectTitle)
            ?: request.preflightSignals.ocrTextHints
                .firstOrNull(::isSafeVisualSearchObjectTitle)
                ?.take(64)
            ?: request.preflightSignals.imageLabelHints
                .firstOrNull(::isSafeVisualSearchObjectTitle)
                ?.take(64)
            ?: request.preflightSignals.barcodeValue
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
        val categoryTitle = resolvedCategoryCode
            ?.let { resolved ->
                catalogRepository.getCategoryEffectiveSpec(resolved)?.category?.displayTitle()
            }
            ?: requestedCategoryCode
            ?: inferredTitle
        val captureMode = request.preflightSignals.captureMode
        val reasonCodes = buildList {
            addAll(request.preflightSignals.reasonCodes)
            if (request.selectedRegion != null) add("FOCUS_REGION_PROVIDED")
            if (requestedCategoryCode != null) add("CATEGORY_HINT_PRESENT")
            if (request.preflightSignals.cheapProjectionReady) add("CHEAP_PROJECTION_READY")
            captureMode?.let { mode -> add("CAPTURE_MODE_${mode.name}") }
            if (request.preflightSignals.ocrTextHints.isNotEmpty()) add("OCR_HINT_PRESENT")
            if (request.preflightSignals.imageLabelHints.isNotEmpty()) add("IMAGE_LABEL_HINT_PRESENT")
            if (!request.preflightSignals.objectLabel.isNullOrBlank()) add("OBJECT_LABEL_PRESENT")
            if (!request.preflightSignals.barcodeValue.isNullOrBlank()) add("BARCODE_HINT_PRESENT")
            if (request.preflightSignals.categoryCandidates.isNotEmpty()) add("CATEGORY_SHORTLIST_PRESENT")
            if (config.providerReady) {
                add("SERVER_AI_PROVIDER_PENDING")
            } else {
                add("SERVER_AI_DISABLED_CHEAP_PATH")
                if (!config.serverAiEnabled) add("SERVER_AI_FLAG_DISABLED")
                addAll(config.providerMissingReasonCodes)
            }
            if (!request.preflightSignals.admitServerAi) {
                add("SERVER_AI_NOT_ADMISSIBLE")
            }
        }.distinct()
        val categoryConfidence = when {
            !request.manualCategoryCode.isNullOrBlank() -> 1f
            !request.preflightSignals.exactCategoryCode.isNullOrBlank() -> 0.92f
            else -> null
        }
        logger.info(
            "visualsearch.normalize.cheap_draft session={} category={} selectionMode={} aiEnabled={} serverAiEnabled={} hasApiKey={} hasProjectId={} model={} admitServerAi={} reasons={}",
            metadata.visualSessionId,
            resolvedCategoryCode ?: requestedCategoryCode ?: "none",
            request.selectionMode,
            config.providerReady,
            config.serverAiEnabled,
            !config.activeApiKey.isNullOrBlank(),
            !config.activeProjectId.isNullOrBlank(),
            config.activeModel,
            request.preflightSignals.admitServerAi,
            reasonCodes.joinToString(","),
        )
        return VisualSearchNormalizeDraftResponse(
            status = VisualSearchEnvelopeStatus.OK,
            draft = VisualSearchNormalizationDraft(
                projection = VisualSearchCandidateProjection(
                    categoryCode = resolvedCategoryCode ?: requestedCategoryCode,
                    categoryConfidence = categoryConfidence,
                    title = categoryTitle,
                    reasonCodes = reasonCodes,
                ),
                confidence = categoryConfidence,
                reasonCodes = reasonCodes,
                providerName = "server_cheap_projection",
                providerSchemaVersion = "visual-search/cheap-draft/1",
            ),
        )
    }

    override suspend fun bindQuery(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchBindQueryRequest,
    ): VisualSearchBindQueryResponse {
        if (!config.enabled) {
            return VisualSearchBindQueryResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                error = disabledError(),
            )
        }

        request.reusedQuery
            ?.takeIf { it.binderStatus != VisualSearchBinderStatus.REJECTED }
            ?.let { reused ->
                return VisualSearchBindQueryResponse(
                    status = VisualSearchEnvelopeStatus.OK,
                    boundQuery = reused.copy(
                        searchCriteria = reused.searchCriteria.copy(
                            querySessionId = request.querySessionId ?: reused.searchCriteria.querySessionId,
                            location = request.location ?: reused.searchCriteria.location,
                            radiusKm = request.radiusKm ?: reused.searchCriteria.radiusKm,
                            conditions = request.conditions.ifEmpty { reused.searchCriteria.conditions },
                            sort = request.sort,
                        ),
                    ),
                )
            }

        val rankedProjections = buildRankedProjectionCandidates(request)
        val bindAttempts = rankedProjections.map { candidate ->
            bindProjectionCandidate(
                metadata = metadata,
                request = request,
                rankedProjection = candidate,
            )
        }
        val primaryResult = bindAttempts.firstOrNull { it.boundQuery != null }
            ?: return bindAttempts.firstOrNull()?.rejectedResponse
            ?: rejectedResponse(
                reasonCodes = listOf("CATEGORY_UNRESOLVED", "MANUAL_CATEGORY_REQUIRED"),
                recommendedAction = VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY,
            )
        val boundQuery = requireNotNull(primaryResult.boundQuery)
        val rankedCandidates = bindAttempts
            .filter { it.boundQuery != null }
            .take(maxRankedQueryCandidates)
            .map { result ->
                VisualSearchBoundCandidate(
                    rank = result.rank,
                    confidence = result.confidence,
                    query = requireNotNull(result.boundQuery),
                    reasonCodes = result.reasonCodes,
                    isPrimary = result.rank == primaryResult.rank,
                )
            }
        request.fingerprint?.trim()?.takeIf { it.isNotEmpty() }?.let { fingerprint ->
            contextStore.saveReusableContext(fingerprint, boundQuery)
        }
        logger.info(
            "visualsearch.bind.success session={} category={} status={} candidates={}",
            metadata.visualSessionId,
            boundQuery.categoryCode,
            boundQuery.binderStatus,
            rankedCandidates.size,
        )
        return VisualSearchBindQueryResponse(
            status = VisualSearchEnvelopeStatus.OK,
            boundQuery = boundQuery,
            rankedCandidates = rankedCandidates,
        )
    }

    override suspend fun recoveryPlan(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchRecoveryPlanRequest,
    ): VisualSearchRecoveryPlanResponse {
        if (!config.enabled) {
            return VisualSearchRecoveryPlanResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                error = disabledError(),
            )
        }
        val normalizedReasonCodes = request.reasonCodes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val actions = buildList {
            if (request.binderStatus == VisualSearchBinderStatus.REJECTED) {
                add(VisualSearchRecoveryAction(VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY, "visual_search.recovery.choose_category"))
                add(VisualSearchRecoveryAction(VisualSearchRecoveryActionType.RETAKE_PHOTO, "visual_search.recovery.retake_photo"))
            }
            if (request.binderStatus != VisualSearchBinderStatus.ACCEPTED) {
                add(VisualSearchRecoveryAction(VisualSearchRecoveryActionType.ADD_TEXT, "visual_search.recovery.add_text"))
            }
            if (normalizedReasonCodes.any {
                    it.equals("OFFLINE_DURING_AI", ignoreCase = true) ||
                        it.equals("SERVER_AI_DISABLED_CHEAP_PATH", ignoreCase = true) ||
                        it.startsWith("YANDEX_", ignoreCase = true) ||
                        it.equals("SERVER_AI_FLAG_DISABLED", ignoreCase = true) ||
                        it.equals("SERVER_AI_NOT_ADMISSIBLE", ignoreCase = true)
                }) {
                add(VisualSearchRecoveryAction(VisualSearchRecoveryActionType.CONTINUE_WITHOUT_AI, "visual_search.recovery.continue_without_ai"))
            }
            if (normalizedReasonCodes.isNotEmpty()) {
                add(VisualSearchRecoveryAction(VisualSearchRecoveryActionType.RETRY, "visual_search.recovery.retry"))
            }
        }.distinctBy { it.type }
        val messageKey = when {
            request.binderStatus == VisualSearchBinderStatus.REJECTED -> "visual_search.recovery.hard_stop"
            normalizedReasonCodes.any { it.equals("RESULTS_WEAK", ignoreCase = true) } -> "visual_search.recovery.weak_results"
            else -> "visual_search.recovery.refine"
        }
        return VisualSearchRecoveryPlanResponse(
            status = VisualSearchEnvelopeStatus.OK,
            recoveryPlan = VisualSearchRecoveryPlan(
                messageKey = messageKey,
                reasonCodes = normalizedReasonCodes,
                actions = actions,
            ),
        )
    }

    override suspend fun ingestEvents(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchEventBatchRequest,
    ): VisualSearchEventBatchResponse {
        if (!config.enabled) {
            return VisualSearchEventBatchResponse(
                status = VisualSearchEnvelopeStatus.FAILED,
                acceptedCount = 0,
                error = disabledError(),
            )
        }
        val acceptedCount = request.events.count { event ->
            event.name.trim().isNotEmpty() && event.happenedAtMs > 0L
        }
        logger.info(
            "visualsearch.events.accepted session={} accepted={} total={}",
            metadata.visualSessionId,
            acceptedCount,
            request.events.size,
        )
        return VisualSearchEventBatchResponse(
            status = VisualSearchEnvelopeStatus.OK,
            acceptedCount = acceptedCount,
            rejectedCount = (request.events.size - acceptedCount).coerceAtLeast(0),
        )
    }

    private suspend fun resolveCategoryCode(requestedCategoryCode: String): String? {
        val normalizedCategoryCode = requestedCategoryCode.trim()
        if (normalizedCategoryCode.isEmpty()) return null
        val resolution = catalogTaxonomyRepository.resolveCategoryCode(normalizedCategoryCode)
        val resolvedCode = resolution?.resolvedCode?.trim()?.takeIf { it.isNotEmpty() }
        if (resolvedCode != null) return resolvedCode
        return catalogTaxonomyRepository.listCategories()
            .firstOrNull { category -> category.code.equals(normalizedCategoryCode, ignoreCase = true) }
            ?.code
            ?: normalizedCategoryCode
    }

    private fun buildRankedProjectionCandidates(
        request: VisualSearchBindQueryRequest,
    ): List<RankedProjectionCandidate> {
        val hasRankedHypotheses = request.normalizationDraft?.hypotheses?.isNotEmpty() == true
        val primaryCandidate =
            RankedProjectionCandidate(
                rank = 1,
                confidence = request.normalizationDraft?.confidence
                    ?: request.normalizationDraft?.projection?.categoryConfidence
                    ?: request.cheapProjection?.categoryConfidence,
                projection = mergeProjection(request),
                reasonCodes = request.normalizationDraft?.reasonCodes.orEmpty(),
                needsMorePhotos = request.normalizationDraft?.needsMorePhotos ?: false,
                missingEvidence = request.normalizationDraft?.missingEvidence.orEmpty(),
            )
        val hypothesisCandidates = request.normalizationDraft?.hypotheses
            .orEmpty()
            .sortedWith(compareBy<com.example.shoppingassistant.domain.visualsearch.VisualSearchHypothesis> { it.rank ?: Int.MAX_VALUE }
                .thenByDescending { it.confidence ?: 0f })
            .mapIndexed { index, hypothesis ->
                RankedProjectionCandidate(
                    rank = (hypothesis.rank ?: index + 1).coerceAtLeast(1),
                    confidence = hypothesis.confidence,
                    projection = mergeProjection(request, preferredProjection = hypothesis.projection),
                    reasonCodes = hypothesis.reasonCodes,
                    needsMorePhotos = false,
                    missingEvidence = hypothesis.missingEvidence,
                )
            }
        val acceptedHypothesisCandidates = hypothesisCandidates
            .filter { candidate ->
                candidate.confidence == null || candidate.confidence >= alternateCandidateMinConfidence
            }

        return buildList {
            if (!hasRankedHypotheses || acceptedHypothesisCandidates.isEmpty()) {
                add(primaryCandidate)
            }
            acceptedHypothesisCandidates.forEach { add(it) }
        }
            .distinctBy { candidate -> candidate.signature() }
            .sortedWith(compareBy<RankedProjectionCandidate> { it.rank }.thenByDescending { it.confidence ?: 0f })
            .take(maxRankedQueryCandidates)
    }

    private suspend fun bindProjectionCandidate(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchBindQueryRequest,
        rankedProjection: RankedProjectionCandidate,
    ): CandidateBindAttempt {
        val projection = rankedProjection.projection
        val manualCategoryCode = request.manualCategoryCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val aiCategoryCode = projection.categoryCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val preflightCategoryCode = request.preflightSignals.exactCategoryCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val categoryCode = manualCategoryCode ?: aiCategoryCode ?: preflightCategoryCode
        val categoryWasInferred = categoryCode == null
        val initiallyResolvedCategoryCode = if (categoryCode != null) {
            resolveCategoryCode(categoryCode)
        } else {
            inferCategoryCode(projection = projection, locale = request.locale)
        }
        val itemTypeResolvedCategoryCode = if (manualCategoryCode == null) {
            inferCategoryCodeFromItemType(projection = projection, locale = request.locale)
        } else {
            null
        }
        val resolvedCategoryCode = chooseCategoryByItemTypeConsistency(
            resolvedCategoryCode = initiallyResolvedCategoryCode,
            itemTypeResolvedCategoryCode = itemTypeResolvedCategoryCode,
            projection = projection,
            lockedByManualCategory = manualCategoryCode != null,
        )
        val categoryAdjustedByItemType = initiallyResolvedCategoryCode != null &&
            resolvedCategoryCode != null &&
            !initiallyResolvedCategoryCode.equals(resolvedCategoryCode, ignoreCase = true)
        if (resolvedCategoryCode == null) {
            return CandidateBindAttempt(
                rank = rankedProjection.rank,
                confidence = rankedProjection.confidence,
                reasonCodes = rankedProjection.reasonCodes,
                rejectedResponse = rejectedResponse(
                    reasonCodes = listOf("CATEGORY_UNRESOLVED", "MANUAL_CATEGORY_REQUIRED"),
                    recommendedAction = VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY,
                ),
            )
        }

        val chosenItemType = sanitizeItemTypeValue(projection.itemType)
        val chosenBrand = sanitizeIdentityValue(projection.brand)
        val chosenFamily = sanitizeIdentityValue(projection.family)
        var chosenModel = sanitizeModelForSearch(projection.model)
        val modelCandidates = buildBoundModelCandidates(
            projection = projection,
            appliedModel = chosenModel,
        )

        val effectiveSpec = catalogRepository.getCategoryEffectiveSpec(
            categoryCode = resolvedCategoryCode,
            brand = chosenBrand?.text,
            model = chosenModel?.text,
        ) ?: buildTaxonomyOnlyEffectiveSpec(resolvedCategoryCode)
            ?: return CandidateBindAttempt(
                rank = rankedProjection.rank,
                confidence = rankedProjection.confidence,
                reasonCodes = rankedProjection.reasonCodes,
                rejectedResponse = rejectedResponse(
                    reasonCodes = listOf("CATEGORY_UNRESOLVED"),
                    recommendedAction = VisualSearchRecoveryActionType.CHOOSE_CATEGORY_MANUALLY,
                ),
            )

        val acceptedAttributes = collectAcceptedAttributes(effectiveSpec, projection, request.locale)
            .withItemTypeAttribute(
                spec = effectiveSpec,
                itemType = chosenItemType,
                locale = request.locale,
            )
        val droppedAttributeCodes = projection.attributes
            .map { it.code.trim() }
            .filter { rawCode ->
                acceptedAttributes.keys.none { acceptedCode -> acceptedCode.equals(rawCode, ignoreCase = true) }
            }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }

        val isApplCoarse = effectiveSpec.category.segment.name == "APPL" &&
            effectiveSpec.category.code.count { it == '.' } <= 1
        if (isApplCoarse && confidenceOf(projection.model) < strongSignalThreshold) {
            chosenModel = null
        }

        val unresolvedRequiredCodes = resolveUnresolvedRequiredAttributes(
            spec = effectiveSpec,
            brand = chosenBrand?.text,
            model = chosenModel?.text,
            acceptedAttributes = acceptedAttributes,
        )

        val normalizedQuery = buildNormalizedQuery(
            brand = chosenBrand?.text,
            model = chosenModel?.text,
            attributes = acceptedAttributes,
        )
        val searchCriteria = OfferSearchCriteria(
            brand = chosenBrand?.text,
            model = chosenModel?.text,
            categoryCode = resolvedCategoryCode,
            location = request.location,
            radiusKm = request.radiusKm,
            conditions = request.conditions,
            attributes = acceptedAttributes,
            sort = request.sort,
            querySessionId = request.querySessionId,
        )

        val strongBrand = confidenceOf(chosenBrand) >= strongSignalThreshold
        val strongModel = confidenceOf(chosenModel) >= strongSignalThreshold
        val strongFamily = confidenceOf(chosenFamily) >= mediumSignalThreshold
        val hasItemType = chosenItemType != null
        val hasModelCandidates = modelCandidates.isNotEmpty()
        val hasIdentity = !chosenBrand?.text.isNullOrBlank() ||
            !chosenModel?.text.isNullOrBlank() ||
            !chosenFamily?.text.isNullOrBlank() ||
            hasModelCandidates
        val hasUsefulAttributes = acceptedAttributes.isNotEmpty()
        val hasResolvedCategoryHint = !projection.categoryCode.isNullOrBlank() || !resolvedCategoryCode.isNullOrBlank()
        val branchConfidence = when {
            categoryAdjustedByItemType -> maxOf(projection.categoryConfidence ?: 0f, inferredCategorySignalConfidence)
            else -> projection.categoryConfidence ?: when {
                !request.manualCategoryCode.isNullOrBlank() -> 1f
                !request.preflightSignals.exactCategoryCode.isNullOrBlank() -> 0.92f
                hasResolvedCategoryHint -> inferredCategorySignalConfidence
                categoryWasInferred -> inferredCategorySignalConfidence
                else -> 0f
            }
        }
        val strongBranch = branchConfidence >= branchSignalThreshold
        val hasBranchEvidence = !request.manualCategoryCode.isNullOrBlank() ||
            !request.preflightSignals.exactCategoryCode.isNullOrBlank() ||
            hasResolvedCategoryHint ||
            request.preflightSignals.ocrTextHints.isNotEmpty() ||
            request.preflightSignals.barcodeValue?.isNotBlank() == true ||
            request.preflightSignals.categoryCandidates.isNotEmpty() ||
            request.preflightSignals.objectConfidence?.let { it >= strongObjectSignalThreshold } == true ||
            hasItemType ||
            strongFamily
        val exactRoute = request.preflightSignals.exactRouteReady ||
            request.preflightSignals.barcodeValue?.isNotBlank() == true ||
            request.preflightSignals.reasonCodes.any { it.equals("BARCODE_EXACT_STRONG", ignoreCase = true) }

        val decision = when {
            exactRoute ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED,
                    retrievalStrategy = VisualSearchRetrievalStrategy.EXACT,
                    recommendedAction = null,
                    routeKind = VisualSearchRouteKind.EXACT,
                    qualityApproved = true,
                )
            request.intent == VisualSearchIntent.EXACT_SAME && strongBrand && strongModel && unresolvedRequiredCodes.isEmpty() ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED,
                    retrievalStrategy = VisualSearchRetrievalStrategy.EXACT,
                    recommendedAction = null,
                    routeKind = VisualSearchRouteKind.EXACT,
                    qualityApproved = true,
                )
            request.intent == VisualSearchIntent.EXACT_SAME && isApplCoarse && strongBrand && chosenModel == null ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED_PARTIAL,
                    retrievalStrategy = VisualSearchRetrievalStrategy.SIMILARITY,
                    recommendedAction = VisualSearchRecoveryActionType.REFINE_INTENT,
                    routeKind = VisualSearchRouteKind.FAMILY_ANCHOR,
                    qualityApproved = true,
                )
            request.intent == VisualSearchIntent.EXACT_SAME && !hasIdentity && !strongBranch ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.REJECTED,
                    retrievalStrategy = VisualSearchRetrievalStrategy.EXACT,
                    recommendedAction = if (rankedProjection.needsMorePhotos || rankedProjection.missingEvidence.isNotEmpty()) {
                        VisualSearchRecoveryActionType.RETAKE_PHOTO
                    } else {
                        VisualSearchRecoveryActionType.ADD_TEXT
                    },
                    routeKind = null,
                    qualityApproved = false,
                )
            request.intent == VisualSearchIntent.EXACT_SAME && strongBranch && (hasIdentity || hasUsefulAttributes || hasBranchEvidence) ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED_PARTIAL,
                    retrievalStrategy = VisualSearchRetrievalStrategy.SIMILARITY,
                    recommendedAction = if (hasIdentity || hasUsefulAttributes) null else VisualSearchRecoveryActionType.ADD_TEXT,
                    routeKind = if (hasIdentity) VisualSearchRouteKind.FAMILY_ANCHOR else VisualSearchRouteKind.BRANCH_ONLY,
                    qualityApproved = true,
                )
            strongBrand && strongModel && unresolvedRequiredCodes.isEmpty() ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED,
                    retrievalStrategy = VisualSearchRetrievalStrategy.SIMILARITY,
                    recommendedAction = null,
                    routeKind = VisualSearchRouteKind.FAMILY_ANCHOR,
                    qualityApproved = true,
                )
            (hasIdentity || hasModelCandidates) && strongBranch ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED_PARTIAL,
                    retrievalStrategy = VisualSearchRetrievalStrategy.SIMILARITY,
                    recommendedAction = when {
                        chosenModel == null && modelCandidates.isEmpty() && chosenBrand != null -> VisualSearchRecoveryActionType.ADD_TEXT
                        else -> null
                    },
                    routeKind = VisualSearchRouteKind.FAMILY_ANCHOR,
                    qualityApproved = true,
                )
            hasUsefulAttributes && strongBranch ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED_PARTIAL,
                    retrievalStrategy = VisualSearchRetrievalStrategy.SIMILARITY,
                    recommendedAction = if (request.intent == VisualSearchIntent.EXACT_SAME) {
                        VisualSearchRecoveryActionType.REFINE_INTENT
                    } else {
                        null
                    },
                    routeKind = VisualSearchRouteKind.TYPED_ENTITY,
                    qualityApproved = true,
                )
            strongBranch && hasBranchEvidence ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.ACCEPTED_PARTIAL,
                    retrievalStrategy = VisualSearchRetrievalStrategy.CATEGORY_DISCOVERY,
                    recommendedAction = if (rankedProjection.needsMorePhotos || rankedProjection.missingEvidence.isNotEmpty()) {
                        VisualSearchRecoveryActionType.RETAKE_PHOTO
                    } else if (request.intent == VisualSearchIntent.IDENTIFY_FIRST) {
                        null
                    } else {
                        VisualSearchRecoveryActionType.REFINE_INTENT
                    },
                    routeKind = VisualSearchRouteKind.BRANCH_ONLY,
                    qualityApproved = true,
                )
            else ->
                BinderDecision(
                    binderStatus = VisualSearchBinderStatus.REJECTED,
                    retrievalStrategy = VisualSearchRetrievalStrategy.CATEGORY_DISCOVERY,
                    recommendedAction = VisualSearchRecoveryActionType.RETAKE_PHOTO,
                    routeKind = null,
                    qualityApproved = false,
                )
        }

        if (decision.binderStatus == VisualSearchBinderStatus.REJECTED) {
            return CandidateBindAttempt(
                rank = rankedProjection.rank,
                confidence = rankedProjection.confidence,
                reasonCodes = rankedProjection.reasonCodes,
                rejectedResponse = rejectedResponse(
                    reasonCodes = buildList {
                        add("BINDER_REJECTED_ALL")
                        if (!hasIdentity && !hasUsefulAttributes) add("ATTRIBUTE_UNRESOLVED")
                        if (rankedProjection.needsMorePhotos) add("NEEDS_MORE_PHOTOS")
                        addAll(rankedProjection.missingEvidence.map { "missing_evidence:$it" })
                        addAll(unresolvedRequiredCodes.map { "required:$it" })
                    }.distinct(),
                    recommendedAction = decision.recommendedAction,
                ),
            )
        }

        val finalStatus = if (decision.binderStatus == VisualSearchBinderStatus.ACCEPTED &&
            unresolvedRequiredCodes.isNotEmpty()
        ) {
            VisualSearchBinderStatus.ACCEPTED_PARTIAL
        } else {
            decision.binderStatus
        }
        val recoveryReasonCodes = buildList {
            if (finalStatus == VisualSearchBinderStatus.ACCEPTED_PARTIAL) add("ATTRIBUTE_UNRESOLVED")
            if (unresolvedRequiredCodes.isNotEmpty()) add("triggered_unresolved")
            if (rankedProjection.needsMorePhotos) add("NEEDS_MORE_PHOTOS")
            if (modelCandidates.isNotEmpty() && chosenModel == null) add("MODEL_CANDIDATES_ONLY")
            addAll(rankedProjection.missingEvidence.map { "missing_evidence:$it" })
        }.distinct()
        return CandidateBindAttempt(
            rank = rankedProjection.rank,
            confidence = rankedProjection.confidence,
            reasonCodes = rankedProjection.reasonCodes,
            boundQuery = VisualSearchBoundQuery(
                binderStatus = finalStatus,
                routeKind = decision.routeKind ?: VisualSearchRouteKind.BRANCH_ONLY,
                qualityApproved = decision.qualityApproved,
                retrievalStrategy = decision.retrievalStrategy,
                categoryCode = resolvedCategoryCode,
                itemType = chosenItemType,
                normalizedQuery = normalizedQuery,
                searchCriteria = searchCriteria,
                chips = buildChips(
                    spec = effectiveSpec,
                    itemType = chosenItemType?.text,
                    brand = chosenBrand?.text,
                    family = chosenFamily?.text,
                    model = chosenModel?.text,
                    attributes = acceptedAttributes,
                    barcodeValue = request.preflightSignals.barcodeValue,
                ),
                modelCandidates = modelCandidates,
                previewTitle = buildPreviewTitle(
                    brand = chosenBrand?.text,
                    itemType = chosenItemType?.text,
                    family = chosenFamily?.text,
                    model = chosenModel?.text,
                    categoryTitle = effectiveSpec.category.displayTitle(),
                ),
                droppedAttributeCodes = droppedAttributeCodes,
                unresolvedRequiredAttributeCodes = unresolvedRequiredCodes,
                recoveryReasonCodes = recoveryReasonCodes,
                recommendedAction = decision.recommendedAction,
                allowTextRefinement = finalStatus != VisualSearchBinderStatus.ACCEPTED || unresolvedRequiredCodes.isNotEmpty(),
                exactRoute = exactRoute,
                reusableFingerprint = request.fingerprint,
            ),
        )
    }

    private suspend fun inferCategoryCode(
        projection: VisualSearchCandidateProjection,
        locale: String?,
    ): String? {
        val hints = buildList {
            projection.itemType?.text?.trim()?.takeIf(::isSafeVisualSearchObjectTitle)?.let(::add)
            projection.title?.trim()?.takeIf(::isSafeVisualSearchObjectTitle)?.let(::add)
            projection.model?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            projection.family?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            projection.brand?.text?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            val brand = projection.brand?.text?.trim().orEmpty()
            val family = projection.family?.text?.trim().orEmpty()
            val model = projection.model?.text?.trim().orEmpty()
            if (brand.isNotEmpty() && model.isNotEmpty()) {
                add("$brand $model")
            }
            if (brand.isNotEmpty() && family.isNotEmpty()) {
                add("$brand $family")
            }
            val title = projection.title?.trim()?.takeIf(::isSafeVisualSearchObjectTitle).orEmpty()
            if (brand.isNotEmpty() && title.isNotEmpty()) {
                add("$brand $title")
            }
        }.distinct()

        return hints.firstNotNullOfOrNull { hint ->
            categoryHintResolver.resolveCategoryCode(
                rawHint = hint,
                locale = locale,
            )
        }
    }

    private suspend fun inferCategoryCodeFromItemType(
        projection: VisualSearchCandidateProjection,
        locale: String?,
    ): String? {
        val hints = buildList {
            projection.itemType?.text?.trim()?.takeIf(::isSafeVisualSearchObjectTitle)?.let(::add)
            projection.title?.trim()?.takeIf(::isSafeVisualSearchObjectTitle)?.let(::add)
        }.distinct()

        return hints.firstNotNullOfOrNull { hint ->
            categoryHintResolver.resolveCategoryCode(
                rawHint = hint,
                locale = locale,
            )
        }
    }

    private fun chooseCategoryByItemTypeConsistency(
        resolvedCategoryCode: String?,
        itemTypeResolvedCategoryCode: String?,
        projection: VisualSearchCandidateProjection,
        lockedByManualCategory: Boolean,
    ): String? {
        if (resolvedCategoryCode == null) return itemTypeResolvedCategoryCode
        if (lockedByManualCategory) return resolvedCategoryCode
        val itemTypeCategory = itemTypeResolvedCategoryCode ?: return resolvedCategoryCode
        if (resolvedCategoryCode.equals(itemTypeCategory, ignoreCase = true)) return resolvedCategoryCode
        if (!hasStrongVisualItemType(projection)) return resolvedCategoryCode

        val categoryConfidence = projection.categoryConfidence ?: 0f
        val itemTypeIsAtLeastAsSpecific = categorySpecificity(itemTypeCategory) >= categorySpecificity(resolvedCategoryCode)
        val crossesSegment = !categorySegment(resolvedCategoryCode).equals(categorySegment(itemTypeCategory), ignoreCase = true)
        return when {
            categoryConfidence < strongSignalThreshold && itemTypeIsAtLeastAsSpecific -> itemTypeCategory
            crossesSegment && confidenceOf(projection.itemType) >= exactVisualModelThreshold -> itemTypeCategory
            else -> resolvedCategoryCode
        }
    }

    private fun hasStrongVisualItemType(projection: VisualSearchCandidateProjection): Boolean {
        val itemType = projection.itemType?.text?.trim()?.takeIf(::isSafeVisualSearchObjectTitle) ?: return false
        if (isGenericVisualSearchProductType(itemType)) return false
        if (confidenceOf(projection.itemType) >= strongObjectSignalThreshold) return true
        val title = projection.title?.trim()?.takeIf(::isSafeVisualSearchObjectTitle) ?: return false
        val normalizedItemType = normalizeVisualSearchText(itemType)
        val normalizedTitle = normalizeVisualSearchText(title)
        return normalizedTitle.contains(normalizedItemType) || normalizedItemType.contains(normalizedTitle)
    }

    private fun categorySpecificity(categoryCode: String): Int =
        categoryCode.count { it == '.' }

    private fun categorySegment(categoryCode: String): String =
        categoryCode.substringBefore('.').trim().uppercase(Locale.ROOT)

    private fun Map<String, TypedAttributeValue>.withItemTypeAttribute(
        spec: CatalogCategoryEffectiveSpec,
        itemType: VisualSearchCandidateValue?,
        locale: String?,
    ): Map<String, TypedAttributeValue> {
        val rawItemType = itemType?.text?.trim()?.takeIf { it.isNotEmpty() } ?: return this
        if (confidenceOf(itemType) < mediumSignalThreshold) return this
        val specByCode = spec.allAttributes().associateBy { Normalization.attributeKey(it.code) }
        val attributeSpec = itemTypeAttributeCodes
            .firstNotNullOfOrNull { code -> specByCode[code] }
            ?: return this
        val normalizedCode = Normalization.attributeKey(attributeSpec.code)
        if (normalizedCode in keys) return this
        val value = sanitizeAttributeValue(
            spec = attributeSpec,
            value = TypedAttributeValue.Text(rawItemType),
            locale = locale,
        ) ?: return this
        return LinkedHashMap(this).apply {
            put(normalizedCode, value)
        }
    }

    private fun mergeProjection(
        request: VisualSearchBindQueryRequest,
        preferredProjection: VisualSearchCandidateProjection? = request.normalizationDraft?.projection,
    ): VisualSearchCandidateProjection {
        val cheap = request.cheapProjection
        val draft = preferredProjection
        val chosenAttributes = LinkedHashMap<String, VisualSearchCandidateAttribute>()
        cheap?.attributes.orEmpty().forEach { candidate ->
            val normalizedCode = Normalization.attributeKey(candidate.code)
            if (normalizedCode.isNotEmpty()) {
                chosenAttributes[normalizedCode] = candidate.copy(code = normalizedCode)
            }
        }
        draft?.attributes.orEmpty().forEach { candidate ->
            val normalizedCode = Normalization.attributeKey(candidate.code)
            if (normalizedCode.isEmpty()) return@forEach
            val existing = chosenAttributes[normalizedCode]
            if (existing == null || confidenceOf(candidate) >= confidenceOf(existing)) {
                chosenAttributes[normalizedCode] = candidate.copy(code = normalizedCode)
            }
        }
        return VisualSearchCandidateProjection(
            categoryCode = chooseTextValue(
                preferredText = request.manualCategoryCode,
                preferredConfidence = 1f,
                fallbackText = chooseTextValue(
                    preferredText = draft?.categoryCode,
                    preferredConfidence = draft?.categoryConfidence,
                    fallbackText = cheap?.categoryCode,
                    fallbackConfidence = cheap?.categoryConfidence,
                ),
                fallbackConfidence = maxOf(draft?.categoryConfidence ?: 0f, cheap?.categoryConfidence ?: 0f),
            ),
            categoryConfidence = maxOf(draft?.categoryConfidence ?: 0f, cheap?.categoryConfidence ?: 0f).takeIf { it > 0f },
            itemType = chooseCandidateValue(draft?.itemType, cheap?.itemType),
            brand = chooseCandidateValue(draft?.brand, cheap?.brand),
            brandCandidates = mergeIdentityCandidates(
                preferred = draft?.brandCandidates.orEmpty(),
                fallback = cheap?.brandCandidates.orEmpty(),
                appliedValue = chooseCandidateValue(draft?.brand, cheap?.brand)?.text,
            ),
            family = chooseCandidateValue(draft?.family, cheap?.family),
            familyCandidates = mergeIdentityCandidates(
                preferred = draft?.familyCandidates.orEmpty(),
                fallback = cheap?.familyCandidates.orEmpty(),
                appliedValue = chooseCandidateValue(draft?.family, cheap?.family)?.text,
            ),
            model = chooseCandidateValue(draft?.model, cheap?.model),
            modelCandidates = mergeModelCandidates(
                preferred = draft?.modelCandidates.orEmpty(),
                fallback = cheap?.modelCandidates.orEmpty(),
                appliedModel = chooseCandidateValue(draft?.model, cheap?.model)?.text,
            ),
            color = chooseCandidateValue(draft?.color, cheap?.color),
            condition = chooseCandidateValue(draft?.condition, cheap?.condition),
            attributes = chosenAttributes.values.toList(),
            title = sanitizeVisualSearchProjectionTitle(draft?.title) ?: sanitizeVisualSearchProjectionTitle(cheap?.title),
            reasonCodes = (cheap?.reasonCodes.orEmpty() + draft?.reasonCodes.orEmpty()).distinct(),
        )
    }

    private fun chooseCandidateValue(
        preferred: VisualSearchCandidateValue?,
        fallback: VisualSearchCandidateValue?,
    ): VisualSearchCandidateValue? {
        val preferredText = preferred?.text?.trim().orEmpty()
        val fallbackText = fallback?.text?.trim().orEmpty()
        if (preferredText.isEmpty()) return fallback?.takeIf { fallbackText.isNotEmpty() }
        if (fallbackText.isEmpty()) return preferred
        return if (confidenceOf(preferred) >= confidenceOf(fallback)) preferred else fallback
    }

    private fun mergeModelCandidates(
        preferred: List<VisualSearchCandidateValue>,
        fallback: List<VisualSearchCandidateValue>,
        appliedModel: String?,
    ): List<VisualSearchCandidateValue> = (preferred + fallback)
        .mapNotNull(::sanitizeModelCandidate)
        .filterNot { candidate -> candidate.text.equals(appliedModel, ignoreCase = true) }
        .distinctBy { candidate -> Normalization.key(candidate.text) }
        .sortedByDescending { candidate -> confidenceOf(candidate) }
        .take(2)

    private fun chooseTextValue(
        preferredText: String?,
        preferredConfidence: Float?,
        fallbackText: String?,
        fallbackConfidence: Float?,
    ): String? {
        val preferred = preferredText?.trim().orEmpty()
        val fallback = fallbackText?.trim().orEmpty()
        if (preferred.isEmpty()) return fallback.takeIf { it.isNotEmpty() }
        if (fallback.isEmpty()) return preferred
        return if ((preferredConfidence ?: 1f) >= (fallbackConfidence ?: 1f)) preferred else fallback
    }

    private fun sanitizeVisualSearchProjectionTitle(raw: String?): String? =
        raw
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf(::isSafeVisualSearchObjectTitle)

    private fun isSafeVisualSearchObjectTitle(raw: String): Boolean {
        val normalized = normalizeVisualSearchText(raw)
        if (normalized.isBlank()) return false
        if (normalized in weakVisualSearchSceneTitles) return false
        if (weakVisualSearchSceneTokens.any { token -> normalized.contains(token) }) return false
        return true
    }

    private fun isUnsafeStoredVisualSearchContext(query: VisualSearchBoundQuery): Boolean {
        var hasSafePreviewTitle = false
        query.previewTitle
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { title ->
                if (!isSafeVisualSearchObjectTitle(title)) return true
                hasSafePreviewTitle = true
            }
        val hasSafeIdentityChip = query.chips.any { chip ->
            chip.kind == VisualSearchChipKind.BRAND ||
                chip.kind == VisualSearchChipKind.MODEL ||
                chip.kind == VisualSearchChipKind.ITEM_TYPE ||
                (chip.kind == VisualSearchChipKind.CATEGORY && isSafeVisualSearchObjectTitle(chip.label))
        }
        if (
            query.categoryCode.equals("HOME.KITCHEN_DINING", ignoreCase = true) &&
            !hasSafeIdentityChip &&
            !hasSafePreviewTitle
        ) {
            return true
        }
        return false
    }

    private fun normalizeVisualSearchText(raw: String): String =
        raw
            .trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun sanitizeIdentityValue(candidate: VisualSearchCandidateValue?): VisualSearchCandidateValue? {
        val text = candidate?.text?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (isGenericIdentityAnchor(text)) return null
        if (confidenceOf(candidate) < mediumSignalThreshold) return null
        return candidate?.copy(text = text)
    }

    private fun sanitizeItemTypeValue(candidate: VisualSearchCandidateValue?): VisualSearchCandidateValue? {
        val text = candidate?.text?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!isSafeVisualSearchObjectTitle(text)) return null
        if (isGenericVisualSearchProductType(text)) return null
        if (confidenceOf(candidate) < 0.42f) return null
        return candidate?.copy(text = text)
    }

    private fun sanitizeModelForSearch(candidate: VisualSearchCandidateValue?): VisualSearchCandidateValue? {
        val sanitized = sanitizeIdentityValue(candidate) ?: return null
        return sanitized.takeIf { shouldApplyModelAsFilter(it) }
    }

    private fun sanitizeModelCandidate(candidate: VisualSearchCandidateValue?): VisualSearchCandidateValue? {
        val value = candidate ?: return null
        val text = value.text.trim()
        if (text.isEmpty()) return null
        if (isGenericIdentityAnchor(text)) return null
        if (confidenceOf(value) < modelCandidateThreshold) return null
        return value.copy(text = text)
    }

    private fun mergeIdentityCandidates(
        preferred: List<VisualSearchCandidateValue>,
        fallback: List<VisualSearchCandidateValue>,
        appliedValue: String?,
    ): List<VisualSearchCandidateValue> = (preferred + fallback)
        .mapNotNull(::sanitizeIdentityValue)
        .filterNot { candidate -> candidate.text.equals(appliedValue, ignoreCase = true) }
        .distinctBy { candidate -> Normalization.key(candidate.text) }
        .sortedByDescending { candidate -> confidenceOf(candidate) }
        .take(2)

    private fun buildBoundModelCandidates(
        projection: VisualSearchCandidateProjection,
        appliedModel: VisualSearchCandidateValue?,
    ): List<VisualSearchCandidateValue> = buildList {
        projection.modelCandidates.forEach { candidate -> add(candidate) }
        projection.model?.let { candidate -> add(candidate) }
    }
        .mapNotNull(::sanitizeModelCandidate)
        .filterNot { candidate -> candidate.text.equals(appliedModel?.text, ignoreCase = true) }
        .distinctBy { candidate -> Normalization.key(candidate.text) }
        .sortedByDescending { candidate -> confidenceOf(candidate) }
        .take(2)

    private fun shouldApplyModelAsFilter(candidate: VisualSearchCandidateValue): Boolean {
        val source = normalizeIdentitySource(candidate.source)
        val confidence = confidenceOf(candidate)
        if (source in exactModelSources) return confidence >= mediumSignalThreshold
        return source == "VISUAL_DISTINCTIVE" && confidence >= exactVisualModelThreshold
    }

    private fun collectAcceptedAttributes(
        spec: CatalogCategoryEffectiveSpec,
        projection: VisualSearchCandidateProjection,
        locale: String?,
    ): Map<String, TypedAttributeValue> {
        val specByCode = spec.allAttributes().associateBy { Normalization.attributeKey(it.code) }
        return projection.attributes
            .mapNotNull { candidate ->
                val normalizedCode = Normalization.attributeKey(candidate.code)
                if (normalizedCode.isEmpty()) return@mapNotNull null
                val attributeSpec = specByCode[normalizedCode] ?: return@mapNotNull null
                if (confidenceOf(candidate) < mediumSignalThreshold) return@mapNotNull null
                sanitizeAttributeValue(attributeSpec, candidate.value, locale)?.let { normalizedCode to it }
            }
            .toMap(LinkedHashMap())
    }

    private fun sanitizeAttributeValue(
        spec: CatalogAttributeSpec,
        value: TypedAttributeValue,
        locale: String?,
    ): TypedAttributeValue? {
        if (spec.options.isEmpty()) {
            val normalized = when (spec.dataType.name) {
                "BOOL" -> value.asBooleanOrNull()?.let(TypedAttributeValue::Bool)
                "INT", "DECIMAL" -> value.asDoubleOrNull()?.let(TypedAttributeValue::Number)
                else -> value.asRawString().trim().takeIf { it.isNotEmpty() }?.let(TypedAttributeValue::Text)
            } ?: return null
            val number = normalized.asDoubleOrNull()
            val minValue = spec.minValue
            val maxValue = spec.maxValue
            if (number != null && minValue != null && number < minValue) return null
            if (number != null && maxValue != null && number > maxValue) return null
            return normalized
        }

        val raw = value.asRawString().trim()
        if (raw.isEmpty()) return null
        val matchedOption = spec.options.firstOrNull { option ->
            option.valueCode.equals(raw, ignoreCase = true) ||
                option.labels.resolve(locale)?.equals(raw, ignoreCase = true) == true ||
                option.aliases.any { alias -> alias.equals(raw, ignoreCase = true) }
        } ?: return null
        return TypedAttributeValue.Text(matchedOption.labels.resolve(locale) ?: matchedOption.valueCode)
    }

    private fun resolveUnresolvedRequiredAttributes(
        spec: CatalogCategoryEffectiveSpec,
        brand: String?,
        model: String?,
        acceptedAttributes: Map<String, TypedAttributeValue>,
    ): List<String> {
        val present = acceptedAttributes.keys.toMutableSet()
        if (!brand.isNullOrBlank()) present += "brand"
        if (!model.isNullOrBlank()) present += "model"
        return spec.requiredIfRules
            .filter { rule -> rule.whenAll.all { conditionSatisfied(it, brand, model, acceptedAttributes) } }
            .map { Normalization.attributeKey(it.requiredAttributeCode) }
            .filter { it.isNotEmpty() }
            .filterNot { it in present }
            .distinct()
            .sorted()
    }

    private fun conditionSatisfied(
        condition: AttributeCondition,
        brand: String?,
        model: String?,
        acceptedAttributes: Map<String, TypedAttributeValue>,
    ): Boolean {
        val actual = when (Normalization.attributeKey(condition.attributeCode)) {
            "brand" -> brand
            "model" -> model
            else -> acceptedAttributes[Normalization.attributeKey(condition.attributeCode)]?.asRawString()
        }?.trim()?.lowercase(Locale.ROOT) ?: return false
        val expected = condition.values.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        return when (condition.op.name) {
            "STARTS_WITH_ANY" -> expected.any { actual.startsWith(it) }
            else -> expected.any { actual == it }
        }
    }

    private fun buildNormalizedQuery(
        brand: String?,
        model: String?,
        attributes: Map<String, TypedAttributeValue>,
    ): NormalizedQuery? {
        if (brand.isNullOrBlank() && model.isNullOrBlank() && attributes.isEmpty()) return null
        return NormalizedQuery(
            brand = brand.orEmpty(),
            model = model.orEmpty(),
            attributes = attributes,
        )
    }

    private fun buildChips(
        spec: CatalogCategoryEffectiveSpec,
        itemType: String?,
        brand: String?,
        family: String?,
        model: String?,
        attributes: Map<String, TypedAttributeValue>,
        barcodeValue: String?,
    ): List<VisualSearchChip> = buildList {
        add(VisualSearchChip(VisualSearchChipKind.CATEGORY, spec.category.code, spec.category.displayTitle(), true))
        barcodeValue?.trim()?.takeIf { it.isNotEmpty() }?.let { add(VisualSearchChip(VisualSearchChipKind.BARCODE, null, it, false)) }
        itemType?.trim()?.takeIf { it.isNotEmpty() }?.let { add(VisualSearchChip(VisualSearchChipKind.ITEM_TYPE, "item_type", it, false)) }
        brand?.trim()?.takeIf { it.isNotEmpty() }?.let { add(VisualSearchChip(VisualSearchChipKind.BRAND, null, it, true)) }
        family?.trim()?.takeIf { it.isNotEmpty() && shouldExposeFamily(it, model) }?.let {
            add(VisualSearchChip(VisualSearchChipKind.MODEL, null, it, true))
        }
        model?.trim()?.takeIf { it.isNotEmpty() }?.let { add(VisualSearchChip(VisualSearchChipKind.MODEL, null, it, true)) }
        attributes
            .filterKeys { code -> Normalization.attributeKey(code) !in itemTypeAttributeCodes }
            .forEach { (code, value) -> add(VisualSearchChip(VisualSearchChipKind.ATTRIBUTE, code, value.asRawString(), true)) }
    }

    private fun buildPreviewTitle(
        brand: String?,
        itemType: String?,
        family: String?,
        model: String?,
        categoryTitle: String,
    ): String {
        val identityTitle = listOfNotNull(
            brand?.trim()?.takeIf { it.isNotEmpty() },
            family?.trim()?.takeIf { it.isNotEmpty() && shouldExposeFamily(it, model) },
            model?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
        return identityTitle.ifBlank {
            itemType
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: categoryTitle
        }
    }

    private fun shouldExposeFamily(
        family: String,
        model: String?,
    ): Boolean {
        val normalizedFamily = family.trim().lowercase(Locale.ROOT)
        if (normalizedFamily.isEmpty()) return false
        val normalizedModel = model?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return normalizedModel.isEmpty() || !normalizedModel.contains(normalizedFamily)
    }

    private fun RankedProjectionCandidate.signature(): String = buildString {
        append(projection.categoryCode.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.itemType?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.brand?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.family?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.model?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.title.orEmpty().trim().lowercase(Locale.ROOT))
    }

    private fun rejectedResponse(
        reasonCodes: List<String>,
        recommendedAction: VisualSearchRecoveryActionType?,
    ): VisualSearchBindQueryResponse = VisualSearchBindQueryResponse(
        status = VisualSearchEnvelopeStatus.FAILED,
        error = VisualSearchErrorEnvelope(
            code = VisualSearchErrorCode.BINDER_REJECTED,
            messageKey = "visual_search.error.server",
            retryable = false,
            details = buildMap {
                if (reasonCodes.isNotEmpty()) put("reasonCodes", reasonCodes.joinToString(","))
                recommendedAction?.let { put("recommendedAction", it.name.lowercase(Locale.ROOT)) }
            },
        ),
    )

    private fun disabledError(): VisualSearchErrorEnvelope = VisualSearchErrorEnvelope(
        code = VisualSearchErrorCode.FEATURE_DISABLED,
        messageKey = "visual_search.error.server",
        retryable = false,
    )

    private fun normalizeIdentitySource(raw: String?): String? =
        raw
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?.takeIf { it.isNotEmpty() }

    private fun isGenericIdentityAnchor(raw: String): Boolean {
        val normalized = Normalization.key(raw).replace('-', ' ')
        return normalized.isBlank() || normalized in genericIdentityAnchors
    }

    private fun isGenericVisualSearchProductType(raw: String): Boolean {
        val normalized = Normalization.key(raw).replace('-', ' ')
        return normalized.isBlank() || normalized in genericVisualSearchProductTypes
    }

    private suspend fun buildTaxonomyOnlyEffectiveSpec(categoryCode: String): CatalogCategoryEffectiveSpec? {
        val normalizedCategoryCode = categoryCode.trim()
        if (normalizedCategoryCode.isEmpty()) return null
        val category = catalogTaxonomyRepository.listCategories()
            .firstOrNull { category -> category.code.equals(normalizedCategoryCode, ignoreCase = true) }
            ?: syntheticVisualSearchCategory(normalizedCategoryCode)
        return CatalogCategoryEffectiveSpec(
            category = category,
            readiness = CatalogCategoryReadiness.INTERNAL,
            attributes = emptyList(),
        )
    }

    private fun syntheticVisualSearchCategory(categoryCode: String): Category {
        val segment = CategorySegment.entries.firstOrNull { segment ->
            categoryCode.equals(segment.name, ignoreCase = true) ||
                categoryCode.startsWith("${segment.name}.", ignoreCase = true)
        } ?: CategorySegment.OTHER
        return Category(
            code = categoryCode,
            segment = segment,
        )
    }

    private fun confidenceOf(candidate: VisualSearchCandidateValue?): Float = when {
        candidate == null -> 0f
        candidate.confidence != null -> requireNotNull(candidate.confidence)
        else -> 1f
    }
    private fun confidenceOf(candidate: VisualSearchCandidateAttribute): Float = candidate.confidence ?: 1f

    private data class BinderDecision(
        val binderStatus: VisualSearchBinderStatus,
        val retrievalStrategy: VisualSearchRetrievalStrategy,
        val recommendedAction: VisualSearchRecoveryActionType?,
        val routeKind: VisualSearchRouteKind?,
        val qualityApproved: Boolean,
    )

    private data class RankedProjectionCandidate(
        val rank: Int,
        val confidence: Float?,
        val projection: VisualSearchCandidateProjection,
        val reasonCodes: List<String>,
        val needsMorePhotos: Boolean,
        val missingEvidence: List<String>,
    )

    private data class CandidateBindAttempt(
        val rank: Int,
        val confidence: Float?,
        val reasonCodes: List<String>,
        val boundQuery: VisualSearchBoundQuery? = null,
        val rejectedResponse: VisualSearchBindQueryResponse? = null,
    )

    private companion object {
        const val maxRankedQueryCandidates: Int = 3
        const val alternateCandidateMinConfidence: Float = 0.45f
        const val mediumSignalThreshold: Float = 0.55f
        const val strongSignalThreshold: Float = 0.78f
        const val branchSignalThreshold: Float = 0.68f
        const val inferredCategorySignalConfidence: Float = 0.74f
        const val strongObjectSignalThreshold: Float = 0.72f
        const val modelCandidateThreshold: Float = 0.42f
        const val exactVisualModelThreshold: Float = 0.92f
        val itemTypeAttributeCodes: Set<String> = setOf(
            "item_type",
            "product_type",
            "type_of_item",
            "item_kind",
            "product_kind",
        )
        val exactModelSources: Set<String> = setOf(
            "TEXT_EXACT",
            "BARCODE_EXACT",
            "USER_HINT",
            "CATALOG_SHORTLIST",
        )
        val genericIdentityAnchors: Set<String> = setOf(
            "accessory",
            "battery",
            "bracelet",
            "camera",
            "candy",
            "charger",
            "chocolate",
            "coffee",
            "console",
            "cutlery",
            "drink",
            "grocery",
            "headphones",
            "honey",
            "hoodie",
            "jacket",
            "jeans",
            "laptop",
            "mouse",
            "pants",
            "phone",
            "product",
            "shoe",
            "shoes",
            "shirt",
            "smartphone",
            "sneakers",
            "speaker",
            "suit",
            "sweater",
            "tea",
            "thermometer",
            "watch",
            "wrist watch",
        )
        val genericVisualSearchProductTypes: Set<String> = setOf(
            "item",
            "object",
            "photo",
            "product",
            "product photo",
            "sale item",
            "thing",
            "товар",
            "предмет",
            "изделие",
            "фото товара",
        )
        val weakVisualSearchSceneTitles: Set<String> = setOf(
            "tableware",
            "cutlery",
            "dishware",
            "kitchenware",
            "serveware",
            "flatware",
            "silverware",
            "utensil",
            "utensils",
            "посуда",
            "посуда и кухня",
            "кухня",
            "wall",
            "room",
            "home",
            "interior",
            "furniture",
            "screen",
            "display",
        )
        val weakVisualSearchSceneTokens: Set<String> = setOf(
            "tableware",
            "cutlery",
            "dishware",
            "kitchenware",
            "serveware",
            "flatware",
            "silverware",
            "utensil",
            "посуда",
            "кухн",
            "interior",
            "furniture",
        )
    }
}
