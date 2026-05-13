package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LocalOfferServiceBoundaryTest {

    @Test
    fun create_or_resume_session_rejects_legacy_public_draft_namespace() = runBlocking {
        val service = LocalOfferBackendServiceImpl(NoopDraftRuntime())

        val error = assertFailsWith<LocalOfferValidationException> {
            service.createOrResumeSession(
                userId = 42L,
                request = LocalOfferSessionRequest(
                    correlationId = "corr-1",
                    draftId = "sldraft-1",
                ),
            )
        }

        assertEquals("DRAFT_ID_MUST_USE_CANONICAL_NAMESPACE", error.message)
    }

    @Test
    fun get_draft_rejects_legacy_public_draft_namespace() = runBlocking {
        val service = LocalOfferBackendServiceImpl(NoopDraftRuntime())

        val error = assertFailsWith<LocalOfferValidationException> {
            service.getDraft(userId = 42L, draftId = "sldraft-1")
        }

        assertEquals("DRAFT_ID_MUST_USE_CANONICAL_NAMESPACE", error.message)
    }
}

private class NoopDraftRuntime : LocalOfferDraftRuntime {
    override suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession> = emptyList()

    override suspend fun createDraft(
        userId: Long,
        request: LocalOfferCreateDraftRequest,
    ): LocalOfferRuntimeDraft = error("Not needed")

    override suspend fun getDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimeDraft? = null

    override suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferRuntimeDraft = error("Not needed")

    override suspend fun getPublishPreflight(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimePreflight = error("Not needed")

    override suspend fun publishDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimePublishAttempt = error("Not needed")

    override fun toStorageDraftId(canonicalDraftId: String): String = canonicalDraftId

    override fun toPublicDraftId(storageDraftId: String): String = storageDraftId
}
