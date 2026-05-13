package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AliasEntry
import com.example.shoppingassistant.domain.catalog.AliasEntryRepository
import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.BrowseNodeRepository
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRepository
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesRequest
import com.example.shoppingassistant.domain.catalog.CatalogLiveValuesSnapshot
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.CategoryReplacementResolver
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.CategoryAlias
import com.example.shoppingassistant.domain.catalog.CategoryAliasRepository
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMapping
import com.example.shoppingassistant.domain.catalog.GoogleTaxonomyMappingRepository
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class CatalogRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var repository: InMemoryCatalogRepository
    private lateinit var taxonomyRepository: InMemoryTaxonomyRepository
    private lateinit var liveValuesRepository: InMemoryCatalogLiveValuesRepository
    private lateinit var readinessHistoryService: InMemoryCatalogReadinessHistoryService
    private lateinit var readinessGovernanceService: InMemoryCatalogReadinessGovernanceService
    private lateinit var governanceReportsService: InMemoryCatalogGovernanceReportsService
    private lateinit var runtimeCompatibilityService: InMemoryCatalogRuntimeCompatibilityService
    private lateinit var openApiService: InMemoryCatalogOpenApiService
    private lateinit var readinessSnapshotRepository: InMemoryCatalogReadinessSnapshotRepository
    private lateinit var readinessInventoryService: InMemoryCatalogReadinessInventoryService
    private lateinit var governanceRefreshSurfaceService: InMemoryCatalogGovernanceRefreshSurfaceService

    @Before
    fun setUp() {
        taxonomyRepository = InMemoryTaxonomyRepository()
        repository = InMemoryCatalogRepository()
        liveValuesRepository = InMemoryCatalogLiveValuesRepository()
        readinessHistoryService = InMemoryCatalogReadinessHistoryService(repository)
        readinessGovernanceService = InMemoryCatalogReadinessGovernanceService()
        governanceReportsService = InMemoryCatalogGovernanceReportsService()
        runtimeCompatibilityService = InMemoryCatalogRuntimeCompatibilityService()
        openApiService = InMemoryCatalogOpenApiService()
        readinessSnapshotRepository = InMemoryCatalogReadinessSnapshotRepository()
        governanceRefreshSurfaceService = InMemoryCatalogGovernanceRefreshSurfaceService()
        readinessInventoryService = InMemoryCatalogReadinessInventoryService(
            repository = repository,
            taxonomyRepository = taxonomyRepository,
        )
        startKoin {
            modules(
                module {
                    single<CatalogReadRepository> { repository }
                    single<CatalogTaxonomyRepository> { taxonomyRepository }
                    single<CatalogLiveValuesRepository> { liveValuesRepository }
                    single<CatalogReadinessInventoryService> { readinessInventoryService }
                    single<CatalogReadinessHistoryService> { readinessHistoryService }
                    single<CatalogReadinessGovernanceService> { readinessGovernanceService }
                    single<CatalogGovernanceReportsService> { governanceReportsService }
                    single<CatalogRuntimeCompatibilityService> { runtimeCompatibilityService }
                    single<CatalogOpenApiService> { openApiService }
                    single<CatalogReadinessSnapshotRepository> { readinessSnapshotRepository }
                    single<CatalogGovernanceRefreshSurfaceService> { governanceRefreshSurfaceService }
                    single<CategoryAliasRepository> { taxonomyRepository }
                    single<BrowseNodeRepository> { taxonomyRepository }
                    single<AliasEntryRepository> { taxonomyRepository }
                    single<GoogleTaxonomyMappingRepository> { taxonomyRepository }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun categories_endpoint_returns_catalog_categories() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/categories")

        assertEquals(HttpStatusCode.OK, response.status)
        val categories = json.decodeFromString(
            deserializer = ListSerializer(Category.serializer()),
            string = response.bodyAsText(),
        )
        assertTrue(categories.isNotEmpty())
        assertTrue(categories.any { it.code == "TECH.PHONES" })
    }

    @Test
    fun effective_spec_endpoint_returns_404_for_unknown_category() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/effective-spec/UNKNOWN.CATEGORY")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun openapi_endpoint_returns_catalog_contract_document() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/openapi.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"/api/catalog/effective-spec/{categoryCode}\""))
        assertTrue(response.bodyAsText().contains("\"/api/catalog/runtime-compatibility\""))
    }

    @Test
    fun effective_spec_endpoint_forwards_brand_model_and_resolves_redirect() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get(
            "/api/catalog/effective-spec/TECH.OLD_PHONES?brand=apple&model=iphone%2016%20pro",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("TECH.PHONES", repository.lastConstraintsRequest?.categoryCode)
        assertEquals("apple", repository.lastConstraintsRequest?.brand)
        assertEquals("iphone 16 pro", repository.lastConstraintsRequest?.model)
        assertEquals("true", response.headers[CATALOG_CATEGORY_REDIRECTED_HEADER])
        assertEquals("TECH.OLD_PHONES", response.headers[CATALOG_CATEGORY_REQUESTED_CODE_HEADER])
        assertEquals("TECH.PHONES", response.headers[CATALOG_CATEGORY_RESOLVED_CODE_HEADER])

        val spec = json.decodeFromString(
            deserializer = CatalogCategoryEffectiveSpec.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("TECH.PHONES", spec.category.code)
        assertTrue(spec.meta.completenessGatePassed)
        assertTrue(spec.attributes.any { it.code == "brand" })
    }

    @Test
    fun readiness_endpoint_returns_inventory_with_split_readiness_fields() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/readiness")

        assertEquals(HttpStatusCode.OK, response.status)
        val items = json.decodeFromString(
            deserializer = ListSerializer(CatalogReadinessInventoryItem.serializer()),
            string = response.bodyAsText(),
        )
        val phones = items.firstOrNull { it.category.code == "TECH.PHONES" }
        assertNotNull(phones)
        assertEquals(phones?.readiness, phones?.editorialReadiness)
        assertEquals(phones?.editorialReadiness, phones?.operationalReadiness)
        assertTrue(phones?.completenessGatePassed == true)
        assertTrue(readinessSnapshotRepository.recordedInventory.isEmpty())
    }

    @Test
    fun readiness_history_endpoint_returns_redirect_aware_history() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/readiness/TECH.OLD_PHONES/history?days=7")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("true", response.headers[CATALOG_CATEGORY_REDIRECTED_HEADER])
        assertEquals("TECH.PHONES", response.headers[CATALOG_CATEGORY_RESOLVED_CODE_HEADER])
        assertEquals("TECH.PHONES", readinessHistoryService.lastCategoryCode)
        assertEquals(7, readinessHistoryService.lastDays)

        val history = json.decodeFromString(
            deserializer = CatalogReadinessHistoryResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("TECH.PHONES", history.category.code)
        assertEquals(7, history.requestedDays)
        assertTrue(history.points.isNotEmpty())
        assertEquals(
            history.points.first().editorialReadiness,
            history.points.first().readiness,
        )
    }

    @Test
    fun readiness_governance_endpoint_returns_machine_readable_policy() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/readiness/governance")

        assertEquals(HttpStatusCode.OK, response.status)
        val governance = json.decodeFromString(
            deserializer = CatalogReadinessGovernanceResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertTrue(governance.releasePolicy.owner.isNotBlank())
        assertTrue(governance.readinessPolicy.sqlChecks.isNotEmpty())
        assertTrue(governance.compatibilityPolicy.requiredVersionHeaders.isNotEmpty())
        assertTrue(
            governance.releasePolicy.requiredArtifactStatuses.any { artifact ->
                artifact.declaredPath == "readiness_governance_policy.json" && artifact.exists
            },
        )
    }

    @Test
    fun readiness_governance_reports_endpoint_returns_persisted_reports() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/readiness/governance/reports?limit=5")

        assertEquals(HttpStatusCode.OK, response.status)
        val reports = json.decodeFromString(
            deserializer = ListSerializer(CatalogGovernanceReportResponse.serializer()),
            string = response.bodyAsText(),
        )
        assertEquals(1, reports.size)
        assertEquals("WEEKLY_SUMMARY", reports.first().reportType)
    }

    @Test
    fun live_values_endpoint_returns_server_authoritative_snapshot() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get(
            "/api/catalog/live-values?categoryCode=TECH.PHONES&brand=Apple&attributeCode=color&attributeCode=storage",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val snapshot = json.decodeFromString(
            deserializer = CatalogLiveValuesSnapshot.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("TECH.PHONES", liveValuesRepository.lastRequest?.categoryCode)
        assertEquals("Apple", liveValuesRepository.lastRequest?.brand)
        assertTrue(snapshot.brandOptions.contains("Apple"))
        assertTrue(snapshot.valuesByAttributeCode["color"].orEmpty().contains("Black"))
        assertTrue(snapshot.knownValuesByAttributeCode["color"].orEmpty().contains("Deep Blue"))
        assertTrue(snapshot.knownValueAliasesByAttributeCode["color"].orEmpty()["Deep Blue"].orEmpty().contains("deep blue"))
    }

    @Test
    fun runtime_compatibility_endpoint_returns_machine_readable_policy() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/runtime-compatibility")

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString(
            deserializer = CatalogRuntimeCompatibilityResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("/api/catalog/version", payload.policy.versionEndpoint)
        assertTrue(payload.policy.negotiatedDataVersionMustStayStable)
        assertTrue(payload.minSupportedClientSchemaVersion.isNotBlank())
        assertEquals(CatalogDataVersion.current, payload.currentDataVersion)
    }

    @Test
    fun aliases_endpoint_reads_from_repository() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/aliases")

        assertEquals(HttpStatusCode.OK, response.status)
        val aliases = json.decodeFromString(
            deserializer = ListSerializer(CategoryAlias.serializer()),
            string = response.bodyAsText(),
        )
        assertEquals(taxonomyRepository.aliases, aliases)
    }

    @Test
    fun governance_sources_endpoint_returns_operational_surface_entries() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/governance/sources?categoryCode=TECH.PHONES")

        assertEquals(HttpStatusCode.OK, response.status)
        val sources = json.decodeFromString(
            deserializer = ListSerializer(CatalogGovernanceSourceRegistryEntryResponse.serializer()),
            string = response.bodyAsText(),
        )
        assertTrue(sources.any { it.registryCode == "TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE" })
    }

    @Test
    fun governance_refresh_status_endpoint_returns_scheduler_state() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/governance/refresh-status?categoryCode=TECH.PHONES")

        assertEquals(HttpStatusCode.OK, response.status)
        val status = json.decodeFromString(
            deserializer = CatalogGovernanceRefreshStatusResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertTrue(status.enabled)
        assertEquals("TECH.PHONES", status.configuredCategoryCode)
        assertTrue(status.matchedRegistryCodes.contains("TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE"))
    }

    @Test
    fun governance_refresh_endpoint_triggers_refresh_runs() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.post(
            "/api/catalog/governance/refresh?categoryCode=TECH.PHONES&registryCode=TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString(
            deserializer = CatalogGovernanceRefreshTriggerResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals("TECH.PHONES", payload.categoryCode)
        assertEquals(1, payload.runs.size)
        assertEquals("TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE", payload.runs.single().registryCode)
    }

    @Test
    fun governance_model_enrichment_queue_endpoint_returns_runtime_candidates() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get(
            "/api/catalog/governance/model-enrichment-queue?categoryCode=TECH.PHONES&status=ready_for_official_enrichment",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val queue = json.decodeFromString(
            deserializer = ListSerializer(CatalogGovernanceModelEnrichmentCandidateResponse.serializer()),
            string = response.bodyAsText(),
        )
        assertEquals(1, queue.size)
        assertEquals("READY_FOR_OFFICIAL_ENRICHMENT", queue.single().status)
        assertEquals("Honor", queue.single().brandRaw)
        assertEquals("HONOR_400", queue.single().canonicalModelCode)
    }

    @Test
    fun governance_model_enrichment_promote_endpoint_accepts_operator_payload() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.post("/api/catalog/governance/model-enrichment-promote?categoryCode=TECH.PHONES") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    CatalogGovernanceModelEnrichmentPromotionRequest(
                        candidateId = 301L,
                        actor = "tester",
                        sourceUri = "https://www.honor.com/global/phones/honor-400/",
                        parserType = "GENERIC_PHONE_SPECS_PAGE",
                        releaseDate = "2026-04-01",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString(
            deserializer = CatalogGovernanceModelEnrichmentPromotionResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals(301L, payload.candidateId)
        assertEquals("PROMOTE_TO_OFFICIAL_SOURCE", payload.action)
        assertEquals("ENRICHED", payload.candidateStatus)
        assertEquals("HONOR_OFFICIAL_PHONES", payload.sourceCode)
        assertEquals("HONOR_400", payload.endpointCode)
    }

    @Test
    fun governance_review_queue_endpoint_returns_candidate_clusters() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.get("/api/catalog/governance/review-queue?categoryCode=TECH.PHONES")

        assertEquals(HttpStatusCode.OK, response.status)
        val queue = json.decodeFromString(
            deserializer = ListSerializer(CatalogGovernanceReviewQueueItemResponse.serializer()),
            string = response.bodyAsText(),
        )
        assertTrue(queue.any { it.clusterKey == "phones.color.review" })
    }

    @Test
    fun governance_review_action_endpoint_submits_candidate_decision() = testApplication {
        application {
            configureSerialization()
            routing { catalogRoutes() }
        }

        val response = client.post(
            "/api/catalog/governance/review-action?categoryCode=TECH.PHONES&candidateId=99&action=approve&actor=tester",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString(
            deserializer = CatalogGovernanceReviewActionResponse.serializer(),
            string = response.bodyAsText(),
        )
        assertEquals(99L, payload.candidateId)
        assertEquals("APPROVE", payload.action)
        assertEquals("APPROVED", payload.candidateStatus)
    }

}

