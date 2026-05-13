package com.example.shoppingassistant.domain.visualsearch

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface VisualSearchRepository {
    suspend fun reuseContext(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchContextReuseRequest,
    ): VisualSearchContextReuseResponse

    suspend fun normalizeDraft(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
    ): VisualSearchNormalizeDraftResponse

    suspend fun bindQuery(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchBindQueryRequest,
    ): VisualSearchBindQueryResponse

    suspend fun recoveryPlan(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchRecoveryPlanRequest,
    ): VisualSearchRecoveryPlanResponse

    suspend fun ingestEvents(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchEventBatchRequest,
    ): VisualSearchEventBatchResponse
}

@Serializable
data class VisualSearchTransportMetadata(
    val visualSessionId: String,
    val clientSchemaVersion: String = VisualSearchSchemaVersion.current,
    val catalogDataVersion: String = CatalogDataVersion.current,
    val idempotencyKey: String? = null,
)

@Serializable
enum class VisualSearchEnvelopeStatus {
    @SerialName("ok")
    OK,

    @SerialName("accepted")
    ACCEPTED,

    @SerialName("failed")
    FAILED,
}

@Serializable
enum class VisualSearchSource {
    @SerialName("camera")
    CAMERA,

    @SerialName("gallery")
    GALLERY,

    @SerialName("screenshot")
    SCREENSHOT,

    @SerialName("barcode_mode")
    BARCODE_MODE,
}

@Serializable
enum class VisualSearchCaptureMode {
    @SerialName("image")
    IMAGE,

    @SerialName("barcode")
    BARCODE,

    @SerialName("ocr")
    OCR,
}

@Serializable
enum class VisualSearchEntryPoint {
    @SerialName("search_hub_photo_card")
    SEARCH_HUB_PHOTO_CARD,

    @SerialName("retry")
    RETRY,

    @SerialName("recovery")
    RECOVERY,
}

@Serializable
enum class VisualSearchSelectionMode {
    @SerialName("auto_target")
    AUTO_TARGET,

    @SerialName("manual_crop")
    MANUAL_CROP,

    @SerialName("whole_frame")
    WHOLE_FRAME,
}

@Serializable
enum class VisualSearchIntent {
    @SerialName("exact_same")
    EXACT_SAME,

    @SerialName("similar")
    SIMILAR,

    @SerialName("part_accessory")
    PART_ACCESSORY,

    @SerialName("identify_first")
    IDENTIFY_FIRST,
}

@Serializable
enum class VisualSearchRetrievalStrategy {
    @SerialName("exact")
    EXACT,

    @SerialName("similarity")
    SIMILARITY,

    @SerialName("category_discovery")
    CATEGORY_DISCOVERY,
}

@Serializable
enum class VisualSearchBinderStatus {
    @SerialName("accepted")
    ACCEPTED,

    @SerialName("accepted_partial")
    ACCEPTED_PARTIAL,

    @SerialName("rejected")
    REJECTED,
}

@Serializable
enum class VisualSearchRouteKind {
    @SerialName("exact")
    EXACT,

    @SerialName("family_anchor")
    FAMILY_ANCHOR,

    @SerialName("typed_entity")
    TYPED_ENTITY,

    @SerialName("branch_only")
    BRANCH_ONLY,
}

@Serializable
enum class VisualSearchContextReuseStatus {
    @SerialName("hit")
    HIT,

    @SerialName("miss")
    MISS,

    @SerialName("error")
    ERROR,
}

@Serializable
enum class VisualSearchChipKind {
    @SerialName("category")
    CATEGORY,

    @SerialName("item_type")
    ITEM_TYPE,

    @SerialName("brand")
    BRAND,

    @SerialName("model")
    MODEL,

    @SerialName("attribute")
    ATTRIBUTE,

    @SerialName("barcode")
    BARCODE,
}

@Serializable
enum class VisualSearchRecoveryActionType {
    @SerialName("retake_photo")
    RETAKE_PHOTO,

    @SerialName("choose_category_manually")
    CHOOSE_CATEGORY_MANUALLY,

    @SerialName("add_text")
    ADD_TEXT,

    @SerialName("refine_intent")
    REFINE_INTENT,

    @SerialName("continue_without_ai")
    CONTINUE_WITHOUT_AI,

    @SerialName("retry")
    RETRY,
}

@Serializable
enum class VisualSearchErrorCode {
    @SerialName("BAD_REQUEST")
    BAD_REQUEST,

    @SerialName("INVALID_ASSET")
    INVALID_ASSET,

    @SerialName("UNSUPPORTED_SCHEMA")
    UNSUPPORTED_SCHEMA,

    @SerialName("IDEMPOTENCY_CONFLICT")
    IDEMPOTENCY_CONFLICT,

    @SerialName("CATALOG_VERSION_MISMATCH")
    CATALOG_VERSION_MISMATCH,

    @SerialName("FEATURE_DISABLED")
    FEATURE_DISABLED,

    @SerialName("RATE_LIMITED")
    RATE_LIMITED,

