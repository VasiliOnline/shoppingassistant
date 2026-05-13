package com.example.shoppingassistant.server.shortlisting

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.model.Money
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.model.rawAttributes
import com.example.shoppingassistant.domain.shortlisting.ShortListingCategoryCandidate
import com.example.shoppingassistant.domain.shortlisting.ShortListingConfidenceBand
import com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingDedupDecision
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftEnvelope
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftSession
import com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceBlockingLevel
import com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceStatus
import com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceTask
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldAtom
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldKind
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldValue
import com.example.shoppingassistant.domain.shortlisting.ShortListingIdentitySignature
import com.example.shoppingassistant.domain.shortlisting.ShortListingIncomingPhoto
import com.example.shoppingassistant.domain.shortlisting.ShortListingMediaReceipt
import com.example.shoppingassistant.domain.shortlisting.ShortListingNormalizedPayload
import com.example.shoppingassistant.domain.shortlisting.ShortListingPhotoRole
import com.example.shoppingassistant.domain.shortlisting.ShortListingProfileGate
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishBlocker
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLifecycleStatus
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLocals
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishPreflight
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishReadiness
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishResult
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishWarning
import com.example.shoppingassistant.domain.shortlisting.ShortListingResultAction
import com.example.shoppingassistant.domain.shortlisting.ShortListingReviewUpdateRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingStage
import com.example.shoppingassistant.domain.shortlisting.ShortListingValueType
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionAttributeCandidate
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionBindOutcome
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionDelta
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionRawExtraction
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionResult
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionRuntimeFlags
import com.example.shoppingassistant.domain.vision.VisionCategoryCandidate
import com.example.shoppingassistant.domain.vision.VisionNextAction
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionNormalizeResult
import com.example.shoppingassistant.domain.vision.VisionPhotoInput
import com.example.shoppingassistant.domain.vision.VisionPhotoRole
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayer
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.OFFER_PUBLICATION_STATE_PENDING_REVIEW
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.UserProfilesTable
import com.example.shoppingassistant.server.offers.normalizeOfferCondition
import com.example.shoppingassistant.server.storage.PhotoStorageService
import com.example.shoppingassistant.server.vision.VisionService
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

interface ShortListingBackendService {
    suspend fun listDrafts(userId: Long): List<ShortListingDraftSession>
    suspend fun createDraft(userId: Long, request: ShortListingCreateDraftRequest): ShortListingDraftEnvelope
    suspend fun getDraft(userId: Long, draftId: String): ShortListingDraftEnvelope?
    suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: ShortListingReviewUpdateRequest,
    ): ShortListingDraftEnvelope
    suspend fun getPublishPreflight(userId: Long, draftId: String): ShortListingPublishPreflight
    suspend fun publishDraft(userId: Long, draftId: String): ShortListingPublishAttemptResult
}

