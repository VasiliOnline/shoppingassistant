package com.example.shoppingassistant.feature.pages.localoffer

import com.example.shoppingassistant.domain.localoffer.CreateLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.CreateOrResumeLocalOfferSessionTask
import com.example.shoppingassistant.domain.localoffer.ConfirmLocalOfferGeoSnapshotTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPreviewTask
import com.example.shoppingassistant.domain.localoffer.GetLocalOfferPublishPreflightTask
import com.example.shoppingassistant.domain.localoffer.LocalOfferCommercials
import com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldValue
import com.example.shoppingassistant.domain.localoffer.LocalOfferFlowStep
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoConsentState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoFreshnessState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoSnapshot
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoStatus
import com.example.shoppingassistant.domain.localoffer.LocalOfferMediaReceipt
import com.example.shoppingassistant.domain.localoffer.LocalOfferModerationDecision
import com.example.shoppingassistant.domain.localoffer.LocalOfferNode
import com.example.shoppingassistant.domain.localoffer.LocalOfferPhotoRole
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewHero
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewSummary
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublicationState
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishCommandRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishResult
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreflightRoutes
import com.example.shoppingassistant.domain.localoffer.LocalOfferRepository
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferRuntimeEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionAttributeCandidate
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionBindOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionRawExtraction
import com.example.shoppingassistant.domain.localoffer.LocalOfferVisionReview
import com.example.shoppingassistant.domain.localoffer.PublishLocalOfferDraftTask
import com.example.shoppingassistant.domain.localoffer.UpdateLocalOfferDraftReviewTask
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferCardUi
import com.example.shoppingassistant.feature.pages.useroffers.tasks.UserOffersCreatedStore
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LocalOfferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun open_draft_blocks_legacy_sldraft_public_entry() = runTest {
        val repository = FakeLocalOfferRepository()
        val viewModel = repository.createViewModel()

        viewModel.openDraft(draftId = "sldraft-1", origin = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LocalOfferFlowStep.BLOCKED_STATE, state.activeStep)
        assertNotNull(state.blockedState)
        assertNull(state.session)
        assertEquals(0, repository.createOrResumeSessionCalls)
        assertEquals(0, repository.getDraftCalls)
    }

    @Test
    fun open_draft_restores_preflight_from_persisted_flow_step() = runTest {
        val repository = FakeLocalOfferRepository().apply {
            storedSession = LocalOfferFlowSession(
                sessionId = sampleSession.sessionId,
                correlationId = sampleSession.correlationId,
                draftId = sampleDraft.draftId,
                geoSnapshot = sampleGeoSnapshot,
                flowStep = LocalOfferFlowStep.PUBLISH_PREFLIGHT,
            )
        }
        val viewModel = repository.createViewModel()

        viewModel.openDraft(draftId = null, origin = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LocalOfferFlowStep.PUBLISH_PREFLIGHT, state.activeStep)
        assertNotNull(state.preview)
        assertNotNull(state.preflight)
        assertEquals(1, repository.createOrResumeSessionCalls)
        assertEquals(1, repository.getDraftCalls)
        assertEquals(1, repository.getPreviewCalls)
        assertEquals(1, repository.getPublishPreflightCalls)
    }

    @Test
    fun open_draft_with_stale_geo_routes_to_blocked_state() = runTest {
        val staleGeoSnapshot = FakeLocalOfferRepository().sampleGeoSnapshot.copy(
            draftId = null,
            freshnessState = LocalOfferGeoFreshnessState.STALE,
        )
        val repository = FakeLocalOfferRepository().apply {
            sessionDraftId = null
            storedSession = LocalOfferFlowSession(
                sessionId = sampleSession.sessionId,
                correlationId = sampleSession.correlationId,
                draftId = null,
                geoSnapshot = staleGeoSnapshot,
                flowStep = LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
            )
        }
        val viewModel = repository.createViewModel()

        viewModel.openDraft(draftId = null, origin = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LocalOfferFlowStep.BLOCKED_STATE, state.activeStep)
        assertEquals(LocalOfferBlockedAction.REQUEST_DEVICE_LOCATION, state.blockedState?.action)
        assertEquals("Нужно обновить местоположение", state.blockedState?.title)
    }

    @Test
    fun open_draft_without_geo_starts_from_primary_photo_capture() = runTest {
        val repository = FakeLocalOfferRepository().apply {
            sessionDraftId = null
            storedSession = null
        }
        val viewModel = repository.createViewModel()

        viewModel.openDraft(draftId = null, origin = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE, state.activeStep)
        assertNull(state.blockedState)
        assertNull(state.geoSnapshot)
    }

    @Test
    fun dismiss_blocked_state_with_stale_geo_returns_to_primary_photo_capture() = runTest {
        val staleGeoSnapshot = FakeLocalOfferRepository().sampleGeoSnapshot.copy(
            draftId = null,
            freshnessState = LocalOfferGeoFreshnessState.STALE,
        )
        val repository = FakeLocalOfferRepository().apply {
            sessionDraftId = null
            storedSession = LocalOfferFlowSession(
                sessionId = sampleSession.sessionId,
                correlationId = sampleSession.correlationId,
                draftId = null,
                geoSnapshot = staleGeoSnapshot,
                flowStep = LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE,
            )
        }
        val viewModel = repository.createViewModel()

        viewModel.openDraft(draftId = null, origin = null)
        advanceUntilIdle()
        viewModel.dismissBlockedState()

        val state = viewModel.state.value
        assertEquals(LocalOfferFlowStep.PRIMARY_PHOTO_CAPTURE, state.activeStep)
        assertNull(state.blockedState)
    }

    @Test
    fun open_draft_polls_pending_ai_refresh_until_review_is_ready() = runTest {
        val repository = FakeLocalOfferRepository().apply {
            val pendingDraft = sampleDraft.copy(
                stage = LocalOfferDraftStage.REVIEW_READY,
                aiRefreshPending = true,
                revision = 3,
            )
            val readyDraft = pendingDraft.copy(
                aiRefreshPending = false,
                revision = 4,
            )
            queuedDrafts += sampleDraftEnvelope.copy(draft = pendingDraft)
            queuedDrafts += sampleDraftEnvelope.copy(
                draft = readyDraft,
                envelope = sampleDraftEnvelope.envelope.copy(revision = readyDraft.revision),
            )
        }
        val viewModel = repository.createViewModel(
            aiRefreshPollIntervalMillis = 1L,
            aiRefreshTimeoutMillis = 10L,
        )

        viewModel.openDraft(draftId = "lodraft-1", origin = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(LocalOfferFlowStep.DRAFT_REVIEW, state.activeStep)
        assertEquals(false, state.draftEnvelope?.draft?.aiRefreshPending)
        assertTrue(repository.getDraftCalls >= 2)
    }

    @Test
    fun open_draft_exposes_review_insights_from_vision_review() = runTest {
        val repository = FakeLocalOfferRepository().apply {
            val reviewDraft = sampleDraft.copy(
                stage = LocalOfferDraftStage.REVIEW_READY,
                resolvedCategoryCode = "TECH.PC_COMPONENTS",
                predictedFields = mapOf(
                    "brand" to LocalOfferFieldValue(displayValue = "Logitech"),
                    "model" to LocalOfferFieldValue(displayValue = "MX Master 3S"),
                    "connection_type" to LocalOfferFieldValue(displayValue = "Wireless"),
                ),
                missingRequiredFields = listOf("color"),
            )
            queuedDrafts += sampleDraftEnvelope.copy(
                draft = reviewDraft,
                envelope = sampleDraftEnvelope.envelope.copy(revision = reviewDraft.revision),
                visionReview = LocalOfferVisionReview(
                    runId = "vision-attempt-1",
                    rawExtraction = LocalOfferVisionRawExtraction(
                        categoryHint = "Компьютерная мышь",
                        brand = "Logitech",
                        model = "MX Master 3S",
                        title = "Беспроводная мышь",
                        attributes = listOf(
                            LocalOfferVisionAttributeCandidate(
                                code = "connection_type",
                                text = "Wireless",
                                confidence = 0.9f,
                            ),
                            LocalOfferVisionAttributeCandidate(
                                code = "color",
                                text = "Graphite",
                                confidence = 0.55f,
                            ),
                        ),
                    ),
                    bindOutcome = LocalOfferVisionBindOutcome(
                        rawCategoryHint = "Компьютерная мышь",
                        resolvedCategoryCode = "TECH.PC_COMPONENTS",
                        acceptedAttributeCodes = listOf("brand", "model", "connection_type"),
                        unresolvedAttributeCodes = listOf("color"),
                        missingRequiredKeys = listOf("color"),
                    ),
                ),
            )
        }
        val viewModel = repository.createViewModel()

        viewModel.openDraft(draftId = "lodraft-1", origin = null)
        advanceUntilIdle()

        val state = viewModel.state.value
        val insights = requireNotNull(state.reviewInsights)
        assertEquals(LocalOfferFlowStep.DRAFT_REVIEW, state.activeStep)
        assertTrue(insights.recognized.any { it.label == "Категория по фото" && it.value == "Компьютерная мышь" })
        assertTrue(insights.accepted.any { it.label == "Brand" && it.value == "Logitech" })
        assertTrue(insights.accepted.any { it.label == "Connection Type" && it.value == "Wireless" })
        assertTrue(insights.unresolved.any { it.startsWith("Color:") })
    }

    private class FakeLocalOfferRepository : LocalOfferRepository {
        val sampleSession = LocalOfferSession(
            sessionId = "losess-1",
            correlationId = "locorr-1",
            draftId = "lodraft-1",
        )
        val sampleGeoSnapshot = LocalOfferGeoSnapshot(
            geoSnapshotId = "geo-1",
            sessionId = sampleSession.sessionId,
            draftId = "lodraft-1",
            consentState = LocalOfferGeoConsentState.GRANTED,
            status = LocalOfferGeoStatus.CAPTURED,
            capturedAtMillis = 100L,
            expiresAtMillis = 200_000L,
            freshnessState = LocalOfferGeoFreshnessState.FRESH,
            accuracyMeters = 20.0,
            countryCode = "RU",
            adminArea = "Moscow",
            city = "Moscow",
            lat = 55.75,
            lon = 37.61,
            source = "device",
        )
        val sampleDraft = LocalOfferDraftSession(
            draftId = "lodraft-1",
            sessionId = sampleSession.sessionId,
            stage = LocalOfferDraftStage.PREVIEW_READY,
            safeToExit = true,
            createdAtMillis = 1L,
            updatedAtMillis = 2L,
            revision = 3,
            commercials = LocalOfferCommercials(
                priceMajor = 1000.0,
                currency = "USD",
                city = "Moscow",
            ),
            media = listOf(
                LocalOfferMediaReceipt(
                    mediaId = "media-1",
                    role = LocalOfferPhotoRole.FRONT,
                    storageUrl = "/tmp/front.jpg",
                    sha256 = "sha",
                    sizeBytes = 123L,
                    createdAtMillis = 1L,
                ),
            ),
            activeGeoSnapshotId = sampleGeoSnapshot.geoSnapshotId,
        )
        val sampleDraftEnvelope = LocalOfferDraftEnvelope(
            draft = sampleDraft,
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = sampleSession.sessionId,
                correlationId = sampleSession.correlationId,
                draftId = sampleDraft.draftId,
                revision = sampleDraft.revision,
                geoSnapshotId = sampleGeoSnapshot.geoSnapshotId,
            ),
        )
        val samplePreview = LocalOfferPreviewResponse(
            envelope = sampleDraftEnvelope.envelope,
            hero = LocalOfferPreviewHero(
                title = "iPhone 15",
                priceLabel = "1000 USD",
                categoryCode = "TECH.PHONES",
                city = "Moscow",
            ),
            summary = LocalOfferPreviewSummary(),
            effectiveSpecVersion = "test-spec",
            geoSnapshot = sampleGeoSnapshot,
        )
        val samplePreflight = LocalOfferPublishPreflightResponse(
            envelope = sampleDraftEnvelope.envelope.copy(
                publishCommandId = "lopub-1",
                effectiveSpecVersion = "test-spec",
            ),
            effectiveSpecVersion = "test-spec",
            publishAllowed = true,
            geoSnapshot = sampleGeoSnapshot,
            moderationDecision = LocalOfferModerationDecision.REVIEW_REQUIRED,
            nextRoutes = LocalOfferPreflightRoutes(defaultFixNode = LocalOfferNode.PUBLISH_PREFLIGHT),
        )

        var storedSession: LocalOfferFlowSession? = null
        var sessionDraftId: String? = sampleSession.draftId
        val queuedDrafts: ArrayDeque<LocalOfferDraftEnvelope> = ArrayDeque()
        var createOrResumeSessionCalls: Int = 0
        var getDraftCalls: Int = 0
        var getPreviewCalls: Int = 0
        var getPublishPreflightCalls: Int = 0

        fun createViewModel(
            aiRefreshPollIntervalMillis: Long = 1_200L,
            aiRefreshTimeoutMillis: Long = 18_000L,
        ): LocalOfferViewModel =
            LocalOfferViewModel(
                createOrResumeSession = CreateOrResumeLocalOfferSessionTask(this),
                confirmGeoSnapshot = ConfirmLocalOfferGeoSnapshotTask(this),
                createDraft = CreateLocalOfferDraftTask(this),
                getDraft = GetLocalOfferDraftTask(this),
                updateDraftReview = UpdateLocalOfferDraftReviewTask(this),
                getPreview = GetLocalOfferPreviewTask(this),
                getPublishPreflight = GetLocalOfferPublishPreflightTask(this),
                publishDraft = PublishLocalOfferDraftTask(this),
                createdStore = FakeCreatedStore(),
                sessionStore = FakeSessionStore(::storedSession),
                aiRefreshPollIntervalMillis = aiRefreshPollIntervalMillis,
                aiRefreshTimeoutMillis = aiRefreshTimeoutMillis,
            )

        override suspend fun createOrResumeSession(request: LocalOfferSessionRequest): LocalOfferSession {
            createOrResumeSessionCalls += 1
            return sampleSession.copy(draftId = sessionDraftId)
        }

        override suspend fun confirmGeoSnapshot(request: LocalOfferConfirmGeoSnapshotRequest): LocalOfferGeoSnapshot =
            sampleGeoSnapshot

        override suspend fun listDrafts(): List<LocalOfferDraftSession> = listOf(sampleDraft)

        override suspend fun createDraft(request: LocalOfferCreateDraftRequest): LocalOfferDraftEnvelope =
            sampleDraftEnvelope

        override suspend fun getDraft(draftId: String): LocalOfferDraftEnvelope? {
            getDraftCalls += 1
            val queued = if (queuedDrafts.isEmpty()) null else queuedDrafts.removeFirst()
            return queued?.takeIf { it.draft.draftId == draftId }
                ?: sampleDraftEnvelope.takeIf { it.draft.draftId == draftId }
        }

        override suspend fun updateDraftReview(
            draftId: String,
            request: LocalOfferReviewUpdateRequest,
        ): LocalOfferDraftEnvelope = sampleDraftEnvelope

        override suspend fun getPreview(
            draftId: String,
            request: LocalOfferPreviewRequest,
        ): LocalOfferPreviewResponse {
            getPreviewCalls += 1
            return samplePreview
        }

        override suspend fun getPublishPreflight(
            draftId: String,
            request: LocalOfferPublishPreflightRequest,
        ): LocalOfferPublishPreflightResponse {
            getPublishPreflightCalls += 1
            return samplePreflight
        }

        override suspend fun publishDraft(
            draftId: String,
            request: LocalOfferPublishCommandRequest,
        ): LocalOfferPublishResult =
            LocalOfferPublishResult(
                envelope = samplePreflight.envelope,
                outcome = LocalOfferPublishOutcome.PUBLISHED,
                publicationState = LocalOfferPublicationState.PENDING_REVIEW,
                offerId = "offer-1",
                publishedAtMillis = 10L,
            )
    }

    private class FakeCreatedStore : UserOffersCreatedStore {
        override val items: StateFlow<List<UserOfferCardUi>> = MutableStateFlow(emptyList())
        override val errorMessage: StateFlow<String?> = MutableStateFlow(null)
        override fun add(offer: UserOfferCardUi) = Unit
        override fun update(offer: UserOfferCardUi) = Unit
        override fun remove(offerId: String) = Unit
        override fun replaceId(oldId: String, newId: String) = Unit
        override fun replaceAll(items: List<UserOfferCardUi>) = Unit
        override fun clear() = Unit
    }

    private class FakeSessionStore(
        private val stateProvider: () -> LocalOfferFlowSession?,
    ) : LocalOfferFlowSessionStore {
        var saved: LocalOfferFlowSession? = null

        override suspend fun get(): LocalOfferFlowSession? = stateProvider()

        override suspend fun set(session: LocalOfferFlowSession) {
            saved = session
        }

        override suspend fun clear() {
            saved = null
        }
    }

    class MainDispatcherRule : TestWatcher() {
        private val dispatcher = StandardTestDispatcher()

        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
