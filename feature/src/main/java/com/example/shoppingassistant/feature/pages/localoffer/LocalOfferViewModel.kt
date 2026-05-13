package com.example.shoppingassistant.feature.pages.localoffer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shoppingassistant.domain.localoffer.CreateLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.CreateOrResumeLocalOfferSessionTask
import com.example.shoppingassistant.domain.localoffer.ConfirmLocalOfferGeoSnapshotTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPreviewTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPublishPreflightTask
import com.example.shoppingassistant.domain.localoffer.LocalOfferCommercials
import com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftIds
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldAtom
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldKind
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldValue
import com.example.shoppingassistant.domain.localoffer.LocalOfferFlowStep
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoConsentState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoFreshnessState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoSnapshot
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssue
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssueSeverity
import com.example.shoppingassistant.domain.localoffer.LocalOfferNode
import com.example.shoppingassistant.domain.localoffer.LocalOfferPhotoRole
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublicationState
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishCommandRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishResult
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferValueType
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionAttributeCandidate
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionReview
import com.example.shoppingassistant.domain.localoffer.PublishLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.UpdateLocalOfferDraftReviewTask
import com.example.shoppingassistant.domain.ugc.draft.DraftInputOrigin
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferPublicationStatus
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferStatus
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import java.util.LinkedHashMap
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalOfferUiState(
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val publishing: Boolean = false,
    val origin: DraftInputOrigin? = null,
    val session: LocalOfferSession? = null,
    val geoSnapshot: LocalOfferGeoSnapshot? = null,
    val draftEnvelope: LocalOfferDraftEnvelope? = null,
    val preview: LocalOfferPreviewResponse? = null,
    val preflight: LocalOfferPublishPreflightResponse? = null,
    val publishResult: LocalOfferPublishResult? = null,
    val selectedCategoryCode: String? = null,
    val fieldInputs: Map<String, String> = emptyMap(),
    val reviewInsights: LocalOfferReviewInsightsUi? = null,
    val priceInput: String = "",
    val currencyInput: String = "USD",
    val ttlDaysInput: String = "30",
    val deliveryInput: String = "",
    val descriptionInput: String = "",
    val errorMessage: String? = null,
    val blockedState: LocalOfferBlockedState? = null,
    val activeStep: LocalOfferFlowStep = LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
)

data class LocalOfferReviewInsightFactUi(
    val label: String,
    val value: String,
)

data class LocalOfferReviewInsightsUi(
    val recognized: List<LocalOfferReviewInsightFactUi> = emptyList(),
    val accepted: List<LocalOfferReviewInsightFactUi> = emptyList(),
    val unresolved: List<String> = emptyList(),
)

enum class LocalOfferBlockedAction {
    REQUEST_DEVICE_LOCATION,
    OPEN_APP_SETTINGS,
}

data class LocalOfferBlockedState(
    val title: String,
    val body: String,
    val issues: List<LocalOfferIssue> = emptyList(),
    val action: LocalOfferBlockedAction? = null,
    val actionLabel: String? = null,
)