private class InMemoryCatalogReadinessHistoryService(
    private val repository: CatalogReadRepository,
) : CatalogReadinessHistoryService {
    var lastCategoryCode: String? = null
        private set
    var lastDays: Int? = null
        private set

    override suspend fun getCategoryHistory(
        categoryCode: String,
        days: Int,
    ): CatalogReadinessHistoryResponse? {
        lastCategoryCode = categoryCode
        lastDays = days
        val spec = repository.getCategoryEffectiveSpec(categoryCode) ?: return null
        return CatalogReadinessHistoryResponse(
            category = spec.category,
            requestedDays = days,
            operationalWindowDays = OPERATIONAL_WINDOW_DAYS,
            points = listOf(
                CatalogReadinessHistoryPoint(
                    windowEndDate = "2026-03-10",
                    readiness = spec.meta.editorialReadiness,
                    editorialReadiness = spec.meta.editorialReadiness,
                    operationalReadiness = spec.meta.editorialReadiness,
                    blockingIssues = spec.meta.readinessBlockingIssues,
                    editorialBlockingIssues = spec.meta.editorialBlockingIssues,
                    operationalBlockingIssues = emptyList(),
                    operationalSampleCount = 5,
                    operationalDroppedRate = 0.0,
                    operationalUnknownAttributeRate = 0.0,
                    operationalRequiredMissingRate = 0.0,
                    operationalLowConfidenceRate = 0.0,
                ),
            ),
        )
    }
}

