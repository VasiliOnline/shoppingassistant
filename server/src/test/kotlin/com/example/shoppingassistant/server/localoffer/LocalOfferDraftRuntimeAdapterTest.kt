package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftEnvelope
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftSession
import com.example.shoppingassistant.domain.shortlisting.ShortListingStage
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionAttributeCandidate
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionBindOutcome
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionRawExtraction
import com.example.shoppingassistant.domain.shortlisting.ShortListingVisionResult
import com.example.shoppingassistant.server.shortlisting.ShortListingBackendService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LocalOfferDraftRuntimeAdapterTest {

    @Test
    fun get_draft_maps_pending_ai_refresh_and_run_id_to_localoffer() = runBlocking {
        val adapter = LocalOfferDraftRuntimeAdapter(
            shortListingBackendService = object : ShortListingBackendService {
                override suspend fun listDrafts(userId: Long): List<ShortListingDraftSession> = emptyList()

                override suspend fun createDraft(
                    userId: Long,
                    request: com.example.shoppingassistant.domain.shortlisting.ShortListingCreateDraftRequest,
                ): ShortListingDraftEnvelope = error("unused")

                override suspend fun getDraft(userId: Long, draftId: String): ShortListingDraftEnvelope =
                    ShortListingDraftEnvelope(
                        draft = ShortListingDraftSession(
                            draftId = "sldraft-1",
                            sessionId = "slsess-1",
                            userId = userId.toString(),
                            stage = ShortListingStage.READY_FOR_REVIEW,
                            aiRefreshPending = true,
                            safeToExit = true,
                            createdAtMillis = 1L,
                            updatedAtMillis = 2L,
                            revision = 3,
                        ),
                        visionResult = ShortListingVisionResult(
                            attemptId = "vision-attempt-1",
                            serverRequestId = "vision-job-1",
                            draftId = "sldraft-1",
                            status = ShortListingStage.ANALYZING,
                            rawExtraction = ShortListingVisionRawExtraction(
                                categoryHint = "Компьютерная мышь",
                                brand = "Logitech",
                                model = "MX Master 3S",
                                title = "Беспроводная мышь",
                                attributes = listOf(
                                    ShortListingVisionAttributeCandidate(
                                        code = "connection_type",
                                        text = "Wireless",
                                        confidence = 0.91f,
                                    ),
                                ),
                            ),
                            bindOutcome = ShortListingVisionBindOutcome(
                                rawCategoryHint = "Компьютерная мышь",
                                resolvedCategoryCode = "TECH.PC_COMPONENTS",
                                acceptedAttributeCodes = listOf("connection_type"),
                                unresolvedAttributeCodes = listOf("color"),
                                missingRequiredKeys = listOf("color"),
                            ),
                            nextAction = "review",
                            warnings = listOf("brand_unresolved"),
                        ),
                    )

                override suspend fun updateDraftReview(
                    userId: Long,
                    draftId: String,
                    request: com.example.shoppingassistant.domain.shortlisting.ShortListingReviewUpdateRequest,
                ): ShortListingDraftEnvelope = error("unused")

                override suspend fun getPublishPreflight(
                    userId: Long,
                    draftId: String,
                ): com.example.shoppingassistant.domain.shortlisting.ShortListingPublishPreflight = error("unused")

                override suspend fun publishDraft(
                    userId: Long,
                    draftId: String,
                ): com.example.shoppingassistant.domain.shortlisting.ShortListingPublishAttemptResult = error("unused")
            },
        )

        val draft = adapter.getDraft(userId = 7L, draftId = "lodraft-1")

        assertNotNull(draft)
        assertEquals("lodraft-1", draft.draft.draftId)
        assertTrue(draft.draft.aiRefreshPending)
        assertEquals("vision-attempt-1", draft.runId)
        assertNotNull(draft.visionReview)
        assertEquals("Компьютерная мышь", draft.visionReview?.rawExtraction?.categoryHint)
        assertEquals("Logitech", draft.visionReview?.rawExtraction?.brand)
        assertEquals("TECH.PC_COMPONENTS", draft.visionReview?.bindOutcome?.resolvedCategoryCode)
        assertEquals(listOf("color"), draft.visionReview?.bindOutcome?.unresolvedAttributeCodes)
    }
}