class LocalOfferViewModel(
    private val createOrResumeSession: CreateOrResumeLocalOfferSessionTask,
    private val confirmGeoSnapshot: ConfirmLocalOfferGeoSnapshotTask,
    private val createDraft: CreateLocalOfferDraftTask,
    private val getDraft: GetLocalOfferDraftTask,
    private val updateDraftReview: UpdateLocalOfferDraftReviewTask,
    private val getPreview: GetLocalOfferPreviewTask,
    private val getPublishPreflight: GetLocalOfferPublishPreflightTask,
    private val publishDraft: PublishLocalOfferDraftTask,
    private val createdStore: UserOffersCreatedStore,
    private val sessionStore: LocalOfferFlowSessionStore,
    private val aiRefreshPollIntervalMillis: Long = DEFAULT_AI_REFRESH_POLL_INTERVAL_MILLIS,
    private val aiRefreshTimeoutMillis: Long = DEFAULT_AI_REFRESH_TIMEOUT_MILLIS,
) : ViewModel() {

    private val _state = MutableStateFlow(LocalOfferUiState())
    val state: StateFlow<LocalOfferUiState> = _state.asStateFlow()
    private var aiRefreshPollJob: Job? = null
    private var aiRefreshPollingDraftId: String? = null

    fun openDraft(draftId: String?, origin: DraftInputOrigin?) {
        viewModelScope.launch { openDraftInternal(draftId = draftId, origin = origin) }
    }

    fun startNewDraft() {
        viewModelScope.launch {
            sessionStore.clear()
            openDraftInternal(draftId = null, origin = state.value.origin)
        }
    }

    fun confirmDeviceGeoSnapshot(
        city: String,
        adminArea: String?,
        countryCode: String?,
        lat: Double?,
        lon: Double?,
    ) {
        submitGeoSnapshot(
            city = city,
            adminArea = adminArea,
            countryCode = countryCode?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_COUNTRY_CODE,
            lat = lat,
            lon = lon,
            accuracyMeters = DEVICE_GEO_ACCURACY_METERS,
            source = "device",
        )
    }

    fun showGeoAccessBlockedState(permanentlyDenied: Boolean) {
        _state.update {
            it.copy(
                blockedState = if (permanentlyDenied) geoSettingsBlockedState() else geoPermissionBlockedState(),
                activeStep = LocalOfferFlowStep.BLOCKED_STATE,
                errorMessage = null,
            )
        }
    }

    fun showGeoUnavailableBlockedState() {
        _state.update {
            it.copy(
                blockedState = geoUnavailableBlockedState(),
                activeStep = LocalOfferFlowStep.BLOCKED_STATE,
                errorMessage = null,
            )
        }
    }

    fun showGeoRefreshRequiredBlockedState() {
        _state.update {
            it.copy(
                blockedState = geoRefreshBlockedState(),
                activeStep = LocalOfferFlowStep.BLOCKED_STATE,
                errorMessage = null,
            )
        }
    }

    fun updateField(code: String, value: String) {
        val normalizedCode = code.trim().lowercase()
        if (normalizedCode.isEmpty()) return
        _state.update { current ->
            current.copy(
                fieldInputs = LinkedHashMap(current.fieldInputs).apply { put(normalizedCode, value) },
                preview = null,
                preflight = null,
                errorMessage = null,
            )
        }
    }

    fun updateCategory(code: String?) {
        _state.update {
            it.copy(
                selectedCategoryCode = code?.trim()?.takeIf { value -> value.isNotEmpty() },
                preview = null,
                preflight = null,
                errorMessage = null,
            )
        }
    }

    fun updatePrice(value: String) {
        _state.update { it.copy(priceInput = value, preview = null, preflight = null, errorMessage = null) }
    }

    fun updateCurrency(value: String) {
        _state.update { it.copy(currencyInput = value, preview = null, preflight = null, errorMessage = null) }
    }

    fun updateTtlDays(value: String) {
        _state.update { it.copy(ttlDaysInput = value, preview = null, preflight = null, errorMessage = null) }
    }

    fun updateDelivery(value: String) {
        _state.update { it.copy(deliveryInput = value, preview = null, preflight = null, errorMessage = null) }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(descriptionInput = value, preview = null, preflight = null, errorMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun dismissPublishResult() {
        _state.update { it.copy(publishResult = null) }
    }

    fun dismissBlockedState() {
        _state.update {
            val geoIsFresh = it.geoSnapshot?.freshnessState == LocalOfferGeoFreshnessState.FRESH
            it.copy(
                blockedState = null,
                activeStep = when {
                    !geoIsFresh -> LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                    it.draftEnvelope != null -> it.draftEnvelope.draft.toOwnedStep()
                    else -> LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                },
            )
        }
    }

    fun backToReview() {
        _state.update {
            it.copy(
                preview = null,
                preflight = null,
                publishResult = null,
                blockedState = null,
                activeStep = if (it.draftEnvelope == null) {
                    LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                } else {
                    it.draftEnvelope.draft.toOwnedStep()
                },
            )
        }
    }

    fun backToPreview() {
        _state.update {
            it.copy(
                preflight = null,
                publishResult = null,
                blockedState = null,
                activeStep = LocalOfferFlowStep.PREVIEW,
            )
        }
    }

    fun addPhoto(photo: com.example.shoppingassistant.domain.localoffer.LocalOfferIncomingPhoto) {
        viewModelScope.launch {
            val current = state.value
            if (current.submitting || current.publishing) return@launch
            val session = current.session ?: createSessionOrEmitError(current) ?: return@launch
            val geoSnapshot = requireFreshGeoSnapshot(current.geoSnapshot) ?: return@launch
            _state.update { it.copy(submitting = true, errorMessage = null, preview = null, preflight = null) }

            val result = runCatching {
                if (current.draftEnvelope == null) {
                    createDraft(
                        LocalOfferCreateDraftRequest(
                            sessionId = session.sessionId,
                            correlationId = session.correlationId,
                            geoSnapshotId = geoSnapshot.geoSnapshotId,
                            photos = listOf(photo),
                            requestedCategoryCode = current.selectedCategoryCode,
                            commercials = buildCommercialsOrEmitError(current)
                                ?: LocalOfferCommercials(city = geoSnapshot.city),
                            confirmedFields = emptyMap(),
                        ),
                    )
                } else {
                    val draft = current.draftEnvelope.draft
                    updateDraftReview(
                        draftId = draft.draftId,
                        request = buildReviewPayload(draft, current).copy(appendPhotos = listOf(photo)),
                    )
                }
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = error.message ?: "Не удалось обработать фото.",
                    )
                }
                return@launch
            }

            applyEnvelope(
                session = session,
                geoSnapshot = geoSnapshot,
                envelope = result,
                origin = current.origin,
                preview = null,
                preflight = null,
                publishResult = null,
            )
        }
    }

    fun saveReview() {
        viewModelScope.launch {
            val current = state.value
            val session = current.session ?: createSessionOrEmitError(current) ?: return@launch
            val geoSnapshot = current.geoSnapshot
            _state.update { it.copy(submitting = true, errorMessage = null, preview = null, preflight = null) }
            val envelope = persistReview(current) ?: return@launch
            applyEnvelope(
                session = session,
                geoSnapshot = geoSnapshot,
                envelope = envelope,
                origin = current.origin,
                preview = null,
                preflight = null,
                publishResult = null,
            )
        }
    }

    fun openPreview() {
        viewModelScope.launch {
            val current = state.value
            if (current.draftEnvelope?.draft?.aiRefreshPending == true) {
                _state.update {
                    it.copy(errorMessage = "Объявление ещё заполняется. Подождите пару секунд.")
                }
                return@launch
            }
            val session = current.session ?: createSessionOrEmitError(current) ?: return@launch
            val geoSnapshot = requireFreshGeoSnapshot(current.geoSnapshot) ?: return@launch
            _state.update { it.copy(submitting = true, errorMessage = null, preflight = null) }
            val envelope = persistReview(current) ?: return@launch
            val preview = runCatching {
                getPreview(
                    draftId = envelope.draft.draftId,
                    request = LocalOfferPreviewRequest(
                        correlationId = session.correlationId,
                        revision = envelope.draft.revision,
                        geoSnapshotId = geoSnapshot.geoSnapshotId,
                    ),
                )
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = error.message ?: "Не удалось показать объявление.",
                    )
                }
                return@launch
            }

            applyEnvelope(
                session = session,
                geoSnapshot = preview.geoSnapshot,
                envelope = envelope,
                origin = current.origin,
                preview = preview,
                preflight = null,
                publishResult = null,
            )
        }
    }

    fun refreshPreflight() {
        viewModelScope.launch {
            val current = state.value
            if (current.draftEnvelope?.draft?.aiRefreshPending == true) {
                _state.update {
                    it.copy(errorMessage = "Объявление ещё заполняется. Подождите пару секунд.")
                }
                return@launch
            }
            val session = current.session ?: createSessionOrEmitError(current) ?: return@launch
            val geoSnapshot = requireFreshGeoSnapshot(current.geoSnapshot) ?: return@launch
            _state.update { it.copy(submitting = true, errorMessage = null) }
            val envelope = persistReview(current) ?: return@launch
            val preview = runCatching {
                getPreview(
                    draftId = envelope.draft.draftId,
                    request = LocalOfferPreviewRequest(
                        correlationId = session.correlationId,
                        revision = envelope.draft.revision,
                        geoSnapshotId = geoSnapshot.geoSnapshotId,
                    ),
                )
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = error.message ?: "Не удалось обновить объявление.",
                    )
                }
                return@launch
            }
            val preflight = runCatching {
                getPublishPreflight(
                    draftId = envelope.draft.draftId,
                    request = LocalOfferPublishPreflightRequest(
                        correlationId = session.correlationId,
                        revision = envelope.draft.revision,
                        geoSnapshotId = preview.geoSnapshot.geoSnapshotId,
                    ),
                )
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = error.message ?: "Не удалось проверить объявление перед публикацией.",
                    )
                }
                return@launch
            }

            applyEnvelope(
                session = session,
                geoSnapshot = preflight.geoSnapshot,
                envelope = envelope,
                origin = current.origin,
                preview = preview,
                preflight = preflight,
                publishResult = null,
            )
        }
    }

    fun publish() {
        viewModelScope.launch {
            val current = state.value
            if (current.draftEnvelope?.draft?.aiRefreshPending == true) {
                _state.update {
                    it.copy(errorMessage = "Объявление ещё заполняется. Подождите пару секунд.")
                }
                return@launch
            }
            val session = current.session ?: createSessionOrEmitError(current) ?: return@launch
            val envelope = current.draftEnvelope ?: run {
                _state.update { it.copy(errorMessage = "Черновик не найден.") }
                return@launch
            }
            val geoSnapshot = requireFreshGeoSnapshot(current.geoSnapshot ?: current.preflight?.geoSnapshot) ?: return@launch
            val preflight = current.preflight ?: run {
                _state.update { it.copy(errorMessage = "Сначала проверьте объявление перед публикацией.") }
                return@launch
            }
            if (!preflight.publishAllowed) {
                _state.update { it.copy(errorMessage = "Сначала исправьте замечания перед публикацией.") }
                return@launch
            }
            val publishCommandId = preflight.envelope.publishCommandId
            if (publishCommandId.isNullOrBlank()) {
                _state.update { it.copy(errorMessage = "Не удалось подготовить публикацию. Попробуйте ещё раз.") }
                return@launch
            }

            _state.update { it.copy(publishing = true, errorMessage = null) }
            val result = runCatching {
                publishDraft(
                    draftId = envelope.draft.draftId,
                    request = LocalOfferPublishCommandRequest(
                        correlationId = session.correlationId,
                        revision = preflight.envelope.revision ?: envelope.draft.revision,
                        effectiveSpecVersion = preflight.effectiveSpecVersion,
                        geoSnapshotId = geoSnapshot.geoSnapshotId,
                        publishCommandId = publishCommandId,
                    ),
                )
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        publishing = false,
                        errorMessage = error.message ?: "Не удалось опубликовать объявление.",
                    )
                }
                return@launch
            }

            if (result.outcome == LocalOfferPublishOutcome.PUBLISHED && !result.offerId.isNullOrBlank()) {
                sessionStore.clear()
                createdStore.remove(envelope.draft.draftId)
                createdStore.add(result.toUserOfferCard(envelope.draft))
            }

            val updatedDraft = envelope.draft.copy(
                publishedOfferId = result.offerId ?: envelope.draft.publishedOfferId,
                publicationState = result.publicationState ?: envelope.draft.publicationState,
            )
            applyEnvelope(
                session = session,
                geoSnapshot = preflight.geoSnapshot,
                envelope = envelope.copy(draft = updatedDraft),
                origin = current.origin,
                preview = current.preview,
                preflight = preflight,
                publishResult = result,
                publishing = false,
                persistSession = result.outcome != LocalOfferPublishOutcome.PUBLISHED,
                syncDraftCard = result.outcome != LocalOfferPublishOutcome.PUBLISHED,
            )
        }
    }

    private suspend fun openDraftInternal(
        draftId: String?,
        origin: DraftInputOrigin?,
    ) {
        _state.update {
            it.copy(
                loading = true,
                submitting = false,
                publishing = false,
                origin = origin,
                errorMessage = null,
                preview = null,
                preflight = null,
                publishResult = null,
                blockedState = null,
                activeStep = LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
            )
        }

        val explicitDraftId = draftId?.trim()?.takeIf { it.isNotEmpty() }
        val canonicalDraftId = explicitDraftId?.toCanonicalLocalOfferDraftIdOrNull()
        if (explicitDraftId != null && canonicalDraftId == null) {
            _state.update {
                it.copy(
                    loading = false,
                    draftEnvelope = null,
                    preview = null,
                    preflight = null,
                    blockedState = publishedOfferEditBlockedState(),
                    activeStep = LocalOfferFlowStep.BLOCKED_STATE,
                )
            }
            return
        }

        val stored = sessionStore.get()
            ?.takeIf { persisted ->
                canonicalDraftId == null || persisted.draftId == null || persisted.draftId == canonicalDraftId
            }
        val session = runCatching {
            createOrResumeSession(
                LocalOfferSessionRequest(
                    correlationId = stored?.correlationId ?: newCorrelationId(),
                    sessionId = stored?.sessionId,
                    draftId = canonicalDraftId ?: stored?.draftId,
                ),
            )
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    loading = false,
                    errorMessage = error.message ?: "Не удалось открыть создание объявления.",
                )
            }
            return
        }

        val targetDraftId = canonicalDraftId ?: session.draftId ?: stored?.draftId
        val restoredGeoSnapshot = stored?.geoSnapshot?.takeIf { it.sessionId == session.sessionId }
        if (targetDraftId == null) {
            val geoNeedsRefresh = restoredGeoSnapshot?.freshnessState != null &&
                restoredGeoSnapshot.freshnessState != LocalOfferGeoFreshnessState.FRESH
            val flowStep = if (restoredGeoSnapshot == null) {
                LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
            } else if (geoNeedsRefresh) {
                LocalOfferFlowStep.BLOCKED_STATE
            } else {
                LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
            }
            persistRuntime(session = session, draft = null, geoSnapshot = restoredGeoSnapshot, flowStep = flowStep)
            _state.update {
                it.copy(
                    loading = false,
                    session = session,
                    geoSnapshot = restoredGeoSnapshot,
                    draftEnvelope = null,
                    selectedCategoryCode = null,
                    fieldInputs = emptyMap(),
                    priceInput = "",
                    currencyInput = "USD",
                    ttlDaysInput = "30",
                    deliveryInput = "",
                    descriptionInput = "",
                    blockedState = if (geoNeedsRefresh) geoRefreshBlockedState() else null,
                    activeStep = flowStep,
                )
            }
            return
        }

        val envelope = runCatching { getDraft(targetDraftId) }.getOrNull()
        if (envelope == null) {
            sessionStore.clear()
            _state.update {
                it.copy(
                    loading = false,
                    session = session,
                    geoSnapshot = restoredGeoSnapshot,
                    draftEnvelope = null,
                    errorMessage = "Черновик не найден. Начните новое объявление.",
                    blockedState = if (restoredGeoSnapshot?.freshnessState != null &&
                        restoredGeoSnapshot.freshnessState != LocalOfferGeoFreshnessState.FRESH
                    ) {
                        geoRefreshBlockedState()
                    } else {
                        null
                    },
                    activeStep = if (restoredGeoSnapshot == null) {
                        LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                    } else if (restoredGeoSnapshot.freshnessState != LocalOfferGeoFreshnessState.FRESH) {
                        LocalOfferFlowStep.BLOCKED_STATE
                    } else {
                        LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                    },
                )
            }
            return
        }

        val restoredFlowStep = stored?.flowStep
        when {
            envelope.draft.aiRefreshPending -> {
                applyEnvelope(
                    session = session,
                    geoSnapshot = restoredGeoSnapshot,
                    envelope = envelope,
                    origin = origin,
                    preview = null,
                    preflight = null,
                    publishResult = null,
                )
            }

            restoredFlowStep == LocalOfferFlowStep.PUBLISH_PREFLIGHT && restoredGeoSnapshot != null -> {
                restorePreflightFlow(
                    session = session,
                    origin = origin,
                    envelope = envelope,
                    geoSnapshot = restoredGeoSnapshot,
                )
            }

            restoredFlowStep == LocalOfferFlowStep.PREVIEW && restoredGeoSnapshot != null -> {
                restorePreviewFlow(
                    session = session,
                    origin = origin,
                    envelope = envelope,
                    geoSnapshot = restoredGeoSnapshot,
                )
            }

            else -> {
                applyEnvelope(
                    session = session,
                    geoSnapshot = restoredGeoSnapshot,
                    envelope = envelope,
                    origin = origin,
                    preview = null,
                    preflight = null,
                    publishResult = null,
                )
            }
        }
    }

    private suspend fun restorePreviewFlow(
        session: LocalOfferSession,
        origin: DraftInputOrigin?,
        envelope: LocalOfferDraftEnvelope,
        geoSnapshot: LocalOfferGeoSnapshot,
    ) {
        val preview = runCatching {
            getPreview(
                draftId = envelope.draft.draftId,
                request = LocalOfferPreviewRequest(
                    correlationId = session.correlationId,
                    revision = envelope.draft.revision,
                    geoSnapshotId = geoSnapshot.geoSnapshotId,
                ),
            )
        }.getOrNull()

        applyEnvelope(
            session = session,
            geoSnapshot = preview?.geoSnapshot ?: geoSnapshot,
            envelope = envelope,
            origin = origin,
            preview = preview,
            preflight = null,
            publishResult = null,
        )
    }

    private suspend fun restorePreflightFlow(
        session: LocalOfferSession,
        origin: DraftInputOrigin?,
        envelope: LocalOfferDraftEnvelope,
        geoSnapshot: LocalOfferGeoSnapshot,
    ) {
        val preview = runCatching {
            getPreview(
                draftId = envelope.draft.draftId,
                request = LocalOfferPreviewRequest(
                    correlationId = session.correlationId,
                    revision = envelope.draft.revision,
                    geoSnapshotId = geoSnapshot.geoSnapshotId,
                ),
            )
        }.getOrNull()
        val preflight = preview?.let { restoredPreview ->
            runCatching {
                getPublishPreflight(
                    draftId = envelope.draft.draftId,
                    request = LocalOfferPublishPreflightRequest(
                        correlationId = session.correlationId,
                        revision = envelope.draft.revision,
                        geoSnapshotId = restoredPreview.geoSnapshot.geoSnapshotId,
                    ),
                )
            }.getOrNull()
        }

        applyEnvelope(
            session = session,
            geoSnapshot = preflight?.geoSnapshot ?: preview?.geoSnapshot ?: geoSnapshot,
            envelope = envelope,
            origin = origin,
            preview = preview,
            preflight = preflight,
            publishResult = null,
        )
    }

    private fun submitGeoSnapshot(
        city: String,
        adminArea: String?,
        countryCode: String,
        lat: Double?,
        lon: Double?,
        accuracyMeters: Double,
        source: String,
    ) {
        viewModelScope.launch {
            val current = state.value
            val session = current.session ?: createSessionOrEmitError(current) ?: return@launch
            _state.update { it.copy(submitting = true, errorMessage = null, preview = null, preflight = null) }
            val snapshot = runCatching {
                confirmGeoSnapshot(
                    LocalOfferConfirmGeoSnapshotRequest(
                        sessionId = session.sessionId,
                        correlationId = session.correlationId,
                        consentState = LocalOfferGeoConsentState.GRANTED,
                        city = city,
                        adminArea = adminArea,
                        countryCode = countryCode,
                        accuracyMeters = accuracyMeters,
                        lat = lat,
                        lon = lon,
                        source = source,
                        draftId = current.draftEnvelope?.draft?.draftId,
                    ),
                )
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = error.message ?: "Не удалось подтвердить геоданные.",
                    )
                }
                return@launch
            }

            persistRuntime(
                session = session,
                draft = current.draftEnvelope?.draft,
                geoSnapshot = snapshot,
                flowStep = if (current.draftEnvelope == null) {
                    LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                } else {
                    current.activeStep
                },
            )
            _state.update {
                it.copy(
                    submitting = false,
                    session = session,
                    geoSnapshot = snapshot,
                    errorMessage = null,
                    activeStep = if (current.draftEnvelope == null) {
                        LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
                    } else {
                        it.activeStep
                    },
                )
            }
        }
    }

    private suspend fun createSessionOrEmitError(current: LocalOfferUiState): LocalOfferSession? {
        val draftId = current.draftEnvelope?.draft?.draftId
        return runCatching {
            createOrResumeSession(
                LocalOfferSessionRequest(
                    correlationId = current.session?.correlationId ?: newCorrelationId(),
                    sessionId = current.session?.sessionId,
                    draftId = draftId,
                ),
            )
        }.onSuccess { session ->
            persistRuntime(
                session = session,
                draft = current.draftEnvelope?.draft,
                geoSnapshot = current.geoSnapshot,
                flowStep = current.activeStep,
            )
            _state.update { it.copy(session = session) }
        }.getOrElse { error ->
            _state.update {
                it.copy(errorMessage = error.message ?: "Не удалось подготовить объявление.")
            }
            null
        }
    }

    private suspend fun persistReview(current: LocalOfferUiState): LocalOfferDraftEnvelope? {
        val draft = current.draftEnvelope?.draft ?: return null
        val request = buildReviewPayload(draft, current)
        return runCatching {
            updateDraftReview(draftId = draft.draftId, request = request)
        }.getOrElse { error ->
            _state.update {
                it.copy(
                    submitting = false,
                    publishing = false,
                    errorMessage = error.message ?: "Не удалось сохранить изменения.",
                )
            }
            null
        }
    }

    private fun buildReviewPayload(
        draft: LocalOfferDraftSession,
        current: LocalOfferUiState,
    ): LocalOfferReviewUpdateRequest {
        val mergedFields = draft.mergedFields()
        val confirmedFields = LinkedHashMap<String, LocalOfferFieldValue>()
        val clearedFieldCodes = mutableListOf<String>()
        current.fieldInputs.forEach { (rawCode, rawValue) ->
            val code = rawCode.trim().lowercase()
            if (code.isEmpty()) return@forEach
            val value = rawValue.trim()
            val baseline = mergedFields[code]
            val confirmed = draft.confirmedUserFields[code]
            if (value.isEmpty()) {
                if (confirmed != null) {
                    clearedFieldCodes += code
                }
                return@forEach
            }
            confirmedFields[code] = when {
                confirmed != null && value == confirmed.toEditorText() -> confirmed
                baseline != null && value == baseline.toEditorText() -> baseline
                else -> value.toFieldValue(baseline)
            }
        }

        return LocalOfferReviewUpdateRequest(
            correlationId = current.session?.correlationId ?: newCorrelationId(),
            revision = draft.revision,
            confirmedCategoryCode = current.selectedCategoryCode ?: draft.confirmedCategoryCode,
            confirmedFields = confirmedFields,
            clearedFieldCodes = clearedFieldCodes.distinct(),
            commercials = buildCommercialsOrEmitError(current),
        )
    }

    private fun buildCommercialsOrEmitError(current: LocalOfferUiState): LocalOfferCommercials? {
        val priceRaw = current.priceInput.trim()
        val ttlRaw = current.ttlDaysInput.trim()
        val price = if (priceRaw.isBlank()) {
            null
        } else {
            priceRaw.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
        }
        if (priceRaw.isNotBlank() && price == null) {
            _state.update {
                it.copy(
                    submitting = false,
                    publishing = false,
                    errorMessage = "Цена должна быть положительным числом.",
                )
            }
            return null
        }
        val ttlDays = if (ttlRaw.isBlank()) {
            null
        } else {
            ttlRaw.toIntOrNull()?.takeIf { it > 0 }
        }
        if (ttlRaw.isNotBlank() && ttlDays == null) {
            _state.update {
                it.copy(
                    submitting = false,
                    publishing = false,
                    errorMessage = "Срок размещения должен быть целым числом больше нуля.",
                )
            }
            return null
        }
        return LocalOfferCommercials(
            priceMajor = price,
            currency = current.currencyInput.trim().uppercase().takeIf { it.isNotEmpty() },
            ttlPresetDays = ttlDays,
            city = current.geoSnapshot?.city,
            deliveryChannel = current.deliveryInput.trim().takeIf { it.isNotEmpty() },
            description = current.descriptionInput.trim().takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun applyEnvelope(
        session: LocalOfferSession,
        geoSnapshot: LocalOfferGeoSnapshot?,
        envelope: LocalOfferDraftEnvelope,
        origin: DraftInputOrigin?,
        preview: LocalOfferPreviewResponse?,
        preflight: LocalOfferPublishPreflightResponse?,
        publishResult: LocalOfferPublishResult?,
        publishing: Boolean = false,
        persistSession: Boolean = true,
        syncDraftCard: Boolean = true,
    ) {
        val effectiveGeoSnapshot = preflight?.geoSnapshot ?: preview?.geoSnapshot ?: geoSnapshot
        val blockedState = publishResult.toBlockedStateOrNull()
            ?: preflight.toBlockedStateOrNull()
            ?: effectiveGeoSnapshot.toGeoBlockedStateOrNull()
        val activeStep = resolveActiveStep(
            draft = envelope.draft,
            geoSnapshot = effectiveGeoSnapshot,
            preview = preview,
            preflight = preflight,
            publishResult = publishResult,
            blockedState = blockedState,
        )
        if (persistSession) {
            persistRuntime(
                session = session,
                draft = envelope.draft,
                geoSnapshot = effectiveGeoSnapshot,
                flowStep = activeStep,
            )
        }
        if (syncDraftCard) {
            createdStore.update(envelope.draft.toDraftCard())
        }
        _state.update {
            it.copy(
                loading = false,
                submitting = false,
                publishing = publishing,
                origin = origin,
                session = session,
                geoSnapshot = effectiveGeoSnapshot,
                draftEnvelope = envelope,
                preview = preview,
                preflight = preflight,
                publishResult = publishResult,
                selectedCategoryCode = envelope.draft.confirmedCategoryCode
                    ?: envelope.draft.candidateCategory?.code
                    ?: envelope.draft.resolvedCategoryCode,
                fieldInputs = buildFieldInputs(envelope.draft),
                reviewInsights = buildReviewInsights(envelope),
                priceInput = envelope.draft.commercials.priceMajor?.toPlainInput().orEmpty(),
                currencyInput = envelope.draft.commercials.currency ?: "USD",
                ttlDaysInput = envelope.draft.commercials.ttlPresetDays?.toString() ?: "30",
                deliveryInput = envelope.draft.commercials.deliveryChannel.orEmpty(),
                descriptionInput = envelope.draft.commercials.description.orEmpty(),
                errorMessage = null,
                blockedState = blockedState,
                activeStep = activeStep,
            )
        }
        syncAiRefreshPolling(
            session = session,
            origin = origin,
            geoSnapshot = effectiveGeoSnapshot,
            envelope = envelope,
        )
    }

    private suspend fun persistRuntime(
        session: LocalOfferSession,
        draft: LocalOfferDraftSession?,
        geoSnapshot: LocalOfferGeoSnapshot?,
        flowStep: LocalOfferFlowStep?,
    ) {
        sessionStore.set(
            LocalOfferFlowSession(
                sessionId = session.sessionId,
                correlationId = session.correlationId,
                draftId = draft?.draftId ?: session.draftId,
                geoSnapshot = geoSnapshot,
                flowStep = flowStep,
            ),
        )
    }

    private fun syncAiRefreshPolling(
        session: LocalOfferSession,
        origin: DraftInputOrigin?,
        geoSnapshot: LocalOfferGeoSnapshot?,
        envelope: LocalOfferDraftEnvelope,
    ) {
        if (!envelope.draft.aiRefreshPending) {
            if (aiRefreshPollingDraftId != envelope.draft.draftId) {
                aiRefreshPollJob?.cancel()
            }
            aiRefreshPollJob = null
            aiRefreshPollingDraftId = null
            return
        }
        val draftId = envelope.draft.draftId
        if (aiRefreshPollJob?.isActive == true && aiRefreshPollingDraftId == draftId) return

        aiRefreshPollJob?.cancel()
        aiRefreshPollingDraftId = draftId
        aiRefreshPollJob = viewModelScope.launch {
            val maxAttempts = (aiRefreshTimeoutMillis / aiRefreshPollIntervalMillis)
                .toInt()
                .coerceAtLeast(1)
            repeat(maxAttempts) {
                delay(aiRefreshPollIntervalMillis)
                val latest = runCatching { getDraft(draftId) }.getOrNull() ?: return@repeat
                val activeSession = state.value.session ?: session
                val activeOrigin = state.value.origin ?: origin
                val activeGeo = state.value.geoSnapshot ?: geoSnapshot
                applyEnvelope(
                    session = activeSession,
                    geoSnapshot = activeGeo,
                    envelope = latest,
                    origin = activeOrigin,
                    preview = null,
                    preflight = null,
                    publishResult = null,
                )
                if (!latest.draft.aiRefreshPending) {
                    return@launch
                }
            }
            if (state.value.draftEnvelope?.draft?.draftId == draftId &&
                state.value.draftEnvelope?.draft?.aiRefreshPending == true
            ) {
                _state.update {
                    it.copy(errorMessage = "Заполнение по фото заняло больше времени, чем обычно. Подождите ещё немного.")
                }
            }
        }
    }

    override fun onCleared() {
        aiRefreshPollJob?.cancel()
        super.onCleared()
    }

    private fun buildFieldInputs(draft: LocalOfferDraftSession): Map<String, String> {
        val merged = draft.mergedFields()
        val orderedCodes = linkedSetOf<String>()
        orderedCodes += "title"
        orderedCodes += "brand"
        orderedCodes += "model"
        orderedCodes += draft.missingRequiredFields.map { it.trim().lowercase() }
        orderedCodes += merged.keys.map { it.trim().lowercase() }
        return orderedCodes.associateWithTo(LinkedHashMap()) { code ->
            merged[code]?.toEditorText().orEmpty()
        }
    }

    private fun buildReviewInsights(envelope: LocalOfferDraftEnvelope): LocalOfferReviewInsightsUi? {
        val review = envelope.visionReview ?: return null
        val draft = envelope.draft
        val merged = draft.mergedFields()
        val raw = review.rawExtraction
        val bind = review.bindOutcome

        val recognized = buildList {
            raw?.categoryHint?.takeIf { it.isNotBlank() }?.let { value ->
                add(LocalOfferReviewInsightFactUi(label = "Категория по фото", value = value))
            }
            raw?.title?.takeIf { it.isNotBlank() }?.let { value ->
                add(LocalOfferReviewInsightFactUi(label = "Название", value = value))
            }
            raw?.brand?.takeIf { it.isNotBlank() }?.let { value ->
                add(LocalOfferReviewInsightFactUi(label = "Бренд", value = value))
            }
            raw?.model?.takeIf { it.isNotBlank() }?.let { value ->
                add(LocalOfferReviewInsightFactUi(label = "Модель", value = value))
            }
            raw?.attributes
                .orEmpty()
                .distinctBy { candidate -> candidate.code.trim().lowercase() }
                .take(6)
                .forEach { candidate ->
                    candidate.toReviewValue()?.let { value ->
                        add(
                            LocalOfferReviewInsightFactUi(
                                label = candidate.code.toReviewLabel(),
                                value = value,
                            ),
                        )
                    }
                }
        }

        val accepted = buildList {
            draft.resolvedCategoryCode?.takeIf { it.isNotBlank() }?.let { code ->
                add(
                    LocalOfferReviewInsightFactUi(
                        label = "Категория профиля",
                        value = draft.candidateCategory?.title ?: code,
                    ),
                )
            }
            bind?.acceptedAttributeCodes
                .orEmpty()
                .distinct()
                .forEach { code ->
                    val value = merged[code.trim().lowercase()]?.toEditorText()?.takeIf { it.isNotBlank() }
                        ?: return@forEach
                    add(
                        LocalOfferReviewInsightFactUi(
                            label = code.toReviewLabel(),
                            value = value,
                        ),
                    )
                }
        }

        val unresolved = linkedSetOf<String>()
        bind?.unresolvedAttributeCodes
            .orEmpty()
            .distinct()
            .forEach { code ->
                val rawCandidate = raw?.attributes.orEmpty()
                    .firstOrNull { candidate ->
                        candidate.code.trim().equals(code.trim(), ignoreCase = true)
                    }
                val suffix = rawCandidate?.toReviewValue()?.takeIf { it.isNotBlank() }
                unresolved += if (suffix != null) {
                    "${code.toReviewLabel()}: $suffix"
                } else {
                    code.toReviewLabel()
                }
            }
        draft.missingRequiredFields
            .map { code -> code.trim().lowercase() }
            .filter { code -> code.isNotBlank() }
            .forEach { code ->
                unresolved += "${code.toReviewLabel()}: не указано"
            }

        if (recognized.isEmpty() && accepted.isEmpty() && unresolved.isEmpty()) return null
        return LocalOfferReviewInsightsUi(
            recognized = recognized,
            accepted = accepted,
            unresolved = unresolved.toList(),
        )
    }

    private fun LocalOfferDraftSession.mergedFields(): Map<String, LocalOfferFieldValue> =
        LinkedHashMap<String, LocalOfferFieldValue>().apply {
            putAll(predictedFields.mapKeys { it.key.trim().lowercase() })
            putAll(confirmedUserFields.mapKeys { it.key.trim().lowercase() })
        }

    private fun LocalOfferDraftSession.toDraftCard(): UserOfferCardUi {
        val merged = mergedFields()
        val title = merged["title"]?.toEditorText()
            ?.takeIf { it.isNotBlank() }
            ?: candidateCategory?.title
            ?: "Черновик объявления"
        return UserOfferCardUi(
            id = draftId,
            title = title,
            category = candidateCategory?.title ?: resolvedCategoryCode,
            priceMajor = commercials.priceMajor,
            currency = commercials.currency ?: "USD",
            status = UserOfferStatus.DRAFT,
            publicationStatus = UserOfferPublicationStatus.PUBLISHED,
            coverUrl = media.firstOrNull()?.storageUrl,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun LocalOfferPublishResult.toUserOfferCard(
        draft: LocalOfferDraftSession,
    ): UserOfferCardUi {
        val merged = draft.mergedFields()
        val title = merged["title"]?.toEditorText()
            ?.takeIf { it.isNotBlank() }
            ?: draft.candidateCategory?.title
            ?: "Новое объявление"
        return UserOfferCardUi(
            id = offerId ?: draft.draftId,
            title = title,
            category = draft.candidateCategory?.title ?: draft.resolvedCategoryCode,
            priceMajor = draft.commercials.priceMajor,
            currency = draft.commercials.currency ?: "USD",
            status = if (publicationState == LocalOfferPublicationState.REMOVED) {
                UserOfferStatus.FINISHED
            } else {
                UserOfferStatus.ACTIVE
            },
            publicationStatus = publicationState.toPublicationStatus(),
            coverUrl = draft.media.firstOrNull()?.storageUrl,
            publishedAtMillis = publishedAtMillis,
            updatedAtMillis = publishedAtMillis,
        )
    }

    private fun LocalOfferPublicationState?.toPublicationStatus(): UserOfferPublicationStatus =
        when (this) {
            LocalOfferPublicationState.PENDING_REVIEW -> UserOfferPublicationStatus.ON_MODERATION
            LocalOfferPublicationState.REJECTED -> UserOfferPublicationStatus.ERROR
            else -> UserOfferPublicationStatus.PUBLISHED
        }

    private fun LocalOfferFieldValue.toEditorText(): String = when (kind) {
        LocalOfferFieldKind.SCALAR -> displayValue ?: normalizedValue ?: canonicalValueCode.orEmpty()
        LocalOfferFieldKind.MULTI -> values.joinToString(", ") { atom ->
            atom.displayValue.ifBlank { atom.normalizedValue ?: atom.canonicalValueCode.orEmpty() }
        }
    }.trim()

    private fun LocalOfferVisionAttributeCandidate.toReviewValue(): String? {
        val textValue = text?.trim()
        if (!textValue.isNullOrBlank()) return textValue

        val numericValue = number
        if (numericValue != null) {
            val longValue = numericValue.toLong()
            return if (longValue.toDouble() == numericValue) {
                longValue.toString()
            } else {
                numericValue.toString()
            }
        }

        val booleanValue = bool
        if (booleanValue != null) {
            return if (booleanValue) "Да" else "Нет"
        }

        return null
    }

    private fun String.toReviewLabel(): String =
        split('_', '-', '.')
            .filter { token -> token.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase() else char.toString()
                }
            }
            .ifBlank { this }

    private fun String.toFieldValue(baseline: LocalOfferFieldValue?): LocalOfferFieldValue {
        val normalized = trim()
        val useMulti = baseline?.kind == LocalOfferFieldKind.MULTI || normalized.contains(',')
        return if (useMulti) {
            val atoms = normalized.split(',')
                .mapNotNull { token ->
                    val value = token.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    LocalOfferFieldAtom(displayValue = value)
                }
            LocalOfferFieldValue(
                kind = LocalOfferFieldKind.MULTI,
                valueType = baseline?.valueType ?: LocalOfferValueType.STRING,
                values = atoms,
            )
        } else {
            LocalOfferFieldValue(
                kind = LocalOfferFieldKind.SCALAR,
                valueType = baseline?.valueType ?: LocalOfferValueType.STRING,
                displayValue = normalized,
            )
        }
    }

    private fun Double.toPlainInput(): String {
        val longValue = toLong()
        return if (longValue.toDouble() == this) longValue.toString() else toString()
    }

    private fun resolveActiveStep(
        draft: LocalOfferDraftSession?,
        geoSnapshot: LocalOfferGeoSnapshot?,
        preview: LocalOfferPreviewResponse?,
        preflight: LocalOfferPublishPreflightResponse?,
        publishResult: LocalOfferPublishResult?,
        blockedState: LocalOfferBlockedState?,
    ): LocalOfferFlowStep = when {
        blockedState != null -> LocalOfferFlowStep.BLOCKED_STATE
        publishResult?.outcome == LocalOfferPublishOutcome.PUBLISHED -> LocalOfferFlowStep.PUBLISHED
        draft?.shouldShowAiNormalizationStep() == true -> LocalOfferFlowStep.AI_NORMALIZATION_RUN
        preflight != null -> LocalOfferFlowStep.PUBLISH_PREFLIGHT
        preview != null -> LocalOfferFlowStep.PREVIEW
        draft != null -> draft.toOwnedStep()
        geoSnapshot != null -> LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
        else -> LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE
    }

    private fun LocalOfferDraftSession.toOwnedStep(): LocalOfferFlowStep = when {
        shouldShowAiNormalizationStep() -> LocalOfferFlowStep.AI_NORMALIZATION_RUN
        else -> when (stage) {
        com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage.REVIEW_READY,
        com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage.NEEDS_ENRICHMENT,
            -> LocalOfferFlowStep.DRAFT_REVIEW

        com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage.PREVIEW_READY ->
            LocalOfferFlowStep.PREVIEW

        com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage.PUBLISHED ->
            LocalOfferFlowStep.PUBLISHED
        }
    }

    private fun LocalOfferDraftSession.shouldShowAiNormalizationStep(): Boolean =
        aiRefreshPending &&
            predictedFields.isEmpty() &&
            confirmedUserFields.isEmpty() &&
            media.isNotEmpty()

    private fun String.toCanonicalLocalOfferDraftIdOrNull(): String? =
        LocalOfferDraftIds.canonicalOrNull(this)

    private fun requireFreshGeoSnapshot(snapshot: LocalOfferGeoSnapshot?): LocalOfferGeoSnapshot? {
        val actual = snapshot ?: run {
            showGeoUnavailableBlockedState()
            return null
        }
        if (actual.freshnessState != LocalOfferGeoFreshnessState.FRESH) {
            showGeoRefreshRequiredBlockedState()
            return null
        }
        return actual
    }

    private fun publishedOfferEditBlockedState(): LocalOfferBlockedState =
        LocalOfferBlockedState(
            title = "Редактирование пока недоступно",
            body = "Для уже размещённого объявления ещё нет отдельного сценария редактирования. Откройте карточку объявления или создайте новое.",
        )

    private fun LocalOfferPublishPreflightResponse?.toBlockedStateOrNull(): LocalOfferBlockedState? {
        if (this == null) return null
        val routedIssues = blockingIssues.filter { issue -> issue.targetNode == LocalOfferNode.BLOCKED_STATE }
        if (nextRoutes.defaultFixNode != LocalOfferNode.BLOCKED_STATE && routedIssues.isEmpty()) {
            return null
        }
        val issues = if (routedIssues.isNotEmpty()) routedIssues else blockingIssues
        return LocalOfferBlockedState(
            title = "Нужно исправить несколько моментов",
            body = issues.firstOrNull()?.message
                ?: "Сейчас объявление нельзя продолжить. Исправьте проблему и попробуйте снова.",
            issues = issues,
        )
    }

    private fun LocalOfferPublishResult?.toBlockedStateOrNull(): LocalOfferBlockedState? {
        if (this == null || outcome != LocalOfferPublishOutcome.BLOCKED) return null
        val topIssue = issue ?: return null
        if (topIssue.targetNode != LocalOfferNode.BLOCKED_STATE) return null
        return LocalOfferBlockedState(
            title = "Публикация остановлена",
            body = topIssue.message,
            issues = listOf(topIssue),
        )
    }

    private fun geoPermissionBlockedState(): LocalOfferBlockedState =
        LocalOfferBlockedState(
            title = "Нужна геолокация",
            body = "Локальное объявление привязано к вашему текущему местоположению. Разрешите доступ к геолокации, чтобы продолжить.",
            action = LocalOfferBlockedAction.REQUEST_DEVICE_LOCATION,
            actionLabel = "Разрешить геолокацию",
        )

    private fun geoSettingsBlockedState(): LocalOfferBlockedState =
        LocalOfferBlockedState(
            title = "Включите геолокацию в настройках",
            body = "Доступ к местоположению для приложения отключён. Включите его в настройках и вернитесь сюда.",
            action = LocalOfferBlockedAction.OPEN_APP_SETTINGS,
            actionLabel = "Открыть настройки",
        )

    private fun geoUnavailableBlockedState(): LocalOfferBlockedState =
        LocalOfferBlockedState(
            title = "Не удалось определить местоположение",
            body = "Проверьте, что на устройстве включена геолокация и есть точный сигнал, затем попробуйте ещё раз.",
            action = LocalOfferBlockedAction.REQUEST_DEVICE_LOCATION,
            actionLabel = "Повторить",
        )

    private fun geoRefreshBlockedState(): LocalOfferBlockedState =
        LocalOfferBlockedState(
            title = "Нужно обновить местоположение",
            body = "Чтобы разместить локальное объявление, нужно заново получить ваше текущее местоположение.",
            action = LocalOfferBlockedAction.REQUEST_DEVICE_LOCATION,
            actionLabel = "Обновить местоположение",
        )

    private fun LocalOfferGeoSnapshot?.toGeoBlockedStateOrNull(): LocalOfferBlockedState? = when (this?.freshnessState) {
        null, LocalOfferGeoFreshnessState.FRESH -> null
        LocalOfferGeoFreshnessState.STALE, LocalOfferGeoFreshnessState.EXPIRED -> geoRefreshBlockedState()
    }

    private fun newCorrelationId(): String = "locorr-${UUID.randomUUID()}"

    companion object {
        val defaultPhotoRoles: List<LocalOfferPhotoRole> = listOf(
            LocalOfferPhotoRole.FRONT,
            LocalOfferPhotoRole.BACK,
            LocalOfferPhotoRole.LEFT,
            LocalOfferPhotoRole.RIGHT,
            LocalOfferPhotoRole.TOP,
            LocalOfferPhotoRole.BOTTOM,
            LocalOfferPhotoRole.TECH_1,
            LocalOfferPhotoRole.TECH_2,
        )
        private const val DEFAULT_COUNTRY_CODE = "RU"
        private const val DEVICE_GEO_ACCURACY_METERS = 50.0
        private const val DEFAULT_AI_REFRESH_POLL_INTERVAL_MILLIS = 1_200L
        private const val DEFAULT_AI_REFRESH_TIMEOUT_MILLIS = 18_000L
    }
}

fun recommendedLocalOfferPhotoRole(
    draft: LocalOfferDraftSession?,
): LocalOfferPhotoRole {
    val taskRole = draft?.evidenceTasks
        ?.firstOrNull { task -> task.photoRole != null && task.status.name in setOf("OPEN", "UNRESOLVED") }
        ?.photoRole
    if (taskRole != null) return taskRole
    val usedRoles = draft?.media?.map { it.role }.orEmpty()
    return LocalOfferViewModel.defaultPhotoRoles.firstOrNull { role -> role !in usedRoles }
        ?: LocalOfferPhotoRole.TECH_1
}

fun orderedLocalOfferFieldCodes(
    state: LocalOfferUiState,
    draft: LocalOfferDraftSession,
): List<String> {
    val priority = linkedSetOf<String>()
    priority += "title"
    priority += "brand"
    priority += "model"
    priority += draft.missingRequiredFields.map { it.trim().lowercase() }
    priority += state.fieldInputs.keys
    priority += draft.confirmedUserFields.keys.map { it.trim().lowercase() }
    priority += draft.predictedFields.keys.map { it.trim().lowercase() }
    return priority.toList()
}

fun LocalOfferDraftSession.primaryIssues(): List<String> =
    issues
        .sortedByDescending { issue -> if (issue.severity == LocalOfferIssueSeverity.ERROR) 1 else 0 }
        .map { issue -> issue.message }
