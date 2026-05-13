package com.example.shoppingassistant.domain.localoffer

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import kotlinx.serialization.Serializable

object LocalOfferHttpContractPaths {
    const val base = "/api/localoffer"
    const val sessions = "$base/sessions"
    const val geoSnapshots = "$base/geo-snapshots"
    const val drafts = "$base/drafts"
    const val draftById = "$drafts/{draftId}"
    const val draftReview = "$draftById/review"
    const val draftPreview = "$draftById/preview"
    const val draftPreflight = "$draftById/preflight"
    const val draftPublish = "$draftById/publish"
}

object LocalOfferDraftIds {
    const val canonicalPrefix = "lodraft-"
    const val legacyPrefix = "sldraft-"

    fun canonicalOrNull(draftId: String?): String? {
        val normalized = draftId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return normalized.takeIf { it.startsWith(canonicalPrefix) }
    }

    fun migrateLegacyToCanonicalOrNull(draftId: String?): String? {
        val normalized = draftId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            normalized.startsWith(canonicalPrefix) -> normalized
            normalized.startsWith(legacyPrefix) -> canonicalPrefix + normalized.removePrefix(legacyPrefix)
            else -> null
        }
    }

    fun toStorageDraftId(canonicalDraftId: String): String =
        legacyPrefix + canonicalDraftId.removePrefix(canonicalPrefix)
}