class ShortListingBackendServiceImpl(
    private val catalogReadRepository: CatalogReadRepository,
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val stage4ExecutionLayer: Stage4ExecutionLayer,
    private val visionService: VisionService,
    private val photoStorageService: PhotoStorageService,
) : ShortListingBackendService {

    private val aiRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val aiRefreshJobs = ConcurrentHashMap<String, Unit>()

    override suspend fun listDrafts(userId: Long): List<ShortListingDraftSession> =
        DatabaseFactory.dbQuery {
            ShortListingDraftsTable
                .selectAll()
                .apply {
                    andWhere { ShortListingDraftsTable.userId eq userId }
                    andWhere { ShortListingDraftsTable.deleted eq false }
                }
                .orderBy(ShortListingDraftsTable.updatedAt to SortOrder.DESC)
                .map { row -> row.toEnvelope() }
                .also { envelopes ->
                    envelopes
                        .filter { envelope -> envelope.draft.aiRefreshPending }
                        .forEach { envelope ->
                            scheduleAiRefreshIfNeeded(
                                userId = userId,
                                draftId = envelope.draft.draftId,
                                expectedRevision = envelope.draft.revision,
                            )
                        }
                }
                .map { envelope -> envelope.draft }
        }

    override suspend fun createDraft(
        userId: Long,
        request: ShortListingCreateDraftRequest,
    ): ShortListingDraftEnvelope {
        require(request.photos.isNotEmpty()) { "AT_LEAST_ONE_PHOTO_REQUIRED" }

        val now = System.currentTimeMillis()
        val draftId = generateId("sldraft")
        val sessionId = generateId("slsess")
        val media = storeIncomingPhotos(request.photos, now)
        val confirmedFields = sanitizeFieldMap(request.confirmedFields)
        val publishLocals = sanitizePublishLocals(request.publishLocals)
        val requestedCategoryCode = normalizeCategoryCode(request.requestedCategoryCode)

        val analysis = analyzeDraft(
            userId = userId,
            draftId = draftId,
            locale = request.locale,
            requestedCategoryCode = requestedCategoryCode,
            confirmedCategoryCode = null,
            media = media,
            predictedFields = emptyMap(),
            previousPredictedFields = emptyMap(),
            confirmedFields = confirmedFields,
            publishLocals = publishLocals,
            rerunVision = false,
            previousVisionResult = pendingVisionResult(
                draftId = draftId,
                stage = ShortListingStage.ANALYZING,
            ),
        )

        val draft = ShortListingDraftSession(
            draftId = draftId,
            sessionId = sessionId,
            userId = userId.toString(),
            stage = analysis.stage,
            aiRefreshPending = true,
            safeToExit = true,
            createdAtMillis = now,
            updatedAtMillis = now,
            revision = 1,
            requestedCategoryCode = requestedCategoryCode,
            resolvedCategoryCode = analysis.resolvedCategoryCode,
            candidateCategory = analysis.candidateCategory,
            confirmedCategoryCode = null,
            media = media,
            predictedFields = analysis.predictedFields,
            confirmedUserFields = confirmedFields,
            missingRequiredFields = analysis.missingRequiredFields,
            evidenceTasks = analysis.evidenceTasks,
            publishLocals = publishLocals,
            publishReadiness = analysis.publishReadiness,
            profileGate = analysis.profileGate,
            identitySignature = analysis.identitySignature,
            publishedOfferId = null,
            lifecycleStatus = null,
            expiresAtMillis = analysis.preflight.expiresAtMillis,
        )
        val envelope = ShortListingDraftEnvelope(
            draft = draft,
            visionResult = analysis.visionResult,
        )
        persistNewEnvelope(envelope, now)
        scheduleAiRefreshIfNeeded(
            userId = userId,
            draftId = draftId,
            expectedRevision = draft.revision,
        )
        return envelope
    }

    override suspend fun getDraft(userId: Long, draftId: String): ShortListingDraftEnvelope? =
        loadOwnedEnvelope(userId = userId, draftId = draftId)
            ?.also { envelope ->
                if (envelope.draft.aiRefreshPending) {
                    scheduleAiRefreshIfNeeded(
                        userId = userId,
                        draftId = envelope.draft.draftId,
                        expectedRevision = envelope.draft.revision,
                    )
                }
            }

    override suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: ShortListingReviewUpdateRequest,
    ): ShortListingDraftEnvelope {
        val existing = loadOwnedEnvelope(userId = userId, draftId = draftId)
            ?: throw ShortListingNotFoundException(draftId)

        val now = System.currentTimeMillis()
        val appendedMedia = if (request.appendPhotos.isEmpty()) emptyList() else storeIncomingPhotos(request.appendPhotos, now)
        val media = existing.draft.media + appendedMedia
        val clearedFieldCodes = request.clearedFieldCodes
            .map { code -> code.normalizedFieldCode() }
            .filter { it.isNotEmpty() }
            .distinct()
        val confirmedFields = LinkedHashMap(
            existing.draft.confirmedUserFields
                .filterKeys { key -> key.normalizedFieldCode() !in clearedFieldCodes },
        ).apply {
            putAll(sanitizeFieldMap(request.confirmedFields))
        }.toMap(LinkedHashMap())
        val publishLocals = request.publishLocals?.let(::sanitizePublishLocals) ?: existing.draft.publishLocals
        val confirmedCategoryCode = if (request.confirmedCategoryCode != null) {
            normalizeCategoryCode(request.confirmedCategoryCode)
        } else {
            existing.draft.confirmedCategoryCode
        }
        val shouldRefreshVisionAsync = existing.draft.aiRefreshPending || appendedMedia.isNotEmpty()
        val pendingVision = if (shouldRefreshVisionAsync) {
            pendingVisionResult(
                draftId = existing.draft.draftId,
                stage = if (appendedMedia.isNotEmpty()) ShortListingStage.RERUN_IN_PROGRESS else existing.draft.stage,
                baseline = existing.visionResult,
                predictedFields = existing.draft.predictedFields,
                candidateCategory = existing.draft.candidateCategory,
            )
        } else {
            existing.visionResult
        }

        val analysis = analyzeDraft(
            userId = userId,
            draftId = draftId,
            locale = null,
            requestedCategoryCode = existing.draft.requestedCategoryCode,
            confirmedCategoryCode = confirmedCategoryCode,
            media = media,
            predictedFields = existing.draft.predictedFields,
            previousPredictedFields = existing.draft.predictedFields,
            confirmedFields = confirmedFields,
            publishLocals = publishLocals,
            rerunVision = false,
            previousVisionResult = pendingVision,
        )

        val updatedDraft = existing.draft.copy(
            stage = analysis.stage,
            aiRefreshPending = shouldRefreshVisionAsync,
            safeToExit = true,
            updatedAtMillis = now,
            revision = existing.draft.revision + 1,
            resolvedCategoryCode = analysis.resolvedCategoryCode,
            candidateCategory = analysis.candidateCategory,
            confirmedCategoryCode = confirmedCategoryCode,
            media = media,
            predictedFields = analysis.predictedFields,
            confirmedUserFields = confirmedFields,
            missingRequiredFields = analysis.missingRequiredFields,
            evidenceTasks = analysis.evidenceTasks,
            publishLocals = publishLocals,
            publishReadiness = analysis.publishReadiness,
            profileGate = analysis.profileGate,
            identitySignature = analysis.identitySignature,
            expiresAtMillis = analysis.preflight.expiresAtMillis,
        )
        val envelope = ShortListingDraftEnvelope(
            draft = updatedDraft,
            visionResult = analysis.visionResult ?: existing.visionResult,
        )
        persistUpdatedEnvelope(envelope, now)
        if (updatedDraft.aiRefreshPending) {
            scheduleAiRefreshIfNeeded(
                userId = userId,
                draftId = updatedDraft.draftId,
                expectedRevision = updatedDraft.revision,
            )
        }
        return envelope
    }

    override suspend fun getPublishPreflight(
        userId: Long,
        draftId: String,
    ): ShortListingPublishPreflight =
        loadOwnedEnvelope(userId = userId, draftId = draftId)?.draft?.toPublishPreflight()
            ?: throw ShortListingNotFoundException(draftId)

    override suspend fun publishDraft(
        userId: Long,
        draftId: String,
    ): ShortListingPublishAttemptResult {
        val existing = loadOwnedEnvelope(userId = userId, draftId = draftId)
            ?: throw ShortListingNotFoundException(draftId)
        val preflight = existing.draft.toPublishPreflight()
        if (!preflight.eligible) {
            return ShortListingPublishAttemptResult.Blocked(preflight)
        }

        val publishedOfferId = existing.draft.publishedOfferId?.toLongOrNull()
        if (publishedOfferId != null) {
            return ShortListingPublishAttemptResult.Published(
                result = buildPublishResult(existing.draft, publishedOfferId),
            )
        }

        val payload = preflight.normalizedPayload
            ?: return ShortListingPublishAttemptResult.Blocked(
                preflight.copy(
                    eligible = false,
                    blockers = preflight.blockers + ShortListingPublishBlocker(
                        code = "NORMALIZED_PAYLOAD_MISSING",
                        message = "Черновик не готов к публикации.",
                    ),
                ),
            )

        val now = System.currentTimeMillis()
        val typedAttributes = payload.attributes.toOfferTypedAttributes()
        val condition = normalizeOfferCondition(payload.attributes["condition"]?.primaryValue())
        val deliveryChannel = normalizeDeliveryChannel(payload.deliveryChannel)
        val titleNorm = payload.title.takeIf { it.isNotBlank() } ?: payload.categoryCode

        val createdOfferId = DatabaseFactory.dbQuery {
            val productId = ProductsTable.insert { stmt ->
                stmt[ProductsTable.category] = payload.categoryCode
                stmt[ProductsTable.brand] = payload.brand?.trim()?.takeIf { it.isNotEmpty() }
                stmt[ProductsTable.model] = payload.model?.trim()?.takeIf { it.isNotEmpty() }
                stmt[ProductsTable.titleNorm] = titleNorm
                stmt[ProductsTable.imageUrls] = payload.imageUrls
                stmt[ProductsTable.specs] = typedAttributes.ifEmpty { null }
                stmt[ProductsTable.description] = payload.description
                stmt[ProductsTable.updatedAt] = now
            }.resultedValues?.single()?.get(ProductsTable.id)
                ?: error("PRODUCT_INSERT_FAILED")

            ProductsTable.update({ ProductsTable.id eq productId }) { stmt ->
                stmt[ProductsTable.updatedAt] = now
            }

            OffersTable.insert { stmt ->
                stmt[OffersTable.productId] = productId
                stmt[OffersTable.userId] = userId
                stmt[OffersTable.priceCents] = Money.fromMajor(payload.priceMajor).minor
                stmt[OffersTable.currency] = payload.currency
                stmt[OffersTable.attributes] = typedAttributes.ifEmpty { null }
                stmt[OffersTable.description] = payload.description
                stmt[OffersTable.imageUrls] = payload.imageUrls
                stmt[OffersTable.condition] = condition
                stmt[OffersTable.deliveryChannel] = deliveryChannel
                stmt[OffersTable.status] = "ACTIVE"
                stmt[OffersTable.publicationState] = OFFER_PUBLICATION_STATE_PENDING_REVIEW
                stmt[OffersTable.updatedAt] = now
            }.resultedValues?.single()?.get(OffersTable.id)
                ?: error("OFFER_INSERT_FAILED")
        }

        val lifecycleStatus = ShortListingPublishLifecycleStatus.PENDING_REVIEW
        val updatedDraft = existing.draft.copy(
            stage = ShortListingStage.PUBLISHED,
            aiRefreshPending = false,
            updatedAtMillis = now,
            revision = existing.draft.revision + 1,
            publishReadiness = existing.draft.publishReadiness.copy(ready = true),
            publishedOfferId = createdOfferId.toString(),
            lifecycleStatus = lifecycleStatus,
            expiresAtMillis = preflight.expiresAtMillis,
        )
        val updatedEnvelope = existing.copy(draft = updatedDraft)
        persistPublishedEnvelope(updatedEnvelope, createdOfferId, lifecycleStatus, now)

        return ShortListingPublishAttemptResult.Published(
            result = buildPublishResult(updatedDraft, createdOfferId),
        )
    }

    private suspend fun analyzeDraft(
        userId: Long,
        draftId: String,
        locale: String?,
        requestedCategoryCode: String?,
        confirmedCategoryCode: String?,
        media: List<ShortListingMediaReceipt>,
        predictedFields: Map<String, ShortListingFieldValue>,
        previousPredictedFields: Map<String, ShortListingFieldValue>,
        confirmedFields: Map<String, ShortListingFieldValue>,
        publishLocals: ShortListingPublishLocals,
        rerunVision: Boolean,
        previousVisionResult: ShortListingVisionResult?,
    ): DraftAnalysis {
        val visionOutcome = if (rerunVision) {
            runVision(
                userId = userId,
                media = media,
                locale = locale,
                categoryHint = confirmedCategoryCode ?: requestedCategoryCode,
            )
        } else {
            null
        }

        val effectivePredictedFields = if (visionOutcome != null) {
            visionOutcome.normalizedQuery.toPredictedFieldMap()
        } else {
            predictedFields
        }
        val effectiveRequestedCategory = normalizeCategoryCode(
            requestedCategoryCode
                ?: visionOutcome?.categoryCode
                ?: previousVisionResult?.candidateCategory?.code,
        )
        val candidateCategory = resolveCandidateCategory(
            explicitConfirmed = confirmedCategoryCode,
            requestedCategoryCode = effectiveRequestedCategory,
            visionOutcome = visionOutcome,
            fallback = previousVisionResult?.candidateCategory,
            locale = locale,
        )
        val resolvedCategoryCode = resolveCategoryCode(
            confirmedCategoryCode
                ?: candidateCategory?.code
                ?: effectiveRequestedCategory,
        )
        val mergedFields = mergeFields(
            predictedFields = effectivePredictedFields,
            confirmedFields = confirmedFields,
        )
        val effectiveSpec = loadEffectiveSpec(
            categoryCode = resolvedCategoryCode,
            mergedFields = mergedFields,
        )
        val missingRequiredFields = computeMissingRequiredFields(
            spec = effectiveSpec,
            mergedFields = mergedFields,
        )
        val evidenceTasks = buildEvidenceTasks(
            missingRequiredFields = missingRequiredFields,
            visionOutcome = visionOutcome,
        )
        val profileGate = loadProfileGate(
            userId = userId,
            publishLocals = publishLocals,
        )
        val identitySignature = buildIdentitySignature(
            userId = userId,
            resolvedCategoryCode = resolvedCategoryCode,
            mergedFields = mergedFields,
            spec = effectiveSpec,
        )
        val preflight = buildPublishPreflight(
            draftId = draftId,
            confirmedCategoryCode = confirmedCategoryCode,
            requestedCategoryCode = effectiveRequestedCategory,
            resolvedCategoryCode = resolvedCategoryCode,
            mergedFields = mergedFields,
            publishLocals = publishLocals,
            media = media,
            missingRequiredFields = missingRequiredFields,
            evidenceTasks = evidenceTasks,
            profileGate = profileGate,
            identitySignature = identitySignature,
            spec = effectiveSpec,
        )
        val stage = resolveStage(
            preflight = preflight,
            evidenceTasks = evidenceTasks,
        )
        val delta = if (rerunVision) {
            computeVisionDelta(
                previousPredictedFields = previousPredictedFields,
                newPredictedFields = effectivePredictedFields,
                confirmedFields = confirmedFields,
            )
        } else {
            ShortListingVisionDelta()
        }
        val visionResult = when {
            visionOutcome != null -> visionOutcome.toContract(
                draftId = draftId,
                stage = stage,
                candidateCategory = candidateCategory,
                predictedFields = effectivePredictedFields,
                missingRequiredFields = missingRequiredFields,
                evidenceTasks = evidenceTasks,
                delta = delta,
            )
            previousVisionResult != null -> previousVisionResult.copy(
                status = stage,
                predictedFields = effectivePredictedFields,
                missingRequiredFields = missingRequiredFields,
                evidenceTasks = evidenceTasks,
                delta = delta,
            )
            else -> null
        }

        return DraftAnalysis(
            resolvedCategoryCode = resolvedCategoryCode,
            candidateCategory = candidateCategory,
            predictedFields = effectivePredictedFields,
            missingRequiredFields = missingRequiredFields,
            evidenceTasks = evidenceTasks,
            publishReadiness = ShortListingPublishReadiness(
                ready = preflight.eligible,
                blockers = preflight.blockers,
                warnings = preflight.warnings,
            ),
            profileGate = profileGate,
            identitySignature = identitySignature,
            preflight = preflight,
            stage = stage,
            visionResult = visionResult,
        )
    }

    private suspend fun runVision(
        userId: Long,
        media: List<ShortListingMediaReceipt>,
        locale: String?,
        categoryHint: String?,
    ): VisionNormalizeResult? {
        if (media.isEmpty()) return null
        val photos = media.mapNotNull { receipt ->
            val bytes = runCatching { Files.readAllBytes(Path.of(receipt.storageUrl)) }.getOrNull()
                ?: return@mapNotNull null
            VisionPhotoInput(
                role = receipt.role.toVisionRole(),
                base64 = Base64.getEncoder().encodeToString(bytes),
            )
        }
        if (photos.isEmpty()) return null
        return visionService.normalizePhotos(
            VisionNormalizeRequest(
                photos = photos,
                userKey = userId.toString(),
                locale = locale,
                categoryHint = categoryHint,
            ),
        )
    }

    private fun scheduleAiRefreshIfNeeded(
        userId: Long,
        draftId: String,
        expectedRevision: Int,
    ) {
        val jobKey = "$draftId@$expectedRevision"
        if (aiRefreshJobs.putIfAbsent(jobKey, Unit) != null) return
        aiRefreshScope.launch {
            try {
                refreshDraftInBackground(
                    userId = userId,
                    draftId = draftId,
                    expectedRevision = expectedRevision,
                )
            } finally {
                aiRefreshJobs.remove(jobKey)
            }
        }
    }

    private suspend fun refreshDraftInBackground(
        userId: Long,
        draftId: String,
        expectedRevision: Int,
    ) {
        val existing = loadOwnedEnvelope(userId = userId, draftId = draftId) ?: return
        if (existing.draft.revision != expectedRevision || !existing.draft.aiRefreshPending) return

        val draft = existing.draft
        val baselineVision = existing.visionResult.clearAiRefreshRuntimeFlags()
        val refreshed = runCatching {
            analyzeDraft(
                userId = userId,
                draftId = draftId,
                locale = null,
                requestedCategoryCode = draft.requestedCategoryCode,
                confirmedCategoryCode = draft.confirmedCategoryCode,
                media = draft.media,
                predictedFields = draft.predictedFields,
                previousPredictedFields = draft.predictedFields,
                confirmedFields = draft.confirmedUserFields,
                publishLocals = draft.publishLocals,
                rerunVision = true,
                previousVisionResult = baselineVision,
            )
        }

        val now = System.currentTimeMillis()
        val envelope = refreshed.fold(
            onSuccess = { analysis ->
                val updatedDraft = draft.copy(
                    stage = analysis.stage,
                    aiRefreshPending = false,
                    safeToExit = true,
                    updatedAtMillis = now,
                    revision = draft.revision + 1,
                    resolvedCategoryCode = analysis.resolvedCategoryCode,
                    candidateCategory = analysis.candidateCategory,
                    confirmedCategoryCode = draft.confirmedCategoryCode,
                    media = draft.media,
                    predictedFields = analysis.predictedFields,
                    confirmedUserFields = draft.confirmedUserFields,
                    missingRequiredFields = analysis.missingRequiredFields,
                    evidenceTasks = analysis.evidenceTasks,
                    publishLocals = draft.publishLocals,
                    publishReadiness = analysis.publishReadiness,
                    profileGate = analysis.profileGate,
                    identitySignature = analysis.identitySignature,
                    expiresAtMillis = analysis.preflight.expiresAtMillis,
                )
                ShortListingDraftEnvelope(
                    draft = updatedDraft,
                    visionResult = analysis.visionResult?.clearAiRefreshRuntimeFlags(),
                )
            },
            onFailure = { error ->
                val updatedDraft = draft.copy(
                    aiRefreshPending = false,
                    updatedAtMillis = now,
                    revision = draft.revision + 1,
                )
                ShortListingDraftEnvelope(
                    draft = updatedDraft,
                    visionResult = buildAiRefreshFailureResult(
                        draft = updatedDraft,
                        baseline = baselineVision,
                        errorCode = error.message ?: ShortListingVisionRuntimeFlags.AI_REFRESH_FAILED,
                    ),
                )
            },
        )

        persistEnvelopeIfRevisionMatches(
            expectedRevision = expectedRevision,
            envelope = envelope,
            now = now,
        )
    }

    private fun pendingVisionResult(
        draftId: String,
        stage: ShortListingStage,
        baseline: ShortListingVisionResult? = null,
        predictedFields: Map<String, ShortListingFieldValue> = emptyMap(),
        candidateCategory: ShortListingCategoryCandidate? = null,
    ): ShortListingVisionResult =
        (baseline?.clearAiRefreshRuntimeFlags() ?: ShortListingVisionResult(
            attemptId = generateId("vision-attempt"),
            serverRequestId = generateId("vision-job"),
            draftId = draftId,
            status = stage,
        )).copy(
            attemptId = generateId("vision-attempt"),
            serverRequestId = generateId("vision-job"),
            draftId = draftId,
            status = stage,
            candidateCategory = baseline?.candidateCategory ?: candidateCategory,
            predictedFields = predictedFields,
            warnings = ((baseline?.warnings ?: emptyList()) + ShortListingVisionRuntimeFlags.AI_REFRESH_PENDING)
                .distinct(),
            errors = baseline?.errors.orEmpty(),
        )

    private fun buildAiRefreshFailureResult(
        draft: ShortListingDraftSession,
        baseline: ShortListingVisionResult?,
        errorCode: String,
    ): ShortListingVisionResult =
        (baseline ?: ShortListingVisionResult(
            attemptId = generateId("vision-attempt"),
            serverRequestId = generateId("vision-job"),
            draftId = draft.draftId,
            status = draft.stage,
        )).copy(
            attemptId = generateId("vision-attempt"),
            serverRequestId = generateId("vision-job"),
            draftId = draft.draftId,
            status = draft.stage,
            candidateCategory = draft.candidateCategory,
            predictedFields = draft.predictedFields,
            missingRequiredFields = draft.missingRequiredFields,
            evidenceTasks = draft.evidenceTasks,
            warnings = (baseline?.warnings.orEmpty() + ShortListingVisionRuntimeFlags.AI_REFRESH_FAILED).distinct(),
            errors = (baseline?.errors.orEmpty() + errorCode).distinct(),
        )

    private fun ShortListingVisionResult?.clearAiRefreshRuntimeFlags(): ShortListingVisionResult? =
        this?.copy(
            warnings = warnings.filterNot { warning ->
                warning == ShortListingVisionRuntimeFlags.AI_REFRESH_PENDING ||
                    warning == ShortListingVisionRuntimeFlags.AI_REFRESH_FAILED
            },
        )

    private fun ShortListingVisionResult?.isAiRefreshPending(): Boolean =
        this?.warnings?.contains(ShortListingVisionRuntimeFlags.AI_REFRESH_PENDING) == true

    private suspend fun resolveCandidateCategory(
        explicitConfirmed: String?,
        requestedCategoryCode: String?,
        visionOutcome: VisionNormalizeResult?,
        fallback: ShortListingCategoryCandidate?,
        locale: String?,
    ): ShortListingCategoryCandidate? {
        val explicit = normalizeCategoryCode(explicitConfirmed)
        if (explicit != null) {
            return categoryCandidateFor(explicit, locale) ?: ShortListingCategoryCandidate(
                code = explicit,
                title = explicit,
                confidenceBand = ShortListingConfidenceBand.HIGH,
            )
        }
        val primaryVision = visionOutcome?.categoryCandidates?.firstOrNull()?.toContractCategoryCandidate()
        if (primaryVision != null) return primaryVision
        val requested = normalizeCategoryCode(requestedCategoryCode)
        if (requested != null) {
            return categoryCandidateFor(requested, locale) ?: ShortListingCategoryCandidate(
                code = requested,
                title = requested,
                confidenceBand = ShortListingConfidenceBand.MEDIUM,
            )
        }
        return fallback
    }

    private suspend fun categoryCandidateFor(
        categoryCode: String,
        locale: String?,
    ): ShortListingCategoryCandidate? {
        val category = runCatching { catalogTaxonomyRepository.listCategories() }.getOrNull()
            ?.firstOrNull { it.code.equals(categoryCode, ignoreCase = true) }
            ?: return null
        return ShortListingCategoryCandidate(
            code = category.code,
            title = category.displayTitle(locale),
            confidenceBand = ShortListingConfidenceBand.MEDIUM,
        )
    }

    private suspend fun resolveCategoryCode(categoryCode: String?): String? {
        val normalized = normalizeCategoryCode(categoryCode) ?: return null
        val resolution = runCatching { catalogTaxonomyRepository.resolveCategoryCode(normalized) }.getOrNull()
        return if (resolution != null &&
            resolution.wasRedirected &&
            !resolution.cycleDetected &&
            resolution.unresolvedTarget == null
        ) {
            normalizeCategoryCode(resolution.resolvedCode)
        } else {
            normalized
        }
    }

    private suspend fun loadEffectiveSpec(
        categoryCode: String?,
        mergedFields: Map<String, ShortListingFieldValue>,
    ): CatalogCategoryEffectiveSpec? {
        val resolvedCategoryCode = normalizeCategoryCode(categoryCode) ?: return null
        return runCatching {
            catalogReadRepository.getCategoryEffectiveSpec(
                categoryCode = resolvedCategoryCode,
                brand = mergedFields["brand"]?.primaryValue(),
                model = mergedFields["model"]?.primaryValue(),
            )
        }.getOrNull()
    }

    private fun computeMissingRequiredFields(
        spec: CatalogCategoryEffectiveSpec?,
        mergedFields: Map<String, ShortListingFieldValue>,
    ): List<String> {
        if (spec == null) return emptyList()
        val required = spec.allAttributes()
            .filter { it.requiredForCategory || it.requiredForOffer || it.requiredForExpress }
            .map { it.code.normalizedFieldCode() }
            .toMutableSet()
        spec.requiredIfRules.forEach { rule ->
            if (rule.matches(mergedFields)) {
                required += rule.requiredAttributeCode.normalizedFieldCode()
            }
        }
        return required
            .filterNot { mergedFields[it]?.isMeaningful() == true }
            .sorted()
    }

    private suspend fun loadProfileGate(
        userId: Long,
        publishLocals: ShortListingPublishLocals,
    ): ShortListingProfileGate {
        val authRow = DatabaseFactory.dbQuery {
            AuthUsersTable
                .selectAll()
                .apply { andWhere { AuthUsersTable.id eq userId } }
                .singleOrNull()
        } ?: return ShortListingProfileGate(
            eligible = false,
            missingFields = listOf("account"),
        )
        val profileRow = DatabaseFactory.dbQuery {
            UserProfilesTable
                .selectAll()
                .apply { andWhere { UserProfilesTable.userId eq userId } }
                .singleOrNull()
        }

        val missing = buildList {
            val displayName = authRow[AuthUsersTable.displayName]
                ?: profileRow?.tryGet(UserProfilesTable.displayName)
            if (displayName.isNullOrBlank()) add("displayName")

            val city = publishLocals.city
                ?: authRow[AuthUsersTable.city]
                ?: profileRow?.tryGet(UserProfilesTable.city)
            if (city.isNullOrBlank()) add("city")

            val emailVerified = authRow[AuthUsersTable.emailVerified] ||
                authRow[AuthUsersTable.emailVerifiedAt] != null
            val phoneVerified = authRow[AuthUsersTable.phoneVerifiedAt] != null
            if (!emailVerified && !phoneVerified) add("verifiedContact")
        }

        return ShortListingProfileGate(
            eligible = missing.isEmpty(),
            missingFields = missing,
        )
    }

    private suspend fun buildIdentitySignature(
        userId: Long,
        resolvedCategoryCode: String?,
        mergedFields: Map<String, ShortListingFieldValue>,
        spec: CatalogCategoryEffectiveSpec?,
    ): ShortListingIdentitySignature? {
        val categoryCode = normalizeCategoryCode(resolvedCategoryCode) ?: return null
        val identityCodes = (spec?.meta?.identityAttributeCodes.orEmpty().map { it.normalizedFieldCode() } + listOf("brand", "model"))
            .distinct()
        val identityAttributes = LinkedHashMap<String, ShortListingFieldValue>()
        identityCodes.forEach { code ->
            val value = mergedFields[code]
            if (value?.isMeaningful() == true) {
                identityAttributes[code] = value
            }
        }
        if (identityAttributes.isEmpty()) return null

        val signatureSource = buildString {
            append(categoryCode)
            identityAttributes.toSortedMap().forEach { (key, value) ->
                append('|')
                append(key)
                append('=')
                append(value.signatureToken())
            }
        }
        val signature = sha256Hex(signatureSource.toByteArray(Charsets.UTF_8))
        val duplicate = findSameUserDuplicateOffer(
            userId = userId,
            categoryCode = categoryCode,
            signature = signature,
            identityCodes = identityCodes,
        )
        return ShortListingIdentitySignature(
            resolvedCategoryCode = categoryCode,
            identityAttributes = identityAttributes,
            identitySignature = signature,
            dedupDecision = if (duplicate) {
                ShortListingDedupDecision.SAME_USER_SIMILAR_ACTIVE
            } else {
                ShortListingDedupDecision.NO_DUPLICATE
            },
            dedupReasonCodes = if (duplicate) listOf("SAME_USER_SIMILAR_ACTIVE") else emptyList(),
        )
    }

    private suspend fun findSameUserDuplicateOffer(
        userId: Long,
        categoryCode: String,
        signature: String,
        identityCodes: List<String>,
    ): Boolean = DatabaseFactory.dbQuery {
        OffersTable
            .innerJoin(ProductsTable, { productId }, { ProductsTable.id })
            .selectAll()
            .apply {
                andWhere { OffersTable.userId eq userId }
                andWhere { OffersTable.status eq "ACTIVE" }
                andWhere { ProductsTable.category eq categoryCode }
            }
            .any { row ->
                val merged = linkedMapOf<String, ShortListingFieldValue>()
                row[ProductsTable.brand]?.let { merged["brand"] = scalarField(it) }
                row[ProductsTable.model]?.let { merged["model"] = scalarField(it) }
                row[ProductsTable.specs].orEmpty().forEach { (key, value) ->
                    merged[key.normalizedFieldCode()] = typedValueToField(value)
                }
                row[OffersTable.attributes].orEmpty().forEach { (key, value) ->
                    merged[key.normalizedFieldCode()] = typedValueToField(value)
                }
                val existingSource = buildString {
                    append(categoryCode)
                    identityCodes.distinct().sorted().forEach { code ->
                        val value = merged[code]
                        if (value?.isMeaningful() == true) {
                            append('|')
                            append(code)
                            append('=')
                            append(value.signatureToken())
                        }
                    }
                }
                sha256Hex(existingSource.toByteArray(Charsets.UTF_8)) == signature
            }
    }

    private fun buildPublishPreflight(
        draftId: String,
        confirmedCategoryCode: String?,
        requestedCategoryCode: String?,
        resolvedCategoryCode: String?,
        mergedFields: Map<String, ShortListingFieldValue>,
        publishLocals: ShortListingPublishLocals,
        media: List<ShortListingMediaReceipt>,
        missingRequiredFields: List<String>,
        evidenceTasks: List<ShortListingEvidenceTask>,
        profileGate: ShortListingProfileGate,
        identitySignature: ShortListingIdentitySignature?,
        spec: CatalogCategoryEffectiveSpec?,
    ): ShortListingPublishPreflight {
        val blockers = mutableListOf<ShortListingPublishBlocker>()
        val warnings = mutableListOf<ShortListingPublishWarning>()
        val resolvedConfirmed = normalizeCategoryCode(confirmedCategoryCode)
        val resolvedRequested = normalizeCategoryCode(requestedCategoryCode)
        val resolvedCategory = normalizeCategoryCode(resolvedCategoryCode)

        if (resolvedConfirmed == null) {
            blockers += ShortListingPublishBlocker(
                code = "CATEGORY_CONFIRMATION_REQUIRED",
                message = "Подтвердите категорию перед публикацией.",
            )
        }
        if (resolvedCategory == null || spec == null) {
            blockers += ShortListingPublishBlocker(
                code = "CATEGORY_UNSUPPORTED",
                message = "Для категории нет валидного runtime-контракта.",
            )
        }
        if (resolvedRequested != null && resolvedCategory != null && resolvedRequested != resolvedCategory) {
            warnings += ShortListingPublishWarning(
                code = "CATEGORY_REDIRECT_APPLIED",
                message = "Категория была приведена к каноническому коду.",
            )
        }
        if (missingRequiredFields.isNotEmpty()) {
            blockers += ShortListingPublishBlocker(
                code = "REQUIRED_FIELDS_MISSING",
                message = "Не заполнены обязательные поля объявления.",
                fieldCodes = missingRequiredFields,
            )
        }
        val blockingEvidence = evidenceTasks.filter { it.blockingLevel == ShortListingEvidenceBlockingLevel.PUBLISH_BLOCKING }
        if (blockingEvidence.isNotEmpty()) {
            blockers += ShortListingPublishBlocker(
                code = "EVIDENCE_REQUIRED",
                message = "Нужны дополнительные фото перед публикацией.",
                fieldCodes = blockingEvidence.flatMap { it.targetFieldCodes }.distinct(),
            )
        }
        if (!profileGate.eligible) {
            blockers += ShortListingPublishBlocker(
                code = "PROFILE_INCOMPLETE",
                message = "Профиль не готов к публикации.",
                fieldCodes = profileGate.missingFields,
            )
        }

        val priceMajor = publishLocals.priceMajor
        if (priceMajor == null || Money.fromMajorOrNull(priceMajor) == null || priceMajor <= 0.0) {
            blockers += ShortListingPublishBlocker(
                code = "PRICE_REQUIRED",
                message = "Укажите корректную цену.",
                fieldCodes = listOf("price"),
            )
        }
        val currency = Money.normalizeCurrencyCode(publishLocals.currency)
        if (currency == null) {
            blockers += ShortListingPublishBlocker(
                code = "CURRENCY_REQUIRED",
                message = "Укажите валюту в ISO-формате.",
                fieldCodes = listOf("currency"),
            )
        }
        val ttlDays = publishLocals.ttlPresetDays?.takeIf { it in MIN_TTL_DAYS..MAX_TTL_DAYS }
        if (ttlDays == null) {
            blockers += ShortListingPublishBlocker(
                code = "TTL_REQUIRED",
                message = "Выберите срок жизни объявления.",
                fieldCodes = listOf("ttlPresetDays"),
            )
        }
        if (publishLocals.city.isNullOrBlank()) {
            blockers += ShortListingPublishBlocker(
                code = "CITY_REQUIRED",
                message = "Укажите город размещения.",
                fieldCodes = listOf("city"),
            )
        }
        if (publishLocals.deliveryChannel != null && normalizeDeliveryChannel(publishLocals.deliveryChannel) == null) {
            blockers += ShortListingPublishBlocker(
                code = "DELIVERY_CHANNEL_INVALID",
                message = "Способ передачи должен быть одним из: delivery, pickup, meeting.",
                fieldCodes = listOf("deliveryChannel"),
            )
        }
        val multiFields = mergedFields
            .filterValues { it.kind == ShortListingFieldKind.MULTI && it.isMeaningful() }
            .keys
            .sorted()
        if (multiFields.isNotEmpty()) {
            blockers += ShortListingPublishBlocker(
                code = "MULTI_VALUED_FIELDS_UNSUPPORTED",
                message = "Перед публикацией сверьте multi-valued поля до финального выбора.",
                fieldCodes = multiFields,
            )
        }
        if (media.isEmpty()) {
            blockers += ShortListingPublishBlocker(
                code = "MEDIA_REQUIRED",
                message = "Добавьте хотя бы одно фото.",
                fieldCodes = listOf("media"),
            )
        }
        if (identitySignature?.dedupDecision == ShortListingDedupDecision.SAME_USER_SIMILAR_ACTIVE) {
            warnings += ShortListingPublishWarning(
                code = "SAME_USER_SIMILAR_ACTIVE",
                message = "У вас уже есть похожее активное объявление.",
            )
        }

        val normalizedPayload = if (blockers.isEmpty()) {
            buildNormalizedPayload(
                resolvedCategoryCode = resolvedCategory ?: resolvedConfirmed,
                mergedFields = mergedFields,
                publishLocals = publishLocals,
                media = media,
                spec = spec,
            )
        } else {
            null
        }
        return ShortListingPublishPreflight(
            draftId = draftId,
            eligible = blockers.isEmpty() && normalizedPayload != null,
            normalizedPayload = normalizedPayload,
            blockers = blockers,
            warnings = warnings.distinctBy { it.code + ":" + it.fieldCodes.joinToString(",") },
            expiresAtMillis = ttlDays?.let { System.currentTimeMillis() + it * MILLIS_PER_DAY },
            identitySignature = identitySignature,
        )
    }

    private fun buildNormalizedPayload(
        resolvedCategoryCode: String?,
        mergedFields: Map<String, ShortListingFieldValue>,
        publishLocals: ShortListingPublishLocals,
        media: List<ShortListingMediaReceipt>,
        spec: CatalogCategoryEffectiveSpec?,
    ): ShortListingNormalizedPayload? {
        val categoryCode = normalizeCategoryCode(resolvedCategoryCode) ?: return null
        val priceMajor = publishLocals.priceMajor ?: return null
        val currency = Money.normalizeCurrencyCode(publishLocals.currency) ?: return null
        val ttlDays = publishLocals.ttlPresetDays?.takeIf { it in MIN_TTL_DAYS..MAX_TTL_DAYS } ?: return null
        return ShortListingNormalizedPayload(
            title = resolveTitle(
                mergedFields = mergedFields,
                spec = spec,
                categoryCode = categoryCode,
            ),
            categoryCode = categoryCode,
            brand = mergedFields["brand"]?.primaryValue(),
            model = mergedFields["model"]?.primaryValue(),
            attributes = mergedFields
                .filterKeys { it !in ignoredPayloadFieldCodes }
                .filterValues { it.isMeaningful() },
            priceMajor = priceMajor,
            currency = currency,
            ttlPresetDays = ttlDays,
            city = publishLocals.city?.trim()?.takeIf { it.isNotEmpty() },
            deliveryChannel = normalizeDeliveryChannel(publishLocals.deliveryChannel),
            description = publishLocals.description?.trim()?.takeIf { it.isNotEmpty() },
            imageUrls = media.map { it.storageUrl },
        )
    }

    private fun resolveStage(
        preflight: ShortListingPublishPreflight,
        evidenceTasks: List<ShortListingEvidenceTask>,
    ): ShortListingStage {
        if (preflight.eligible) return ShortListingStage.READY_FOR_PUBLISH
        if (evidenceTasks.isNotEmpty()) return ShortListingStage.NEEDS_MORE_EVIDENCE
        return ShortListingStage.READY_FOR_REVIEW
    }

    private fun buildEvidenceTasks(
        missingRequiredFields: List<String>,
        visionOutcome: VisionNormalizeResult?,
    ): List<ShortListingEvidenceTask> {
        val tasks = mutableListOf<ShortListingEvidenceTask>()
        missingRequiredFields.forEach { fieldCode ->
            tasks += ShortListingEvidenceTask(
                taskId = "field:$fieldCode",
                reasonCode = "MISSING_REQUIRED_FIELD",
                targetFieldCodes = listOf(fieldCode),
                headline = "Подтвердите поле $fieldCode",
                explanation = "Без этого поля объявление не пройдёт publish preflight.",
                blockingLevel = ShortListingEvidenceBlockingLevel.PUBLISH_BLOCKING,
                canSkip = false,
                status = ShortListingEvidenceStatus.OPEN,
            )
        }
        visionOutcome?.nextAction?.let { nextAction ->
            tasks += ShortListingEvidenceTask(
                taskId = "vision:${nextAction.name.lowercase(Locale.ROOT)}",
                reasonCode = nextAction.name,
                photoRole = nextAction.toPhotoRoleOrNull(),
                headline = nextAction.toHeadline(),
                explanation = "Нужен дополнительный кадр для уверенной нормализации.",
                exampleHint = nextAction.toExampleHint(),
                blockingLevel = if (missingRequiredFields.isEmpty()) {
                    ShortListingEvidenceBlockingLevel.SOFT
                } else {
                    ShortListingEvidenceBlockingLevel.PUBLISH_BLOCKING
                },
                canSkip = missingRequiredFields.isEmpty(),
                status = ShortListingEvidenceStatus.OPEN,
            )
        }
        return tasks.distinctBy { it.taskId }
    }

    private fun computeVisionDelta(
        previousPredictedFields: Map<String, ShortListingFieldValue>,
        newPredictedFields: Map<String, ShortListingFieldValue>,
        confirmedFields: Map<String, ShortListingFieldValue>,
    ): ShortListingVisionDelta {
        val changedPredictions = newPredictedFields.keys.union(previousPredictedFields.keys)
            .filter { previousPredictedFields[it] != newPredictedFields[it] }
            .sorted()
        val unchangedConfirmedFields = confirmedFields.keys
            .filter { code -> confirmedFields[code] == newPredictedFields[code] }
            .sorted()
        val conflictedFields = confirmedFields.keys
            .filter { code ->
                val confirmed = confirmedFields[code]
                val predicted = newPredictedFields[code]
                confirmed != null && predicted != null && confirmed != predicted
            }
            .sorted()
        return ShortListingVisionDelta(
            changedPredictions = changedPredictions,
            unchangedConfirmedFields = unchangedConfirmedFields,
            conflictedFields = conflictedFields,
        )
    }

    private suspend fun storeIncomingPhotos(
        photos: List<ShortListingIncomingPhoto>,
        now: Long,
    ): List<ShortListingMediaReceipt> =
        photos.map { photo ->
            val bytes = runCatching { Base64.getDecoder().decode(photo.dataBase64) }
                .getOrElse { throw ShortListingValidationException("INVALID_PHOTO_BASE64") }
            val storageUrl = photoStorageService.save(
                bytes = bytes,
                filename = photo.filename,
                contentType = photo.contentType,
            )
            ShortListingMediaReceipt(
                mediaId = generateId("media"),
                role = photo.role,
                storageUrl = storageUrl,
                contentType = photo.contentType,
                sha256 = sha256Hex(bytes),
                sizeBytes = bytes.size.toLong(),
                createdAtMillis = now,
            )
        }

    private suspend fun persistNewEnvelope(
        envelope: ShortListingDraftEnvelope,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            val draft = envelope.draft
            ShortListingDraftsTable.insert { stmt ->
                stmt[id] = draft.draftId
                stmt[sessionId] = draft.sessionId
                stmt[userId] = draft.userId.toLong()
                stmt[stage] = draft.stage.name
                stmt[requestedCategoryCode] = draft.requestedCategoryCode
                stmt[resolvedCategoryCode] = draft.resolvedCategoryCode
                stmt[candidateCategory] = draft.candidateCategory
                stmt[confirmedCategoryCode] = draft.confirmedCategoryCode
                stmt[media] = draft.media
                stmt[predictedFields] = draft.predictedFields
                stmt[confirmedUserFields] = draft.confirmedUserFields
                stmt[missingRequiredFields] = draft.missingRequiredFields
                stmt[evidenceTasks] = draft.evidenceTasks
                stmt[publishLocals] = draft.publishLocals
                stmt[publishReadiness] = draft.publishReadiness
                stmt[profileGate] = draft.profileGate
                stmt[identitySignature] = draft.identitySignature
                stmt[lastVisionResult] = envelope.visionResult
                stmt[publishedOfferId] = draft.publishedOfferId?.toLongOrNull()
                stmt[lifecycleStatus] = draft.lifecycleStatus?.name
                stmt[expiresAtMillis] = draft.expiresAtMillis
                stmt[safeToExit] = draft.safeToExit
                stmt[revision] = draft.revision
                stmt[createdAt] = draft.createdAtMillis
                stmt[updatedAt] = now
                stmt[deleted] = false
            }
        }
    }

    private suspend fun persistUpdatedEnvelope(
        envelope: ShortListingDraftEnvelope,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            val draft = envelope.draft
            ShortListingDraftsTable.update({
                (ShortListingDraftsTable.id eq draft.draftId) and
                    (ShortListingDraftsTable.deleted eq false)
            }) { stmt ->
                stmt[stage] = draft.stage.name
                stmt[requestedCategoryCode] = draft.requestedCategoryCode
                stmt[resolvedCategoryCode] = draft.resolvedCategoryCode
                stmt[candidateCategory] = draft.candidateCategory
                stmt[confirmedCategoryCode] = draft.confirmedCategoryCode
                stmt[media] = draft.media
                stmt[predictedFields] = draft.predictedFields
                stmt[confirmedUserFields] = draft.confirmedUserFields
                stmt[missingRequiredFields] = draft.missingRequiredFields
                stmt[evidenceTasks] = draft.evidenceTasks
                stmt[publishLocals] = draft.publishLocals
                stmt[publishReadiness] = draft.publishReadiness
                stmt[profileGate] = draft.profileGate
                stmt[identitySignature] = draft.identitySignature
                stmt[lastVisionResult] = envelope.visionResult
                stmt[ShortListingDraftsTable.publishedOfferId] = draft.publishedOfferId?.toLongOrNull()
                stmt[ShortListingDraftsTable.lifecycleStatus] = draft.lifecycleStatus?.name
                stmt[ShortListingDraftsTable.expiresAtMillis] = draft.expiresAtMillis
                stmt[ShortListingDraftsTable.safeToExit] = draft.safeToExit
                stmt[ShortListingDraftsTable.revision] = draft.revision
                stmt[ShortListingDraftsTable.updatedAt] = now
            }
        }
    }

    private suspend fun persistEnvelopeIfRevisionMatches(
        expectedRevision: Int,
        envelope: ShortListingDraftEnvelope,
        now: Long,
    ): Boolean =
        DatabaseFactory.dbQuery {
            val draft = envelope.draft
            ShortListingDraftsTable.update({
                (ShortListingDraftsTable.id eq draft.draftId) and
                    (ShortListingDraftsTable.deleted eq false) and
                    (ShortListingDraftsTable.revision eq expectedRevision)
            }) { stmt ->
                stmt[stage] = draft.stage.name
                stmt[requestedCategoryCode] = draft.requestedCategoryCode
                stmt[resolvedCategoryCode] = draft.resolvedCategoryCode
                stmt[candidateCategory] = draft.candidateCategory
                stmt[confirmedCategoryCode] = draft.confirmedCategoryCode
                stmt[media] = draft.media
                stmt[predictedFields] = draft.predictedFields
                stmt[confirmedUserFields] = draft.confirmedUserFields
                stmt[missingRequiredFields] = draft.missingRequiredFields
                stmt[evidenceTasks] = draft.evidenceTasks
                stmt[publishLocals] = draft.publishLocals
                stmt[publishReadiness] = draft.publishReadiness
                stmt[profileGate] = draft.profileGate
                stmt[identitySignature] = draft.identitySignature
                stmt[lastVisionResult] = envelope.visionResult
                stmt[ShortListingDraftsTable.publishedOfferId] = draft.publishedOfferId?.toLongOrNull()
                stmt[ShortListingDraftsTable.lifecycleStatus] = draft.lifecycleStatus?.name
                stmt[ShortListingDraftsTable.expiresAtMillis] = draft.expiresAtMillis
                stmt[ShortListingDraftsTable.safeToExit] = draft.safeToExit
                stmt[ShortListingDraftsTable.revision] = draft.revision
                stmt[ShortListingDraftsTable.updatedAt] = now
            } > 0
        }

    private suspend fun persistPublishedEnvelope(
        envelope: ShortListingDraftEnvelope,
        offerId: Long,
        lifecycleStatus: ShortListingPublishLifecycleStatus,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            ShortListingDraftsTable.update({
                (ShortListingDraftsTable.id eq envelope.draft.draftId) and
                    (ShortListingDraftsTable.deleted eq false)
            }) { stmt ->
                stmt[stage] = envelope.draft.stage.name
                stmt[publishReadiness] = envelope.draft.publishReadiness
                stmt[ShortListingDraftsTable.publishedOfferId] = offerId
                stmt[ShortListingDraftsTable.lifecycleStatus] = lifecycleStatus.name
                stmt[ShortListingDraftsTable.expiresAtMillis] = envelope.draft.expiresAtMillis
                stmt[revision] = envelope.draft.revision
                stmt[updatedAt] = now
            }
        }
    }

    private suspend fun loadOwnedEnvelope(
        userId: Long,
        draftId: String,
    ): ShortListingDraftEnvelope? =
        DatabaseFactory.dbQuery {
            ShortListingDraftsTable
                .selectAll()
                .apply {
                    andWhere { ShortListingDraftsTable.id eq draftId }
                    andWhere { ShortListingDraftsTable.userId eq userId }
                    andWhere { ShortListingDraftsTable.deleted eq false }
                }
                .singleOrNull()
                ?.toEnvelope()
        }

    private fun ResultRow.toEnvelope(): ShortListingDraftEnvelope {
        val visionResult = this[ShortListingDraftsTable.lastVisionResult]
        val draft = ShortListingDraftSession(
            draftId = this[ShortListingDraftsTable.id],
            sessionId = this[ShortListingDraftsTable.sessionId],
            userId = this[ShortListingDraftsTable.userId].toString(),
            stage = this[ShortListingDraftsTable.stage].toShortListingStage(),
            aiRefreshPending = visionResult.isAiRefreshPending(),
            safeToExit = this[ShortListingDraftsTable.safeToExit],
            createdAtMillis = this[ShortListingDraftsTable.createdAt],
            updatedAtMillis = this[ShortListingDraftsTable.updatedAt],
            revision = this[ShortListingDraftsTable.revision],
            requestedCategoryCode = this[ShortListingDraftsTable.requestedCategoryCode],
            resolvedCategoryCode = this[ShortListingDraftsTable.resolvedCategoryCode],
            candidateCategory = this[ShortListingDraftsTable.candidateCategory],
            confirmedCategoryCode = this[ShortListingDraftsTable.confirmedCategoryCode],
            media = this[ShortListingDraftsTable.media],
            predictedFields = this[ShortListingDraftsTable.predictedFields],
            confirmedUserFields = this[ShortListingDraftsTable.confirmedUserFields],
            missingRequiredFields = this[ShortListingDraftsTable.missingRequiredFields],
            evidenceTasks = this[ShortListingDraftsTable.evidenceTasks],
            publishLocals = this[ShortListingDraftsTable.publishLocals],
            publishReadiness = this[ShortListingDraftsTable.publishReadiness],
            profileGate = this[ShortListingDraftsTable.profileGate],
            identitySignature = this[ShortListingDraftsTable.identitySignature],
            publishedOfferId = this[ShortListingDraftsTable.publishedOfferId]?.toString(),
            lifecycleStatus = this[ShortListingDraftsTable.lifecycleStatus].toShortListingLifecycleStatus(),
            expiresAtMillis = this[ShortListingDraftsTable.expiresAtMillis],
        )
        return ShortListingDraftEnvelope(
            draft = draft,
            visionResult = visionResult,
        )
    }

    private fun ShortListingDraftSession.toPublishPreflight(): ShortListingPublishPreflight =
        ShortListingPublishPreflight(
            draftId = draftId,
            eligible = publishReadiness.ready,
            normalizedPayload = if (publishReadiness.ready) {
                buildNormalizedPayload(
                    resolvedCategoryCode = resolvedCategoryCode ?: confirmedCategoryCode,
                    mergedFields = mergeFields(predictedFields, confirmedUserFields),
                    publishLocals = publishLocals,
                    media = media,
                    spec = null,
                )
            } else {
                null
            },
            blockers = publishReadiness.blockers,
            warnings = publishReadiness.warnings,
            expiresAtMillis = expiresAtMillis,
            identitySignature = identitySignature,
        )

    private fun buildPublishResult(
        draft: ShortListingDraftSession,
        offerId: Long,
    ): ShortListingPublishResult =
        ShortListingPublishResult(
            draftId = draft.draftId,
            publishedOfferId = offerId.toString(),
            status = draft.lifecycleStatus ?: ShortListingPublishLifecycleStatus.PENDING_REVIEW,
            publishedAtMillis = draft.updatedAtMillis,
            expiresAtMillis = draft.expiresAtMillis,
            primaryAction = ShortListingResultAction.OPEN_STATUS,
            secondaryActions = listOf(
                ShortListingResultAction.EDIT,
                ShortListingResultAction.SHARE,
                ShortListingResultAction.DEACTIVATE,
                ShortListingResultAction.RENEW,
            ),
        )

    private fun mergeFields(
        predictedFields: Map<String, ShortListingFieldValue>,
        confirmedFields: Map<String, ShortListingFieldValue>,
    ): Map<String, ShortListingFieldValue> =
        LinkedHashMap<String, ShortListingFieldValue>().apply {
            putAll(predictedFields.mapKeys { it.key.normalizedFieldCode() })
            putAll(confirmedFields.mapKeys { it.key.normalizedFieldCode() })
        }

    private fun sanitizeFieldMap(
        fields: Map<String, ShortListingFieldValue>,
    ): Map<String, ShortListingFieldValue> =
        fields.entries
            .mapNotNull { (rawCode, rawValue) ->
                val code = rawCode.normalizedFieldCode()
                if (code.isEmpty()) return@mapNotNull null
                val sanitized = rawValue.sanitized() ?: return@mapNotNull null
                code to sanitized
            }
            .toMap(LinkedHashMap())

    private fun sanitizePublishLocals(locals: ShortListingPublishLocals?): ShortListingPublishLocals =
        locals?.copy(
            currency = Money.normalizeCurrencyCode(locals.currency),
            city = locals.city?.trim()?.takeIf { it.isNotEmpty() },
            deliveryChannel = normalizeDeliveryChannel(locals.deliveryChannel),
            description = locals.description?.trim()?.takeIf { it.isNotEmpty() },
        ) ?: ShortListingPublishLocals()

    private fun resolveTitle(
        mergedFields: Map<String, ShortListingFieldValue>,
        spec: CatalogCategoryEffectiveSpec?,
        categoryCode: String,
    ): String {
        val explicitTitle = mergedFields["title"]?.primaryValue()
            ?: mergedFields["product_name"]?.primaryValue()
        if (!explicitTitle.isNullOrBlank()) return explicitTitle
        val brand = mergedFields["brand"]?.primaryValue()
        val model = mergedFields["model"]?.primaryValue()
        val parts = listOfNotNull(brand, model).filter { it.isNotBlank() }
        if (parts.isNotEmpty()) return parts.joinToString(" ")
        return spec?.category?.displayTitle() ?: categoryCode
    }

    private fun normalizeDeliveryChannel(value: String?): String? {
        val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when (normalized) {
            "delivery", "pickup", "meeting" -> normalized
            else -> null
        }
    }

    private fun generateId(prefix: String): String =
        "$prefix-${UUID.randomUUID()}"

    private fun normalizeCategoryCode(value: String?): String? =
        stage4ExecutionLayer.normalizeCatalogCode(value)

    private fun String.normalizedFieldCode(): String =
        trim().lowercase(Locale.ROOT)

    private fun ShortListingFieldValue.primaryValue(): String? = when (kind) {
        ShortListingFieldKind.SCALAR -> normalizedValue ?: canonicalValueCode ?: displayValue
        ShortListingFieldKind.MULTI -> values.firstOrNull()?.normalizedValue
            ?: values.firstOrNull()?.canonicalValueCode
            ?: values.firstOrNull()?.displayValue
    }?.trim()?.takeIf { it.isNotEmpty() }

    private fun ShortListingFieldValue.signatureToken(): String = when (kind) {
        ShortListingFieldKind.SCALAR -> listOfNotNull(normalizedValue, canonicalValueCode, displayValue)
            .firstOrNull()
            .orEmpty()
            .trim()
        ShortListingFieldKind.MULTI -> values.joinToString("|") { atom ->
            listOfNotNull(atom.normalizedValue, atom.canonicalValueCode, atom.displayValue)
                .firstOrNull()
                .orEmpty()
                .trim()
        }
    }

    private fun ShortListingFieldValue.sanitized(): ShortListingFieldValue? {
        val sanitized = when (kind) {
            ShortListingFieldKind.SCALAR -> copy(
                displayValue = displayValue?.trim()?.takeIf { it.isNotEmpty() },
                normalizedValue = normalizedValue?.trim()?.takeIf { it.isNotEmpty() },
                canonicalValueCode = canonicalValueCode?.trim()?.takeIf { it.isNotEmpty() },
            )
            ShortListingFieldKind.MULTI -> copy(
                values = values.mapNotNull { atom ->
                    val display = atom.displayValue.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    ShortListingFieldAtom(
                        displayValue = display,
                        normalizedValue = atom.normalizedValue?.trim()?.takeIf { it.isNotEmpty() },
                        canonicalValueCode = atom.canonicalValueCode?.trim()?.takeIf { it.isNotEmpty() },
                    )
                },
                displayValue = null,
                normalizedValue = null,
                canonicalValueCode = null,
            )
        }
        return sanitized.takeIf { it.isMeaningful() }
    }

    private fun Map<String, ShortListingFieldValue>.toOfferTypedAttributes(): Map<String, TypedAttributeValue> =
        entries
            .mapNotNull { (rawCode, value) ->
                if (rawCode in ignoredPayloadFieldCodes) return@mapNotNull null
                if (value.kind == ShortListingFieldKind.MULTI || !value.isMeaningful()) return@mapNotNull null
                value.toTypedAttributeValue()?.let { rawCode.normalizedFieldCode() to it }
            }
            .toMap(LinkedHashMap())

    private fun ShortListingFieldValue.toTypedAttributeValue(): TypedAttributeValue? {
        val raw = primaryValue() ?: return null
        return when (valueType) {
            ShortListingValueType.NUMBER -> raw.replace(',', '.').toDoubleOrNull()?.let { TypedAttributeValue.Number(it) }
            ShortListingValueType.BOOLEAN -> when (raw.lowercase(Locale.ROOT)) {
                "true", "1", "yes", "y", "да" -> TypedAttributeValue.Bool(true)
                "false", "0", "no", "n", "нет" -> TypedAttributeValue.Bool(false)
                else -> null
            }
            ShortListingValueType.ENUM, ShortListingValueType.STRING -> TypedAttributeValue.Text(raw)
        }
    }

    private fun typedValueToField(value: TypedAttributeValue): ShortListingFieldValue = when (value) {
        is TypedAttributeValue.Number -> ShortListingFieldValue(
            valueType = ShortListingValueType.NUMBER,
            displayValue = value.asRawString(),
            normalizedValue = value.asRawString(),
        )
        is TypedAttributeValue.Bool -> ShortListingFieldValue(
            valueType = ShortListingValueType.BOOLEAN,
            displayValue = value.asRawString(),
            normalizedValue = value.asRawString(),
        )
        is TypedAttributeValue.Text -> scalarField(value.value)
    }

    private fun scalarField(
        value: String,
        valueType: ShortListingValueType = ShortListingValueType.STRING,
    ): ShortListingFieldValue = ShortListingFieldValue(
        kind = ShortListingFieldKind.SCALAR,
        valueType = valueType,
        displayValue = value,
        normalizedValue = value.trim().takeIf { it.isNotEmpty() },
    )

    private fun com.example.shoppingassistant.domain.model.NormalizedQuery?.toPredictedFieldMap(): Map<String, ShortListingFieldValue> {
        if (this == null) return emptyMap()
        val mapped = LinkedHashMap<String, ShortListingFieldValue>()
        if (brand.isNotBlank()) mapped["brand"] = scalarField(brand)
        if (model.isNotBlank()) mapped["model"] = scalarField(model)
        rawAttributes().forEach { (rawCode, rawValue) ->
            val code = rawCode.normalizedFieldCode()
            if (code.isEmpty() || rawValue.isBlank()) return@forEach
            val normalizedValue = stage4ExecutionLayer.normalizeValueForSearch(code, rawValue) ?: rawValue.trim()
            val valueType = when {
                normalizedValue == "true" || normalizedValue == "false" -> ShortListingValueType.BOOLEAN
                normalizedValue.toDoubleOrNull() != null -> ShortListingValueType.NUMBER
                else -> ShortListingValueType.STRING
            }
            mapped[code] = ShortListingFieldValue(
                valueType = valueType,
                displayValue = rawValue.trim(),
                normalizedValue = normalizedValue,
            )
        }
        return mapped
    }

    private fun VisionNormalizeResult.toContract(
        draftId: String,
        stage: ShortListingStage,
        candidateCategory: ShortListingCategoryCandidate?,
        predictedFields: Map<String, ShortListingFieldValue>,
        missingRequiredFields: List<String>,
        evidenceTasks: List<ShortListingEvidenceTask>,
        delta: ShortListingVisionDelta,
    ): ShortListingVisionResult =
        ShortListingVisionResult(
            attemptId = generateId("vision-attempt"),
            serverRequestId = generateId("vision-job"),
            draftId = draftId,
            status = stage,
            candidateCategory = candidateCategory,
            categoryCandidates = categoryCandidates.map { it.toContractCategoryCandidate() },
            rawExtraction = rawExtraction?.toContractRawExtraction(),
            bindOutcome = bindOutcome?.toContractBindOutcome(),
            predictedFields = predictedFields,
            missingRequiredFields = missingRequiredFields,
            evidenceTasks = evidenceTasks,
            delta = delta,
            nextAction = nextAction?.name,
            warnings = warnings,
            errors = errors,
        )

    private fun VisionCategoryCandidate.toContractCategoryCandidate(): ShortListingCategoryCandidate =
        ShortListingCategoryCandidate(
            code = code,
            title = title ?: code,
            score = score,
            confidenceBand = score.toConfidenceBand(),
        )

    private fun com.example.shoppingassistant.domain.vision.VisionRawExtraction.toContractRawExtraction():
        ShortListingVisionRawExtraction =
        ShortListingVisionRawExtraction(
            categoryHint = categoryHint,
            brand = brand,
            model = model,
            title = title,
            reasonCodes = reasonCodes,
            attributes = attributes.map { attribute ->
                ShortListingVisionAttributeCandidate(
                    code = attribute.code,
                    kind = attribute.kind,
                    text = attribute.text,
                    number = attribute.number,
                    bool = attribute.bool,
                    confidence = attribute.confidence,
                )
            },
        )

    private fun com.example.shoppingassistant.domain.vision.VisionBindOutcome.toContractBindOutcome():
        ShortListingVisionBindOutcome =
        ShortListingVisionBindOutcome(
            rawCategoryHint = rawCategoryHint,
            resolvedCategoryCode = resolvedCategoryCode,
            acceptedAttributeCodes = acceptedAttributeCodes,
            unresolvedAttributeCodes = unresolvedAttributeCodes,
            missingRequiredKeys = missingRequiredKeys,
        )

    private fun Float?.toConfidenceBand(): ShortListingConfidenceBand = when {
        this == null -> ShortListingConfidenceBand.LOW
        this >= 0.8f -> ShortListingConfidenceBand.HIGH
        this >= 0.5f -> ShortListingConfidenceBand.MEDIUM
        else -> ShortListingConfidenceBand.LOW
    }

    private fun RequiredIfRule.matches(
        mergedFields: Map<String, ShortListingFieldValue>,
    ): Boolean = whenAll.all { condition ->
        val currentValue = mergedFields[condition.attributeCode.normalizedFieldCode()]?.primaryValue()
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return@all false
        when (condition.op) {
            com.example.shoppingassistant.domain.catalog.AttributeConditionOp.EQUALS_ANY ->
                condition.values.any { value -> currentValue == value.trim().lowercase(Locale.ROOT) }
            com.example.shoppingassistant.domain.catalog.AttributeConditionOp.STARTS_WITH_ANY ->
                condition.values.any { value -> currentValue.startsWith(value.trim().lowercase(Locale.ROOT)) }
        }
    }

    private fun String.toShortListingStage(): ShortListingStage =
        runCatching { ShortListingStage.valueOf(this) }.getOrDefault(ShortListingStage.READY_FOR_REVIEW)

    private fun String?.toShortListingLifecycleStatus(): ShortListingPublishLifecycleStatus? =
        this?.let { runCatching { ShortListingPublishLifecycleStatus.valueOf(it) }.getOrNull() }

    private fun VisionNextAction.toPhotoRoleOrNull(): ShortListingPhotoRole? = when (this) {
        VisionNextAction.ADD_TECH_PHOTO -> ShortListingPhotoRole.TECH_1
        VisionNextAction.ADD_BACK_PHOTO -> ShortListingPhotoRole.BACK
        VisionNextAction.RETAKE_CLEAR_TEXT -> ShortListingPhotoRole.TECH_1
        VisionNextAction.RETAKE_PHOTO -> ShortListingPhotoRole.FRONT
    }

    private fun VisionNextAction.toHeadline(): String = when (this) {
        VisionNextAction.ADD_TECH_PHOTO -> "Добавьте фото технической информации"
        VisionNextAction.ADD_BACK_PHOTO -> "Добавьте фото обратной стороны"
        VisionNextAction.RETAKE_CLEAR_TEXT -> "Переснимите фото с читаемым текстом"
        VisionNextAction.RETAKE_PHOTO -> "Переснимите основной кадр"
    }

    private fun VisionNextAction.toExampleHint(): String = when (this) {
        VisionNextAction.ADD_TECH_PHOTO -> "Сфотографируйте шильдик, наклейку или серийный блок."
        VisionNextAction.ADD_BACK_PHOTO -> "Снимите заднюю часть товара целиком."
        VisionNextAction.RETAKE_CLEAR_TEXT -> "Текст должен быть резким и без бликов."
        VisionNextAction.RETAKE_PHOTO -> "Сделайте кадр при хорошем освещении и без обрезки."
    }

    private fun ShortListingPhotoRole.toVisionRole(): VisionPhotoRole = when (this) {
        ShortListingPhotoRole.FRONT -> VisionPhotoRole.FRONT
        ShortListingPhotoRole.BACK -> VisionPhotoRole.BACK
        ShortListingPhotoRole.LEFT -> VisionPhotoRole.LEFT
        ShortListingPhotoRole.RIGHT -> VisionPhotoRole.RIGHT
        ShortListingPhotoRole.TOP -> VisionPhotoRole.TOP
        ShortListingPhotoRole.BOTTOM -> VisionPhotoRole.BOTTOM
        ShortListingPhotoRole.TECH_1 -> VisionPhotoRole.TECH_1
        ShortListingPhotoRole.TECH_2 -> VisionPhotoRole.TECH_2
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun <T> ResultRow.tryGet(column: Column<T>): T? =
        runCatching { this[column] }.getOrNull()

    private data class DraftAnalysis(
        val resolvedCategoryCode: String?,
        val candidateCategory: ShortListingCategoryCandidate?,
        val predictedFields: Map<String, ShortListingFieldValue>,
        val missingRequiredFields: List<String>,
        val evidenceTasks: List<ShortListingEvidenceTask>,
        val publishReadiness: ShortListingPublishReadiness,
        val profileGate: ShortListingProfileGate,
        val identitySignature: ShortListingIdentitySignature?,
        val preflight: ShortListingPublishPreflight,
        val stage: ShortListingStage,
        val visionResult: ShortListingVisionResult?,
    )

    private companion object {
        const val MIN_TTL_DAYS = 1
        const val MAX_TTL_DAYS = 90
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        val ignoredPayloadFieldCodes = setOf("title", "product_name")
    }
}

class ShortListingNotFoundException(
    draftId: String,
) : IllegalStateException("SHORT_LISTING_DRAFT_NOT_FOUND:$draftId")

class ShortListingValidationException(
    code: String,
) : IllegalArgumentException(code)