private class InMemoryCatalogReadinessGovernanceService : CatalogReadinessGovernanceService {
    override fun getGovernance(): CatalogReadinessGovernanceResponse =
        CatalogReadinessGovernanceServiceImpl().getGovernance()
}

private class InMemoryCatalogGovernanceReportsService : CatalogGovernanceReportsService {
    override suspend fun listReports(limit: Int): List<CatalogGovernanceReportResponse> =
        listOf(
            CatalogGovernanceReportResponse(
                reportType = "WEEKLY_SUMMARY",
                reportDate = "2026-03-10",
                generatedAt = "2026-03-10T09:31:00Z",
                windowStartDate = "2026-03-04",
                windowEndDate = "2026-03-10",
                totalCategories = 2,
                readyCategories = 2,
                betaCategories = 0,
                internalCategories = 0,
                categoriesWithBlockingIssues = emptyList(),
                dataVersion = "2.2.0",
                schemaVersion = "1.0.0",
            ),
        ).take(limit)
}

private class InMemoryCatalogRuntimeCompatibilityService : CatalogRuntimeCompatibilityService {
    override fun getRuntimeCompatibility(): CatalogRuntimeCompatibilityResponse =
        CatalogRuntimeCompatibilityServiceImpl().getRuntimeCompatibility()
}

