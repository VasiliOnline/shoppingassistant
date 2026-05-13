package com.example.shoppingassistant.server.offers

import com.example.shoppingassistant.core.rank.RankService
import com.example.shoppingassistant.core.rank.ScoreBreakdown
import com.example.shoppingassistant.core.rank.ScoringEngine
import com.example.shoppingassistant.domain.ingest.DefaultUrlNormalizer
import com.example.shoppingassistant.domain.ingest.SourceCapabilities
import com.example.shoppingassistant.domain.ingest.SourceRegistry
import com.example.shoppingassistant.domain.ingest.SourceRegistryEntry
import com.example.shoppingassistant.domain.ingest.SourceType
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.OfferFacetType
import com.example.shoppingassistant.domain.model.OfferRepository
import com.example.shoppingassistant.domain.model.OfferSearchCriteria
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsRequest
import com.example.shoppingassistant.domain.model.OfferSearchWithFacetsResponse
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchRequest
import com.example.shoppingassistant.domain.model.PresetObservabilityBatchResponse
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferResult
import com.example.shoppingassistant.domain.offers.CreateTrackedOfferStatus
import com.example.shoppingassistant.domain.offers.TrackedOfferInput
import com.example.shoppingassistant.domain.offers.TrackedOfferRepository
import com.example.shoppingassistant.domain.offers.TrackedOfferSource
import com.example.shoppingassistant.server.auth.SessionManager
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentCandidate
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentService
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelEnrichmentStatus
import com.example.shoppingassistant.server.catalog.CatalogPhoneModelRuntimeSignal
import com.example.shoppingassistant.server.catalog.Stage4ExecutionLayerImpl
import com.example.shoppingassistant.server.config.DatabaseConfig
import com.example.shoppingassistant.server.db.AuthUsersTable
import com.example.shoppingassistant.server.db.DatabaseFactory
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager

