package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.localoffer.LocalOfferCategoryCandidate
import com.example.shoppingassistant.domain.localoffer.LocalOfferCommercials
import com.example.shoppingassistant.domain.localoffer.LocalOfferConfidenceBand
import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftIds
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceBlockingLevel
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceStatus
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceTask
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldAtom
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldKind
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldValue
import com.example.shoppingassistant.domain.localoffer.LocalOfferIncomingPhoto
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssue
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssueSeverity
import com.example.shoppingassistant.domain.localoffer.LocalOfferMediaReceipt
import com.example.shoppingassistant.domain.localoffer.LocalOfferModerationDecision
import com.example.shoppingassistant.domain.localoffer.LocalOfferNode
import com.example.shoppingassistant.domain.localoffer.LocalOfferPhotoRole
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublicationState
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionAttributeCandidate
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionBindOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionRawExtraction
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionReview
import com.example.shoppingassistant.domain.localoffer.LocalOfferValueType
import com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftEnvelope
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftSession
import com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceBlockingLevel
import com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceStatus
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldAtom
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldKind
import com.example.shoppingassistant.domain.shortlisting.ShortListingFieldValue
import com.example.shoppingassistant.domain.shortlisting.ShortListingIncomingPhoto
import com.example.shoppingassistant.domain.shortlisting.ShortListingMediaReceipt
import com.example.shoppingassistant.domain.shortlisting.ShortListingPhotoRole
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishBlocker
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLifecycleStatus
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLocals
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishPreflight
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishWarning
import com.example.shoppingassistant.domain.shortlisting.ShortListingReviewUpdateRequest
import com.example.shoppingassistant.domain.shortlisting.ShortListingStage
import com.example.shoppingassistant.domain.shortlisting.ShortListingValueType
import com.example.shoppingassistant.server.shortlisting.ShortListingBackendService

data class LocalOfferRuntimeDraft(
    val draft: LocalOfferDraftSession,
    val runId: String? = null,
    val visionReview: LocalOfferVisionReview? = null,
)

data class LocalOfferRuntimeNormalizedPayload(
    val title: String,
)

data class LocalOfferRuntimePreflight(
    val draftId: String,
    val publishAllowed: Boolean,
    val normalizedPayload: LocalOfferRuntimeNormalizedPayload? = null,
    val blockingIssues: List<LocalOfferIssue> = emptyList(),
    val warnings: List<LocalOfferIssue> = emptyList(),
    val moderationDecision: LocalOfferModerationDecision = LocalOfferModerationDecision.HOLD,
)

sealed interface LocalOfferRuntimePublishAttempt {
    data class Published(
        val offerId: String,
        val publicationState: LocalOfferPublicationState,
        val publishedAtMillis: Long,
    ) : LocalOfferRuntimePublishAttempt

    data class Blocked(
        val blockingIssues: List<LocalOfferIssue> = emptyList(),
    ) : LocalOfferRuntimePublishAttempt
}

interface LocalOfferDraftRuntime {
    suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession>
    suspend fun createDraft(userId: Long, request: LocalOfferCreateDraftRequest): LocalOfferRuntimeDraft
    suspend fun getDraft(userId: Long, draftId: String): LocalOfferRuntimeDraft?
    suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferRuntimeDraft
    suspend fun getPublishPreflight(userId: Long, draftId: String): LocalOfferRuntimePreflight
    suspend fun publishDraft(userId: Long, draftId: String): LocalOfferRuntimePublishAttempt
    fun toStorageDraftId(canonicalDraftId: String): String
    fun toPublicDraftId(storageDraftId: String): String
}

