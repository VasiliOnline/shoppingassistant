package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.AttributeDataType
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
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightCategoryCandidate
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectedRegion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.server.ai.AiAgentRegistry
import com.example.shoppingassistant.server.ai.AiAgentRoute
import com.example.shoppingassistant.server.ai.EnvAiAgentRegistry
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.NoopAiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.yandex.DefaultYandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioContentPart
import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredRequest
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioTransportConfig
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import com.example.shoppingassistant.server.config.VisualSearchAiProvider
import com.example.shoppingassistant.server.config.VisualSearchConfig
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class YandexVisualSearchAiDraftNormalizerTest {

    @Test
    fun normalize_accepts_saved_agent_payload_and_maps_it_to_visual_search_draft() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
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
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
                enabled = true,
                serverAiEnabled = true,
                contextReuseTtlSeconds = 900,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "folder-id",
                yandexModel = "gemma-3-27b-it",
                yandexAgentId = "prompt-123",
                yandexFallbackAgentIds = emptyList(),
                yandexAllowModelFallback = true,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                        promptId = "prompt-123",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-1",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-1",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-mouse-1",
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals("prompt-123", aiClient.lastRequest?.promptId)
        assertEquals("Logitech", result.projection.brand?.text)
        assertEquals("MX Master 3S", result.projection.model?.text)
        assertEquals("Беспроводная мышь", result.projection.title)
        assertEquals("TECH.PC_COMPONENTS", result.projection.categoryCode)
        assertEquals("yandex_ai_studio_agent", result.providerName)
    }

    @Test
    fun normalize_router_profile_uses_route_contract_and_maps_type_other_to_search_anchor() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "route_status": "TYPE_OTHER",
                  "identity_mode": "TYPE_ONLY",
                  "confidence": 0.91,
                  "primary_object": "wireless computer mouse",
                  "primary_object_confidence": 0.95,
                  "category_code": "TECH.PC_COMPONENTS",
                  "category_confidence": 0.9,
                  "browse_node_code": null,
                  "browse_node_confidence": null,
                  "item_type": "OTHER",
                  "item_type_label": null,
                  "item_type_confidence": 0.88,
                  "free_text_type": "computer mouse",
                  "visible_brand": null,
                  "visible_brand_confidence": null,
                  "visible_brand_source": null,
                  "reason_codes": ["TYPE_NOT_IN_ALLOWED_VALUES"],
                  "missing_evidence": []
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                yandexAllowModelFallback = false,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                aiPromptProfile = "router",
                aiBlockingIdentityEnrichmentEnabled = true,
                provider = VisualSearchAiProvider.OPENAI,
                openAiApiKey = "openai-token",
                openAiModel = "gpt-5-mini",
                openAiImageDetail = "low",
                openAiImageMaxSidePx = 512,
                openAiImageJpegQuality = 0.60f,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt-5-mini",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-router",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-router",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-router-mouse",
                        mimeType = "image/png",
                        widthPx = 616,
                        heightPx = 1125,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    contextAssets = listOf(
                        VisualSearchImageAsset(
                            sha256 = "sha256-router-mouse-back",
                            mimeType = "image/png",
                            widthPx = 616,
                            heightPx = 1125,
                            inlineBase64 = "ZmFrZTI=",
                        ),
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        val routeRequest = aiClient.lastRequest
        assertEquals("visual_search_route", routeRequest?.schemaName)
        assertEquals(2_800L, routeRequest?.timeoutMs)
        assertEquals(180, routeRequest?.maxCompletionTokens)
        assertEquals(1, routeRequest?.contentParts.orEmpty().filterIsInstance<YandexAiStudioContentPart.ImageBase64>().size)
        assertTrue(routeRequest?.systemInstruction.orEmpty().contains("visual search router"))
        assertTrue(routeRequest?.contentParts.orEmpty().filterIsInstance<YandexAiStudioContentPart.Text>().none { part ->
            part.text.contains("Multiple images are extra views")
        })
        assertEquals("TECH.PC_COMPONENTS", result.projection.categoryCode)
        assertEquals("computer mouse", result.projection.itemType?.text)
        assertNull(result.projection.brand)
        assertEquals("visual_search_router_openai", result.providerName)
        assertTrue(result.reasonCodes.contains("AI_TYPE_OTHER"))
    }

    @Test
    fun normalize_router_profile_ignores_other_label_and_falls_back_to_primary_object() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "route_status": "CATEGORY_ONLY",
                  "identity_mode": "TYPE_ONLY",
                  "confidence": 0.95,
                  "primary_object": "computer mouse",
                  "primary_object_confidence": 0.92,
                  "category_code": "TECH.PC_COMPONENTS",
                  "category_confidence": 0.58,
                  "browse_node_code": null,
                  "browse_node_confidence": null,
                  "item_type": "OTHER",
                  "item_type_label": "OTHER",
                  "item_type_confidence": 0.82,
                  "free_text_type": null,
                  "visible_brand": null,
                  "visible_brand_confidence": null,
                  "visible_brand_source": null,
                  "reason_codes": ["BROAD_CATEGORY_ONLY"],
                  "missing_evidence": []
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                yandexAllowModelFallback = false,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                aiPromptProfile = "router",
                aiBlockingIdentityEnrichmentEnabled = true,
                provider = VisualSearchAiProvider.OPENAI,
                openAiApiKey = "openai-token",
                openAiModel = "gpt-5-mini",
                openAiImageDetail = "low",
                openAiImageMaxSidePx = 512,
                openAiImageJpegQuality = 0.60f,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt-5-mini",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-router-category-only",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-router-category-only",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-router-category-only",
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals("TECH.PC_COMPONENTS", result.projection.categoryCode)
        assertEquals(0.95f, result.projection.categoryConfidence)
        assertEquals("computer mouse", result.projection.itemType?.text)
        assertTrue(result.reasonCodes.contains("AI_CATEGORY_ONLY"))
    }

    @Test
    fun normalize_router_profile_runs_identity_enrichment_for_rich_identity_categories() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "route_status": "ROUTED",
                  "identity_mode": "RICH_IDENTITY",
                  "confidence": 0.94,
                  "primary_object": "smartphone",
                  "primary_object_confidence": 0.96,
                  "category_code": "TECH.PHONES",
                  "category_confidence": 0.92,
                  "browse_node_code": null,
                  "browse_node_confidence": null,
                  "item_type": "smartphone",
                  "item_type_label": "Smartphone",
                  "item_type_confidence": 0.95,
                  "free_text_type": null,
                  "visible_brand": null,
                  "visible_brand_confidence": null,
                  "visible_brand_source": null,
                  "reason_codes": ["RICH_PRODUCT_CLASS"],
                  "missing_evidence": ["MODEL"]
                }
                """.trimIndent(),
                """
                {
                  "identity_status": "ENRICHED",
                  "confidence": 0.88,
                  "route_conflict": false,
                  "primary_object": "smartphone",
                  "primary_object_confidence": 0.96,
                  "category_code": "TECH.PHONES",
                  "category_confidence": 0.92,
                  "browse_node_code": null,
                  "item_type": "smartphone",
                  "item_type_label": "Smartphone",
                  "item_type_confidence": 0.95,
                  "visible_brand": null,
                  "visible_brand_confidence": null,
                  "visible_brand_source": null,
                  "brand": "Apple",
                  "brand_confidence": 0.72,
                  "brand_source": "VISUAL_PATTERN",
                  "family": "iPhone",
                  "family_confidence": 0.7,
                  "family_source": "VISUAL_PATTERN",
                  "model": null,
                  "model_confidence": null,
                  "model_source": null,
                  "model_candidates": [
                    {
                      "model": "iPhone 15 Pro",
                      "confidence": 0.55,
                      "source": "VISUAL_PATTERN"
                    }
                  ],
                  "color": null,
                  "color_confidence": null,
                  "search_title": "Apple iPhone",
                  "needs_more_photos": true,
                  "missing_evidence": ["BACK_LABEL"],
                  "reason_codes": ["VISUAL_PATTERN_MATCH"]
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                yandexAllowModelFallback = false,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                aiPromptProfile = "router",
                aiBlockingIdentityEnrichmentEnabled = true,
                provider = VisualSearchAiProvider.OPENAI,
                openAiApiKey = "openai-token",
                openAiModel = "gpt-5-mini",
                openAiImageDetail = "low",
                openAiImageMaxSidePx = 512,
                openAiImageJpegQuality = 0.60f,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt-5-mini",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-router-identity",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-router-identity",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-router-identity",
                        mimeType = "image/png",
                        widthPx = 616,
                        heightPx = 1125,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    contextAssets = listOf(
                        VisualSearchImageAsset(
                            sha256 = "sha256-router-identity-back",
                            mimeType = "image/png",
                            widthPx = 616,
                            heightPx = 1125,
                            inlineBase64 = "ZmFrZTI=",
                        ),
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals(listOf("visual_search_route", "visual_search_identity_enrich"), aiClient.requests.map { it.schemaName })
        val routeRequest = aiClient.requests[0]
        val identityRequest = aiClient.requests[1]
        assertEquals(2_800L, routeRequest.timeoutMs)
        assertEquals(180, routeRequest.maxCompletionTokens)
        assertEquals(1, routeRequest.contentParts.filterIsInstance<YandexAiStudioContentPart.ImageBase64>().size)
        assertEquals(2, identityRequest.contentParts.filterIsInstance<YandexAiStudioContentPart.ImageBase64>().size)
        assertTrue(routeRequest.contentParts.filterIsInstance<YandexAiStudioContentPart.ImageBase64>().all { part ->
            part.imageDetail == "low"
        })
        assertTrue(identityRequest.contentParts.filterIsInstance<YandexAiStudioContentPart.ImageBase64>().all { part ->
            part.imageDetail == "high" && part.preprocessMaxSidePx == 768
        })
        assertTrue(identityRequest.contentParts.filterIsInstance<YandexAiStudioContentPart.Text>().any { part ->
            part.text.contains("Multiple images are extra views")
        })
        assertEquals("TECH.PHONES", result.projection.categoryCode)
        assertEquals("Smartphone", result.projection.itemType?.text)
        assertEquals("Apple", result.projection.brand?.text)
        assertEquals("iPhone", result.projection.family?.text)
        assertEquals("iPhone 15 Pro", result.projection.modelCandidates.firstOrNull()?.text)
        assertTrue(result.reasonCodes.contains("AI_STAGED_IDENTITY"))
        assertEquals("visual_search_identity_openai", result.providerName)
        assertTrue(result.needsMorePhotos)
    }

    @Test
    fun normalize_falls_back_to_secondary_saved_agent_when_primary_agent_response_is_invalid() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """{ "status": "completed" }""",
                """
                {
                  "sale_object_candidates": [
                    {
                      "title_guess": "Беспроводная мышь",
                      "brand_guess": "Logitech",
                      "category_guess": "Компьютерная мышь",
                      "model_guess": "MX Master 3S",
                      "confidence": 0.91,
                      "attributes": []
                    }
                  ],
                  "ambiguities": []
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
                enabled = true,
                serverAiEnabled = true,
                contextReuseTtlSeconds = 900,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "folder-id",
                yandexModel = "gemma-3-27b-it",
                yandexAgentId = "prompt-primary",
                yandexFallbackAgentIds = listOf("prompt-fallback"),
                yandexAllowModelFallback = false,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                        promptId = "prompt-primary",
                    ),
                    YandexAiExecutionTarget(
                        key = "fallback_1",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                        promptId = "prompt-fallback",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-2",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-2",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-mouse-2",
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals(listOf<String?>("prompt-primary", "prompt-fallback"), aiClient.requestedPromptIds)
        assertEquals("TECH.PC_COMPONENTS", result.projection.categoryCode)
        assertEquals("visual-search/yandex-agent/fallback_1", result.providerSchemaVersion)
    }

    @Test
    fun normalize_handles_hintless_phone_images_with_catalog_broad_category_cards() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "title": "smartphone",
                  "category_code": "smartphone",
                  "reason_codes": ["BROAD_CATEGORY_ONLY"]
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-3",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-3",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-phone-1",
                        mimeType = "image/webp",
                        widthPx = 1600,
                        heightPx = 1600,
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals("TECH.PHONES", result.projection.categoryCode)
        val promptText = (aiClient.lastRequest?.contentParts?.firstOrNull() as? YandexAiStudioContentPart.Text)?.text
        assertNotNull(promptText)
        assertTrue(promptText.contains("Grounding packet:"))
        assertTrue(!promptText.contains("\"candidateCategories\":[]"))
        assertTrue(promptText.contains("infer a safe broad marketplace branch"))
    }

    @Test
    fun normalize_uses_preflight_category_candidates_as_llm_shortlist() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "title": "Xbox Series X",
                  "category_code": "TECH.GAMING",
                  "brand": "Microsoft",
                  "family": "Xbox Series X",
                  "reason_codes": ["PREFLIGHT_SHORTLIST_USED"]
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                aiShortlistCategoryLimit = 4,
                aiShortlistFamilyLimit = 4,
                aiShortlistModelLimit = 4,
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-shortlist",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-shortlist",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-console-1",
                        mimeType = "image/jpeg",
                        widthPx = 1200,
                        heightPx = 900,
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
                        categoryCandidates = listOf(
                            VisualSearchPreflightCategoryCandidate(
                                categoryCode = "TECH.GAMING",
                                confidence = 0.87f,
                                source = "embedding",
                                label = "game console",
                            ),
                            VisualSearchPreflightCategoryCandidate(
                                categoryCode = "TECH.PHONES",
                                confidence = 0.31f,
                                source = "embedding",
                                label = "black electronics",
                            ),
                        ),
                    ),
                    locale = "ru",
                ),
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals("TECH.GAMING", result.projection.categoryCode)
        val promptText = (aiClient.lastRequest?.contentParts?.firstOrNull() as? YandexAiStudioContentPart.Text)?.text
        assertNotNull(promptText)
        assertTrue(promptText.contains("\"code\":\"TECH.GAMING\""))
        assertTrue(promptText.contains("\"source\":\"embedding\""))
        assertTrue(promptText.contains("\"label\":\"game console\""))
    }

    @Test
    fun normalize_repairs_trailing_commas_from_gemini_style_json() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "needs_more_photos": true,
                  "category_code": "smartphone",
                  "brand": "Samsung",
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-trailing-commas",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-trailing-commas",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-gemini-trailing",
                        mimeType = "image/jpeg",
                        widthPx = 1200,
                        heightPx = 1600,
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertEquals("TECH.PHONES", result.projection.categoryCode)
        assertEquals("Samsung", result.projection.brand?.text)
        assertTrue(result.needsMorePhotos)
    }

    @Test
    fun normalize_maps_ranked_hypotheses_and_missing_evidence_from_default_contract() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "needs_more_photos": true,
                  "missing_evidence": ["rear_panel", "logo_closeup"],
                  "confidence": 0.78,
                  "title": "Apple smartphone",
                  "category_code": "TECH.PHONES",
                  "category_confidence": 0.86,
                  "item_type": "smartphone",
                  "item_type_label": "smartphone",
                  "item_type_confidence": 0.94,
                  "brand": "Apple",
                  "brand_confidence": 0.9,
                  "reason_codes": ["TOP_LEVEL_BRAND_ONLY"],
                  "hypotheses": [
                    {
                      "rank": 1,
                      "confidence": 0.78,
                      "title": "Apple iPhone 17 Pro Max",
                      "category_code": "TECH.PHONES",
                      "category_confidence": 0.86,
                      "item_type": "smartphone",
                      "item_type_label": "smartphone",
                      "item_type_confidence": 0.94,
                      "brand": "Apple",
                      "brand_confidence": 0.94,
                      "family": "iPhone Pro Max",
                      "family_confidence": 0.8,
                      "missing_evidence": ["rear_panel", "camera_block_closeup"]
                    },
                    {
                      "rank": 2,
                      "confidence": 0.63,
                      "title": "Apple iPhone 16 Pro Max",
                      "category_code": "TECH.PHONES",
                      "category_confidence": 0.84,
                      "item_type": "smartphone",
                      "item_type_label": "smartphone",
                      "item_type_confidence": 0.93,
                      "brand": "Apple",
                      "brand_confidence": 0.9,
                      "family": "iPhone Pro Max",
                      "family_confidence": 0.76
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-4",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-4",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-phone-2",
                        mimeType = "image/webp",
                        widthPx = 1600,
                        heightPx = 1600,
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
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertTrue(result.needsMorePhotos)
        assertEquals(listOf("rear_panel", "logo_closeup", "camera_block_closeup"), result.missingEvidence)
        assertEquals(2, result.hypotheses.size)
        assertEquals("Apple", result.projection.brand?.text)
        assertEquals("iPhone Pro Max", result.projection.family?.text)
        assertEquals("TECH.PHONES", result.projection.categoryCode)
    }

    @Test
    fun normalize_drops_scene_cutlery_when_primary_object_is_electronics() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "primary_object": "computer mouse",
                  "primary_object_confidence": 0.91,
                  "title": "Cutlery",
                  "category_code": "HOME.KITCHEN_DINING",
                  "category_confidence": 0.74,
                  "reason_codes": ["BACKGROUND_LABEL"]
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
                enabled = true,
                serverAiEnabled = true,
                contextReuseTtlSeconds = 900,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "folder-id",
                yandexModel = "qwen2.5-vl-32b-instruct",
                yandexAgentId = null,
                yandexFallbackAgentIds = emptyList(),
                yandexAllowModelFallback = true,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://folder-id/qwen2.5-vl-32b-instruct/latest",
                        promptId = null,
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-cutlery-scene",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-cutlery-scene",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-mouse-cutlery-scene",
                        mimeType = "image/jpeg",
                        widthPx = 591,
                        heightPx = 1280,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    source = VisualSearchSource.CAMERA,
                    entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
                    selectionMode = VisualSearchSelectionMode.AUTO_TARGET,
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                        imageLabelHints = listOf("cutlery", "tableware"),
                    ),
                    locale = "ru",
                ),
                requestedCategoryCode = null,
            )
        }

        assertNotNull(result)
        assertNull(result.projection.categoryCode)
        assertEquals("computer mouse", result.projection.title)
    }

    @Test
    fun normalize_passes_selected_region_into_image_preprocessor_focus_region() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "needs_more_photos": false,
                  "category_code": "pc component",
                  "brand": "Logitech"
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-focus-region",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-focus-region",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-mouse-focus",
                        mimeType = "image/png",
                        widthPx = 1000,
                        heightPx = 1000,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    source = VisualSearchSource.GALLERY,
                    entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
                    selectionMode = VisualSearchSelectionMode.MANUAL_CROP,
                    intent = VisualSearchIntent.IDENTIFY_FIRST,
                    selectedRegion = VisualSearchSelectedRegion(
                        left = 0.1f,
                        top = 0.2f,
                        width = 0.5f,
                        height = 0.4f,
                    ),
                    preflightSignals = VisualSearchPreflightSignals(
                        cheapProjectionReady = true,
                        admitServerAi = true,
                        captureMode = VisualSearchCaptureMode.IMAGE,
                    ),
                    locale = "ru",
                ),
                requestedCategoryCode = null,
            )
        }

        val imagePart = aiClient.lastRequest
            ?.contentParts
            ?.filterIsInstance<YandexAiStudioContentPart.ImageBase64>()
            ?.singleOrNull()
        assertNotNull(imagePart)
        assertEquals(0.1f, imagePart.focusRegion?.left)
        assertEquals(0.2f, imagePart.focusRegion?.top)
        assertEquals(0.5f, imagePart.focusRegion?.width)
        assertEquals(0.4f, imagePart.focusRegion?.height)
    }

    @Test
    fun normalize_passes_context_assets_as_additional_images_with_multiview_instruction() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "needs_more_photos": false,
                  "category_code": "phone",
                  "brand": "Samsung"
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = VisualSearchConfig(
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
                provider = VisualSearchAiProvider.YANDEX,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://folder-id/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-context-images",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-context-images",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-phone-front",
                        mimeType = "image/jpeg",
                        widthPx = 1000,
                        heightPx = 1400,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    contextAssets = listOf(
                        VisualSearchImageAsset(
                            sha256 = "sha256-phone-back",
                            mimeType = "image/jpeg",
                            widthPx = 1000,
                            heightPx = 1400,
                            inlineBase64 = "ZmFrZTI=",
                        ),
                        VisualSearchImageAsset(
                            sha256 = "sha256-phone-label",
                            mimeType = "image/jpeg",
                            widthPx = 1000,
                            heightPx = 1400,
                            inlineBase64 = "ZmFrZTM=",
                        ),
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
                requestedCategoryCode = null,
            )
        }

        val contentParts = aiClient.lastRequest?.contentParts.orEmpty()
        val imageParts = contentParts.filterIsInstance<YandexAiStudioContentPart.ImageBase64>()
        val textParts = contentParts.filterIsInstance<YandexAiStudioContentPart.Text>()
        assertEquals(3, imageParts.size)
        assertTrue(textParts.any { it.text.contains("Multiple images are extra views") })
    }

    @Test
    fun normalize_uses_context_image_quality_when_low_detail_openai_request_has_multiple_images() {
        val aiClient = FakeVisualAiClient(
            responses = listOf(
                """
                {
                  "needs_retake": false,
                  "needs_more_photos": false,
                  "category_code": "phone",
                  "brand": "Samsung"
                }
                """.trimIndent(),
            ),
        )
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
            provider = VisualSearchAiProvider.OPENAI,
            openAiApiKey = "openai-token",
            openAiModel = "gpt-5.4-mini",
            openAiImageDetail = "low",
            openAiImageMaxSidePx = 640,
            openAiImageJpegQuality = 0.68f,
        )
        val normalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = FakeVisualCatalogRepository(),
            catalogTaxonomyRepository = FakeVisualCatalogRepository(),
            config = config,
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt-5.4-mini",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        runBlocking {
            normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-openai-context-quality",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-openai-context-quality",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-phone-front",
                        mimeType = "image/jpeg",
                        widthPx = 1000,
                        heightPx = 1400,
                        inlineBase64 = "ZmFrZQ==",
                    ),
                    contextAssets = listOf(
                        VisualSearchImageAsset(
                            sha256 = "sha256-phone-back",
                            mimeType = "image/jpeg",
                            widthPx = 1000,
                            heightPx = 1400,
                            inlineBase64 = "ZmFrZTI=",
                        ),
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
                requestedCategoryCode = null,
            )
        }

        val imageParts = aiClient.lastRequest
            ?.contentParts
            .orEmpty()
            .filterIsInstance<YandexAiStudioContentPart.ImageBase64>()
        assertEquals(2, imageParts.size)
        assertTrue(imageParts.all { part -> part.imageDetail == null })
        assertTrue(imageParts.all { part -> part.preprocessMaxSidePx == 768 })
        assertTrue(imageParts.all { part -> part.preprocessJpegQuality == 0.74f })
    }

    @Test
    fun normalize_supports_gemini_via_openai_compatible_chat_completions() = runBlocking {
        var capturedAuthorization: String? = null
        var capturedProjectHeader: String? = null
        var capturedBody: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/chat/completions") { exchange ->
            capturedAuthorization = exchange.requestHeaders.getFirst("Authorization")
            capturedProjectHeader = exchange.requestHeaders.getFirst("OpenAI-Project")
            capturedBody = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val response = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"needs_retake\":false,\"needs_more_photos\":false,\"category_code\":\"TECH.PHONES\",\"brand\":\"Google\",\"model\":\"Pixel 9 Pro\"}"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 1200,
                    "completion_tokens": 90,
                    "total_tokens": 1290
                  }
                }
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
            exchange.responseBody.use { body ->
                body.write(response.toByteArray(Charsets.UTF_8))
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
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
                yandexAllowModelFallback = false,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 10_000,
                aiShortlistCategoryLimit = 12,
                aiShortlistFamilyLimit = 6,
                aiShortlistModelLimit = 6,
                provider = VisualSearchAiProvider.GEMINI,
                geminiBaseUrl = baseUrl,
                geminiApiKey = "gemini-token",
                geminiModel = "gemini-2.5-flash",
                geminiTimeoutMs = 10_000,
            )
            val normalizer = YandexVisualSearchAiDraftNormalizer(
                catalogRepository = FakeVisualCatalogRepository(),
                catalogTaxonomyRepository = FakeVisualCatalogRepository(),
                config = config,
                orchestrator = AiNormalizationOrchestrator(
                    agentRegistry = EnvAiAgentRegistry(
                        visualSearchConfig = config,
                        listingVisionAiConfig = ListingVisionAiConfig(
                            serverAiEnabled = false,
                            yandexBaseUrl = "https://example.test/v1",
                            yandexResponsesBaseUrl = "https://example.test/v1",
                            yandexApiKey = null,
                            yandexProjectId = null,
                            yandexModel = "unused",
                            yandexAgentId = null,
                            yandexFallbackAgentIds = emptyList(),
                            yandexAllowModelFallback = false,
                            yandexSystemPrompt = null,
                            yandexTimeoutMs = 10_000,
                            shortlistCategoryLimit = 8,
                            categoryAttributeLimit = 8,
                            maxPhotosPerRequest = 4,
                        ),
                    ),
                    aiClient = DefaultYandexAiStudioClient(),
                    telemetry = NoopAiNormalizationTelemetry,
                ),
            )

            val result = normalizer.normalize(
                metadata = VisualSearchTransportMetadata(
                    visualSessionId = "vs-test-gemini",
                    clientSchemaVersion = "visual-search/1.0",
                    catalogDataVersion = CatalogDataVersion.current,
                    idempotencyKey = "idem-gemini",
                ),
                request = VisualSearchNormalizeDraftRequest(
                    asset = VisualSearchImageAsset(
                        sha256 = "sha256-gemini-phone",
                        mimeType = "image/jpeg",
                        widthPx = 1200,
                        heightPx = 1600,
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
                requestedCategoryCode = null,
            )

            assertNotNull(result)
            assertEquals("gemini_openai_compat", result.providerName)
            assertEquals("TECH.PHONES", result.projection.categoryCode)
            assertEquals("Google", result.projection.brand?.text)
            assertEquals("Pixel 9 Pro", result.projection.model?.text)
            assertEquals("Bearer gemini-token", capturedAuthorization)
            assertNull(capturedProjectHeader)
            assertTrue(capturedBody.orEmpty().contains("\"model\":\"gemini-2.5-flash\""))
        } finally {
            server.stop(0)
        }
    }
}

private class FakeVisualAiClient(
    private val responses: List<String>,
) : YandexAiStudioClient {
    var lastRequest: YandexAiStudioStructuredRequest? = null
    val requests = mutableListOf<YandexAiStudioStructuredRequest>()
    val requestedPromptIds = mutableListOf<String?>()
    private var index: Int = 0

    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        lastRequest = request
        requests += request
        requestedPromptIds += request.promptId
        val responseIndex = index.coerceAtMost(responses.lastIndex)
        index += 1
        return YandexAiStudioStructuredResponse.Success(
            responseBody = responses[responseIndex],
            httpStatus = 200,
            invocationMode = if (request.promptId.isNullOrBlank()) {
                com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode.CHAT_COMPLETIONS
            } else {
                com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode.RESPONSES
            },
        )
    }
}

private fun testOrchestrator(
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

        override fun describe(flow: AiNormalizationFlow): com.example.shoppingassistant.server.ai.AiAgentFlowDescriptor =
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

private class FakeVisualCatalogRepository : CatalogReadRepository, CatalogTaxonomyRepository {
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
            code = "TECH.LAPTOPS",
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
        Category(
            code = "HOME.KITCHEN_DINING",
            segment = CategorySegment.HOME,
            title = localizedTextOf("ru" to "Посуда и кухня", "en" to "Tableware and Kitchen"),
            parentCode = "HOME",
            status = CategoryStatus.ACTIVE,
        ),
    )

    private val specs = mapOf(
        "TECH.PC_COMPONENTS" to CatalogCategoryWriteSpec(
            category = categories.first { it.code == "TECH.PC_COMPONENTS" },
            attributes = listOf(
                AttributeDef(code = "brand", title = "Brand", dataType = AttributeDataType.STRING, requiredForSearch = true),
                AttributeDef(code = "model", title = "Model", dataType = AttributeDataType.STRING, requiredForSearch = true),
            ),
            categoryAttributes = listOf(
                CategoryAttribute(categoryCode = "TECH.PC_COMPONENTS", attributeCode = "brand", uiOrder = 10, isRequiredForCategory = true),
                CategoryAttribute(categoryCode = "TECH.PC_COMPONENTS", attributeCode = "model", uiOrder = 20, isRequiredForCategory = true),
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
        "HOME.KITCHEN_DINING" to CatalogCategoryWriteSpec(
            category = categories.first { it.code == "HOME.KITCHEN_DINING" },
            attributes = emptyList(),
            categoryAttributes = emptyList(),
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