class OfferTypedContractE2EIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var testUserId: Long = 0L

    @Before
    fun setUp() {
        requireDocker()
        runBlocking {
            DatabaseFactory.dbQuery {
                OffersTable.deleteAll()
                ProductI18nTable.deleteAll()
                ProductsTable.deleteAll()
                OfferPriceHistoryTable.deleteAll()
                OfferSourcesTable.deleteAll()
                AlertsTable.deleteAll()
                UserReviewsTable.deleteAll()
                UserPreferencesTable.deleteAll()
                SellerStatsTable.deleteAll()
                UserProfilesTable.deleteAll()
                AuthUsersTable.deleteAll()

                val now = System.currentTimeMillis()
                testUserId = AuthUsersTable.insert {
                    it[email] = "typed-e2e-user@example.com"
                    it[password] = "secret"
                    it[displayName] = "Typed E2E User"
                    it[city] = "Moscow"
                    it[emailVerified] = true
                    it[createdAt] = now
                }.resultedValues!!.single()[AuthUsersTable.id]
            }
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun api_to_db_to_search_facets_respects_typed_stage4_constraints() = testApplication {
        requireDocker()
        val stage4ExecutionLayer = Stage4ExecutionLayerImpl()
        val offerRepository: OfferRepository = OfferRepositoryImpl(
            rankService = RankService(engine = ZeroScoringEngineE2E),
            stage4ExecutionLayer = stage4ExecutionLayer,
        )
        val trackedRepository: TrackedOfferRepository = TrackedOfferRepositoryImpl(
            urlNormalizer = DefaultUrlNormalizer(),
            sourceRegistry = StaticSourceRegistry,
            stage4ExecutionLayer = stage4ExecutionLayer,
        )
        val sessionManager = FixedSessionManager(
            token = TEST_TOKEN,
            userId = testUserId,
        )

        startKoin {
            modules(
                module {
                    single<OfferRepository> { offerRepository }
                    single<TrackedOfferRepository> { trackedRepository }
                    single<PresetObservabilityRepository> { NoopPresetObservabilityRepositoryE2E() }
                    single<SessionManager> { sessionManager }
                },
            )
        }

        application {
            configureSerialization()
            routing {
                offerTrackingRoutes()
                offerRoutes()
            }
        }

        val createPayload = TrackedOfferInput(
            userId = "ignored-by-auth",
            title = "Acme X1",
            categoryCode = "TECH.PHONES",
            brand = "Acme",
            model = "X1",
            priceValue = 1000.0,
            currency = "RUB",
            imageUrls = listOf("https://example.com/image.jpg"),
            attributes = mapOf(
                "condition" to "used",
                "ram_gb" to "8",
                "battery_health_percent" to "150",
                "region_code" to "bad@@",
                "shelf_life_days" to "30 kg",
            ),
            source = TrackedOfferSource(
                sourceType = SourceType.AVITO,
                url = "https://www.avito.ru/moskva/telefony/acme_x1_123",
            ),
        )

        val createResponse = client.post("/api/offers/tracked") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            setBody(json.encodeToString(createPayload))
        }

        assertEquals(HttpStatusCode.OK, createResponse.status)
        val createResult = json.decodeFromString(
            deserializer = CreateTrackedOfferResult.serializer(),
            string = createResponse.bodyAsText(),
        )
        assertEquals(CreateTrackedOfferStatus.CREATED, createResult.status)
        assertTrue(createResult.reasonCodes.any { it == "OUT_OF_RANGE:battery_health_percent" })
        assertTrue(createResult.reasonCodes.any { it == "PATTERN_MISMATCH:region_code" })
        assertTrue(
            createResult.reasonCodes.any { reason ->
                reason == "UNIT_MISMATCH:shelf_life_days" || reason == "UNKNOWN_ATTRIBUTE:shelf_life_days"
            },
        )

        val createdOfferId = createResult.offerId?.toLongOrNull()
        assertNotNull(createdOfferId)

        val storedAttributes = runBlocking {
            DatabaseFactory.dbQuery {
                OffersTable
                    .selectAll()
                    .where { OffersTable.id eq createdOfferId!! }
                    .single()[OffersTable.attributes]
                    .orEmpty()
            }
        }

        assertEquals(TypedAttributeValue.Number(8.0), storedAttributes["ram_gb"])
        assertEquals(TypedAttributeValue.Text("USED"), storedAttributes["condition"])
        assertTrue("battery_health_percent" !in storedAttributes)
        assertTrue("region_code" !in storedAttributes)
        assertTrue("shelf_life_days" !in storedAttributes)

        val searchRequest = OfferSearchWithFacetsRequest(
            criteria = OfferSearchCriteria(
                brand = "Acme",
                model = null,
                attributes = mapOf("ram_gb" to TypedAttributeValue.Number(8.0)),
                limit = 10,
            ),
            facets = setOf(OfferFacetType.BRAND),
        )

        val searchResponse = client.post("/api/offers/searchWithFacets") {
            headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(searchRequest))
        }

        assertEquals(HttpStatusCode.OK, searchResponse.status)
        val searchResult = json.decodeFromString(
            deserializer = OfferSearchWithFacetsResponse.serializer(),
            string = searchResponse.bodyAsText(),
        )

        assertEquals(1, searchResult.total)
        assertEquals(1, searchResult.offers.size)
        assertEquals(createdOfferId.toString(), searchResult.offers.first().id)
        assertEquals(1, searchResult.facets.brands.size)
        assertEquals("Acme", searchResult.facets.brands.first().name)
    }

    @Test
    fun create_missing_required_attributes_allows_create_with_reasons_in_soft_mode() = runBlocking {
        requireDocker()
        val repository = TrackedOfferRepositoryImpl(
            urlNormalizer = DefaultUrlNormalizer(),
            sourceRegistry = StaticSourceRegistry,
            stage4ExecutionLayer = Stage4ExecutionLayerImpl(),
            requiredForCategoryHardFail = false,
        )

        val result = repository.createTrackedOffer(
            request = baseTrackedOfferInput(
                sourceUrl = "https://www.avito.ru/moskva/telefony/e2e_soft_required_${System.currentTimeMillis()}",
                attributes = mapOf("condition" to "new"),
            ),
        )

        assertEquals(CreateTrackedOfferStatus.CREATED, result.status)
        assertTrue(
            "Expected REQUIRED_FOR_CATEGORY_MISSING reason code, got ${result.reasonCodes}",
            result.reasonCodes.any { it.startsWith("REQUIRED_FOR_CATEGORY_MISSING:") },
        )
    }

    @Test
    fun create_missing_required_attributes_fails_in_hard_mode() = runBlocking {
        requireDocker()
        val repository = TrackedOfferRepositoryImpl(
            urlNormalizer = DefaultUrlNormalizer(),
            sourceRegistry = StaticSourceRegistry,
            stage4ExecutionLayer = Stage4ExecutionLayerImpl(),
            requiredForCategoryHardFail = true,
        )

        val result = repository.createTrackedOffer(
            request = baseTrackedOfferInput(
                sourceUrl = "https://www.avito.ru/moskva/telefony/e2e_hard_required_${System.currentTimeMillis()}",
                attributes = mapOf("condition" to "new"),
            ),
        )

        assertEquals(CreateTrackedOfferStatus.INVALID_INPUT, result.status)
        assertTrue(
            "Expected REQUIRED_FOR_CATEGORY_MISSING reason code, got ${result.reasonCodes}",
            result.reasonCodes.any { it.startsWith("REQUIRED_FOR_CATEGORY_MISSING:") },
        )
    }

    @Test
    fun create_triggers_phone_model_runtime_signal_after_successful_offer_create() = runBlocking {
        requireDocker()
        val enrichmentService = RecordingCatalogPhoneModelEnrichmentService()
        val repository = TrackedOfferRepositoryImpl(
            urlNormalizer = DefaultUrlNormalizer(),
            sourceRegistry = StaticSourceRegistry,
            stage4ExecutionLayer = Stage4ExecutionLayerImpl(),
            phoneModelEnrichmentService = enrichmentService,
        )

        val result = repository.createTrackedOffer(
            request = baseTrackedOfferInput(
                sourceUrl = "https://www.avito.ru/moskva/telefony/honor_500_ultra_${System.currentTimeMillis()}",
                attributes = mapOf(
                    "condition" to "used",
                    "model_line" to "Honor 500",
                ),
            ).copy(
                brand = "Honor",
                model = "500 Ultra",
            ),
        )

        assertEquals(CreateTrackedOfferStatus.CREATED, result.status)
        val signal = requireNotNull(enrichmentService.recordedSignals.singleOrNull())
        assertEquals("TECH.PHONES", signal.categoryCode)
        assertEquals("Honor", signal.brandRaw)
        assertEquals("500 Ultra", signal.modelRaw)
        assertEquals("Honor 500", signal.familyRaw)
    }

    private fun baseTrackedOfferInput(
        sourceUrl: String,
        attributes: Map<String, String>,
    ): TrackedOfferInput = TrackedOfferInput(
        userId = testUserId.toString(),
        title = "E2E Required Attrs",
        categoryCode = "TECH.PHONES",
        categoryConfidence = 0.9,
        parserVersion = "test",
        brand = "E2EBrand",
        model = "E2EModel",
        priceValue = 1000.0,
        currency = "RUB",
        imageUrls = listOf("https://example.com/e2e.jpg"),
        attributes = attributes,
        source = TrackedOfferSource(
            sourceType = SourceType.AVITO,
            url = sourceUrl,
        ),
    )

    private fun requireDocker() {
        if (!dockerAvailable) {
            if (strictIntegration) {
                error("Docker is required for server integration tests when strict mode is enabled.")
            }
            Assume.assumeTrue("Docker is required for server integration tests.", dockerAvailable)
        }
    }

    private companion object {
        private const val TEST_TOKEN = "typed-e2e-token"
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
                .withDatabaseName("shoppingassistant_typed_contract_e2e_it")
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

            DatabaseFactory.init(
                DatabaseConfig(
                    url = postgres.jdbcUrl,
                    user = postgres.username,
                    password = postgres.password,
                ),
            )
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            container?.stop()
            container = null
        }
    }
}