@Serializable
enum class LocalOfferPhotoRole {
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
enum class LocalOfferFieldKind {
    SCALAR,
    MULTI,
}

@Serializable
enum class LocalOfferValueType {
    STRING,
    ENUM,
    NUMBER,
    BOOLEAN,
}

@Serializable
data class LocalOfferFieldAtom(
    val displayValue: String,
    val normalizedValue: String? = null,
    val canonicalValueCode: String? = null,
)

@Serializable
data class LocalOfferFieldValue(
    val kind: LocalOfferFieldKind = LocalOfferFieldKind.SCALAR,
    val valueType: LocalOfferValueType = LocalOfferValueType.STRING,
    val displayValue: String? = null,
    val normalizedValue: String? = null,
    val canonicalValueCode: String? = null,
    val values: List<LocalOfferFieldAtom> = emptyList(),
) {
    fun isMeaningful(): Boolean = when (kind) {
        LocalOfferFieldKind.SCALAR -> !displayValue.isNullOrBlank()
        LocalOfferFieldKind.MULTI -> values.any { it.displayValue.isNotBlank() }
    }
}

@Serializable
data class LocalOfferIncomingPhoto(
    val role: LocalOfferPhotoRole,
    val filename: String,
    val contentType: String? = null,
    val dataBase64: String,
)

@Serializable
data class LocalOfferMediaReceipt(
    val mediaId: String,
    val role: LocalOfferPhotoRole,
    val storageUrl: String,
    val contentType: String? = null,
    val sha256: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
)

@Serializable
enum class LocalOfferConfidenceBand {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class LocalOfferCategoryCandidate(
    val code: String,
    val title: String? = null,
    val score: Float? = null,
    val confidenceBand: LocalOfferConfidenceBand = LocalOfferConfidenceBand.LOW,
)

@Serializable
data class LocalOfferVisionAttributeCandidate(
    val code: String,
    val kind: String? = null,
    val text: String? = null,
    val number: Double? = null,
    val bool: Boolean? = null,
    val confidence: Float? = null,
)

@Serializable
data class LocalOfferVisionRawExtraction(
    val categoryHint: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val title: String? = null,
    val reasonCodes: List<String> = emptyList(),
    val attributes: List<LocalOfferVisionAttributeCandidate> = emptyList(),
)

@Serializable
data class LocalOfferVisionBindOutcome(
    val rawCategoryHint: String? = null,
    val resolvedCategoryCode: String? = null,
    val acceptedAttributeCodes: List<String> = emptyList(),
    val unresolvedAttributeCodes: List<String> = emptyList(),
    val missingRequiredKeys: List<String> = emptyList(),
)

@Serializable
data class LocalOfferVisionReview(
    val runId: String? = null,
    val rawExtraction: LocalOfferVisionRawExtraction? = null,
    val bindOutcome: LocalOfferVisionBindOutcome? = null,
    val nextAction: String? = null,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
enum class LocalOfferEvidenceBlockingLevel {
    SOFT,
    PUBLISH_BLOCKING,
}

@Serializable
enum class LocalOfferEvidenceStatus {
    OPEN,
    UPLOADING,
    RERUNNING,
    RESOLVED,
    UNRESOLVED,
    SKIPPED,
}

@Serializable
data class LocalOfferEvidenceTask(
    val taskId: String,
    val reasonCode: String,
    val targetFieldCodes: List<String> = emptyList(),
    val photoRole: LocalOfferPhotoRole? = null,
    val headline: String,
    val explanation: String? = null,
    val exampleHint: String? = null,
    val blockingLevel: LocalOfferEvidenceBlockingLevel = LocalOfferEvidenceBlockingLevel.SOFT,
    val canSkip: Boolean = true,
    val status: LocalOfferEvidenceStatus = LocalOfferEvidenceStatus.OPEN,
)

@Serializable
data class LocalOfferCommercials(
    val priceMajor: Double? = null,
    val currency: String? = null,
    val ttlPresetDays: Int? = null,
    val city: String? = null,
    val deliveryChannel: String? = null,
    val description: String? = null,
)

@Serializable
enum class LocalOfferDraftStage {
    REVIEW_READY,
    NEEDS_ENRICHMENT,
    PREVIEW_READY,
    PUBLISHED,
}

@Serializable
enum class LocalOfferFlowStep {
    GEO_CONSENT_GATE,
    PRIMARY_PHOTO_CAPTURE,
    AI_NORMALIZATION_RUN,
    DRAFT_REVIEW,
    PREVIEW,
    PUBLISH_PREFLIGHT,
    BLOCKED_STATE,
    PUBLISHED,
}

@Serializable
enum class LocalOfferPublicationState {
    PENDING_REVIEW,
    LIVE,
    REJECTED,
    REMOVED,
}

@Serializable
enum class LocalOfferSessionState {
    ACTIVE,
    PUBLISHED,
}

@Serializable
enum class LocalOfferGeoConsentState {
    GRANTED,
    DENIED,
    REVOKED,
}

@Serializable
enum class LocalOfferGeoStatus {
    CAPTURED,
    LOCATION_UNAVAILABLE,
    PERMISSION_DENIED,
    ERROR,
}

@Serializable
enum class LocalOfferGeoFreshnessState {
    FRESH,
    STALE,
    EXPIRED,
}

@Serializable
enum class LocalOfferIssueSeverity {
    WARNING,
    ERROR,
}

@Serializable
enum class LocalOfferModerationDecision {
    CLEAR,
    REVIEW_REQUIRED,
    HOLD,
    REJECTED,
}

@Serializable
enum class LocalOfferNode {
    ENTRY_GATE,
    GEO_CONSENT_GATE,
    PRIMARY_PHOTO_CAPTURE,
    PHOTO_PREFLIGHT,
    AI_NORMALIZATION_RUN,
    DRAFT_REVIEW,
    ENRICHMENT_PROMPT,
    SECONDARY_PHOTO_ENRICHMENT,
    PRICE_AND_TERMS,
    PREVIEW,
    PUBLISH_PREFLIGHT,
    BLOCKED_STATE,
}

@Serializable
data class LocalOfferRuntimeEnvelope(
    val sessionId: String,
    val correlationId: String,
    val draftId: String? = null,
    val revision: Int? = null,
    val runId: String? = null,
    val categoryCode: String? = null,
    val effectiveSpecVersion: String? = null,
    val issueCode: String? = null,
    val publishCommandId: String? = null,
    val geoSnapshotId: String? = null,
)

@Serializable
data class LocalOfferSessionRequest(
    val correlationId: String,
    val sessionId: String? = null,
    val draftId: String? = null,
)

@Serializable
data class LocalOfferSession(
    val sessionId: String,
    val correlationId: String,
    val draftId: String? = null,
    val state: LocalOfferSessionState = LocalOfferSessionState.ACTIVE,
    val effectiveSpecVersion: String = CatalogDataVersion.current,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
)

@Serializable
data class LocalOfferConfirmGeoSnapshotRequest(
    val sessionId: String,
    val correlationId: String,
    val consentState: LocalOfferGeoConsentState,
    val city: String,
    val adminArea: String? = null,
    val countryCode: String = "RU",
    val accuracyMeters: Double = 100.0,
    val lat: Double? = null,
    val lon: Double? = null,
    val source: String = "hybrid",
    val draftId: String? = null,
)

@Serializable
data class LocalOfferGeoSnapshot(
    val geoSnapshotId: String,
    val sessionId: String,
    val draftId: String? = null,
    val consentState: LocalOfferGeoConsentState,
    val status: LocalOfferGeoStatus,
    val capturedAtMillis: Long,
    val expiresAtMillis: Long,
    val freshnessState: LocalOfferGeoFreshnessState,
    val accuracyMeters: Double,
    val countryCode: String,
    val adminArea: String? = null,
    val city: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val source: String,
)

@Serializable
data class LocalOfferIssue(
    val issueCode: String,
    val severity: LocalOfferIssueSeverity,
    val message: String,
    val ownerNode: LocalOfferNode,
    val targetNode: LocalOfferNode,
    val fieldCodes: List<String> = emptyList(),
)

@Serializable
data class LocalOfferPreviewHero(
    val primaryPhotoUrl: String? = null,
    val title: String,
    val priceLabel: String? = null,
    val categoryCode: String? = null,
    val city: String? = null,
)

@Serializable
data class LocalOfferPreviewSummary(
    val blockingIssues: List<LocalOfferIssue> = emptyList(),
    val nonBlockingNotes: List<LocalOfferIssue> = emptyList(),
)

@Serializable
data class LocalOfferPreviewRequest(
    val correlationId: String,
    val revision: Int,
    val geoSnapshotId: String,
)

@Serializable
data class LocalOfferPreviewResponse(
    val envelope: LocalOfferRuntimeEnvelope,
    val hero: LocalOfferPreviewHero,
    val summary: LocalOfferPreviewSummary,
    val effectiveSpecVersion: String,
    val geoSnapshot: LocalOfferGeoSnapshot,
)

@Serializable
data class LocalOfferPreflightRoutes(
    val defaultFixNode: LocalOfferNode,
    val issueRoutes: Map<String, LocalOfferNode> = emptyMap(),
)

@Serializable
data class LocalOfferPublishPreflightRequest(
    val correlationId: String,
    val revision: Int,
    val geoSnapshotId: String,
)

@Serializable
data class LocalOfferPublishPreflightResponse(
    val envelope: LocalOfferRuntimeEnvelope,
    val effectiveSpecVersion: String,
    val publishAllowed: Boolean,
    val blockingIssues: List<LocalOfferIssue> = emptyList(),
    val warnings: List<LocalOfferIssue> = emptyList(),
    val geoSnapshot: LocalOfferGeoSnapshot,
    val moderationDecision: LocalOfferModerationDecision,
    val nextRoutes: LocalOfferPreflightRoutes,
)

@Serializable
enum class LocalOfferPublishOutcome {
    PUBLISHED,
    BLOCKED,
}

@Serializable
data class LocalOfferPublishCommandRequest(
    val correlationId: String,
    val revision: Int,
    val effectiveSpecVersion: String,
    val geoSnapshotId: String,
    val publishCommandId: String,
)

@Serializable
data class LocalOfferPublishResult(
    val envelope: LocalOfferRuntimeEnvelope,
    val outcome: LocalOfferPublishOutcome,
    val publicationState: LocalOfferPublicationState? = null,
    val offerId: String? = null,
    val publishedAtMillis: Long? = null,
    val issue: LocalOfferIssue? = null,
)

@Serializable
data class LocalOfferDraftSession(
    val draftId: String,
    val sessionId: String,
    val stage: LocalOfferDraftStage,
    val aiRefreshPending: Boolean = false,
    val safeToExit: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val revision: Int,
    val requestedCategoryCode: String? = null,
    val resolvedCategoryCode: String? = null,
    val candidateCategory: LocalOfferCategoryCandidate? = null,
    val confirmedCategoryCode: String? = null,
    val media: List<LocalOfferMediaReceipt> = emptyList(),
    val predictedFields: Map<String, LocalOfferFieldValue> = emptyMap(),
    val confirmedUserFields: Map<String, LocalOfferFieldValue> = emptyMap(),
    val missingRequiredFields: List<String> = emptyList(),
    val evidenceTasks: List<LocalOfferEvidenceTask> = emptyList(),
    val commercials: LocalOfferCommercials = LocalOfferCommercials(),
    val issues: List<LocalOfferIssue> = emptyList(),
    val effectiveSpecVersion: String = CatalogDataVersion.current,
    val activeGeoSnapshotId: String? = null,
    val publishedOfferId: String? = null,
    val publicationState: LocalOfferPublicationState? = null,
)

@Serializable
data class LocalOfferDraftEnvelope(
    val draft: LocalOfferDraftSession,
    val envelope: LocalOfferRuntimeEnvelope,
    val visionReview: LocalOfferVisionReview? = null,
)

@Serializable
data class LocalOfferCreateDraftRequest(
    val sessionId: String,
    val correlationId: String,
    val geoSnapshotId: String,
    val photos: List<LocalOfferIncomingPhoto>,
    val requestedCategoryCode: String? = null,
    val locale: String? = null,
    val commercials: LocalOfferCommercials = LocalOfferCommercials(),
    val confirmedFields: Map<String, LocalOfferFieldValue> = emptyMap(),
)

@Serializable
data class LocalOfferReviewUpdateRequest(
    val correlationId: String,
    val revision: Int,
    val confirmedCategoryCode: String? = null,
    val confirmedFields: Map<String, LocalOfferFieldValue> = emptyMap(),
    val clearedFieldCodes: List<String> = emptyList(),
    val commercials: LocalOfferCommercials? = null,
    val appendPhotos: List<LocalOfferIncomingPhoto> = emptyList(),
)

interface LocalOfferRepository {
    suspend fun createOrResumeSession(request: LocalOfferSessionRequest): LocalOfferSession
    suspend fun confirmGeoSnapshot(request: LocalOfferConfirmGeoSnapshotRequest): LocalOfferGeoSnapshot
    suspend fun listDrafts(): List<LocalOfferDraftSession>
    suspend fun createDraft(request: LocalOfferCreateDraftRequest): LocalOfferDraftEnvelope
    suspend fun getDraft(draftId: String): LocalOfferDraftEnvelope?
    suspend fun updateDraftReview(
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferDraftEnvelope
    suspend fun getPreview(
        draftId: String,
        request: LocalOfferPreviewRequest,
    ): LocalOfferPreviewResponse
    suspend fun getPublishPreflight(
        draftId: String,
        request: LocalOfferPublishPreflightRequest,
    ): LocalOfferPublishPreflightResponse
    suspend fun publishDraft(
        draftId: String,
        request: LocalOfferPublishCommandRequest,
    ): LocalOfferPublishResult
}

class CreateOrResumeLocalOfferSessionTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(request: LocalOfferSessionRequest): LocalOfferSession =
        repository.createOrResumeSession(request)
}

class ConfirmLocalOfferGeoSnapshotTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(request: LocalOfferConfirmGeoSnapshotRequest): LocalOfferGeoSnapshot =
        repository.confirmGeoSnapshot(request)
}

class ListLocalOfferDraftsTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(): List<LocalOfferDraftSession> =
        repository.listDrafts()
}

class CreateLocalOfferDraftTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(request: LocalOfferCreateDraftRequest): LocalOfferDraftEnvelope =
        repository.createDraft(request)
}

class GetLocalOfferDraftTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(draftId: String): LocalOfferDraftEnvelope? =
        repository.getDraft(draftId)
}

class UpdateLocalOfferDraftReviewTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferDraftEnvelope =
        repository.updateDraftReview(draftId, request)
}

class GetLocalOfferPreviewTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(
        draftId: String,
        request: LocalOfferPreviewRequest,
    ): LocalOfferPreviewResponse =
        repository.getPreview(draftId, request)
}

class GetLocalOfferPublishPreflightTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(
        draftId: String,
        request: LocalOfferPublishPreflightRequest,
    ): LocalOfferPublishPreflightResponse =
        repository.getPublishPreflight(draftId, request)
}

class PublishLocalOfferDraftTask(
    private val repository: LocalOfferRepository,
) {
    suspend operator fun invoke(
        draftId: String,
        request: LocalOfferPublishCommandRequest,
    ): LocalOfferPublishResult =
        repository.publishDraft(draftId, request)
}
