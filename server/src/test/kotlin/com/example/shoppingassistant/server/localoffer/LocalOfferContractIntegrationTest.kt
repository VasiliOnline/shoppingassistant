package com.example.shoppingassistant.server.localoffer

import com.example.shoppingassistant.domain.localoffer.LocalOfferConfirmGeoSnapshotRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferCommercials
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftSession
import com.example.shoppingassistant.domain.localoffer.LocalOfferDraftStage
import com.example.shoppingassistant.domain.localoffer.LocalOfferGeoConsentState
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssue
import com.example.shoppingassistant.domain.localoffer.LocalOfferIssueSeverity
import com.example.shoppingassistant.domain.localoffer.LocalOfferModerationDecision
import com.example.shoppingassistant.domain.localoffer.LocalOfferNode
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightResponse
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishCommandRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishOutcome
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublishPreflightRequest
import com.example.shoppingassistant.domain.localoffer.LocalOfferPublicationState
import com.example.shoppingassistant.domain.localoffer.LocalOfferSessionRequest
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftEnvelope
import com.example.shoppingassistant.domain.shortlisting.ShortListingDraftSession
import com.example.shoppingassistant.domain.shortlisting.ShortListingProfileGate
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishLocals
import com.example.shoppingassistant.domain.shortlisting.ShortListingPublishReadiness
import com.example.shoppingassistant.domain.shortlisting.ShortListingStage
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.localoffer.LocalOfferGeoSnapshotsTable
import com.example.shoppingassistant.server.localoffer.LocalOfferPublishCommandsTable
import com.example.shoppingassistant.server.localoffer.LocalOfferSessionsTable
import com.example.shoppingassistant.server.offers.OffersTable
import com.example.shoppingassistant.server.offers.ProductsTable
import com.example.shoppingassistant.server.offers.SellerStatsTable
import com.example.shoppingassistant.server.offers.UserProfilesTable
import com.example.shoppingassistant.server.shortlisting.ShortListingDraftsTable
import java.sql.DriverManager
import java.sql.Connection
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.AfterClass
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalOfferContractIntegrationTest {

    @Before
    fun cleanTables() {
        runBlocking {
            requireDocker()
            DatabaseFactory.dbQuery {
                LocalOfferPublishCommandsTable.deleteAll()
                LocalOfferGeoSnapshotsTable.deleteAll()
                LocalOfferSessionsTable.deleteAll()
                ShortListingDraftsTable.deleteAll()
                OffersTable.deleteAll()
                ProductsTable.deleteAll()
                SellerStatsTable.deleteAll()
                UserProfilesTable.deleteAll()
                AuthUsersTable.deleteAll()
            }
        }
    }

    @Test
    fun service_exposes_canonical_lodraft_namespace_even_when_runtime_uses_sldraft() = runBlocking {
        requireDocker()
        val userId = seedUser("canonicalization")
        val runtime = FakeLocalOfferDraftRuntime()
        val service = LocalOfferBackendServiceImpl(runtime)

        val drafts = service.listDrafts(userId)
        val envelope = service.getDraft(userId, "lodraft-1")

        assertEquals(1, drafts.size)
        assertEquals("lodraft-1", drafts.single().draftId)
        assertEquals(LocalOfferDraftStage.PREVIEW_READY, drafts.single().stage)
        assertNotNull(envelope)
        assertEquals("lodraft-1", envelope.draft.draftId)
        assertEquals("lodraft-1", envelope.envelope.draftId)
    }

    @Test
    fun publish_preflight_routes_profile_blockers_to_blocked_state() = runBlocking {
        requireDocker()
        val userId = seedUser("blocked")
        val runtime = FakeLocalOfferDraftRuntime(
            preflight = FakeLocalOfferDraftRuntime.profileIncompletePreflight(),
        )
        val service = LocalOfferBackendServiceImpl(runtime)
        val session = service.createOrResumeSession(
            userId = userId,
            request = LocalOfferSessionRequest(
                correlationId = "corr-blocked",
                sessionId = runtime.sessionId,
            ),
        )
        val snapshot = service.confirmGeoSnapshot(
            userId = userId,
            request = LocalOfferConfirmGeoSnapshotRequest(
                sessionId = session.sessionId,
                correlationId = session.correlationId,
                consentState = LocalOfferGeoConsentState.GRANTED,
                city = "Moscow",
            ),
        )

        val preflight = service.getPublishPreflight(
            userId = userId,
            draftId = "lodraft-1",
            request = LocalOfferPublishPreflightRequest(
                correlationId = session.correlationId,
                revision = runtime.backingEnvelope.draft.revision,
                geoSnapshotId = snapshot.geoSnapshotId,
            ),
        )

        assertTrue(!preflight.publishAllowed)
        assertEquals(LocalOfferNode.BLOCKED_STATE, preflight.nextRoutes.defaultFixNode)
        assertEquals(1, preflight.blockingIssues.size)
        assertEquals("publish_profile_incomplete", preflight.blockingIssues.single().issueCode)
        assertEquals(LocalOfferNode.BLOCKED_STATE, preflight.blockingIssues.single().targetNode)
    }

    @Test
    fun publish_preflight_rejects_expired_geo_snapshot_as_geo_stale() = runBlocking {
        requireDocker()
        val userId = seedUser("geo")
        val runtime = FakeLocalOfferDraftRuntime()
        val service = LocalOfferBackendServiceImpl(runtime)
        val session = service.createOrResumeSession(
            userId = userId,
            request = LocalOfferSessionRequest(
                correlationId = "corr-geo",
                sessionId = runtime.sessionId,
            ),
        )
        val snapshot = service.confirmGeoSnapshot(
            userId = userId,
            request = LocalOfferConfirmGeoSnapshotRequest(
                sessionId = session.sessionId,
                correlationId = session.correlationId,
                consentState = LocalOfferGeoConsentState.GRANTED,
                city = "Moscow",
            ),
        )
        expireGeoSnapshot(snapshot.geoSnapshotId)

        val error = assertFailsWith<LocalOfferConflictException> {
            service.getPublishPreflight(
                userId = userId,
                draftId = "lodraft-1",
                request = LocalOfferPublishPreflightRequest(
                    correlationId = session.correlationId,
                    revision = runtime.backingEnvelope.draft.revision,
                    geoSnapshotId = snapshot.geoSnapshotId,
                ),
            )
        }

        assertEquals("geo_stale", error.message)
    }

    @Test
    fun publish_preflight_rejects_stale_revision_as_conflict() = runBlocking {
        requireDocker()
        val userId = seedUser("revision")
        val runtime = FakeLocalOfferDraftRuntime()
        val service = LocalOfferBackendServiceImpl(runtime)
        val session = service.createOrResumeSession(
            userId = userId,
            request = LocalOfferSessionRequest(
                correlationId = "corr-revision",
                sessionId = runtime.sessionId,
            ),
        )
        val snapshot = service.confirmGeoSnapshot(
            userId = userId,
            request = LocalOfferConfirmGeoSnapshotRequest(
                sessionId = session.sessionId,
                correlationId = session.correlationId,
                consentState = LocalOfferGeoConsentState.GRANTED,
                city = "Moscow",
            ),
        )

        val error = assertFailsWith<LocalOfferConflictException> {
            service.getPublishPreflight(
                userId = userId,
                draftId = "lodraft-1",
                request = LocalOfferPublishPreflightRequest(
                    correlationId = session.correlationId,
                    revision = runtime.runtimeDraft.draft.revision - 1,
                    geoSnapshotId = snapshot.geoSnapshotId,
                ),
            )
        }

        assertEquals("publish_revision_conflict", error.message)
    }

    @Test
    fun publish_reuses_succeeded_command_for_idempotent_retry() = runBlocking {
        requireDocker()
        val userId = seedUser("publish")
        val runtime = FakeLocalOfferDraftRuntime()
        seedBackingDraft(userId, runtime.backingEnvelope)
        runtime.publishedOfferId = seedOffer(userId).toString()
        val service = LocalOfferBackendServiceImpl(runtime)
        val session = service.createOrResumeSession(
            userId = userId,
            request = LocalOfferSessionRequest(
                correlationId = "corr-publish",
                sessionId = runtime.sessionId,
            ),
        )
        val snapshot = service.confirmGeoSnapshot(
            userId = userId,
            request = LocalOfferConfirmGeoSnapshotRequest(
                sessionId = session.sessionId,
                correlationId = session.correlationId,
                consentState = LocalOfferGeoConsentState.GRANTED,
                city = "Moscow",
            ),
        )

        val preflight = service.getPublishPreflight(
            userId = userId,
            draftId = "lodraft-1",
            request = LocalOfferPublishPreflightRequest(
                correlationId = session.correlationId,
                revision = runtime.backingEnvelope.draft.revision,
                geoSnapshotId = snapshot.geoSnapshotId,
            ),
        )
        val publishCommandId = assertNotNull(preflight.envelope.publishCommandId)
        val publishRequest = LocalOfferPublishCommandRequest(
            correlationId = session.correlationId,
            revision = runtime.backingEnvelope.draft.revision,
            effectiveSpecVersion = preflight.effectiveSpecVersion,
            geoSnapshotId = snapshot.geoSnapshotId,
            publishCommandId = publishCommandId,
        )

        val first = service.publishDraft(userId = userId, draftId = "lodraft-1", request = publishRequest)
        val second = service.publishDraft(userId = userId, draftId = "lodraft-1", request = publishRequest)

        assertEquals(LocalOfferPublishOutcome.PUBLISHED, first.outcome)
        assertEquals(LocalOfferPublishOutcome.PUBLISHED, second.outcome)
        assertEquals(first.offerId, second.offerId)
        assertEquals(1, runtime.publishCalls)

        val storedCommands = DatabaseFactory.dbQuery { LocalOfferPublishCommandsTable.selectAll().toList() }
        assertEquals(1, storedCommands.size)
        assertEquals("SUCCEEDED", storedCommands.single()[LocalOfferPublishCommandsTable.state])
    }

    private suspend fun seedUser(tag: String): Long = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        AuthUsersTable.insert {
            it[email] = "localoffer-$tag@example.com"
            it[password] = "secret"
            it[displayName] = "LocalOffer $tag"
            it[city] = "Moscow"
            it[emailVerified] = true
            it[createdAt] = now
        }.resultedValues!!.single()[AuthUsersTable.id]
    }

    private suspend fun seedOffer(userId: Long): Long = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        val productId = ProductsTable.insert {
            it[category] = "TECH.PHONES"
            it[brand] = "Apple"
            it[model] = "iPhone"
            it[titleNorm] = "Apple iPhone"
            it[updatedAt] = now
        }.resultedValues!!.single()[ProductsTable.id]

        OffersTable.insert {
            it[OffersTable.productId] = productId
            it[OffersTable.userId] = userId
            it[priceCents] = 100_000
            it[currency] = "USD"
            it[attributes] = mapOf("condition" to TypedAttributeValue.Text("used"))
            it[condition] = "used"
            it[deliveryChannel] = "pickup"
            it[status] = "ACTIVE"
            it[updatedAt] = now
        }.resultedValues!!.single()[OffersTable.id]
    }

    private suspend fun seedBackingDraft(
        userId: Long,
        envelope: ShortListingDraftEnvelope,
    ) {
        DatabaseFactory.dbQuery {
            val draft = envelope.draft
            ShortListingDraftsTable.insert { stmt ->
                stmt[id] = draft.draftId
                stmt[sessionId] = draft.sessionId
                stmt[ShortListingDraftsTable.userId] = userId
                stmt[stage] = draft.stage.name
                stmt[requestedCategoryCode] = draft.requestedCategoryCode
                stmt[resolvedCategoryCode] = draft.resolvedCategoryCode
                stmt[candidateCategory] = draft.candidateCategory
                stmt[confirmedCategoryCode] = draft.confirmedCategoryCode
                stmt[media] = draft.media
                stmt[predictedFields] = draft.predictedFields
                stmt[confirmedUserFields] = draft.confirmedUserFields
                stmt[missingRequiredFields] = draft.missingRequiredFields
                stmt[evidenceTasks] = draft.evidenceTasks
                stmt[publishLocals] = draft.publishLocals
                stmt[publishReadiness] = draft.publishReadiness
                stmt[profileGate] = draft.profileGate
                stmt[identitySignature] = draft.identitySignature
                stmt[lastVisionResult] = envelope.visionResult
                stmt[publishedOfferId] = draft.publishedOfferId?.toLongOrNull()
                stmt[lifecycleStatus] = draft.lifecycleStatus?.name
                stmt[expiresAtMillis] = draft.expiresAtMillis
                stmt[safeToExit] = draft.safeToExit
                stmt[revision] = draft.revision
                stmt[createdAt] = draft.createdAtMillis
                stmt[updatedAt] = draft.updatedAtMillis
                stmt[deleted] = false
            }
        }
    }

    private suspend fun expireGeoSnapshot(geoSnapshotId: String) {
        val now = System.currentTimeMillis()
        DatabaseFactory.dbQuery {
            LocalOfferGeoSnapshotsTable.update({ LocalOfferGeoSnapshotsTable.id eq geoSnapshotId }) { stmt ->
                stmt[expiresAtMillis] = now - 1_000L
                stmt[updatedAt] = now
            }
        }
    }

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for server integration tests when strict mode is enabled.")
            }
            Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)
        }
    }

    private companion object {
        private var dockerAvailable: Boolean = false
        private val strictIntegration: Boolean by lazy {
            System.getenv("SERVER_IT_STRICT")?.equals("true", ignoreCase = true) == true ||
                System.getenv("CI")?.equals("true", ignoreCase = true) == true
        }
        private var container: PostgreSQLContainer<*>? = null

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            dockerAvailable = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)
            if (!dockerAvailable) {
                if (strictIntegration) {
                    error("Docker is required for server integration tests in strict mode.")
                }
                return
            }

            val postgresImage = DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres")
            val postgres = PostgreSQLContainer(postgresImage)
                .withDatabaseName("shoppingassistant_localoffer_contract_it")
                .withUsername("test")
                .withPassword("test")
            postgres.start()
            container = postgres

            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            ).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE EXTENSION IF NOT EXISTS postgis")
                }
            }

            val database = Database.connect(
                url = postgres.jdbcUrl,
                driver = postgres.driverClassName,
                user = postgres.username,
                password = postgres.password,
            )
            TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_REPEATABLE_READ
            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(
                    AuthUsersTable,
                    UserProfilesTable,
                    SellerStatsTable,
                    ProductsTable,
                    OffersTable,
                    ShortListingDraftsTable,
                    LocalOfferSessionsTable,
                    LocalOfferGeoSnapshotsTable,
                    LocalOfferPublishCommandsTable,
                )
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            container?.stop()
            container = null
        }
    }
}