private class RecordingCatalogPhoneModelEnrichmentService : CatalogPhoneModelEnrichmentService {
    val recordedSignals = mutableListOf<CatalogPhoneModelRuntimeSignal>()

    override suspend fun ingest(signal: CatalogPhoneModelRuntimeSignal) {
        recordedSignals += signal
    }

    override suspend fun listCandidates(
        categoryCode: String,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogPhoneModelEnrichmentCandidate> = emptyList()

    override suspend fun getCandidate(candidateId: Long): CatalogPhoneModelEnrichmentCandidate? = null

    override suspend fun markOfficiallySeeded(
        candidateId: Long,
        officialSourceCode: String,
        officialEndpointCode: String,
        metadata: Map<String, String>,
    ): CatalogPhoneModelEnrichmentCandidate? = null
}

private object ZeroScoringEngineE2E : ScoringEngine {
    override fun score(dto: ProductDto, q: NormalizedQuery, avgPrice: Double): ScoreBreakdown =
        ScoreBreakdown(
            price = 0f,
            delivery = 0f,
            rating = 0f,
            penalties = 0f,
            score = 0f,
        )
}

private object StaticSourceRegistry : SourceRegistry {
    private val entry = SourceRegistryEntry(
        id = "avito",
        sourceType = SourceType.AVITO,
        displayName = "Avito",
        hosts = setOf("avito.ru", "www.avito.ru"),
        capabilities = SourceCapabilities(
            canIngest = true,
            canTrackPrice = true,
            requiresBrowser = false,
        ),
        rolloutEnabled = true,
    )

    override fun all(): List<SourceRegistryEntry> = listOf(entry)

    override fun findById(id: String): SourceRegistryEntry? =
        all().firstOrNull { it.id == id }

    override fun findBySourceType(type: SourceType): SourceRegistryEntry? =
        all().firstOrNull { it.sourceType == type }
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

private class NoopPresetObservabilityRepositoryE2E : PresetObservabilityRepository {
    override suspend fun ingestBatch(
        request: PresetObservabilityBatchRequest,
    ): PresetObservabilityBatchResponse = PresetObservabilityBatchResponse(
        acceptedCount = 0,
        dedupedCount = 0,
        rejectedCount = 0,
    )
}
