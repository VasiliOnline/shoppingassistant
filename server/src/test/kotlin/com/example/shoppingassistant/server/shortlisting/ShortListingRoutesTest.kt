package com.example.shoppingassistant.server.shortlisting

import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftSession
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishBlocker
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishPreflight
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishReadiness
import com.example.shoppingassistant.domain.shortlisting.ShortListingStage
import com.example.shoppingassistant.server.auth.SessionManager
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class ShortListingRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<SessionManager> { FixedSessionManager(token = TEST_TOKEN, userId = TEST_USER_ID) }
                    single<ShortListingBackendService> { FakeShortListingBackendService() }
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
            routing { shortListingRoutes() }
        }

        val response = client.get("/api/shortlisting/drafts")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun drafts_endpoint_returns_server_bound_drafts_for_authorized_user() = testApplication {
        application {
            configureSerialization()
            routing { shortListingRoutes() }
        }

        val response = client.get("/api/shortlisting/drafts") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val drafts = json.decodeFromString(
            ListSerializer(ShortListingDraftSession.serializer()),
            response.bodyAsText(),
        )
        assertEquals(1, drafts.size)
        assertEquals("draft-1", drafts.single().draftId)
        assertEquals(ShortListingStage.READY_FOR_REVIEW, drafts.single().stage)
    }

    @Test
    fun publish_endpoint_returns_conflict_with_preflight_when_blocked() = testApplication {
        application {
            configureSerialization()
            routing { shortListingRoutes() }
        }

        val response = client.post("/api/shortlisting/drafts/draft-1/publish") {
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("CATEGORY_CONFIRMATION_REQUIRED"))
        assertTrue(body.contains("preflight"))
    }

    private class FakeShortListingBackendService : ShortListingBackendService {
        override suspend fun listDrafts(userId: Long): List<ShortListingDraftSession> =
            listOf(sampleDraft())

        override suspend fun createDraft(
            userId: Long,
            request: com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest,
        ) = throw UnsupportedOperationException()

        override suspend fun getDraft(
            userId: Long,
            draftId: String,
        ) = throw UnsupportedOperationException()

        override suspend fun updateDraftReview(
            userId: Long,
            draftId: String,
            request: com.example.shoppingassistant.domain.shortlisting.ShortListingReviewUpdateRequest,
        ) = throw UnsupportedOperationException()

        override suspend fun getPublishPreflight(
            userId: Long,
            draftId: String,
        ): ShortListingPublishPreflight = ShortListingPublishPreflight(
            draftId = draftId,
            eligible = false,
            blockers = listOf(
                ShortListingPublishBlocker(
                    code = "CATEGORY_CONFIRMATION_REQUIRED",
                    message = "Подтвердите категорию.",
                ),
            ),
        )

        override suspend fun publishDraft(
            userId: Long,
            draftId: String,
        ): com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult =
            com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult.Blocked(
                getPublishPreflight(userId, draftId),
            )

        private fun sampleDraft(): ShortListingDraftSession =
            ShortListingDraftSession(
                draftId = "draft-1",
                sessionId = "session-1",
                userId = TEST_USER_ID.toString(),
                stage = ShortListingStage.READY_FOR_REVIEW,
                safeToExit = true,
                createdAtMillis = 1L,
                updatedAtMillis = 2L,
                publishReadiness = ShortListingPublishReadiness(ready = false),
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
        const val TEST_TOKEN = "shortlisting-test-token"
        const val TEST_USER_ID = 42L
    }
}