private class FakeLocalOfferDraftRuntime(
    preflight: LocalOfferRuntimePreflight = readyPreflight(),
) : LocalOfferDraftRuntime {
    val sessionId: String = "losess-contract"
    val backingEnvelope: ShortListingDraftEnvelope = ShortListingDraftEnvelope(
        draft = ShortListingDraftSession(
            draftId = BACKING_DRAFT_ID,
            sessionId = sessionId,
            userId = "42",
            stage = ShortListingStage.READY_FOR_PUBLISH,
            safeToExit = true,
            createdAtMillis = 1L,
            updatedAtMillis = 2L,
            revision = 3,
            requestedCategoryCode = "TECH.PHONES",
            resolvedCategoryCode = "TECH.PHONES",
            publishLocals = ShortListingPublishLocals(
                priceMajor = 1000.0,
                currency = "USD",
                ttlPresetDays = 30,
                city = "Moscow",
                deliveryChannel = "pickup",
                description = "Phone",
            ),
            publishReadiness = ShortListingPublishReadiness(ready = preflight.publishAllowed),
            profileGate = ShortListingProfileGate(eligible = true),
            expiresAtMillis = System.currentTimeMillis() + 60_000L,
        ),
    )
    val runtimeDraft: LocalOfferRuntimeDraft = LocalOfferRuntimeDraft(
        draft = LocalOfferDraftSession(
            draftId = PUBLIC_DRAFT_ID,
            sessionId = sessionId,
            stage = LocalOfferDraftStage.PREVIEW_READY,
            safeToExit = true,
            createdAtMillis = 1L,
            updatedAtMillis = 2L,
            revision = 3,
            requestedCategoryCode = "TECH.PHONES",
            resolvedCategoryCode = "TECH.PHONES",
            commercials = LocalOfferCommercials(
                priceMajor = 1000.0,
                currency = "USD",
                ttlPresetDays = 30,
                city = "Moscow",
                deliveryChannel = "pickup",
                description = "Phone",
            ),
        ),
    )
    var publishedOfferId: String = "1001"
    var publishCalls: Int = 0
    private val configuredPreflight: LocalOfferRuntimePreflight = preflight

    override suspend fun listDrafts(userId: Long): List<LocalOfferDraftSession> =
        listOf(runtimeDraft.draft)

    override suspend fun createDraft(
        userId: Long,
        request: com.example.shoppingassistant.domain.localoffer.LocalOfferCreateDraftRequest,
    ): LocalOfferRuntimeDraft = runtimeDraft

    override suspend fun getDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimeDraft? =
        runtimeDraft.takeIf { toStorageDraftId(draftId) == BACKING_DRAFT_ID }

    override suspend fun updateDraftReview(
        userId: Long,
        draftId: String,
        request: com.example.shoppingassistant.domain.localoffer.LocalOfferReviewUpdateRequest,
    ): LocalOfferRuntimeDraft = runtimeDraft

    override suspend fun getPublishPreflight(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimePreflight = configuredPreflight

    override suspend fun publishDraft(
        userId: Long,
        draftId: String,
    ): LocalOfferRuntimePublishAttempt {
        publishCalls += 1
        return LocalOfferRuntimePublishAttempt.Published(
            offerId = publishedOfferId,
            publicationState = LocalOfferPublicationState.PENDING_REVIEW,
            publishedAtMillis = 123_456L,
        )
    }

    override fun toStorageDraftId(canonicalDraftId: String): String = when {
        canonicalDraftId.startsWith("lodraft-") -> "sldraft-${canonicalDraftId.removePrefix("lodraft-")}"
        canonicalDraftId.startsWith("sldraft-") -> canonicalDraftId
        else -> canonicalDraftId
    }

    override fun toPublicDraftId(storageDraftId: String): String = when {
        storageDraftId.startsWith("sldraft-") -> "lodraft-${storageDraftId.removePrefix("sldraft-")}"
        storageDraftId.startsWith("lodraft-") -> storageDraftId
        else -> storageDraftId
    }

    companion object {
        private const val BACKING_DRAFT_ID = "sldraft-1"
        private const val PUBLIC_DRAFT_ID = "lodraft-1"

        fun readyPreflight(): LocalOfferRuntimePreflight =
            LocalOfferRuntimePreflight(
                draftId = PUBLIC_DRAFT_ID,
                publishAllowed = true,
                normalizedPayload = LocalOfferRuntimeNormalizedPayload(title = "iPhone 15"),
                moderationDecision = LocalOfferModerationDecision.REVIEW_REQUIRED,
            )

        fun profileIncompletePreflight(): LocalOfferRuntimePreflight =
            LocalOfferRuntimePreflight(
                draftId = PUBLIC_DRAFT_ID,
                publishAllowed = false,
                blockingIssues = listOf(
                    LocalOfferIssue(
                        issueCode = "publish_profile_incomplete",
                        severity = LocalOfferIssueSeverity.ERROR,
                        message = "Профиль продавца не заполнен.",
                        ownerNode = LocalOfferNode.PUBLISH_PREFLIGHT,
                        targetNode = LocalOfferNode.BLOCKED_STATE,
                    ),
                ),
                moderationDecision = LocalOfferModerationDecision.HOLD,
            )
    }
}
