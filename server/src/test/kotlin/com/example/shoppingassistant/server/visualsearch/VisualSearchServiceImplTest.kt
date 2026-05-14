package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.AttributeDef
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryWriteSpec
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryAttribute
import com.example.shoppingassistant.domain.catalog.CategoryRedirectResolution
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBinderStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizationDraft
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateProjection
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchRouteKind
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.server.ai.AiAgentRegistry
import com.example.shoppingassistant.server.ai.AiAgentRoute
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.NoopAiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredRequest
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioTransportConfig
import com.example.shoppingassistant.server.config.VisualSearchAiProvider
import com.example.shoppingassistant.server.config.VisualSearchConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class VisualSearchServiceImplTest {

    @Test
    fun mouse_photo_matches_through_normalize_and_bind_layers() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "prompt-mouse",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
            provider = VisualSearchAiProvider.YANDEX,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = YandexVisualSearchAiDraftNormalizer(
                catalogRepository = catalogRepository,
                catalogTaxonomyRepository = catalogRepository,
                config = config,
                orchestrator = visualSearchTestOrchestrator(
                    targets = listOf(
                        YandexAiExecutionTarget(
                            key = "primary",
                            modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                            promptId = "prompt-mouse",
                        ),
                    ),
                    aiClient = VisualSearchServiceFakeAiClient(
                        """
                        {
                          "sale_object_candidates": [
                            {
                              "title_guess": "Беспроводная мышь",
                              "brand_guess": "Logitech",
                              "category_guess": "Компьютерная мышь",
                              "family_guess": "MX Master",
                              "model_guess": "MX Master 3S",
                              "confidence": 0.95,
                              "attributes": []
                            }
                          ],
                          "ambiguities": []
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-mouse-service-test",
            clientSchemaVersion = "visual-search/1.0",
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "idem-mouse-service-test",
        )
        val normalizeResponse = runBlocking {
            service.normalizeDraft(
                metadata = metadata,
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-mouse-service",
                        mimeType = "image/png",
                        widthPx = 616,
                        heightPx = 1125,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    source = VisualSearchSource.GALLERY,
                    entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    locale = "ru",
                ),
            )
        }

        val draft = normalizeResponse.draft
        assertNotNull(draft)
        assertEquals("TECH.PC_COMPONENTS", draft.projection.categoryCode)
        assertEquals("Logitech", draft.projection.brand?.text)
        assertEquals("MX Master 3S", draft.projection.model?.text)
        assertEquals("MX Master 3S", draft.projection.modelCandidates.firstOrNull()?.text)

        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = draft,
                    fingerprint = "sha256-mouse-service",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED_PARTIAL, bound.binderStatus)
        assertEquals(VisualSearchRouteKind.FAMILY_ANCHOR, bound.routeKind)
        assertEquals("TECH.PC_COMPONENTS", bound.categoryCode)
        assertEquals("Logitech", bound.normalizedQuery?.brand)
        assertEquals("", bound.normalizedQuery?.model)
        assertEquals("MX Master 3S", bound.modelCandidates.firstOrNull()?.text)
    }

    @Test
    fun bind_infers_phone_category_from_ai_title_and_model_when_category_is_missing() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "prompt-phone",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = null,
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-phone-bind-test",
            clientSchemaVersion = "visual-search/1.0",
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "idem-phone-bind-test",
        )
        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = VisualSearchNormalizationDraft(
                        projection = VisualSearchCandidateProjection(
                            title = "iPhone 15 Pro Max",
                            brand = VisualSearchCandidateValue(
                                text = "Apple",
                                source = "VISUAL_DISTINCTIVE",
                            ),
                            model = VisualSearchCandidateValue(
                                text = "iPhone 15 Pro Max",
                                source = "TEXT_EXACT",
                            ),
                        ),
                        providerName = "test",
                        providerSchemaVersion = "test/1",
                    ),
                    fingerprint = "sha256-phone-bind",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED, bound.binderStatus)
        assertEquals("TECH.PHONES", bound.categoryCode)
        assertEquals("Apple", bound.normalizedQuery?.brand)
        assertEquals("iPhone 15 Pro Max", bound.normalizedQuery?.model)
    }

    @Test
    fun bind_prefers_type_inferred_category_when_ai_category_conflicts_with_strong_item_type() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "prompt-mouse-category-guard",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = null,
        )

        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-mouse-category-guard",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-mouse-category-guard",
                ),
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = VisualSearchNormalizationDraft(
                        projection = VisualSearchCandidateProjection(
                            categoryCode = "TECH.GAMING",
                            categoryConfidence = 0.62f,
                            itemType = VisualSearchCandidateValue(
                                text = "computer mouse",
                                confidence = 0.98f,
                                source = "ITEM_TYPE",
                            ),
                            title = "computer mouse",
                        ),
                        confidence = 0.88f,
                        providerName = "test",
                        providerSchemaVersion = "test/mouse-category-guard",
                    ),
                    fingerprint = "sha256-mouse-category-guard",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED_PARTIAL, bound.binderStatus)
        assertEquals(VisualSearchRouteKind.TYPED_ENTITY, bound.routeKind)
        assertEquals("TECH.PC_COMPONENTS", bound.categoryCode)
        assertEquals("computer mouse", bound.itemType?.text)
        assertEquals("computer mouse", bound.searchCriteria.attributes["item_type"]?.asRawString())
    }

    @Test
    fun bind_returns_ranked_candidates_and_uses_primary_acceptable_hypothesis() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "prompt-phone-ranked",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = null,
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-ranked-bind-test",
            clientSchemaVersion = "visual-search/1.0",
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "idem-ranked-bind-test",
        )
        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = VisualSearchNormalizationDraft(
                        projection = VisualSearchCandidateProjection(
                            title = "Apple smartphone",
                            brand = VisualSearchCandidateValue("Apple", 0.87f),
                            family = VisualSearchCandidateValue("iPhone Pro Max", 0.71f),
                        ),
                        confidence = 0.87f,
                        hypotheses = listOf(
                            com.example.shoppingassistant.domain.visualsearch.VisualSearchHypothesis(
                                rank = 1,
                                confidence = 0.87f,
                                projection = VisualSearchCandidateProjection(
                                    title = "Apple iPhone Pro Max",
                                    brand = VisualSearchCandidateValue("Apple", 0.87f),
                                    family = VisualSearchCandidateValue("iPhone Pro Max", 0.71f),
                                ),
                            ),
                            com.example.shoppingassistant.domain.visualsearch.VisualSearchHypothesis(
                                rank = 2,
                                confidence = 0.68f,
                                projection = VisualSearchCandidateProjection(
                                    title = "Apple iPhone 15 Pro Max",
                                    brand = VisualSearchCandidateValue("Apple", 0.92f),
                                    family = VisualSearchCandidateValue("iPhone Pro Max", 0.79f),
                                    model = VisualSearchCandidateValue("iPhone 15 Pro Max", 0.64f),
                                ),
                            ),
                        ),
                        providerName = "test",
                        providerSchemaVersion = "test/2",
                    ),
                    fingerprint = "sha256-ranked-bind",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED_PARTIAL, bound.binderStatus)
        assertEquals("TECH.PHONES", bound.categoryCode)
        assertEquals(2, bindResponse.rankedCandidates.size)
        assertEquals(true, bindResponse.rankedCandidates.first().isPrimary)
        assertEquals("Apple", bindResponse.rankedCandidates[1].query.searchCriteria.brand)
    }

    @Test
    fun bind_falls_back_to_primary_projection_when_all_hypotheses_are_below_threshold() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "prompt-phone-low-hypotheses",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = null,
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-low-hypothesis-fallback-bind",
            clientSchemaVersion = "visual-search/1.0",
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "idem-low-hypothesis-fallback-bind",
        )
        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = VisualSearchNormalizationDraft(
                        projection = VisualSearchCandidateProjection(
                            categoryCode = "TECH.PHONES",
                            categoryConfidence = 0.98f,
                            title = "Android smartphone",
                        ),
                        confidence = 0.87f,
                        hypotheses = listOf(
                            com.example.shoppingassistant.domain.visualsearch.VisualSearchHypothesis(
                                rank = 1,
                                confidence = 0.34f,
                                projection = VisualSearchCandidateProjection(
                                    categoryCode = "TECH.PHONES",
                                    categoryConfidence = 0.98f,
                                    title = "Android smartphone",
                                ),
                            ),
                        ),
                        providerName = "test",
                        providerSchemaVersion = "test/low-hypotheses",
                    ),
                    fingerprint = "sha256-low-hypothesis-fallback",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED_PARTIAL, bound.binderStatus)
        assertEquals(VisualSearchRouteKind.BRANCH_ONLY, bound.routeKind)
        assertEquals("TECH.PHONES", bound.categoryCode)
    }

    @Test
    fun bind_accepts_partial_when_ai_provides_category_without_confidence() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "prompt-category-no-confidence",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = null,
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-category-no-confidence-bind",
            clientSchemaVersion = "visual-search/1.0",
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "idem-category-no-confidence-bind",
        )
        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = VisualSearchNormalizationDraft(
                        projection = VisualSearchCandidateProjection(
                            categoryCode = "TECH.PHONES",
                            brand = VisualSearchCandidateValue("Apple"),
                        ),
                        providerName = "test",
                        providerSchemaVersion = "test/3",
                    ),
                    fingerprint = "sha256-category-no-confidence",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED_PARTIAL, bound.binderStatus)
        assertEquals("TECH.PHONES", bound.categoryCode)
        assertEquals("Apple", bound.normalizedQuery?.brand)
    }


    @Test
    fun bind_accepts_branch_only_when_ai_provides_category_fallback_without_identity() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/v1",
            yandexApiKey = "token",
            yandexProjectId = "folder-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = null,
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 10_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
        )
        val catalogRepository = VisualSearchMouseCatalogRepository()
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = InMemoryVisualSearchContextStore(config),
            config = config,
            aiDraftNormalizer = null,
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-category-only-bind",
            clientSchemaVersion = "visual-search/1.0",
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "idem-category-only-bind",
        )
        val bindResponse = runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    source = VisualSearchSource.GALLERY,
                    selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    normalizationDraft = VisualSearchNormalizationDraft(
                        projection = VisualSearchCandidateProjection(
                            categoryCode = "TECH.PC_COMPONENTS",
                        ),
                        providerName = "test",
                        providerSchemaVersion = "test/4",
                    ),
                    fingerprint = "sha256-category-only",
                    locale = "ru",
                ),
            )
        }

        val bound = bindResponse.boundQuery
        assertNotNull(bound)
        assertEquals(VisualSearchBinderStatus.ACCEPTED_PARTIAL, bound.binderStatus)
        assertEquals("TECH.PC_COMPONENTS", bound.categoryCode)
        assertEquals(VisualSearchRouteKind.BRANCH_ONLY, bound.routeKind)
    }
}

private class VisualSearchServiceFakeAiClient(
    private val responseBody: String,
) : YandexAiStudioClient {
    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse =
        YandexAiStudioStructuredResponse.Success(
            responseBody = responseBody,
            httpStatus = 200,
            invocationMode = if (request.promptId.isNullOrBlank()) {
                YandexAiStudioInvocationMode.CHAT_COMPLETIONS
            } else {
                YandexAiStudioInvocationMode.RESPONSES
            },
        )
}

private fun visualSearchTestOrchestrator(
    targets: List<YandexAiExecutionTarget>,
    aiClient: YandexAiStudioClient,
): AiNormalizationOrchestrator = AiNormalizationOrchestrator(
    agentRegistry = object : AiAgentRegistry {
        override fun routeFor(flow: AiNormalizationFlow): AiAgentRoute? =
            AiAgentRoute(
                flow = flow,
                transport = YandexAiStudioTransportConfig(
                    baseUrl = "https://example.test/v1",
                    responsesBaseUrl = "https://example.test/v1",
                    apiKey = "token",
                    projectId = "folder-id",
                ),
                timeoutMs = 10_000,
                targets = targets,
            )

        override fun describe(flow: AiNormalizationFlow) =
            com.example.shoppingassistant.server.ai.AiAgentFlowDescriptor(
                flow = flow,
                enabled = true,
                ready = true,
                targets = targets.map { target ->
                    com.example.shoppingassistant.server.ai.AiAgentTargetDescriptor(
                        key = target.key,
                        modelUri = target.modelUri,
                        usesSavedAgent = target.usesSavedAgent,
                        promptConfigured = target.promptId != null,
                    )
                },
            )
    },
    aiClient = aiClient,
    telemetry = NoopAiNormalizationTelemetry,
)

private class VisualSearchMouseCatalogRepository : CatalogReadRepository, CatalogTaxonomyRepository {
    private val categories = listOf(
        Category(
            code = "TECH.PC_COMPONENTS",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "ПК и комплектующие", "en" to "PC & components"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
        Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Смартфоны", "en" to "Smartphones"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
        Category(
            code = "TECH.COMPUTERS",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Ноутбуки", "en" to "Laptops"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
        Category(
            code = "TECH.GAMING",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Игровые приставки и игры", "en" to "Gaming"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
    )

    private val specs = mapOf(
        "TECH.PC_COMPONENTS" to CatalogCategoryWriteSpec(
            category = categories.first { it.code == "TECH.PC_COMPONENTS" },
            attributes = listOf(
                AttributeDef(code = "brand", title = "Brand", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "model", title = "Model", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "item_type", title = "Item type", dataType = AttributeDataType.STRING, facetEnabled = true),
            ),
            categoryAttributes = listOf(
                CategoryAttribute(categoryCode = "TECH.PC_COMPONENTS", attributeCode = "brand", uiOrder = 10, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "TECH.PC_COMPONENTS", attributeCode = "model", uiOrder = 20, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "TECH.PC_COMPONENTS", attributeCode = "item_type", uiOrder = 30),
            ),
        ).toCategoryEffectiveSpec(),
        "TECH.PHONES" to CatalogCategoryWriteSpec(
            category = categories.first { it.code == "TECH.PHONES" },
            attributes = listOf(
                AttributeDef(code = "brand", title = "Brand", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "model", title = "Model", dataType = AttributeDataType.STRING, requiredForSearch = true),
            ),
            categoryAttributes = listOf(
                CategoryAttribute(categoryCode = "TECH.PHONES", attributeCode = "brand", uiOrder = 10, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "TECH.PHONES", attributeCode = "model", uiOrder = 20, isRequiredForCategory = true),
            ),
        ).toCategoryEffectiveSpec(),
    )

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? = specs[categoryCode.uppercase()]

    override suspend fun listCategories(): List<Category> = categories

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ): CategoryRedirectResolution? =
        categories.firstOrNull { category -> category.code.equals(categoryCode, ignoreCase = true) }?.let { category ->
            CategoryRedirectResolution(
                requestedCode = categoryCode,
                resolvedCode = category.code,
                redirectChain = listOf(category.code),
                wasRedirected = false,
                cycleDetected = false,
            )
        }
}
