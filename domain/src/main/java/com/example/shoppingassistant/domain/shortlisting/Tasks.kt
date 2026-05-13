package com.example.shoppingassistant.domain.shortlisting

import kotlinx.serialization.Serializable

object ShortListingHttpContractPaths {
    const val base = "/api/shortlisting"
    const val drafts = "$base/drafts"
    const val draftById = "$drafts/{draftId}"
    const val draftReview = "$draftById/review"
    const val draftPreflight = "$draftById/preflight"
    const val draftPublish = "$draftById/publish"
}

object ShortListingVisionRuntimeFlags {
    const val AI_REFRESH_PENDING = "AI_REFRESH_PENDING"
    const val AI_REFRESH_FAILED = "AI_REFRESH_FAILED"
}

@Serializable
enum class ShortListingStage {
    DRAFT_CREATED,
    UPLOADING,
    SERVER_RECEIVED,
    ANALYZING,
    READY_FOR_REVIEW,
    NEEDS_MORE_EVIDENCE,
    RERUN_IN_PROGRESS,
    READY_FOR_PUBLISH,
    PUBLISH_CONFIRMING,
    PUBLISH_FAILED,
    PUBLISHED,
}

@Serializable
enum class ShortListingPhotoRole {
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    TECH_1,
    TECH_2,
}

@Serializable
enum class ShortListingFieldKind {
    SCALAR,
    MULTI,
}

@Serializable
enum class ShortListingValueType {
    STRING,
    ENUM,
    NUMBER,
    BOOLEAN,
}

@Serializable
data class ShortListingFieldAtom(
    val displayValue: String,
    val normalizedValue: String? = null,
    val canonicalValueCode: String? = null,
)

@Serializable
data class ShortListingFieldValue(
    val kind: ShortListingFieldKind = ShortListingFieldKind.SCALAR,
    val valueType: ShortListingValueType = ShortListingValueType.STRING,
    val displayValue: String? = null,
    val normalizedValue: String? = null,
    val canonicalValueCode: String? = null,
    val values: List<ShortListingFieldAtom> = emptyList(),
) {
    fun isMeaningful(): Boolean = when (kind) {
        ShortListingFieldKind.SCALAR -> !displayValue.isNullOrBlank()
        ShortListingFieldKind.MULTI -> values.any { it.displayValue.isNotBlank() }
    }
}

@Serializable
data class ShortListingIncomingPhoto(
    val role: ShortListingPhotoRole,
    val filename: String,
    val contentType: String? = null,
    val dataBase64: String,
)

@Serializable
data class ShortListingMediaReceipt(
    val mediaId: String,
    val role: ShortListingPhotoRole,
    val storageUrl: String,
    val contentType: String? = null,
    val sha256: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
)

