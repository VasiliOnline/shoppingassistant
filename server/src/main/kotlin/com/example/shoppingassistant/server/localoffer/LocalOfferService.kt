package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftIds
import com.example.shoppingassistant.domain.localoffer.LocalOfferCategoryCandidate
import com.example.shoppingassistant.domain.localoffer.LocalOfferCommercials
import com.example.shoppingassistant.domain.localoffer.LocalOfferConfidenceBand
import com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceBlockingLevel
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceStatus
import com.example.shoppingassistant.domain.localoffer.LocalOfferEvidenceTask
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldAtom
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldKind
import com.example.shoppingassistant.domain.localoffer.LocalOfferFieldValue
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoConsentState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoFreshnessState
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoSnapshot
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoStatus
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssue
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssueSeverity
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
import com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferRuntimeEnvelope
import com.example.shoppingassistant.domain.localoffer.LocalOfferSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionState
import com.example.shoppingassistant.domain.localoffer.LocalOfferValueType
import com.example.shoppingassistant.server.offers.OFFER_PUBLICATION_STATE_PENDING_REVIEW
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import java.util.UUID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

interface LocalOfferBackendService {
    suspend fun createOrResumeSession(userId: Long, request: LocalOfferSessionRequest): LocalOfferSession
    suspend fun confirmGeoSnapshot(userId: Long, request: LocalOfferConfirmGeoSnapshotRequest): LocalOfferGeoSnapshot
    suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession>
    suspend fun createDraft(userId: Long, request: LocalOfferCreateDraftRequest): LocalOfferDraftEnvelope
    suspend fun getDraft(userId: Long, draftId: String): LocalOfferDraftEnvelope?
    suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferDraftEnvelope
    suspend fun getPreview(
        userId: Long,
        draftId: String,
        request: LocalOfferPreviewRequest,
    ): LocalOfferPreviewResponse
    suspend fun getPublishPreflight(
        userId: Long,
        draftId: String,
        request: LocalOfferPublishPreflightRequest,
    ): LocalOfferPublishPreflightResponse
    suspend fun publishDraft(
        userId: Long,
        draftId: String,
        request: LocalOfferPublishCommandRequest,
    ): LocalOfferPublishResult
}

