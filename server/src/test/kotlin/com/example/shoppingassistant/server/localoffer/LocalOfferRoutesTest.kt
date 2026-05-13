package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssue
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssueSeverity
import com.example.shoppingassistant.domain.localoffer.LocalOfferNode
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreviewRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishCommandRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishResult
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublicationState
import com.example.shoppingassistant.domain.localoffer.LocalOfferRuntimeEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoConsentState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoFreshnessState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoSnapshot
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoStatus
import com.example.shoppingassistant.domain.localoffer.LocalOfferModerationDecision
import com.example.shoppingassistant.domain.localoffer.LocalOfferPreflightRoutes
import com.example.shoppingassistant.server.auth.SessionManager
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class LocalOfferRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<SessionManager> { FixedSessionManager(token = TEST_TOKEN, userId = TEST_USER_ID) }
                    single<LocalOfferBackendService> { FakeLocalOfferBackendService() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun drafts_endpoint_requires_authorization() = testApplication {
        application {
            configureSerialization()
            routing { localOfferRoutes() }
        }

        val response = client.get("/api/localoffer/drafts")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun drafts_endpoint_returns_canonical_localoffer_payload() = testApplication {
        application {
            configureSerialization()
            routing { localOfferRoutes() }
        }

        val response = client.get("/api/localoffer/drafts") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val drafts = json.decodeFromString(
            ListSerializer(LocalOfferDraftSession.serializer()),
            response.bodyAsText(),
        )
        assertEquals(1, drafts.size)
        assertEquals("lodraft-1", drafts.single().draftId)
        assertEquals(LocalOfferDraftStage.REVIEW_READY, drafts.single().stage)
    }

    @Test
    fun publish_endpoint_returns_conflict_when_runtime_blocks_publish() = testApplication {
        application {
            configureSerialization()
            routing { localOfferRoutes() }
        }

        val response = client.post("/api/localoffer/drafts/lodraft-1/publish") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    LocalOfferPublishCommandRequest(
                        correlationId = "corr-1",
                        revision = 3,
                        effectiveSpecVersion = "test-spec",
                        geoSnapshotId = "geo-1",
                        publishCommandId = "pub-1",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("publish_revision_conflict") || body.contains("geo_stale"))
    }

    private class FakeLocalOfferBackendService : LocalOfferBackendService {
        override suspend fun createOrResumeSession(
            userId: Long,
            request: LocalOfferSessionRequest,
        ): LocalOfferSession = LocalOfferSession(
            sessionId = request.sessionId ?: "session-1",
            correlationId = request.correlationId,
            draftId = request.draftId,
        )

        override suspend fun confirmGeoSnapshot(
            userId: Long,
            request: com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest,
        ): LocalOfferGeoSnapshot = sampleGeoSnapshot()

        override suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession> =
            listOf(sampleDraft())

        override suspend fun createDraft(
            userId: Long,
            request: com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest,
        ) = throw UnsupportedOperationException()

        override suspend fun getDraft(
            userId: Long,
            draftId: String,
        ) = throw UnsupportedOperationException()

        override suspend fun updateDraftReview(
            userId: Long,
            draftId: String,
            request: com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest,
        ) = throw UnsupportedOperationException()

        override suspend fun getPreview(
            userId: Long,
            draftId: String,
            request: LocalOfferPreviewRequest,
        ) = throw UnsupportedOperationException()

        override suspend fun getPublishPreflight(
            userId: Long,
            draftId: String,
            request: LocalOfferPublishPreflightRequest,
        ): LocalOfferPublishPreflightResponse = LocalOfferPublishPreflightResponse(
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = "session-1",
                correlationId = request.correlationId,
                draftId = draftId,
                revision = request.revision,
                publishCommandId = "pub-1",
                geoSnapshotId = request.geoSnapshotId,
            ),
            effectiveSpecVersion = "test-spec",
            publishAllowed = false,
            blockingIssues = listOf(
                LocalOfferIssue(
                    issueCode = "geo_stale",
                    severity = LocalOfferIssueSeverity.ERROR,
                    message = "Refresh geo",
                    ownerNode = LocalOfferNode.GEO_CONSENT_GATE,
                    targetNode = LocalOfferNode.GEO_CONSENT_GATE,
                ),
            ),
            warnings = emptyList(),
            geoSnapshot = sampleGeoSnapshot(),
            moderationDecision = LocalOfferModerationDecision.HOLD,
            nextRoutes = LocalOfferPreflightRoutes(defaultFixNode = LocalOfferNode.GEO_CONSENT_GATE),
        )

        override suspend fun publishDraft(
            userId: Long,
            draftId: String,
            request: LocalOfferPublishCommandRequest,
        ): LocalOfferPublishResult = LocalOfferPublishResult(
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = "session-1",
                correlationId = request.correlationId,
                draftId = draftId,
                revision = request.revision,
                publishCommandId = request.publishCommandId,
                geoSnapshotId = request.geoSnapshotId,
            ),
            outcome = LocalOfferPublishOutcome.BLOCKED,
            issue = LocalOfferIssue(
                issueCode = "publish_revision_conflict",
                severity = LocalOfferIssueSeverity.ERROR,
                message = "Refresh preview",
                ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
                targetNode = LocalOfferNode.PREVIEW,
            ),
        )

        private fun sampleDraft(): LocalOfferDraftSession =
            LocalOfferDraftSession(
                draftId = "lodraft-1",
                sessionId = "session-1",
                stage = LocalOfferDraftStage.REVIEW_READY,
                safeToExit = true,
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
                revision = 3,
                activeGeoSnapshotId = "geo-1",
                publicationState = LocalOfferPublicationState.PENDING_REVIEW,
            )
    }

    private class FixedSessionManager(
        private val token: String,
        private val userId: Long,
    ) : SessionManager {
        override fun createSession(userId: Long): String = token

        override fun getUserId(token: String): Long? =
            if (token == this.token) userId else null

        override fun invalidate(token: String) = Unit

        override fun invalidateAllForUser(userId: Long) = Unit
    }

    private companion object {
        const val TEST_TOKEN = "localoffer-test-token"
        const val TEST_USER_ID = 42L

        fun sampleGeoSnapshot(): LocalOfferGeoSnapshot =
            LocalOfferGeoSnapshot(
                geoSnapshotId = "geo-1",
                sessionId = "session-1",
                consentState = LocalOfferGeoConsentState.GRANTED,
                status = LocalOfferGeoStatus.CAPTURED,
                capturedAtMillis = 100L,
                expiresAtMillis = 200L,
                freshnessState = LocalOfferGeoFreshnessState.FRESH,
                accuracyMeters = 10.0,
                countryCode = "RU",
                adminArea = "Moscow",
                city = "Moscow",
                lat = 55.75,
                lon = 37.61,
                source = "gps",
            )
    }
}