@Serializable
enum class ShortListingConfidenceBand {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class ShortListingCategoryCandidate(
    val code: String,
    val title: String? = null,
    val score: Float? = null,
    val confidenceBand: ShortListingConfidenceBand = ShortListingConfidenceBand.LOW,
)

@Serializable
data class ShortListingVisionAttributeCandidate(
    val code: String,
    val kind: String? = null,
    val text: String? = null,
    val number: Double? = null,
    val bool: Boolean? = null,
    val confidence: Float? = null,
)

@Serializable
data class ShortListingVisionRawExtraction(
    val categoryHint: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val title: String? = null,
    val reasonCodes: List<String> = emptyList(),
    val attributes: List<ShortListingVisionAttributeCandidate> = emptyList(),
)

@Serializable
data class ShortListingVisionBindOutcome(
    val rawCategoryHint: String? = null,
    val resolvedCategoryCode: String? = null,
    val acceptedAttributeCodes: List<String> = emptyList(),
    val unresolvedAttributeCodes: List<String> = emptyList(),
    val missingRequiredKeys: List<String> = emptyList(),
)

@Serializable
enum class ShortListingEvidenceBlockingLevel {
    SOFT,
    PUBLISH_BLOCKING,
}

@Serializable
enum class ShortListingEvidenceStatus {
    OPEN,
    UPLOADING,
    RERUNNING,
    RESOLVED,
    UNRESOLVED,
    SKIPPED,
}

@Serializable
data class ShortListingEvidenceTask(
    val taskId: String,
    val reasonCode: String,
    val targetFieldCodes: List<String> = emptyList(),
    val photoRole: ShortListingPhotoRole? = null,
    val headline: String,
    val explanation: String? = null,
    val exampleHint: String? = null,
    val blockingLevel: ShortListingEvidenceBlockingLevel = ShortListingEvidenceBlockingLevel.SOFT,
    val canSkip: Boolean = true,
    val status: ShortListingEvidenceStatus = ShortListingEvidenceStatus.OPEN,
)

@Serializable
data class ShortListingPublishLocals(
    val priceMajor: Double? = null,
    val currency: String? = null,
    val ttlPresetDays: Int? = null,
    val city: String? = null,
    val deliveryChannel: String? = null,
    val description: String? = null,
)

@Serializable
data class ShortListingPublishBlocker(
    val code: String,
    val message: String,
    val fieldCodes: List<String> = emptyList(),
)

@Serializable
data class ShortListingPublishWarning(
    val code: String,
    val message: String,
    val fieldCodes: List<String> = emptyList(),
)

@Serializable
data class ShortListingPublishReadiness(
    val ready: Boolean,
    val blockers: List<ShortListingPublishBlocker> = emptyList(),
    val warnings: List<ShortListingPublishWarning> = emptyList(),
)

@Serializable
data class ShortListingProfileGate(
    val eligible: Boolean,
    val missingFields: List<String> = emptyList(),
)

@Serializable
enum class ShortListingDedupDecision {
    NO_DUPLICATE,
    SAME_DRAFT,
    SAME_USER_SIMILAR_ACTIVE,
    PUBLISH_IDEMPOTENT_RETRY,
}

@Serializable
data class ShortListingIdentitySignature(
    val resolvedCategoryCode: String,
    val identityAttributes: Map<String, ShortListingFieldValue> = emptyMap(),
    val identitySignature: String,
    val dedupDecision: ShortListingDedupDecision = ShortListingDedupDecision.NO_DUPLICATE,
    val dedupReasonCodes: List<String> = emptyList(),
)

@Serializable
data class ShortListingVisionDelta(
    val changedPredictions: List<String> = emptyList(),
    val unchangedConfirmedFields: List<String> = emptyList(),
    val conflictedFields: List<String> = emptyList(),
)

@Serializable
data class ShortListingVisionResult(
    val attemptId: String,
    val serverRequestId: String,
    val draftId: String,
    val status: ShortListingStage,
    val candidateCategory: ShortListingCategoryCandidate? = null,
    val categoryCandidates: List<ShortListingCategoryCandidate> = emptyList(),
    val rawExtraction: ShortListingVisionRawExtraction? = null,
    val bindOutcome: ShortListingVisionBindOutcome? = null,
    val predictedFields: Map<String, ShortListingFieldValue> = emptyMap(),
    val missingRequiredFields: List<String> = emptyList(),
    val evidenceTasks: List<ShortListingEvidenceTask> = emptyList(),
    val delta: ShortListingVisionDelta = ShortListingVisionDelta(),
    val nextAction: String? = null,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
enum class ShortListingPublishLifecycleStatus {
    LIVE,
    PENDING_REVIEW,
    PENDING_MEDIA,
    BLOCKED,
    EXPIRED,
    DEACTIVATED,
}

@Serializable
enum class ShortListingResultAction {
    OPEN_STATUS,
    EDIT,
    SHARE,
    DEACTIVATE,
    RENEW,
}

@Serializable
data class ShortListingNormalizedPayload(
    val title: String,
    val categoryCode: String,
    val brand: String? = null,
    val model: String? = null,
    val attributes: Map<String, ShortListingFieldValue> = emptyMap(),
    val priceMajor: Double,
    val currency: String,
    val ttlPresetDays: Int,
    val city: String? = null,
    val deliveryChannel: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
)

@Serializable
data class ShortListingPublishPreflight(
    val draftId: String,
    val eligible: Boolean,
    val normalizedPayload: ShortListingNormalizedPayload? = null,
    val blockers: List<ShortListingPublishBlocker> = emptyList(),
    val warnings: List<ShortListingPublishWarning> = emptyList(),
    val expiresAtMillis: Long? = null,
    val identitySignature: ShortListingIdentitySignature? = null,
)

@Serializable
data class ShortListingPublishResult(
    val draftId: String,
    val publishedOfferId: String,
    val status: ShortListingPublishLifecycleStatus,
    val publishedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val primaryAction: ShortListingResultAction = ShortListingResultAction.OPEN_STATUS,
    val secondaryActions: List<ShortListingResultAction> = emptyList(),
)

@Serializable
sealed interface ShortListingPublishAttemptResult {
    @Serializable
    data class Published(
        val result: ShortListingPublishResult,
    ) : ShortListingPublishAttemptResult

    @Serializable
    data class Blocked(
        val preflight: ShortListingPublishPreflight,
    ) : ShortListingPublishAttemptResult
}

@Serializable
data class ShortListingDraftSession(
    val draftId: String,
    val sessionId: String,
    val userId: String,
    val stage: ShortListingStage,
    val aiRefreshPending: Boolean = false,
    val safeToExit: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Int = 1,
    val requestedCategoryCode: String? = null,
    val resolvedCategoryCode: String? = null,
    val candidateCategory: ShortListingCategoryCandidate? = null,
    val confirmedCategoryCode: String? = null,
    val media: List<ShortListingMediaReceipt> = emptyList(),
    val predictedFields: Map<String, ShortListingFieldValue> = emptyMap(),
    val confirmedUserFields: Map<String, ShortListingFieldValue> = emptyMap(),
    val missingRequiredFields: List<String> = emptyList(),
    val evidenceTasks: List<ShortListingEvidenceTask> = emptyList(),
    val publishLocals: ShortListingPublishLocals = ShortListingPublishLocals(),
    val publishReadiness: ShortListingPublishReadiness = ShortListingPublishReadiness(ready = false),
    val profileGate: ShortListingProfileGate = ShortListingProfileGate(eligible = false),
    val identitySignature: ShortListingIdentitySignature? = null,
    val publishedOfferId: String? = null,
    val lifecycleStatus: ShortListingPublishLifecycleStatus? = null,
    val expiresAtMillis: Long? = null,
)

@Serializable
data class ShortListingDraftEnvelope(
    val draft: ShortListingDraftSession,
    val visionResult: ShortListingVisionResult? = null,
)

@Serializable
data class ShortListingCreateDraftRequest(
    val photos: List<ShortListingIncomingPhoto>,
    val requestedCategoryCode: String? = null,
    val locale: String? = null,
    val publishLocals: ShortListingPublishLocals = ShortListingPublishLocals(),
    val confirmedFields: Map<String, ShortListingFieldValue> = emptyMap(),
)

@Serializable
data class ShortListingReviewUpdateRequest(
    val confirmedCategoryCode: String? = null,
    val confirmedFields: Map<String, ShortListingFieldValue> = emptyMap(),
    val clearedFieldCodes: List<String> = emptyList(),
    val publishLocals: ShortListingPublishLocals? = null,
    val appendPhotos: List<ShortListingIncomingPhoto> = emptyList(),
)

interface ShortListingRepository {
    suspend fun listDrafts(): List<ShortListingDraftSession>
    suspend fun createDraft(request: ShortListingCreateDraftRequest): ShortListingDraftEnvelope
    suspend fun getDraft(draftId: String): ShortListingDraftEnvelope?
    suspend fun updateDraftReview(
        draftId: String,
        request: ShortListingReviewUpdateRequest,
    ): ShortListingDraftEnvelope
    suspend fun getPublishPreflight(draftId: String): ShortListingPublishPreflight
    suspend fun publishDraft(draftId: String): ShortListingPublishAttemptResult
}

class ListShortListingDraftsTask(
    private val repository: ShortListingRepository,
) {
    suspend operator fun invoke(): List<ShortListingDraftSession> =
        repository.listDrafts()
}

class CreateShortListingDraftTask(
    private val repository: ShortListingRepository,
) {
    suspend operator fun invoke(request: ShortListingCreateDraftRequest): ShortListingDraftEnvelope =
        repository.createDraft(request)
}

class GetShortListingDraftTask(
    private val repository: ShortListingRepository,
) {
    suspend operator fun invoke(draftId: String): ShortListingDraftEnvelope? =
        repository.getDraft(draftId)
}

class UpdateShortListingDraftReviewTask(
    private val repository: ShortListingRepository,
) {
    suspend operator fun invoke(
        draftId: String,
        request: ShortListingReviewUpdateRequest,
    ): ShortListingDraftEnvelope =
        repository.updateDraftReview(draftId, request)
}

class GetShortListingPublishPreflightTask(
    private val repository: ShortListingRepository,
) {
    suspend operator fun invoke(draftId: String): ShortListingPublishPreflight =
        repository.getPublishPreflight(draftId)
}

class PublishShortListingDraftTask(
    private val repository: ShortListingRepository,
) {
    suspend operator fun invoke(draftId: String): ShortListingPublishAttemptResult =
        repository.publishDraft(draftId)
}