class LocalOfferBackendServiceImpl(
    private val draftRuntime: LocalOfferDraftRuntime,
) : LocalOfferBackendService {

    override suspend fun createOrResumeSession(
        userId: Long,
        request: LocalOfferSessionRequest,
    ): LocalOfferSession {
        val correlationId = request.correlationId.trim().takeIf { it.isNotEmpty() }
            ?: throw LocalOfferValidationException("CORRELATION_ID_REQUIRED")
        val canonicalDraftId = requireCanonicalDraftIdOrNull(request.draftId)
        val existing = request.sessionId?.trim()?.takeIf { it.isNotEmpty() }?.let { sessionId ->
            loadSession(userId = userId, sessionId = sessionId)
        } ?: canonicalDraftId?.let { draftId ->
            loadSessionByDraftId(userId = userId, draftId = toStorageDraftId(draftId))
        }
        if (existing != null) {
            return existing.toSession(correlationId = correlationId)
        }

        val now = System.currentTimeMillis()
        val sessionId = request.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: generateId("losess")
        val draftId = canonicalDraftId?.let(::toStorageDraftId)
        persistSession(
            SessionRecord(
                sessionId = sessionId,
                userId = userId,
                currentDraftId = draftId,
                state = LocalOfferSessionState.ACTIVE,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
        return LocalOfferSession(
            sessionId = sessionId,
            correlationId = correlationId,
            draftId = draftId?.let(::toCanonicalDraftId),
            state = LocalOfferSessionState.ACTIVE,
            effectiveSpecVersion = CatalogDataVersion.current,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    override suspend fun confirmGeoSnapshot(
        userId: Long,
        request: LocalOfferConfirmGeoSnapshotRequest,
    ): LocalOfferGeoSnapshot {
        val session = loadSession(
            userId = userId,
            sessionId = request.sessionId.trim(),
        ) ?: throw LocalOfferNotFoundException("SESSION_NOT_FOUND")
        val correlationId = request.correlationId.trim().takeIf { it.isNotEmpty() }
            ?: throw LocalOfferValidationException("CORRELATION_ID_REQUIRED")
        if (correlationId.isEmpty()) {
            throw LocalOfferValidationException("CORRELATION_ID_REQUIRED")
        }
        val now = System.currentTimeMillis()
        val consentState = request.consentState
        val normalizedCity = request.city.trim().takeIf { it.isNotEmpty() }
        val normalizedCountryCode = request.countryCode.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: throw LocalOfferValidationException("COUNTRY_CODE_REQUIRED")
        val snapshot = when (consentState) {
            LocalOfferGeoConsentState.GRANTED -> {
                val city = normalizedCity ?: throw LocalOfferValidationException("CITY_REQUIRED")
                LocalOfferGeoSnapshot(
                    geoSnapshotId = generateId("logeos"),
                    sessionId = session.sessionId,
                    draftId = request.draftId?.trim()?.takeIf { it.isNotEmpty() }?.let(::toStorageDraftId) ?: session.currentDraftId,
                    consentState = LocalOfferGeoConsentState.GRANTED,
                    status = LocalOfferGeoStatus.CAPTURED,
                    capturedAtMillis = now,
                    expiresAtMillis = now + GEO_SNAPSHOT_TTL_MILLIS,
                    freshnessState = LocalOfferGeoFreshnessState.FRESH,
                    accuracyMeters = request.accuracyMeters.coerceAtLeast(0.0),
                    countryCode = normalizedCountryCode,
                    adminArea = request.adminArea?.trim()?.takeIf { it.isNotEmpty() },
                    city = city,
                    lat = request.lat,
                    lon = request.lon,
                    source = request.source.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_GEO_SOURCE,
                )
            }

            LocalOfferGeoConsentState.DENIED,
            LocalOfferGeoConsentState.REVOKED,
                -> LocalOfferGeoSnapshot(
                    geoSnapshotId = generateId("logeos"),
                    sessionId = session.sessionId,
                    draftId = request.draftId?.trim()?.takeIf { it.isNotEmpty() }?.let(::toStorageDraftId) ?: session.currentDraftId,
                    consentState = consentState,
                    status = LocalOfferGeoStatus.PERMISSION_DENIED,
                    capturedAtMillis = now,
                    expiresAtMillis = now,
                    freshnessState = LocalOfferGeoFreshnessState.EXPIRED,
                    accuracyMeters = request.accuracyMeters.coerceAtLeast(0.0),
                    countryCode = normalizedCountryCode,
                    adminArea = request.adminArea?.trim()?.takeIf { it.isNotEmpty() },
                    city = normalizedCity ?: GEO_DENIED_CITY_PLACEHOLDER,
                    lat = null,
                    lon = null,
                    source = request.source.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_GEO_SOURCE,
                )
        }
        persistGeoSnapshot(userId = userId, snapshot = snapshot, now = now)
        return snapshot
    }

    override suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession> {
        val drafts = draftRuntime.listDrafts(userId)
        if (drafts.isEmpty()) return emptyList()
        val storageDraftIds = drafts.map { draft -> toStorageDraftId(draft.draftId) }
        val sessionByDraftId = loadSessionsByDraftIds(userId, storageDraftIds)
        val geoByDraftId = loadLatestGeoSnapshotsByDraftIds(userId, storageDraftIds)
        return drafts.map { draft ->
            val storageDraftId = toStorageDraftId(draft.draftId)
            draft.copy(
                sessionId = sessionByDraftId[storageDraftId]?.sessionId ?: draft.sessionId,
                activeGeoSnapshotId = geoByDraftId[storageDraftId]?.geoSnapshotId,
            )
        }
    }

    override suspend fun createDraft(
        userId: Long,
        request: LocalOfferCreateDraftRequest,
    ): LocalOfferDraftEnvelope {
        val correlationId = request.correlationId.trim().takeIf { it.isNotEmpty() }
            ?: throw LocalOfferValidationException("CORRELATION_ID_REQUIRED")
        val session = loadSession(
            userId = userId,
            sessionId = request.sessionId.trim(),
        ) ?: throw LocalOfferNotFoundException("SESSION_NOT_FOUND")
        val geoSnapshot = loadOwnedGeoSnapshot(
            userId = userId,
            geoSnapshotId = request.geoSnapshotId.trim(),
        ) ?: throw LocalOfferNotFoundException("GEO_SNAPSHOT_NOT_FOUND")
        val validatedGeo = ensureGeoAvailableForSession(
            session = session,
            snapshot = geoSnapshot,
            draftId = null,
        )
        if (request.photos.isEmpty()) {
            throw LocalOfferValidationException("AT_LEAST_ONE_PHOTO_REQUIRED")
        }

        val envelope = draftRuntime.createDraft(
            userId = userId,
            request = request.copy(
                commercials = request.commercials.withGeoCity(validatedGeo.city),
            ),
        )
        val now = System.currentTimeMillis()
        val storageDraftId = toStorageDraftId(envelope.draft.draftId)
        bindSessionToDraft(sessionId = session.sessionId, draftId = storageDraftId, now = now)
        attachGeoSnapshotToDraft(geoSnapshotId = validatedGeo.geoSnapshotId, draftId = storageDraftId, now = now)
        return envelope.toPublicEnvelope(
            sessionId = session.sessionId,
            geoSnapshot = validatedGeo,
            correlationId = correlationId,
            effectiveSpecVersion = CatalogDataVersion.current,
        )
    }

    override suspend fun getDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferDraftEnvelope? {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val storageDraftId = toStorageDraftId(canonicalDraftId)
        val envelope = draftRuntime.getDraft(userId, canonicalDraftId)
            ?: return null
        val session = loadSessionByDraftId(userId = userId, draftId = storageDraftId)
        val geoSnapshot = loadLatestGeoSnapshot(
            userId = userId,
            draftId = storageDraftId,
            sessionId = session?.sessionId ?: envelope.draft.sessionId,
        )
        return envelope.toPublicEnvelope(
            sessionId = session?.sessionId ?: envelope.draft.sessionId,
            geoSnapshot = geoSnapshot,
            correlationId = session?.sessionId ?: envelope.draft.sessionId,
            effectiveSpecVersion = CatalogDataVersion.current,
        )
    }

    override suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: LocalOfferReviewUpdateRequest,
    ): LocalOfferDraftEnvelope {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val storageDraftId = toStorageDraftId(canonicalDraftId)
        val existing = draftRuntime.getDraft(userId, canonicalDraftId)
            ?: throw LocalOfferNotFoundException("DRAFT_NOT_FOUND")
        ensureDraftRevision(
            expectedRevision = existing.draft.revision,
            actualRevision = request.revision,
        )
        val session = loadSessionByDraftId(userId = userId, draftId = storageDraftId)
        val geoSnapshot = loadLatestGeoSnapshot(
            userId = userId,
            draftId = storageDraftId,
            sessionId = session?.sessionId ?: existing.draft.sessionId,
        )
        val updated = draftRuntime.updateDraftReview(
            userId = userId,
            draftId = canonicalDraftId,
            request = request.copy(
                commercials = request.commercials?.let { locals ->
                    locals.withGeoCity(geoSnapshot?.city ?: existing.draft.commercials.city)
                },
            ),
        )
        return updated.toPublicEnvelope(
            sessionId = session?.sessionId ?: updated.draft.sessionId,
            geoSnapshot = geoSnapshot,
            correlationId = request.correlationId,
            effectiveSpecVersion = CatalogDataVersion.current,
        )
    }

    override suspend fun getPreview(
        userId: Long,
        draftId: String,
        request: LocalOfferPreviewRequest,
    ): LocalOfferPreviewResponse {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val storageDraftId = toStorageDraftId(canonicalDraftId)
        val envelope = draftRuntime.getDraft(userId, canonicalDraftId)
            ?: throw LocalOfferNotFoundException("DRAFT_NOT_FOUND")
        ensureDraftRevision(
            expectedRevision = envelope.draft.revision,
            actualRevision = request.revision,
        )
        val geoSnapshot = loadOwnedGeoSnapshot(
            userId = userId,
            geoSnapshotId = request.geoSnapshotId.trim(),
        ) ?: throw LocalOfferNotFoundException("GEO_SNAPSHOT_NOT_FOUND")
        val session = loadSessionByDraftId(userId = userId, draftId = storageDraftId)
        val validatedGeo = ensureGeoAvailableForSession(
            session = session ?: SessionRecord(
                sessionId = envelope.draft.sessionId,
                userId = userId,
                currentDraftId = storageDraftId,
                state = LocalOfferSessionState.ACTIVE,
                createdAtMillis = envelope.draft.createdAtMillis,
                updatedAtMillis = envelope.draft.updatedAtMillis,
            ),
            snapshot = geoSnapshot,
            draftId = storageDraftId,
        )
        val runtimePreflight = draftRuntime.getPublishPreflight(userId, canonicalDraftId)
        val blockingIssues = runtimePreflight.blockingIssues + buildGeoIssues(validatedGeo)
        val warnings = runtimePreflight.warnings
        return LocalOfferPreviewResponse(
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = session?.sessionId ?: envelope.draft.sessionId,
                correlationId = request.correlationId,
                draftId = canonicalDraftId,
                revision = envelope.draft.revision,
                runId = envelope.runId,
                categoryCode = envelope.draft.resolvedCategoryCode,
                effectiveSpecVersion = CatalogDataVersion.current,
                geoSnapshotId = validatedGeo.geoSnapshotId,
            ),
            hero = buildPreviewHero(envelope.draft, runtimePreflight, validatedGeo),
            summary = LocalOfferPreviewSummary(
                blockingIssues = blockingIssues,
                nonBlockingNotes = warnings,
            ),
            effectiveSpecVersion = CatalogDataVersion.current,
            geoSnapshot = validatedGeo,
        )
    }

    override suspend fun getPublishPreflight(
        userId: Long,
        draftId: String,
        request: LocalOfferPublishPreflightRequest,
    ): LocalOfferPublishPreflightResponse =
        buildCanonicalPreflight(
            userId = userId,
            draftId = draftId,
            correlationId = request.correlationId,
            revision = request.revision,
            geoSnapshotId = request.geoSnapshotId,
            createPublishCommand = true,
        )

    override suspend fun publishDraft(
        userId: Long,
        draftId: String,
        request: LocalOfferPublishCommandRequest,
    ): LocalOfferPublishResult {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val storageDraftId = toStorageDraftId(canonicalDraftId)
        val command = loadPublishCommand(
            userId = userId,
            publishCommandId = request.publishCommandId.trim(),
        ) ?: throw LocalOfferConflictException("PUBLISH_COMMAND_NOT_FOUND")
        if (command.draftId != storageDraftId) {
            throw LocalOfferConflictException("PUBLISH_COMMAND_DRAFT_MISMATCH")
        }
        if (command.revision != request.revision) {
            throw LocalOfferConflictException("PUBLISH_REVISION_CONFLICT")
        }
        if (command.effectiveSpecVersion != request.effectiveSpecVersion) {
            throw LocalOfferConflictException("PUBLISH_EFFECTIVE_SPEC_MISMATCH")
        }
        if (command.geoSnapshotId != request.geoSnapshotId) {
            throw LocalOfferConflictException("PUBLISH_GEO_SNAPSHOT_MISMATCH")
        }
        if (command.state == PublishCommandState.SUCCEEDED) {
            return command.toPublishResult(correlationId = request.correlationId)
        }
        if (command.state == PublishCommandState.BLOCKED) {
            return LocalOfferPublishResult(
                envelope = LocalOfferRuntimeEnvelope(
                    sessionId = loadSessionByDraftId(userId, storageDraftId)?.sessionId ?: UNKNOWN_SESSION_ID,
                    correlationId = request.correlationId,
                    draftId = canonicalDraftId,
                    revision = request.revision,
                    effectiveSpecVersion = request.effectiveSpecVersion,
                    publishCommandId = request.publishCommandId,
                    geoSnapshotId = request.geoSnapshotId,
                ),
                outcome = LocalOfferPublishOutcome.BLOCKED,
                issue = command.failureCode?.let(::mapCommandFailure),
            )
        }

        val preflight = buildCanonicalPreflight(
            userId = userId,
            draftId = canonicalDraftId,
            correlationId = request.correlationId,
            revision = request.revision,
            geoSnapshotId = request.geoSnapshotId,
            createPublishCommand = false,
            expectedPublishCommandId = request.publishCommandId,
        )
        if (!preflight.publishAllowed) {
            val topIssue = preflight.blockingIssues.firstOrNull()
            markPublishCommandBlocked(
                publishCommandId = request.publishCommandId,
                failureCode = topIssue?.issueCode,
            )
            return LocalOfferPublishResult(
                envelope = preflight.envelope.copy(publishCommandId = request.publishCommandId),
                outcome = LocalOfferPublishOutcome.BLOCKED,
                issue = topIssue,
            )
        }

        markPublishCommandSubmitted(request.publishCommandId)
        val result = draftRuntime.publishDraft(userId = userId, draftId = canonicalDraftId)
        return when (result) {
            is LocalOfferRuntimePublishAttempt.Published -> {
                val offerId = result.offerId
                val now = System.currentTimeMillis()
                markPublishCommandSucceeded(
                    publishCommandId = request.publishCommandId,
                    offerId = offerId.toLongOrNull(),
                    now = now,
                )
                markSessionPublished(userId = userId, draftId = storageDraftId, now = now)
                LocalOfferPublishResult(
                    envelope = preflight.envelope.copy(publishCommandId = request.publishCommandId),
                    outcome = LocalOfferPublishOutcome.PUBLISHED,
                    publicationState = result.publicationState,
                    offerId = offerId,
                    publishedAtMillis = result.publishedAtMillis,
                )
            }

            is LocalOfferRuntimePublishAttempt.Blocked -> {
                val topIssue = result.blockingIssues.firstOrNull()
                markPublishCommandBlocked(
                    publishCommandId = request.publishCommandId,
                    failureCode = topIssue?.issueCode,
                )
                LocalOfferPublishResult(
                    envelope = preflight.envelope.copy(publishCommandId = request.publishCommandId),
                    outcome = LocalOfferPublishOutcome.BLOCKED,
                    issue = topIssue,
                )
            }
        }
    }

    private suspend fun buildCanonicalPreflight(
        userId: Long,
        draftId: String,
        correlationId: String,
        revision: Int,
        geoSnapshotId: String,
        createPublishCommand: Boolean,
        expectedPublishCommandId: String? = null,
    ): LocalOfferPublishPreflightResponse {
        val canonicalDraftId = requireCanonicalDraftId(draftId)
        val storageDraftId = toStorageDraftId(canonicalDraftId)
        val envelope = draftRuntime.getDraft(userId, canonicalDraftId)
            ?: throw LocalOfferNotFoundException("DRAFT_NOT_FOUND")
        ensureDraftRevision(expectedRevision = envelope.draft.revision, actualRevision = revision)
        val session = loadSessionByDraftId(userId = userId, draftId = storageDraftId)
        val geoSnapshot = loadOwnedGeoSnapshot(userId = userId, geoSnapshotId = geoSnapshotId.trim())
            ?: throw LocalOfferNotFoundException("GEO_SNAPSHOT_NOT_FOUND")
        val validatedGeo = ensureGeoAvailableForSession(
            session = session ?: SessionRecord(
                sessionId = envelope.draft.sessionId,
                userId = userId,
                currentDraftId = storageDraftId,
                state = LocalOfferSessionState.ACTIVE,
                createdAtMillis = envelope.draft.createdAtMillis,
                updatedAtMillis = envelope.draft.updatedAtMillis,
            ),
            snapshot = geoSnapshot,
            draftId = storageDraftId,
        )
        val runtimePreflight = draftRuntime.getPublishPreflight(userId = userId, draftId = canonicalDraftId)
        val blockingIssues = runtimePreflight.blockingIssues + buildGeoIssues(validatedGeo)
        val warnings = runtimePreflight.warnings
        val publishAllowed = blockingIssues.isEmpty() && runtimePreflight.normalizedPayload != null
        val publishCommandId = when {
            publishAllowed && expectedPublishCommandId != null -> expectedPublishCommandId
            publishAllowed && createPublishCommand -> createPublishCommand(
                userId = userId,
                draftId = storageDraftId,
                revision = revision,
                effectiveSpecVersion = CatalogDataVersion.current,
                geoSnapshotId = validatedGeo.geoSnapshotId,
            )
            else -> null
        }
        return LocalOfferPublishPreflightResponse(
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = session?.sessionId ?: envelope.draft.sessionId,
                correlationId = correlationId,
                draftId = canonicalDraftId,
                revision = revision,
                runId = envelope.runId,
                categoryCode = envelope.draft.resolvedCategoryCode,
                effectiveSpecVersion = CatalogDataVersion.current,
                publishCommandId = publishCommandId,
                geoSnapshotId = validatedGeo.geoSnapshotId,
            ),
            effectiveSpecVersion = CatalogDataVersion.current,
            publishAllowed = publishAllowed,
            blockingIssues = blockingIssues,
            warnings = warnings,
            geoSnapshot = validatedGeo,
            moderationDecision = runtimePreflight.moderationDecision,
            nextRoutes = buildPreflightRoutes(blockingIssues),
        )
    }

    private fun buildPreviewHero(
        draft: LocalOfferDraftSession,
        preflight: LocalOfferRuntimePreflight,
        geoSnapshot: LocalOfferGeoSnapshot,
    ): LocalOfferPreviewHero {
        val title = preflight.normalizedPayload?.title
            ?: draft.confirmedUserFields["title"]?.displayValue
            ?: draft.predictedFields["title"]?.displayValue
            ?: draft.candidateCategory?.title
            ?: draft.resolvedCategoryCode
            ?: "Черновик товара"
        val priceLabel = draft.commercials.priceMajor?.let { price ->
            val currency = draft.commercials.currency?.takeIf { it.isNotBlank() } ?: "USD"
            "$price $currency"
        }
        return LocalOfferPreviewHero(
            primaryPhotoUrl = draft.media.firstOrNull()?.storageUrl,
            title = title,
            priceLabel = priceLabel,
            categoryCode = draft.resolvedCategoryCode,
            city = geoSnapshot.city,
        )
    }

    private fun buildPreflightRoutes(issues: List<LocalOfferIssue>): LocalOfferPreflightRoutes {
        val defaultFixNode = issues.firstOrNull()?.targetNode ?: LocalOfferNode.PUBLISH_PREFLIGHT
        return LocalOfferPreflightRoutes(
            defaultFixNode = defaultFixNode,
            issueRoutes = issues.associate { it.issueCode to it.targetNode },
        )
    }

    private fun LocalOfferRuntimeDraft.toPublicEnvelope(
        sessionId: String,
        geoSnapshot: LocalOfferGeoSnapshot?,
        correlationId: String,
        effectiveSpecVersion: String,
    ): LocalOfferDraftEnvelope =
        LocalOfferDraftEnvelope(
            draft = draft.copy(
                sessionId = sessionId,
                effectiveSpecVersion = effectiveSpecVersion,
                activeGeoSnapshotId = geoSnapshot?.geoSnapshotId,
            ),
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = sessionId,
                correlationId = correlationId,
                draftId = draft.draftId,
                revision = draft.revision,
                runId = runId,
                categoryCode = draft.resolvedCategoryCode,
                effectiveSpecVersion = effectiveSpecVersion,
                geoSnapshotId = geoSnapshot?.geoSnapshotId,
            ),
            visionReview = visionReview,
        )

    private fun buildGeoIssues(snapshot: LocalOfferGeoSnapshot): List<LocalOfferIssue> = when {
        snapshot.consentState != LocalOfferGeoConsentState.GRANTED -> listOf(
            LocalOfferIssue(
                issueCode = "geo_permission_denied",
                severity = LocalOfferIssueSeverity.ERROR,
                message = "Для публикации нужен доступ к геолокации.",
                ownerNode = LocalOfferNode.GEO_CONSENT_GATE,
                targetNode = LocalOfferNode.GEO_CONSENT_GATE,
            ),
        )

        snapshot.status != LocalOfferGeoStatus.CAPTURED -> listOf(
            LocalOfferIssue(
                issueCode = "geo_location_unavailable",
                severity = LocalOfferIssueSeverity.ERROR,
                message = "Текущая локация недоступна.",
                ownerNode = LocalOfferNode.GEO_CONSENT_GATE,
                targetNode = LocalOfferNode.GEO_CONSENT_GATE,
            ),
        )

        snapshot.freshnessState != LocalOfferGeoFreshnessState.FRESH -> listOf(
            LocalOfferIssue(
                issueCode = "geo_stale",
                severity = LocalOfferIssueSeverity.ERROR,
                message = "Локация устарела, обновите геоданные.",
                ownerNode = LocalOfferNode.GEO_CONSENT_GATE,
                targetNode = LocalOfferNode.GEO_CONSENT_GATE,
            ),
        )

        snapshot.accuracyMeters > MAX_ACCEPTABLE_GEO_ACCURACY_METERS -> listOf(
            LocalOfferIssue(
                issueCode = "geo_low_accuracy",
                severity = LocalOfferIssueSeverity.ERROR,
                message = "Точность геоданных недостаточна для локальной публикации.",
                ownerNode = LocalOfferNode.GEO_CONSENT_GATE,
                targetNode = LocalOfferNode.GEO_CONSENT_GATE,
            ),
        )

        else -> emptyList()
    }

    private fun mapCommandFailure(code: String): LocalOfferIssue = when (code) {
        "publish_revision_conflict" -> LocalOfferIssue(
            issueCode = code,
            severity = LocalOfferIssueSeverity.ERROR,
            message = "Черновик изменился, обновите превью перед публикацией.",
            ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            targetNode = LocalOfferNode.PREVIEW,
        )

        else -> LocalOfferIssue(
            issueCode = code,
            severity = LocalOfferIssueSeverity.ERROR,
            message = "Публикация заблокирована.",
            ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
            targetNode = LocalOfferNode.BLOCKED_STATE,
        )
    }

    private fun LocalOfferCommercials.withGeoCity(city: String?): LocalOfferCommercials =
        copy(city = city ?: this.city)

    private suspend fun ensureGeoAvailableForSession(
        session: SessionRecord,
        snapshot: LocalOfferGeoSnapshot,
        draftId: String?,
    ): LocalOfferGeoSnapshot {
        if (snapshot.sessionId != session.sessionId) {
            throw LocalOfferConflictException("GEO_SNAPSHOT_SESSION_MISMATCH")
        }
        if (draftId != null && snapshot.draftId != null && snapshot.draftId != draftId) {
            throw LocalOfferConflictException("GEO_SNAPSHOT_DRAFT_MISMATCH")
        }
        val refreshed = snapshot.refreshFreshness()
        val issues = buildGeoIssues(refreshed)
        if (issues.isNotEmpty()) {
            throw LocalOfferConflictException(issues.first().issueCode)
        }
        return refreshed
    }

    private fun LocalOfferGeoSnapshot.refreshFreshness(now: Long = System.currentTimeMillis()): LocalOfferGeoSnapshot {
        val freshnessState = when {
            consentState != LocalOfferGeoConsentState.GRANTED -> LocalOfferGeoFreshnessState.EXPIRED
            expiresAtMillis <= now -> LocalOfferGeoFreshnessState.EXPIRED
            expiresAtMillis - now <= STALE_GEO_THRESHOLD_MILLIS -> LocalOfferGeoFreshnessState.STALE
            else -> LocalOfferGeoFreshnessState.FRESH
        }
        return copy(freshnessState = freshnessState)
    }

    private fun ensureDraftRevision(
        expectedRevision: Int,
        actualRevision: Int,
    ) {
        if (expectedRevision != actualRevision) {
            throw LocalOfferConflictException("publish_revision_conflict")
        }
    }

    private suspend fun persistSession(record: SessionRecord) {
        DatabaseFactory.dbQuery {
            LocalOfferSessionsTable.insert { stmt ->
                stmt[sessionId] = record.sessionId
                stmt[userId] = record.userId
                stmt[currentDraftId] = record.currentDraftId
                stmt[state] = record.state.name
                stmt[createdAt] = record.createdAtMillis
                stmt[updatedAt] = record.updatedAtMillis
            }
        }
    }

    private suspend fun bindSessionToDraft(
        sessionId: String,
        draftId: String,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            LocalOfferSessionsTable.update({ LocalOfferSessionsTable.sessionId eq sessionId }) { stmt ->
                stmt[currentDraftId] = draftId
                stmt[state] = LocalOfferSessionState.ACTIVE.name
                stmt[updatedAt] = now
            }
        }
    }

    private suspend fun markSessionPublished(
        userId: Long,
        draftId: String,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            LocalOfferSessionsTable.update({
                (LocalOfferSessionsTable.userId eq userId) and
                    (LocalOfferSessionsTable.currentDraftId eq draftId)
            }) { stmt ->
                stmt[state] = LocalOfferSessionState.PUBLISHED.name
                stmt[updatedAt] = now
            }
        }
    }

    private suspend fun persistGeoSnapshot(
        userId: Long,
        snapshot: LocalOfferGeoSnapshot,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            LocalOfferGeoSnapshotsTable.insert { stmt ->
                stmt[id] = snapshot.geoSnapshotId
                stmt[sessionId] = snapshot.sessionId
                stmt[LocalOfferGeoSnapshotsTable.userId] = userId
                stmt[draftId] = snapshot.draftId
                stmt[consentState] = snapshot.consentState.name
                stmt[status] = snapshot.status.name
                stmt[capturedAtMillis] = snapshot.capturedAtMillis
                stmt[expiresAtMillis] = snapshot.expiresAtMillis
                stmt[accuracyMeters] = snapshot.accuracyMeters
                stmt[countryCode] = snapshot.countryCode
                stmt[adminArea] = snapshot.adminArea
                stmt[city] = snapshot.city
                stmt[lat] = snapshot.lat
                stmt[lon] = snapshot.lon
                stmt[geoSource] = snapshot.source
                stmt[createdAt] = now
                stmt[updatedAt] = now
                stmt[deleted] = false
            }
        }
    }

    private suspend fun attachGeoSnapshotToDraft(
        geoSnapshotId: String,
        draftId: String,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            LocalOfferGeoSnapshotsTable.update({
                (LocalOfferGeoSnapshotsTable.id eq geoSnapshotId) and
                    (LocalOfferGeoSnapshotsTable.deleted eq false)
            }) { stmt ->
                stmt[LocalOfferGeoSnapshotsTable.draftId] = draftId
                stmt[updatedAt] = now
            }
        }
    }

    private suspend fun createPublishCommand(
        userId: Long,
        draftId: String,
        revision: Int,
        effectiveSpecVersion: String,
        geoSnapshotId: String,
    ): String {
        val now = System.currentTimeMillis()
        val publishCommandId = generateId("lopub")
        DatabaseFactory.dbQuery {
            LocalOfferPublishCommandsTable.insert { stmt ->
                stmt[id] = publishCommandId
                stmt[LocalOfferPublishCommandsTable.draftId] = draftId
                stmt[LocalOfferPublishCommandsTable.userId] = userId
                stmt[LocalOfferPublishCommandsTable.revision] = revision
                stmt[LocalOfferPublishCommandsTable.effectiveSpecVersion] = effectiveSpecVersion
                stmt[LocalOfferPublishCommandsTable.geoSnapshotId] = geoSnapshotId
                stmt[state] = PublishCommandState.CREATED.name
                stmt[offerId] = null
                stmt[failureCode] = null
                stmt[createdAt] = now
                stmt[updatedAt] = now
            }
        }
        return publishCommandId
    }

    private suspend fun markPublishCommandSubmitted(publishCommandId: String) {
        val now = System.currentTimeMillis()
        DatabaseFactory.dbQuery {
            LocalOfferPublishCommandsTable.update({
                LocalOfferPublishCommandsTable.id eq publishCommandId
            }) { stmt ->
                stmt[state] = PublishCommandState.SUBMITTED.name
                stmt[updatedAt] = now
            }
        }
    }

    private suspend fun markPublishCommandSucceeded(
        publishCommandId: String,
        offerId: Long?,
        now: Long,
    ) {
        DatabaseFactory.dbQuery {
            LocalOfferPublishCommandsTable.update({
                LocalOfferPublishCommandsTable.id eq publishCommandId
            }) { stmt ->
                stmt[state] = PublishCommandState.SUCCEEDED.name
                stmt[LocalOfferPublishCommandsTable.offerId] = offerId
                stmt[failureCode] = null
                stmt[updatedAt] = now
            }
            offerId?.let { createdOfferId ->
                OffersTable.update({ OffersTable.id eq createdOfferId }) { stmt ->
                    stmt[publicationState] = OFFER_PUBLICATION_STATE_PENDING_REVIEW
                    stmt[updatedAt] = now
                }
            }
        }
    }

    private suspend fun markPublishCommandBlocked(
        publishCommandId: String,
        failureCode: String?,
    ) {
        val now = System.currentTimeMillis()
        DatabaseFactory.dbQuery {
            LocalOfferPublishCommandsTable.update({
                LocalOfferPublishCommandsTable.id eq publishCommandId
            }) { stmt ->
                stmt[state] = PublishCommandState.BLOCKED.name
                stmt[LocalOfferPublishCommandsTable.failureCode] = failureCode
                stmt[updatedAt] = now
            }
        }
    }

    private suspend fun loadSession(
        userId: Long,
        sessionId: String,
    ): SessionRecord? =
        DatabaseFactory.dbQuery {
            LocalOfferSessionsTable
                .selectAll()
                .apply {
                    andWhere { LocalOfferSessionsTable.sessionId eq sessionId }
                    andWhere { LocalOfferSessionsTable.userId eq userId }
                }
                .singleOrNull()
                ?.toSessionRecord()
        }

    private suspend fun loadSessionByDraftId(
        userId: Long,
        draftId: String,
    ): SessionRecord? =
        DatabaseFactory.dbQuery {
            LocalOfferSessionsTable
                .selectAll()
                .apply {
                    andWhere { LocalOfferSessionsTable.currentDraftId eq draftId }
                    andWhere { LocalOfferSessionsTable.userId eq userId }
                }
                .singleOrNull()
                ?.toSessionRecord()
        }

    private suspend fun loadSessionsByDraftIds(
        userId: Long,
        draftIds: List<String>,
    ): Map<String, SessionRecord> =
        if (draftIds.isEmpty()) {
            emptyMap()
        } else {
            DatabaseFactory.dbQuery {
                LocalOfferSessionsTable
                    .selectAll()
                    .apply {
                        andWhere { LocalOfferSessionsTable.userId eq userId }
                        andWhere { LocalOfferSessionsTable.currentDraftId inList draftIds }
                    }
                    .mapNotNull { row ->
                        row[LocalOfferSessionsTable.currentDraftId]?.let { it to row.toSessionRecord() }
                    }
                    .toMap()
            }
        }

    private suspend fun loadOwnedGeoSnapshot(
        userId: Long,
        geoSnapshotId: String,
    ): LocalOfferGeoSnapshot? =
        DatabaseFactory.dbQuery {
            LocalOfferGeoSnapshotsTable
                .selectAll()
                .apply {
                    andWhere { LocalOfferGeoSnapshotsTable.id eq geoSnapshotId }
                    andWhere { LocalOfferGeoSnapshotsTable.userId eq userId }
                    andWhere { LocalOfferGeoSnapshotsTable.deleted eq false }
                }
                .singleOrNull()
                ?.toGeoSnapshot()
                ?.refreshFreshness()
        }

    private suspend fun loadLatestGeoSnapshot(
        userId: Long,
        draftId: String,
        sessionId: String,
    ): LocalOfferGeoSnapshot? =
        DatabaseFactory.dbQuery {
            LocalOfferGeoSnapshotsTable
                .selectAll()
                .apply {
                    andWhere { LocalOfferGeoSnapshotsTable.userId eq userId }
                    andWhere { LocalOfferGeoSnapshotsTable.deleted eq false }
                    andWhere {
                        (LocalOfferGeoSnapshotsTable.draftId eq draftId) or
                            (LocalOfferGeoSnapshotsTable.sessionId eq sessionId)
                    }
                }
                .orderBy(LocalOfferGeoSnapshotsTable.updatedAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toGeoSnapshot()
                ?.refreshFreshness()
        }

    private suspend fun loadLatestGeoSnapshotsByDraftIds(
        userId: Long,
        draftIds: List<String>,
    ): Map<String, LocalOfferGeoSnapshot> =
        if (draftIds.isEmpty()) {
            emptyMap()
        } else {
            DatabaseFactory.dbQuery {
                LocalOfferGeoSnapshotsTable
                    .selectAll()
                    .apply {
                        andWhere { LocalOfferGeoSnapshotsTable.userId eq userId }
                        andWhere { LocalOfferGeoSnapshotsTable.deleted eq false }
                        andWhere { LocalOfferGeoSnapshotsTable.draftId inList draftIds }
                    }
                    .orderBy(LocalOfferGeoSnapshotsTable.updatedAt to SortOrder.DESC)
                    .toList()
                    .groupBy { it[LocalOfferGeoSnapshotsTable.draftId] }
                    .mapNotNull { (draftId, rows) ->
                        draftId?.let { it to rows.first().toGeoSnapshot().refreshFreshness() }
                    }
                    .toMap()
            }
        }

    private suspend fun loadPublishCommand(
        userId: Long,
        publishCommandId: String,
    ): PublishCommandRecord? =
        DatabaseFactory.dbQuery {
            LocalOfferPublishCommandsTable
                .selectAll()
                .apply {
                    andWhere { LocalOfferPublishCommandsTable.id eq publishCommandId }
                    andWhere { LocalOfferPublishCommandsTable.userId eq userId }
                }
                .singleOrNull()
                ?.toPublishCommandRecord()
        }

    private fun ResultRow.toSessionRecord(): SessionRecord =
        SessionRecord(
            sessionId = this[LocalOfferSessionsTable.sessionId],
            userId = this[LocalOfferSessionsTable.userId],
            currentDraftId = this[LocalOfferSessionsTable.currentDraftId],
            state = this[LocalOfferSessionsTable.state].toLocalOfferSessionState(),
            createdAtMillis = this[LocalOfferSessionsTable.createdAt],
            updatedAtMillis = this[LocalOfferSessionsTable.updatedAt],
        )

    private fun ResultRow.toGeoSnapshot(): LocalOfferGeoSnapshot =
        LocalOfferGeoSnapshot(
            geoSnapshotId = this[LocalOfferGeoSnapshotsTable.id],
            sessionId = this[LocalOfferGeoSnapshotsTable.sessionId],
            draftId = this[LocalOfferGeoSnapshotsTable.draftId],
            consentState = this[LocalOfferGeoSnapshotsTable.consentState].toGeoConsentState(),
            status = this[LocalOfferGeoSnapshotsTable.status].toGeoStatus(),
            capturedAtMillis = this[LocalOfferGeoSnapshotsTable.capturedAtMillis],
            expiresAtMillis = this[LocalOfferGeoSnapshotsTable.expiresAtMillis],
            freshnessState = LocalOfferGeoFreshnessState.FRESH,
            accuracyMeters = this[LocalOfferGeoSnapshotsTable.accuracyMeters],
            countryCode = this[LocalOfferGeoSnapshotsTable.countryCode],
            adminArea = this[LocalOfferGeoSnapshotsTable.adminArea],
            city = this[LocalOfferGeoSnapshotsTable.city],
            lat = this[LocalOfferGeoSnapshotsTable.lat],
            lon = this[LocalOfferGeoSnapshotsTable.lon],
            source = this[LocalOfferGeoSnapshotsTable.geoSource],
        )

    private fun ResultRow.toPublishCommandRecord(): PublishCommandRecord =
        PublishCommandRecord(
            id = this[LocalOfferPublishCommandsTable.id],
            draftId = this[LocalOfferPublishCommandsTable.draftId],
            userId = this[LocalOfferPublishCommandsTable.userId],
            revision = this[LocalOfferPublishCommandsTable.revision],
            effectiveSpecVersion = this[LocalOfferPublishCommandsTable.effectiveSpecVersion],
            geoSnapshotId = this[LocalOfferPublishCommandsTable.geoSnapshotId],
            state = this[LocalOfferPublishCommandsTable.state].toPublishCommandState(),
            offerId = this[LocalOfferPublishCommandsTable.offerId]?.toString(),
            failureCode = this[LocalOfferPublishCommandsTable.failureCode],
            createdAtMillis = this[LocalOfferPublishCommandsTable.createdAt],
            updatedAtMillis = this[LocalOfferPublishCommandsTable.updatedAt],
        )

    private fun SessionRecord.toSession(
        correlationId: String,
    ): LocalOfferSession =
        LocalOfferSession(
            sessionId = sessionId,
            correlationId = correlationId,
            draftId = currentDraftId?.let(::toCanonicalDraftId),
            state = state,
            effectiveSpecVersion = CatalogDataVersion.current,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )

    private fun PublishCommandRecord.toPublishResult(
        correlationId: String,
    ): LocalOfferPublishResult =
        LocalOfferPublishResult(
            envelope = LocalOfferRuntimeEnvelope(
                sessionId = UNKNOWN_SESSION_ID,
                correlationId = correlationId,
                draftId = toCanonicalDraftId(draftId),
                revision = revision,
                effectiveSpecVersion = effectiveSpecVersion,
                publishCommandId = id,
                geoSnapshotId = geoSnapshotId,
            ),
            outcome = LocalOfferPublishOutcome.PUBLISHED,
            publicationState = LocalOfferPublicationState.PENDING_REVIEW,
            offerId = offerId,
            publishedAtMillis = updatedAtMillis,
        )

    private fun String.toLocalOfferSessionState(): LocalOfferSessionState =
        runCatching { LocalOfferSessionState.valueOf(this) }.getOrDefault(LocalOfferSessionState.ACTIVE)

    private fun String.toGeoConsentState(): LocalOfferGeoConsentState =
        runCatching { LocalOfferGeoConsentState.valueOf(this) }.getOrDefault(LocalOfferGeoConsentState.DENIED)

    private fun String.toGeoStatus(): LocalOfferGeoStatus =
        runCatching { LocalOfferGeoStatus.valueOf(this) }.getOrDefault(LocalOfferGeoStatus.ERROR)

    private fun String.toPublishCommandState(): PublishCommandState =
        runCatching { PublishCommandState.valueOf(this) }.getOrDefault(PublishCommandState.CREATED)

    private fun toStorageDraftId(draftId: String): String =
        draftRuntime.toStorageDraftId(draftId.trim())

    private fun toCanonicalDraftId(draftId: String): String =
        draftRuntime.toPublicDraftId(draftId)

    private fun requireCanonicalDraftId(draftId: String): String =
        requireCanonicalDraftIdOrNull(draftId)
            ?: throw LocalOfferValidationException("DRAFT_ID_MUST_USE_CANONICAL_NAMESPACE")

    private fun requireCanonicalDraftIdOrNull(draftId: String?): String? {
        val normalized = draftId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return LocalOfferDraftIds.canonicalOrNull(normalized)
            ?: throw LocalOfferValidationException("DRAFT_ID_MUST_USE_CANONICAL_NAMESPACE")
    }

    private fun generateId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().replace("-", "")}"

    private data class SessionRecord(
        val sessionId: String,
        val userId: Long,
        val currentDraftId: String?,
        val state: LocalOfferSessionState,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
    )

    private data class PublishCommandRecord(
        val id: String,
        val draftId: String,
        val userId: Long,
        val revision: Int,
        val effectiveSpecVersion: String,
        val geoSnapshotId: String,
        val state: PublishCommandState,
        val offerId: String?,
        val failureCode: String?,
        val createdAtMillis: Long,
        val updatedAtMillis: Long,
    )

    private enum class PublishCommandState {
        CREATED,
        SUBMITTED,
        SUCCEEDED,
        BLOCKED,
    }

    private companion object {
        const val GEO_SNAPSHOT_TTL_MILLIS = 15 * 60 * 1000L
        const val STALE_GEO_THRESHOLD_MILLIS = 2 * 60 * 1000L
        const val MAX_ACCEPTABLE_GEO_ACCURACY_METERS = 500.0
        const val DEFAULT_GEO_SOURCE = "hybrid"
        const val GEO_DENIED_CITY_PLACEHOLDER = "unknown"
        const val UNKNOWN_SESSION_ID = "unknown"
    }
}

class LocalOfferValidationException(
    override val message: String,
) : IllegalArgumentException(message)

class LocalOfferNotFoundException(
    override val message: String,
) : IllegalStateException(message)

class LocalOfferConflictException(
    override val message: String,
) : IllegalStateException(message)