    @SerialName("UPSTREAM_TIMEOUT")
    UPSTREAM_TIMEOUT,

    @SerialName("UPSTREAM_FAILURE")
    UPSTREAM_FAILURE,

    @SerialName("BINDER_REJECTED")
    BINDER_REJECTED,
}

@Serializable
data class VisualSearchErrorEnvelope(
    val code: VisualSearchErrorCode,
    val messageKey: String,
    val retryable: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

@Serializable
data class VisualSearchImageAsset(
    val sha256: String,
    val mimeType: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val byteSize: Long? = null,
    val storageKey: String? = null,
    val inlineBase64: String? = null,
)

@Serializable
data class VisualSearchSelectedRegion(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Serializable
data class VisualSearchPreflightCategoryCandidate(
    val categoryCode: String,
    val confidence: Float? = null,
    val source: String? = null,
    val label: String? = null,
)

@Serializable
data class VisualSearchPreflightSignals(
    val reasonCodes: List<String> = emptyList(),
    val exactRouteReady: Boolean = false,
    val cheapProjectionReady: Boolean = false,
    val admitServerAi: Boolean = false,
    val requiresObjectPicker: Boolean = false,
    val exactCategoryCode: String? = null,
    val barcodeValue: String? = null,
    val captureMode: VisualSearchCaptureMode? = null,
    val ocrTextHints: List<String> = emptyList(),
    val imageLabelHints: List<String> = emptyList(),
    val objectLabel: String? = null,
    val objectConfidence: Float? = null,
    val categoryCandidates: List<VisualSearchPreflightCategoryCandidate> = emptyList(),
)

@Serializable
data class VisualSearchCandidateValue(
    val text: String,
    val confidence: Float? = null,
    val source: String? = null,
)

@Serializable
data class VisualSearchCandidateAttribute(
    val code: String,
    val value: TypedAttributeValue,
    val confidence: Float? = null,
)

@Serializable
data class VisualSearchCandidateProjection(
    val categoryCode: String? = null,
    val categoryConfidence: Float? = null,
    val itemType: VisualSearchCandidateValue? = null,
    val brand: VisualSearchCandidateValue? = null,
    val brandCandidates: List<VisualSearchCandidateValue> = emptyList(),
    val family: VisualSearchCandidateValue? = null,
    val familyCandidates: List<VisualSearchCandidateValue> = emptyList(),
    val model: VisualSearchCandidateValue? = null,
    val modelCandidates: List<VisualSearchCandidateValue> = emptyList(),
    val color: VisualSearchCandidateValue? = null,
    val condition: VisualSearchCandidateValue? = null,
    val attributes: List<VisualSearchCandidateAttribute> = emptyList(),
    val title: String? = null,
    val reasonCodes: List<String> = emptyList(),
)

@Serializable
data class VisualSearchHypothesis(
    val rank: Int? = null,
    val confidence: Float? = null,
    val projection: VisualSearchCandidateProjection,
    val missingEvidence: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
)

@Serializable
data class VisualSearchNormalizationDraft(
    val projection: VisualSearchCandidateProjection,
    val confidence: Float? = null,
    val hypotheses: List<VisualSearchHypothesis> = emptyList(),
    val needsMorePhotos: Boolean = false,
    val missingEvidence: List<String> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
    val providerName: String? = null,
    val providerSchemaVersion: String? = null,
)

@Serializable
data class VisualSearchChip(
    val kind: VisualSearchChipKind,
    val code: String? = null,
    val label: String,
    val isEditable: Boolean = false,
)

@Serializable
data class VisualSearchBoundQuery(
    val binderStatus: VisualSearchBinderStatus,
    val routeKind: VisualSearchRouteKind,
    val qualityApproved: Boolean = false,
    val retrievalStrategy: VisualSearchRetrievalStrategy,
    val categoryCode: String,
    val itemType: VisualSearchCandidateValue? = null,
    val normalizedQuery: NormalizedQuery? = null,
    val searchCriteria: OfferSearchCriteria,
    val chips: List<VisualSearchChip> = emptyList(),
    val modelCandidates: List<VisualSearchCandidateValue> = emptyList(),
    val previewTitle: String? = null,
    val droppedAttributeCodes: List<String> = emptyList(),
    val unresolvedRequiredAttributeCodes: List<String> = emptyList(),
    val recoveryReasonCodes: List<String> = emptyList(),
    val recommendedAction: VisualSearchRecoveryActionType? = null,
    val allowTextRefinement: Boolean = false,
    val exactRoute: Boolean = false,
    val reusableFingerprint: String? = null,
)

@Serializable
data class VisualSearchBoundCandidate(
    val rank: Int,
    val confidence: Float? = null,
    val query: VisualSearchBoundQuery,
    val reasonCodes: List<String> = emptyList(),
    val isPrimary: Boolean = false,
)

@Serializable
data class VisualSearchContextReuseRequest(
    val assetFingerprint: String,
    val source: VisualSearchSource,
    val entryPoint: VisualSearchEntryPoint,
)

@Serializable
data class VisualSearchContextReuseResponse(
    val status: VisualSearchContextReuseStatus,
    val reusedQuery: VisualSearchBoundQuery? = null,
    val cacheAgeSeconds: Long? = null,
    val traceId: String? = null,
    val error: VisualSearchErrorEnvelope? = null,
)

@Serializable
data class VisualSearchNormalizeDraftRequest(
    val asset: VisualSearchImageAsset,
    val contextAssets: List<VisualSearchImageAsset> = emptyList(),
    val source: VisualSearchSource,
    val entryPoint: VisualSearchEntryPoint,
    val selectionMode: VisualSearchSelectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
    val intent: VisualSearchIntent,
    val selectedRegion: VisualSearchSelectedRegion? = null,
    val preflightSignals: VisualSearchPreflightSignals = VisualSearchPreflightSignals(),
    val manualCategoryCode: String? = null,
    val locale: String? = null,
)

@Serializable
data class VisualSearchNormalizeDraftResponse(
    val status: VisualSearchEnvelopeStatus,
    val draft: VisualSearchNormalizationDraft? = null,
    val traceId: String? = null,
    val error: VisualSearchErrorEnvelope? = null,
)

@Serializable
data class VisualSearchBindQueryRequest(
    val intent: VisualSearchIntent,
    val source: VisualSearchSource,
    val selectionMode: VisualSearchSelectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
    val querySessionId: String? = null,
    val manualCategoryCode: String? = null,
    val preflightSignals: VisualSearchPreflightSignals = VisualSearchPreflightSignals(),
    val cheapProjection: VisualSearchCandidateProjection? = null,
    val normalizationDraft: VisualSearchNormalizationDraft? = null,
    val reusedQuery: VisualSearchBoundQuery? = null,
    val location: String? = null,
    val radiusKm: Int? = null,
    val conditions: List<String> = emptyList(),
    val sort: OfferSort = OfferSort.RANK,
    val locale: String? = null,
    val fingerprint: String? = null,
)

@Serializable
data class VisualSearchBindQueryResponse(
    val status: VisualSearchEnvelopeStatus,
    val boundQuery: VisualSearchBoundQuery? = null,
    val rankedCandidates: List<VisualSearchBoundCandidate> = emptyList(),
    val traceId: String? = null,
    val error: VisualSearchErrorEnvelope? = null,
)

@Serializable
data class VisualSearchRecoveryAction(
    val type: VisualSearchRecoveryActionType,
    val messageKey: String,
)

@Serializable
data class VisualSearchRecoveryPlan(
    val messageKey: String,
    val reasonCodes: List<String> = emptyList(),
    val actions: List<VisualSearchRecoveryAction> = emptyList(),
)

@Serializable
data class VisualSearchRecoveryPlanRequest(
    val binderStatus: VisualSearchBinderStatus,
    val reasonCodes: List<String> = emptyList(),
    val intent: VisualSearchIntent,
    val boundQuery: VisualSearchBoundQuery? = null,
)

@Serializable
data class VisualSearchRecoveryPlanResponse(
    val status: VisualSearchEnvelopeStatus,
    val recoveryPlan: VisualSearchRecoveryPlan? = null,
    val traceId: String? = null,
    val error: VisualSearchErrorEnvelope? = null,
)

@Serializable
data class VisualSearchEvent(
    val name: String,
    val happenedAtMs: Long,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable
data class VisualSearchEventBatchRequest(
    val events: List<VisualSearchEvent>,
)

@Serializable
data class VisualSearchEventBatchResponse(
    val status: VisualSearchEnvelopeStatus,
    val acceptedCount: Int,
    val rejectedCount: Int = 0,
    val traceId: String? = null,
    val error: VisualSearchErrorEnvelope? = null,
)

class ReuseVisualSearchContextUseCase(
    private val repository: VisualSearchRepository,
) {
    suspend operator fun invoke(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchContextReuseRequest,
    ): VisualSearchContextReuseResponse = repository.reuseContext(metadata, request)
}

class NormalizeVisualSearchDraftUseCase(
    private val repository: VisualSearchRepository,
) {
    suspend operator fun invoke(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
    ): VisualSearchNormalizeDraftResponse = repository.normalizeDraft(metadata, request)
}

class BindVisualSearchQueryUseCase(
    private val repository: VisualSearchRepository,
) {
    suspend operator fun invoke(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchBindQueryRequest,
    ): VisualSearchBindQueryResponse = repository.bindQuery(metadata, request)
}

class GetVisualSearchRecoveryPlanUseCase(
    private val repository: VisualSearchRepository,
) {
    suspend operator fun invoke(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchRecoveryPlanRequest,
    ): VisualSearchRecoveryPlanResponse = repository.recoveryPlan(metadata, request)
}

class TrackVisualSearchEventsUseCase(
    private val repository: VisualSearchRepository,
) {
    suspend operator fun invoke(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchEventBatchRequest,
    ): VisualSearchEventBatchResponse = repository.ingestEvents(metadata, request)
}