private class InMemoryCatalogOpenApiService : CatalogOpenApiService {
    override fun getDocument(): String =
        """{"openapi":"3.1.0","paths":{"/api/catalog/effective-spec/{categoryCode}":{},"/api/catalog/runtime-compatibility":{}}}"""
}

private class InMemoryCatalogGovernanceRefreshSurfaceService : CatalogGovernanceRefreshSurfaceService {
    override suspend fun ensurePhonesSourceRegistry(): List<CatalogGovernanceSourceRegistryEntryResponse> =
        listSources(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE)

    override suspend fun listSources(
        categoryCode: String,
    ): List<CatalogGovernanceSourceRegistryEntryResponse> =
        listOf(
            CatalogGovernanceSourceRegistryEntryResponse(
                registryCode = "TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE",
                categoryCode = categoryCode,
                connectorType = "CURATED_SEED_PACK",
                sourceCode = "apple-official",
                externalRef = "APPLE_IPHONE",
                displayName = "Apple iPhone",
                tier = "AUTHORITATIVE",
                defaultLocale = "en-us",
                marketCode = "US",
                sourceUri = "https://www.apple.com/iphone/",
                enabled = true,
                autoPublish = true,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )

    override suspend fun getRefreshStatus(
        categoryCode: String,
    ): CatalogGovernanceRefreshStatusResponse =
        CatalogGovernanceRefreshStatusResponse(
            enabled = true,
            configuredCategoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE,
            effectiveCategoryCode = categoryCode,
            pollIntervalMs = 60_000L,
            trigger = "SCHEDULED",
            connectorTypes = listOf("OFFICIAL_PHONE_WEB_SOURCE"),
            availableSourceCount = 1,
            matchedSourceCount = 1,
            matchedRegistryCodes = listOf("TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE"),
            latestRunId = 11L,
            latestRunRegistryCode = "TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE",
            latestRunStatus = "COMPLETED",
            latestRunPublishStatus = "PUBLISHED",
            latestRunStartedAt = 100L,
            latestRunFinishedAt = 200L,
        )

    override suspend fun listRefreshRuns(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceRefreshRunResponse> =
        listOf(
            CatalogGovernanceRefreshRunResponse(
                id = 11L,
                registryCode = "TECH.PHONES|CURATED_SEED_PACK|APPLE_IPHONE",
                categoryCode = categoryCode,
                trigger = "MANUAL",
                status = "COMPLETED",
                publishStatus = "PUBLISHED",
                startedAt = 100L,
                finishedAt = 200L,
            ),
        ).take(limit)

    override suspend fun listModelEnrichmentQueue(
        categoryCode: String,
        statuses: Set<CatalogPhoneModelEnrichmentStatus>,
        limit: Int,
    ): List<CatalogGovernanceModelEnrichmentCandidateResponse> =
        listOf(
            CatalogGovernanceModelEnrichmentCandidateResponse(
                id = 301L,
                candidateKey = "TECH.PHONES|MODEL|HONOR_400",
                categoryCode = categoryCode,
                brandRaw = "Honor",
                brandCode = "HONOR",
                familyRaw = "Honor 400",
                familyCode = "HONOR_400",
                canonicalModelCode = "HONOR_400",
                modelRaw = "400",
                modelNormalized = "400",
                officialSourceCode = "HONOR_OFFICIAL_PHONES",
                officialEndpointCode = null,
                status = "READY_FOR_OFFICIAL_ENRICHMENT",
                observedCount = 4,
                distinctSellerCount = 2,
                sellerRefs = listOf("seller-1", "seller-2"),
                sampleOfferRefs = listOf("offer-1", "offer-2"),
                maxConfidence = 0.92,
                reasonCodes = emptyList(),
                metadata = mapOf("sampleTitle" to "Honor 400 12/512"),
                suggestedSourceUri = "https://www.honor.com/global/phones/honor-400/",
                suggestedParserType = "GENERIC_PHONE_SPECS_PAGE",
                sourceUriSuggestionConfidence = "MEDIUM",
                sourceUriSuggestionReason = "test_fixture",
                firstSeenAt = 100L,
                lastSeenAt = 200L,
                createdAt = 100L,
                updatedAt = 200L,
            ),
        )
            .filter { candidate ->
                statuses.isEmpty() || statuses.any { status -> status.name == candidate.status }
            }
            .take(limit)

    override suspend fun promoteModelEnrichmentCandidate(
        categoryCode: String,
        request: CatalogGovernanceModelEnrichmentPromotionRequest,
    ): CatalogGovernanceModelEnrichmentPromotionResponse =
        CatalogGovernanceModelEnrichmentPromotionResponse(
            categoryCode = categoryCode,
            candidateId = request.candidateId,
            action = "PROMOTE_TO_OFFICIAL_SOURCE",
            candidateStatus = "ENRICHED",
            sourceCode = "HONOR_OFFICIAL_PHONES",
            registryCode = "TECH.PHONES|OFFICIAL_PHONE_WEB_SOURCE|HONOR_OFFICIAL_PHONES",
            endpointCode = "HONOR_400",
            modelCode = "HONOR_400",
            familyCode = "HONOR_400",
            parserType = request.parserType,
            triggerRefresh = request.triggerRefresh,
            runs = emptyList(),
            metadata = mapOf("sourceUri" to (request.sourceUri ?: "AUTO")),
        )

    override suspend fun listReviewQueue(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernanceReviewQueueItemResponse> =
        listOf(
            CatalogGovernanceReviewQueueItemResponse(
                clusterKey = "phones.color.review",
                categoryCode = categoryCode,
                attributeCode = "color",
                normalizedValue = "cosmic blue",
                primaryCandidateId = 99L,
                recommendation = "REVIEW_CANONICAL",
                recommendedDisposition = "NEW_CANONICAL_CANDIDATE",
                totalScore = 0.72,
                observedCount = 4,
                candidateCount = 1,
                candidateIds = listOf(99L),
                candidateStatuses = listOf("REVIEWING"),
                reasons = listOf("review_canonical_candidate"),
            ),
        ).take(limit)

    override suspend fun submitReviewAction(
        categoryCode: String,
        candidateId: Long,
        action: CatalogGovernanceReviewAction,
        actor: String,
        reasonCode: String?,
    ): CatalogGovernanceReviewActionResponse =
        CatalogGovernanceReviewActionResponse(
            categoryCode = categoryCode,
            candidateId = candidateId,
            attributeCode = "color",
            action = action.name,
            candidateStatus = when (action) {
                CatalogGovernanceReviewAction.APPROVE -> "APPROVED"
                CatalogGovernanceReviewAction.REJECT -> "REJECTED"
                CatalogGovernanceReviewAction.PROMOTE -> "PROMOTED"
            },
            decisionId = 501L,
            decisionAction = when (action) {
                CatalogGovernanceReviewAction.APPROVE -> "APPROVE"
                CatalogGovernanceReviewAction.REJECT -> "REJECT"
                CatalogGovernanceReviewAction.PROMOTE -> "PROMOTE"
            },
            reasonCode = reasonCode ?: "test_action",
            actor = actor,
            publishStatus = if (action == CatalogGovernanceReviewAction.PROMOTE) "PUBLISHED" else "SKIPPED",
        )

    override suspend fun listPublishEvents(
        categoryCode: String,
        limit: Int,
    ): List<CatalogGovernancePublishEventResponse> =
        listOf(
            CatalogGovernancePublishEventResponse(
                id = 77L,
                refreshRunId = 11L,
                categoryCode = categoryCode,
                eventType = "REFRESH_SYNC",
                artifactType = "SERVING_ARTIFACTS",
                status = "PUBLISHED",
                createdAt = 300L,
            ),
        ).take(limit)

    override suspend fun triggerRefresh(
        categoryCode: String,
        registryCodes: List<String>,
        trigger: String,
    ): CatalogGovernanceRefreshTriggerResponse =
        CatalogGovernanceRefreshTriggerResponse(
            categoryCode = categoryCode,
            requestedRegistryCodes = registryCodes,
            runs = listRefreshRuns(categoryCode = categoryCode, limit = 20),
        )

    override suspend fun triggerRebuild(
        categoryCode: String,
        reason: String,
    ): CatalogGovernancePublishEventResponse =
        CatalogGovernancePublishEventResponse(
            id = 88L,
            categoryCode = categoryCode,
            eventType = reason,
            artifactType = "SERVING_ARTIFACTS",
            status = "PUBLISHED",
            createdAt = 400L,
        )
}

private class InMemoryCatalogReadinessSnapshotRepository : CatalogReadinessSnapshotRepository {
    var recordedInventory: List<CatalogReadinessInventoryItem> = emptyList()
        private set

    override suspend fun recordInventory(
        snapshotDate: kotlinx.datetime.LocalDate,
        inventory: List<CatalogReadinessInventoryItem>,
    ) {
        recordedInventory = inventory
    }

    override suspend fun hasSnapshotForDate(snapshotDate: kotlinx.datetime.LocalDate): Boolean = false

    override suspend fun loadHistory(
        categoryCode: String,
        days: Int,
    ): List<CatalogPersistedReadinessSnapshot> = emptyList()
}

private class InMemoryCatalogLiveValuesRepository : CatalogLiveValuesRepository {
    var lastRequest: CatalogLiveValuesRequest? = null
        private set

    override suspend fun getLiveValues(
        request: CatalogLiveValuesRequest,
    ): CatalogLiveValuesSnapshot {
        lastRequest = request
        return CatalogLiveValuesSnapshot(
            valuesByAttributeCode = linkedMapOf(
                "color" to listOf("Black", "Blue"),
                "storage" to listOf("128GB", "256GB"),
            ),
            knownValuesByAttributeCode = linkedMapOf(
                "color" to listOf("Black", "Blue", "Deep Blue"),
            ),
            knownValueAliasesByAttributeCode = linkedMapOf(
                "color" to linkedMapOf(
                    "Deep Blue" to listOf("deep blue", "тёмно-синий"),
                ),
            ),
            brandOptions = listOf("Apple", "Samsung"),
            modelOptions = listOf("iPhone 16 Pro"),
        )
    }
}

private class InMemoryCatalogRepository : CatalogReadRepository {
    private val specs = CatalogSeed.categoryWriteSpecs

    var lastConstraintsRequest: ConstraintsRequest? = null
        private set

    private fun resolveConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> {
        lastConstraintsRequest = ConstraintsRequest(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
        if (categoryCode.isBlank()) return emptyList()
        return CatalogSeed.constraints.filter { constraint ->
            when (constraint.scope) {
                com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope.GLOBAL -> true
                com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope.CATEGORY ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true
                com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope.BRAND ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand ?: "", ignoreCase = true) == true
                com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope.MODEL ->
                    constraint.categoryCode?.equals(categoryCode, ignoreCase = true) == true &&
                        constraint.brand?.equals(brand ?: "", ignoreCase = true) == true &&
                        constraint.model?.equals(model ?: "", ignoreCase = true) == true
            }
        }
    }

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val normalized = categoryCode.trim()
        if (normalized.isBlank()) return null
        val spec = specs.firstOrNull { it.category.code.equals(normalized, ignoreCase = true) } ?: return null
        val scopedConstraints = resolveConstraints(
            categoryCode = normalized,
            brand = brand,
            model = model,
        )
        return spec.toCategoryEffectiveSpec(constraints = scopedConstraints)
    }

    data class ConstraintsRequest(
        val categoryCode: String,
        val brand: String?,
        val model: String?,
    )
}

private class InMemoryTaxonomyRepository :
    CatalogTaxonomyRepository,
    CategoryAliasRepository,
    BrowseNodeRepository,
    AliasEntryRepository,
    GoogleTaxonomyMappingRepository {

    private val categories: List<Category> = buildList {
        addAll(CatalogSeed.categoryWriteSpecs.map { it.category })
        add(
            Category(
                code = "TECH.OLD_PHONES",
                segment = CategorySegment.TECH,
                title = localizedTextOf("en" to "Old Phones"),
                parentCode = "TECH",
                status = CategoryStatus.DEPRECATED,
                replacementCode = "TECH.PHONES",
            ),
        )
    }
    val aliases: List<CategoryAlias> = CatalogSeed.categoryAliases
    private val browseNodes: List<BrowseNode> = CatalogSeed.browseNodes
    private val aliasEntries: List<AliasEntry> = CatalogSeed.aliasEntries
    private val mappings: List<GoogleTaxonomyMapping> = CatalogSeed.googleMappings

    override suspend fun listCategories(): List<Category> = categories

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ) = CategoryReplacementResolver.resolve(
        requestedCode = categoryCode,
        categories = categories,
        maxHops = maxHops,
    )

    override suspend fun listAliases(): List<CategoryAlias> = aliases

    override suspend fun listBrowseNodes(): List<BrowseNode> = browseNodes

    override suspend fun getBrowseNode(browseCode: String): BrowseNode? =
        browseNodes.firstOrNull { it.browseCode.equals(browseCode, ignoreCase = true) }

    override suspend fun listAliasEntries(locale: String?): List<AliasEntry> {
        if (locale.isNullOrBlank()) return aliasEntries
        return aliasEntries.filter { it.locale.equals(locale, ignoreCase = true) }
    }

    override suspend fun listMappings(): List<GoogleTaxonomyMapping> = mappings

    override suspend fun getMapping(categoryCode: String): GoogleTaxonomyMapping? =
        mappings.firstOrNull { it.canonicalCode.equals(categoryCode, ignoreCase = true) }
}

private class InMemoryCatalogReadinessInventoryService(
    private val repository: CatalogReadRepository,
    private val taxonomyRepository: CatalogTaxonomyRepository,
) : CatalogReadinessInventoryService {
    override suspend fun getInventory(): List<CatalogReadinessInventoryItem> {
        return taxonomyRepository.listCategories()
            .filter { it.status != CategoryStatus.HIDDEN }
            .sortedBy { it.code }
            .mapNotNull { category ->
                repository.getCategoryEffectiveSpec(category.code)?.let { spec ->
                    CatalogReadinessInventoryItem(
                        category = spec.category,
                        readiness = spec.readiness,
                        editorialReadiness = spec.meta.editorialReadiness,
                        operationalReadiness = spec.meta.operationalReadiness,
                        completenessGatePassed = spec.meta.completenessGatePassed,
                        blockingIssues = spec.meta.readinessBlockingIssues,
                        editorialBlockingIssues = spec.meta.editorialBlockingIssues,
                        operationalBlockingIssues = spec.meta.operationalBlockingIssues,
                        operationalSampleCount = spec.meta.operationalSampleCount,
                        operationalDroppedRate = spec.meta.operationalDroppedRate,
                        operationalUnknownAttributeRate = spec.meta.operationalUnknownAttributeRate,
                        operationalRequiredMissingRate = spec.meta.operationalRequiredMissingRate,
                        operationalLowConfidenceRate = spec.meta.operationalLowConfidenceRate,
                    )
                }
            }
    }
}