class LocalOfferDraftRuntimeAdapter(
    private val shortListingBackendService: ShortListingBackendService,
) : LocalOfferDraftRuntime {

    override suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession> =
        shortListingBackendService.listDrafts(userId).map { draft ->
            draft.toLocalOfferDraft()
        }

    override suspend fun createDraft(
        userId: Long,
        request: LocalOfferCreateDraftRequest,
    ): LocalOfferRuntimeDraft =
        shortListingBackendService.createDraft(
            userId = userId,
            request = ShortListingCreateDraftRequest(
                photos = request.photos.map { photo -> photo.toShortListingIncomingPhoto() },
                requestedCategoryCode = request.requestedCategoryCode,
                locale = request.locale,
                publishLocals = request.commercials.toShortListingPublishLocals(),
                confirmedFields = request.confirmedFields.mapValues { (_, value) ->
                    value.toShortListingFieldValue()
                },
            ),
        ).toRuntimeDraft()

    override suspend fun getDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimeDraft? =
        shortListingBackendService.getDraft(userId, toStorageDraftId(draftId))
            ?.toRuntimeDraft()

    override suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferRuntimeDraft =
        shortListingBackendService.updateDraftReview(
            userId = userId,
            draftId = toStorageDraftId(draftId),
            request = ShortListingReviewUpdateRequest(
                confirmedCategoryCode = request.confirmedCategoryCode,
                confirmedFields = request.confirmedFields.mapValues { (_, value) ->
                    value.toShortListingFieldValue()
                },
                clearedFieldCodes = request.clearedFieldCodes,
                publishLocals = request.commercials?.toShortListingPublishLocals(),
                appendPhotos = request.appendPhotos.map { photo -> photo.toShortListingIncomingPhoto() },
            ),
        ).toRuntimeDraft()

    override suspend fun getPublishPreflight(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimePreflight =
        shortListingBackendService.getPublishPreflight(userId, toStorageDraftId(draftId))
            .toRuntimePreflight()

    override suspend fun publishDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimePublishAttempt =
        when (val result = shortListingBackendService.publishDraft(userId, toStorageDraftId(draftId))) {
            is ShortListingPublishAttemptResult.Published -> LocalOfferRuntimePublishAttempt.Published(
                offerId = result.result.publishedOfferId,
                publicationState = result.result.status.toLocalPublicationState(),
                publishedAtMillis = result.result.publishedAtMillis,
            )

            is ShortListingPublishAttemptResult.Blocked -> LocalOfferRuntimePublishAttempt.Blocked(
                blockingIssues = result.preflight.blockers.map(::mapBlocker),
            )
        }

    override fun toStorageDraftId(canonicalDraftId: String): String =
        LocalOfferDraftIds.toStorageDraftId(canonicalDraftId)

    override fun toPublicDraftId(storageDraftId: String): String =
        LocalOfferDraftIds.migrateLegacyToCanonicalOrNull(storageDraftId) ?: storageDraftId

    private fun ShortListingDraftEnvelope.toRuntimeDraft(): LocalOfferRuntimeDraft =
        LocalOfferRuntimeDraft(
            draft = draft.toLocalOfferDraft(),
            runId = visionResult?.attemptId,
            visionReview = visionResult?.toLocalOfferVisionReview(),
        )

    private fun ShortListingDraftSession.toLocalOfferDraft(): LocalOfferDraftSession =
        LocalOfferDraftSession(
            draftId = toPublicDraftId(draftId),
            sessionId = sessionId,
            stage = stage.toLocalDraftStage(),
            aiRefreshPending = aiRefreshPending,
            safeToExit = safeToExit,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            revision = revision,
            requestedCategoryCode = requestedCategoryCode,
            resolvedCategoryCode = resolvedCategoryCode,
            candidateCategory = candidateCategory?.toLocalOfferCategoryCandidate(),
            confirmedCategoryCode = confirmedCategoryCode,
            media = media.map { receipt -> receipt.toLocalOfferMediaReceipt() },
            predictedFields = predictedFields.mapValues { (_, value) -> value.toLocalOfferFieldValue() },
            confirmedUserFields = confirmedUserFields.mapValues { (_, value) -> value.toLocalOfferFieldValue() },
            missingRequiredFields = missingRequiredFields,
            evidenceTasks = evidenceTasks.map { task -> task.toLocalOfferEvidenceTask() },
            commercials = publishLocals.toLocalOfferCommercials(),
            issues = publishReadiness.blockers.map(::mapBlocker) + publishReadiness.warnings.map(::mapWarning),
            effectiveSpecVersion = CatalogDataVersion.current,
            publishedOfferId = publishedOfferId,
            publicationState = lifecycleStatus?.toLocalPublicationState(),
        )

    private fun ShortListingPublishPreflight.toRuntimePreflight(): LocalOfferRuntimePreflight {
        val blockingIssues = blockers.map(::mapBlocker)
        return LocalOfferRuntimePreflight(
            draftId = toPublicDraftId(draftId),
            publishAllowed = eligible && normalizedPayload != null && blockingIssues.isEmpty(),
            normalizedPayload = normalizedPayload?.let { payload ->
                LocalOfferRuntimeNormalizedPayload(title = payload.title)
            },
            blockingIssues = blockingIssues,
            warnings = warnings.map(::mapWarning),
            moderationDecision = if (eligible && normalizedPayload != null && blockingIssues.isEmpty()) {
                LocalOfferModerationDecision.REVIEW_REQUIRED
            } else {
                LocalOfferModerationDecision.HOLD
            },
        )
    }

    private fun com.example.shoppingassistant.domain.shortlisting.ShortListingCategoryCandidate.toLocalOfferCategoryCandidate():
        LocalOfferCategoryCandidate =
        LocalOfferCategoryCandidate(
            code = code,
            title = title,
            score = score,
            confidenceBand = when (confidenceBand.name) {
                "HIGH" -> LocalOfferConfidenceBand.HIGH
                "MEDIUM" -> LocalOfferConfidenceBand.MEDIUM
                else -> LocalOfferConfidenceBand.LOW
            },
        )

    private fun ShortListingMediaReceipt.toLocalOfferMediaReceipt(): LocalOfferMediaReceipt =
        LocalOfferMediaReceipt(
            mediaId = mediaId,
            role = role.toLocalOfferPhotoRole(),
            storageUrl = storageUrl,
            contentType = contentType,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            createdAtMillis = createdAtMillis,
        )

    private fun com.example.shoppingassistant.domain.shortlisting.ShortListingFieldValue.toLocalOfferFieldValue():
        LocalOfferFieldValue =
        LocalOfferFieldValue(
            kind = when (kind) {
                ShortListingFieldKind.MULTI -> LocalOfferFieldKind.MULTI
                ShortListingFieldKind.SCALAR -> LocalOfferFieldKind.SCALAR
            },
            valueType = when (valueType) {
                ShortListingValueType.ENUM -> LocalOfferValueType.ENUM
                ShortListingValueType.NUMBER -> LocalOfferValueType.NUMBER
                ShortListingValueType.BOOLEAN -> LocalOfferValueType.BOOLEAN
                ShortListingValueType.STRING -> LocalOfferValueType.STRING
            },
            displayValue = displayValue,
            normalizedValue = normalizedValue,
            canonicalValueCode = canonicalValueCode,
            values = values.map { atom ->
                LocalOfferFieldAtom(
                    displayValue = atom.displayValue,
                    normalizedValue = atom.normalizedValue,
                    canonicalValueCode = atom.canonicalValueCode,
                )
            },
        )

    private fun com.example.shoppingassistant.domain.shortlisting.ShortListingEvidenceTask.toLocalOfferEvidenceTask():
        LocalOfferEvidenceTask =
        LocalOfferEvidenceTask(
            taskId = taskId,
            reasonCode = reasonCode,
            targetFieldCodes = targetFieldCodes,
            photoRole = photoRole?.toLocalOfferPhotoRole(),
            headline = headline,
            explanation = explanation,
            exampleHint = exampleHint,
            blockingLevel = when (blockingLevel) {
                ShortListingEvidenceBlockingLevel.PUBLISH_BLOCKING -> LocalOfferEvidenceBlockingLevel.PUBLISH_BLOCKING
                ShortListingEvidenceBlockingLevel.SOFT -> LocalOfferEvidenceBlockingLevel.SOFT
            },
            canSkip = canSkip,
            status = when (status) {
                ShortListingEvidenceStatus.OPEN -> LocalOfferEvidenceStatus.OPEN
                ShortListingEvidenceStatus.UPLOADING -> LocalOfferEvidenceStatus.UPLOADING
                ShortListingEvidenceStatus.RERUNNING -> LocalOfferEvidenceStatus.RERUNNING
                ShortListingEvidenceStatus.RESOLVED -> LocalOfferEvidenceStatus.RESOLVED
                ShortListingEvidenceStatus.UNRESOLVED -> LocalOfferEvidenceStatus.UNRESOLVED
                ShortListingEvidenceStatus.SKIPPED -> LocalOfferEvidenceStatus.SKIPPED
            },
        )

    private fun com.example.shoppingassistant.domain.shortlisting.ShortListingVisionResult.toLocalOfferVisionReview():
        LocalOfferVisionReview =
        LocalOfferVisionReview(
            runId = attemptId,
            rawExtraction = rawExtraction?.let { raw ->
                LocalOfferVisionRawExtraction(
                    categoryHint = raw.categoryHint,
                    brand = raw.brand,
                    model = raw.model,
                    title = raw.title,
                    reasonCodes = raw.reasonCodes,
                    attributes = raw.attributes.map { attribute ->
                        LocalOfferVisionAttributeCandidate(
                            code = attribute.code,
                            kind = attribute.kind,
                            text = attribute.text,
                            number = attribute.number,
                            bool = attribute.bool,
                            confidence = attribute.confidence,
                        )
                    },
                )
            },
            bindOutcome = bindOutcome?.let { bind ->
                LocalOfferVisionBindOutcome(
                    rawCategoryHint = bind.rawCategoryHint,
                    resolvedCategoryCode = bind.resolvedCategoryCode,
                    acceptedAttributeCodes = bind.acceptedAttributeCodes,
                    unresolvedAttributeCodes = bind.unresolvedAttributeCodes,
                    missingRequiredKeys = bind.missingRequiredKeys,
                )
            },
            nextAction = nextAction,
            warnings = warnings,
            errors = errors,
        )

    private fun LocalOfferIncomingPhoto.toShortListingIncomingPhoto(): ShortListingIncomingPhoto =
        ShortListingIncomingPhoto(
            role = role.toShortListingPhotoRole(),
            filename = filename,
            contentType = contentType,
            dataBase64 = dataBase64,
        )

    private fun LocalOfferPhotoRole.toShortListingPhotoRole(): ShortListingPhotoRole = when (this) {
        LocalOfferPhotoRole.FRONT -> ShortListingPhotoRole.FRONT
        LocalOfferPhotoRole.BACK -> ShortListingPhotoRole.BACK
        LocalOfferPhotoRole.LEFT -> ShortListingPhotoRole.LEFT
        LocalOfferPhotoRole.RIGHT -> ShortListingPhotoRole.RIGHT
        LocalOfferPhotoRole.TOP -> ShortListingPhotoRole.TOP
        LocalOfferPhotoRole.BOTTOM -> ShortListingPhotoRole.BOTTOM
        LocalOfferPhotoRole.TECH_1 -> ShortListingPhotoRole.TECH_1
        LocalOfferPhotoRole.TECH_2 -> ShortListingPhotoRole.TECH_2
    }

    private fun ShortListingPhotoRole.toLocalOfferPhotoRole(): LocalOfferPhotoRole = when (this) {
        ShortListingPhotoRole.FRONT -> LocalOfferPhotoRole.FRONT
        ShortListingPhotoRole.BACK -> LocalOfferPhotoRole.BACK
        ShortListingPhotoRole.LEFT -> LocalOfferPhotoRole.LEFT
        ShortListingPhotoRole.RIGHT -> LocalOfferPhotoRole.RIGHT
        ShortListingPhotoRole.TOP -> LocalOfferPhotoRole.TOP
        ShortListingPhotoRole.BOTTOM -> LocalOfferPhotoRole.BOTTOM
        ShortListingPhotoRole.TECH_1 -> LocalOfferPhotoRole.TECH_1
        ShortListingPhotoRole.TECH_2 -> LocalOfferPhotoRole.TECH_2
    }

    private fun LocalOfferCommercials.toShortListingPublishLocals(): ShortListingPublishLocals =
        ShortListingPublishLocals(
            priceMajor = priceMajor,
            currency = currency,
            ttlPresetDays = ttlPresetDays,
            city = city,
            deliveryChannel = deliveryChannel,
            description = description,
        )

    private fun ShortListingPublishLocals.toLocalOfferCommercials(): LocalOfferCommercials =
        LocalOfferCommercials(
            priceMajor = priceMajor,
            currency = currency,
            ttlPresetDays = ttlPresetDays,
            city = city,
            deliveryChannel = deliveryChannel,
            description = description,
        )

    private fun LocalOfferFieldValue.toShortListingFieldValue(): ShortListingFieldValue =
        ShortListingFieldValue(
            kind = when (kind) {
                LocalOfferFieldKind.MULTI -> ShortListingFieldKind.MULTI
                LocalOfferFieldKind.SCALAR -> ShortListingFieldKind.SCALAR
            },
            valueType = when (valueType) {
                LocalOfferValueType.ENUM -> ShortListingValueType.ENUM
                LocalOfferValueType.NUMBER -> ShortListingValueType.NUMBER
                LocalOfferValueType.BOOLEAN -> ShortListingValueType.BOOLEAN
                LocalOfferValueType.STRING -> ShortListingValueType.STRING
            },
            displayValue = displayValue,
            normalizedValue = normalizedValue,
            canonicalValueCode = canonicalValueCode,
            values = values.map { atom ->
                ShortListingFieldAtom(
                    displayValue = atom.displayValue,
                    normalizedValue = atom.normalizedValue,
                    canonicalValueCode = atom.canonicalValueCode,
                )
            },
        )

    private fun ShortListingStage.toLocalDraftStage(): LocalOfferDraftStage = when (this) {
        ShortListingStage.DRAFT_CREATED,
        ShortListingStage.UPLOADING,
        ShortListingStage.SERVER_RECEIVED,
        ShortListingStage.ANALYZING,
        ShortListingStage.RERUN_IN_PROGRESS,
        ShortListingStage.READY_FOR_REVIEW,
            -> LocalOfferDraftStage.REVIEW_READY

        ShortListingStage.NEEDS_MORE_EVIDENCE -> LocalOfferDraftStage.NEEDS_ENRICHMENT

        ShortListingStage.READY_FOR_PUBLISH,
        ShortListingStage.PUBLISH_CONFIRMING,
        ShortListingStage.PUBLISH_FAILED,
            -> LocalOfferDraftStage.PREVIEW_READY

        ShortListingStage.PUBLISHED -> LocalOfferDraftStage.PUBLISHED
    }

    private fun ShortListingPublishLifecycleStatus.toLocalPublicationState(): LocalOfferPublicationState = when (this) {
        ShortListingPublishLifecycleStatus.PENDING_REVIEW,
        ShortListingPublishLifecycleStatus.PENDING_MEDIA,
            -> LocalOfferPublicationState.PENDING_REVIEW

        ShortListingPublishLifecycleStatus.LIVE -> LocalOfferPublicationState.LIVE
        ShortListingPublishLifecycleStatus.BLOCKED -> LocalOfferPublicationState.REJECTED
        ShortListingPublishLifecycleStatus.EXPIRED,
        ShortListingPublishLifecycleStatus.DEACTIVATED,
            -> LocalOfferPublicationState.REMOVED
    }

    private fun mapBlocker(blocker: ShortListingPublishBlocker): LocalOfferIssue = when (blocker.code) {
        "CATEGORY_CONFIRMATION_REQUIRED" -> LocalOfferIssue(
            issueCode = "ai_category_unresolved",
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.AI_NORMALIZATION_RUN,
            targetNode = LocalOfferNode.DRAFT_REVIEW,
            fieldCodes = blocker.fieldCodes,
        )

        "CATEGORY_UNSUPPORTED" -> LocalOfferIssue(
            issueCode = "ai_unsupported_category",
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.AI_NORMALIZATION_RUN,
            targetNode = LocalOfferNode.BLOCKED_STATE,
            fieldCodes = blocker.fieldCodes,
        )

        "PRICE_REQUIRED",
        "PRICE_INVALID",
        "CURRENCY_REQUIRED",
        "TTL_REQUIRED",
        "TTL_INVALID",
        "DELIVERY_CHANNEL_INVALID",
        "DESCRIPTION_REQUIRED",
            -> LocalOfferIssue(
            issueCode = when (blocker.code) {
                "PRICE_REQUIRED" -> "price_required"
                "PRICE_INVALID" -> "price_invalid"
                "CURRENCY_REQUIRED" -> "currency_required"
                "TTL_REQUIRED" -> "ttl_required"
                "TTL_INVALID" -> "ttl_invalid"
                "DELIVERY_CHANNEL_INVALID" -> "delivery_invalid"
                else -> "description_required"
            },
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.PRICE_AND_TERMS,
            targetNode = LocalOfferNode.DRAFT_REVIEW,
            fieldCodes = blocker.fieldCodes,
        )

        "MISSING_REQUIRED_FIELDS",
        "MISSING_REQUIRED_IF_FIELDS",
        "EVIDENCE_REQUIRED",
        "TITLE_REQUIRED",
        "MULTI_VALUED_FIELDS_UNSUPPORTED",
            -> LocalOfferIssue(
            issueCode = when (blocker.code) {
                "MISSING_REQUIRED_FIELDS" -> "required_fields_missing"
                "MISSING_REQUIRED_IF_FIELDS" -> "conditional_fields_missing"
                "EVIDENCE_REQUIRED" -> "evidence_required"
                "TITLE_REQUIRED" -> "title_required"
                else -> "offer_unsupported_terms"
            },
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.DRAFT_REVIEW,
            targetNode = when (blocker.code) {
                "MULTI_VALUED_FIELDS_UNSUPPORTED" -> LocalOfferNode.BLOCKED_STATE
                else -> LocalOfferNode.DRAFT_REVIEW
            },
            fieldCodes = blocker.fieldCodes,
        )

        "MEDIA_REQUIRED" -> LocalOfferIssue(
            issueCode = "media_required",
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.PRIMARY_PHOTO_CAPTURE,
            targetNode = LocalOfferNode.PRIMARY_PHOTO_CAPTURE,
            fieldCodes = blocker.fieldCodes,
        )

        "PROFILE_INCOMPLETE" -> LocalOfferIssue(
            issueCode = "publish_profile_incomplete",
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            targetNode = LocalOfferNode.BLOCKED_STATE,
            fieldCodes = blocker.fieldCodes,
        )

        else -> LocalOfferIssue(
            issueCode = blocker.code.lowercase(),
            severity = LocalOfferIssueSeverity.ERROR,
            message = blocker.message,
            ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            targetNode = LocalOfferNode.BLOCKED_STATE,
            fieldCodes = blocker.fieldCodes,
        )
    }

    private fun mapWarning(warning: ShortListingPublishWarning): LocalOfferIssue = when (warning.code) {
        "SAME_USER_SIMILAR_ACTIVE" -> LocalOfferIssue(
            issueCode = "dedup_possible_duplicate",
            severity = LocalOfferIssueSeverity.WARNING,
            message = warning.message,
            ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            targetNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            fieldCodes = warning.fieldCodes,
        )

        else -> LocalOfferIssue(
            issueCode = warning.code.lowercase(),
            severity = LocalOfferIssueSeverity.WARNING,
            message = warning.message,
            ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            targetNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            fieldCodes = warning.fieldCodes,
        )
    }
}
