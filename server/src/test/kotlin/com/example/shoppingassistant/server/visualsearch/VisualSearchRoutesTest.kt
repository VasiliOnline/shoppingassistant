package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryWriteSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryAttribute
import com.example.shoppingassistant.domain.catalog.CategoryRedirectResolution
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.AttributeDef
import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateProjection
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchContextReuseStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchHttpHeaders
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSchemaVersion
import com.example.shoppingassistant.server.config.VisualSearchConfig
import com.example.shoppingassistant.server.plugins.configureSerialization
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
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

class VisualSearchRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var catalogRepository: StubVisualCatalogRepository

    @Before
    fun setUp() {
        catalogRepository = StubVisualCatalogRepository()
        startKoin {
            modules(
                module {
                    single<CatalogReadRepository> { catalogRepository }
                    single<CatalogTaxonomyRepository> { catalogRepository }
                    single {
                        VisualSearchConfig(
                            enabled = true,
                            serverAiEnabled = false,
                            contextReuseTtlSeconds = 900,
                            yandexBaseUrl = "https://example.test",
                            yandexResponsesBaseUrl = "https://example.test",
                            yandexApiKey = null,
                            yandexProjectId = null,
                            yandexModel = "test-model",
                            yandexAgentId = null,
                            yandexFallbackAgentIds = emptyList(),
                            yandexAllowModelFallback = true,
                            yandexSystemPrompt = null,
                            yandexTimeoutMs = 10_000,
                            aiShortlistCategoryLimit = 12,
                            aiShortlistFamilyLimit = 6,
                            aiShortlistModelLimit = 6,
                        )
                    }
                    single<VisualSearchContextStore> { InMemoryVisualSearchContextStore(get()) }
                    single<VisualSearchService> {
                        VisualSearchServiceImpl(
                            catalogRepository = get(),
                            catalogTaxonomyRepository = get(),
                            contextStore = get(),
                            config = get(),
                        )
                    }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun bind_query_returns_partial_for_similar_brand_only_route() = testApplication {
        application {
            configureSerialization()
            routing { visualSearchRoutes() }
        }

        val response = client.post("/api/search/visual/bind-query") {
            applyHeaders(idempotencyKey = "bind-query-1")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    VisualSearchBindQueryRequest.serializer(),
                    VisualSearchBindQueryRequest(
                        intent = VisualSearchIntent.SIMILAR,
                        source = VisualSearchSource.GALLERY,
                        manualCategoryCode = "TECH.PHONES",
                        cheapProjection = VisualSearchCandidateProjection(
                            categoryCode = "TECH.PHONES",
                            brand = VisualSearchCandidateValue(text = "Apple", confidence = 0.93f),
                        ),
                        fingerprint = "sha256-phone-1",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString(
            VisualSearchBindQueryResponse.serializer(),
            response.bodyAsText(),
        )
        assertNotNull(payload.boundQuery)
        assertEquals("TECH.PHONES", payload.boundQuery?.categoryCode)
        assertEquals("Apple", payload.boundQuery?.searchCriteria?.brand)
        assertEquals("accepted_partial", payload.boundQuery?.binderStatus?.serialName())
        assertTrue(payload.boundQuery?.allowTextRefinement == true)
    }

    @Test
    fun normalize_draft_returns_cheap_projection_when_server_ai_is_disabled() = testApplication {
        application {
            configureSerialization()
            routing { visualSearchRoutes() }
        }

        val response = client.post("/api/search/visual/normalize-draft") {
            applyHeaders(idempotencyKey = "normalize-draft-1")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    VisualSearchNormalizeDraftRequest.serializer(),
                    VisualSearchNormalizeDraftRequest(
                        asset = VisualSearchImageAsset(
                            sha256 = "sha256-phone-3",
                            mimeType = "image/jpeg",
                            widthPx = 1600,
                            heightPx = 1200,
                            storageKey = "content://visual/phone-3",
                        ),
                        source = VisualSearchSource.GALLERY,
                        entryPoint = com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
                        selectionMode = VisualSearchSelectionMode.MANUAL_CROP,
                        intent = VisualSearchIntent.IDENTIFY_FIRST,
                        preflightSignals = VisualSearchPreflightSignals(
                            reasonCodes = listOf("CATEGORY_HINT_PRESENT", "CHEAP_PROJECTION_READY"),
                            cheapProjectionReady = true,
                            admitServerAi = true,
                            exactCategoryCode = "TECH.PHONES",
                        ),
                        manualCategoryCode = "TECH.PHONES",
                        locale = "ru-RU",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString(
            VisualSearchNormalizeDraftResponse.serializer(),
            response.bodyAsText(),
        )
        assertEquals("TECH.PHONES", payload.draft?.projection?.categoryCode)
        assertEquals("server_cheap_projection", payload.draft?.providerName)
        assertTrue(payload.draft?.reasonCodes?.contains("SERVER_AI_DISABLED_CHEAP_PATH") == true)
    }

    @Test
    fun context_reuse_returns_hit_after_successful_bind() = testApplication {
        application {
            configureSerialization()
            routing { visualSearchRoutes() }
        }

        val bindResponse = client.post("/api/search/visual/bind-query") {
            applyHeaders(idempotencyKey = "bind-query-2")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    VisualSearchBindQueryRequest.serializer(),
                    VisualSearchBindQueryRequest(
                        intent = VisualSearchIntent.SIMILAR,
                        source = VisualSearchSource.CAMERA,
                        manualCategoryCode = "TECH.PHONES",
                        cheapProjection = VisualSearchCandidateProjection(
                            categoryCode = "TECH.PHONES",
                            brand = VisualSearchCandidateValue(text = "Apple", confidence = 0.95f),
                            model = VisualSearchCandidateValue(text = "iPhone 16 Pro", confidence = 0.91f),
                        ),
                        fingerprint = "sha256-phone-2",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, bindResponse.status)

        val reuseResponse = client.post("/api/search/visual/context/reuse") {
            applyHeaders()
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    VisualSearchContextReuseRequest.serializer(),
                    VisualSearchContextReuseRequest(
                        assetFingerprint = "sha256-phone-2",
                        source = VisualSearchSource.CAMERA,
                        entryPoint = com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint.RETRY,
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, reuseResponse.status)
        val payload = json.decodeFromString(
            VisualSearchContextReuseResponse.serializer(),
            reuseResponse.bodyAsText(),
        )
        assertEquals(VisualSearchContextReuseStatus.HIT, payload.status)
        assertEquals("TECH.PHONES", payload.reusedQuery?.categoryCode)
        assertEquals("Apple", payload.reusedQuery?.searchCriteria?.brand)
    }

    @Test
    fun bind_query_rejects_catalog_version_mismatch() = testApplication {
        application {
            configureSerialization()
            routing { visualSearchRoutes() }
        }

        val response = client.post("/api/search/visual/bind-query") {
            header(VisualSearchHttpHeaders.visualSessionId, "vs-session-1")
            header(VisualSearchHttpHeaders.clientSchemaVersion, VisualSearchSchemaVersion.current)
            header(VisualSearchHttpHeaders.catalogDataVersion, "stale-version")
            header(VisualSearchHttpHeaders.idempotencyKey, "bind-query-3")
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    VisualSearchBindQueryRequest.serializer(),
                    VisualSearchBindQueryRequest(
                        intent = VisualSearchIntent.SIMILAR,
                        source = VisualSearchSource.GALLERY,
                        manualCategoryCode = "TECH.PHONES",
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.PreconditionFailed, response.status)
        assertTrue(response.bodyAsText().contains("CATALOG_VERSION_MISMATCH"))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(
        idempotencyKey: String? = null,
    ) {
        header(VisualSearchHttpHeaders.visualSessionId, "vs-session-1")
        header(VisualSearchHttpHeaders.clientSchemaVersion, VisualSearchSchemaVersion.current)
        header(VisualSearchHttpHeaders.catalogDataVersion, CatalogDataVersion.current)
        idempotencyKey?.let { header(VisualSearchHttpHeaders.idempotencyKey, it) }
    }
}

private class StubVisualCatalogRepository : CatalogReadRepository, CatalogTaxonomyRepository {
    private val categories = listOf(
        Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Смартфоны"),
            status = CategoryStatus.ACTIVE,
        ),
        Category(
            code = "APPL.MAJOR",
            segment = CategorySegment.APPL,
            title = localizedTextOf("ru" to "Крупная техника"),
            status = CategoryStatus.ACTIVE,
        ),
    )
    private val specs = mapOf(
        "TECH.PHONES" to CatalogCategoryWriteSpec(
            category = categories.first { it.code == "TECH.PHONES" },
            attributes = listOf(
                AttributeDef(code = "brand", title = "Brand", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "model", title = "Model", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "color", title = "Color", dataType = AttributeDataType.STRING),
            ),
            categoryAttributes = listOf(
                CategoryAttribute(categoryCode = "TECH.PHONES", attributeCode = "brand", uiOrder = 10, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "TECH.PHONES", attributeCode = "model", uiOrder = 20, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "TECH.PHONES", attributeCode = "color", uiOrder = 30, isRequiredForCategory = false),
            ),
        ).toCategoryEffectiveSpec(),
        "APPL.MAJOR" to CatalogCategoryWriteSpec(
            category = categories.first { it.code == "APPL.MAJOR" },
            attributes = listOf(
                AttributeDef(code = "brand", title = "Brand", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "capacity_l", title = "Capacity", dataType = AttributeDataType.DECIMAL),
            ),
            categoryAttributes = listOf(
                CategoryAttribute(categoryCode = "APPL.MAJOR", attributeCode = "brand", uiOrder = 10, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "APPL.MAJOR", attributeCode = "capacity_l", uiOrder = 20, isRequiredForCategory = false),
            ),
        ).toCategoryEffectiveSpec(),
    )

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? = specs[categoryCode]

    override suspend fun listCategories(): List<Category> = categories

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ): CategoryRedirectResolution? {
        val category = categories.firstOrNull { it.code.equals(categoryCode, ignoreCase = true) } ?: return null
        return CategoryRedirectResolution(
            requestedCode = categoryCode,
            resolvedCode = category.code,
            redirectChain = listOf(category.code),
            wasRedirected = false,
            cycleDetected = false,
            unresolvedTarget = null,
        )
    }
}

private fun Enum<*>.serialName(): String = when (this.name) {
    "ACCEPTED" -> "accepted"
    "ACCEPTED_PARTIAL" -> "accepted_partial"
    "REJECTED" -> "rejected"
    else -> this.name.lowercase()
}
