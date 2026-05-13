package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryReadiness
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryRedirectResolution
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.Stage22ValueSetType
import com.example.shoppingassistant.domain.catalog.Stage22ValueType
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.vision.VisionNextAction
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionPhotoInput
import com.example.shoppingassistant.domain.vision.VisionPhotoRole
import com.example.shoppingassistant.server.ai.AiAgentRegistry
import com.example.shoppingassistant.server.ai.AiAgentRoute
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.NoopAiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredRequest
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioTransportConfig
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class YandexListingVisionAiNormalizerTest {

    @Test
    fun normalize_maps_open_category_hint_to_catalog_and_builds_listing_query() {
        val normalizer = YandexListingVisionAiNormalizer(
            catalogRepository = FakeCatalogReadRepository(),
            catalogTaxonomyRepository = FakeCatalogTaxonomyRepository(),
            config = ListingVisionAiConfig(
                serverAiEnabled = true,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "project",
                yandexModel = "gemma-3-27b-it",
                yandexAgentId = null,
                yandexFallbackAgentIds = emptyList(),
                yandexAllowModelFallback = true,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 5_000L,
                shortlistCategoryLimit = 8,
                categoryAttributeLimit = 8,
                maxPhotosPerRequest = 4,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://project/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = FakeAiClient(
                listOf(
                    """
                    {
                      "needs_retake": false,
                      "confidence": 0.91,
                      "title": "Apple iPhone 13",
                      "category_hint": "смартфоны",
                      "brand": "Apple",
                      "model": "iPhone 13",
                      "reason_codes": ["VISIBLE_BRAND", "VISIBLE_MODEL"],
                      "attributes": [
                        { "code": "condition", "text": "used" }
                      ]
                    }
                    """.trimIndent(),
                ),
                ),
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = jpegBase64()),
                        VisionPhotoInput(role = VisionPhotoRole.BACK, base64 = jpegBase64()),
                    ),
                    locale = "ru",
                ),
            )
        }

        assertNotNull(result)
        assertEquals("TECH.PHONES", result.categoryCode)
        assertEquals("Apple", result.normalizedQuery?.brand)
        assertEquals("iPhone 13", result.normalizedQuery?.model)
        assertEquals("used", result.normalizedQuery?.attributes?.get("condition")?.asRawString())
        assertEquals(emptyList(), result.missingRequiredKeys)
        assertEquals(listOf("condition"), result.bindOutcome?.acceptedAttributeCodes)
        assertEquals(emptyList(), result.bindOutcome?.unresolvedAttributeCodes)
        assertEquals("смартфоны", result.rawExtraction?.categoryHint)
        assertNull(result.nextAction)
    }

    @Test
    fun normalize_requests_tech_photo_when_required_fields_are_missing() {
        val normalizer = YandexListingVisionAiNormalizer(
            catalogRepository = FakeCatalogReadRepository(),
            catalogTaxonomyRepository = FakeCatalogTaxonomyRepository(),
            config = ListingVisionAiConfig(
                serverAiEnabled = true,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "project",
                yandexModel = "gemma-3-27b-it",
                yandexAgentId = null,
                yandexFallbackAgentIds = emptyList(),
                yandexAllowModelFallback = true,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 5_000L,
                shortlistCategoryLimit = 8,
                categoryAttributeLimit = 8,
                maxPhotosPerRequest = 4,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://project/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = FakeAiClient(
                listOf(
                    """
                    {
                      "needs_retake": false,
                      "confidence": 0.76,
                      "category_code": "TECH.PHONES",
                      "brand": "Samsung",
                      "model": "Galaxy S23",
                      "attributes": []
                    }
                    """.trimIndent(),
                ),
                ),
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = jpegBase64()),
                        VisionPhotoInput(role = VisionPhotoRole.BACK, base64 = jpegBase64()),
                    ),
                    locale = "ru",
                    categoryHint = "TECH.PHONES",
                ),
            )
        }

        assertNotNull(result)
        assertEquals("TECH.PHONES", result.categoryCode)
        assertEquals(listOf("condition"), result.missingRequiredKeys)
        assertEquals(VisionNextAction.ADD_TECH_PHOTO, result.nextAction)
    }

    @Test
    fun normalize_falls_back_to_secondary_agent_when_primary_agent_payload_is_invalid() {
        val aiClient = FakeAiClient(
            listOf(
                """{ "status": "completed" }""",
                """
                {
                  "needs_retake": false,
                  "confidence": 0.82,
                  "category_hint": "компьютерная мышь",
                  "brand": "Logitech",
                  "model": "MX Master 3S",
                  "attributes": [
                    { "code": "condition", "text": "used" }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val normalizer = YandexListingVisionAiNormalizer(
            catalogRepository = FakeCatalogReadRepository(),
            catalogTaxonomyRepository = FakeCatalogTaxonomyRepository(),
            config = ListingVisionAiConfig(
                serverAiEnabled = true,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "project",
                yandexModel = "gemma-3-27b-it",
                yandexAgentId = "agent-primary",
                yandexFallbackAgentIds = listOf("agent-fallback"),
                yandexAllowModelFallback = false,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 5_000L,
                shortlistCategoryLimit = 8,
                categoryAttributeLimit = 8,
                maxPhotosPerRequest = 4,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://project/gemma-3-27b-it/latest",
                        promptId = "agent-primary",
                    ),
                    YandexAiExecutionTarget(
                        key = "fallback_1",
                        modelUri = "gpt://project/gemma-3-27b-it/latest",
                        promptId = "agent-fallback",
                    ),
                ),
                aiClient = aiClient,
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = jpegBase64()),
                    ),
                    locale = "ru-RU",
                ),
            )
        }

        assertNotNull(result)
        assertEquals(listOf<String?>("agent-primary", "agent-fallback"), aiClient.requestedPromptIds)
        assertEquals("TECH.PC_COMPONENTS", result.categoryCode)
        assertEquals("Logitech", result.normalizedQuery?.brand)
        assertEquals(true, result.warnings.contains("AI_AGENT_FALLBACK_1"))
    }

    @Test
    fun normalize_preserves_unresolved_attributes_without_projecting_them_into_validated_profile() {
        val normalizer = YandexListingVisionAiNormalizer(
            catalogRepository = FakeCatalogReadRepository(),
            catalogTaxonomyRepository = FakeCatalogTaxonomyRepository(),
            config = ListingVisionAiConfig(
                serverAiEnabled = true,
                yandexBaseUrl = "https://example.test/v1",
                yandexResponsesBaseUrl = "https://example.test/v1",
                yandexApiKey = "token",
                yandexProjectId = "project",
                yandexModel = "gemma-3-27b-it",
                yandexAgentId = null,
                yandexFallbackAgentIds = emptyList(),
                yandexAllowModelFallback = true,
                yandexSystemPrompt = null,
                yandexTimeoutMs = 5_000L,
                shortlistCategoryLimit = 8,
                categoryAttributeLimit = 8,
                maxPhotosPerRequest = 4,
            ),
            orchestrator = testOrchestrator(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "model_primary",
                        modelUri = "gpt://project/gemma-3-27b-it/latest",
                    ),
                ),
                aiClient = FakeAiClient(
                    listOf(
                        """
                        {
                          "needs_retake": false,
                          "confidence": 0.74,
                          "category_code": "TECH.PC_COMPONENTS",
                          "brand": "Logitech",
                          "model": "MX Master 3S",
                          "attributes": [
                            { "code": "condition", "text": "used" },
                            { "code": "dpi", "number": 8000 }
                          ]
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val result = runBlocking {
            normalizer.normalize(
                VisionNormalizeRequest(
                    photos = listOf(
                        VisionPhotoInput(role = VisionPhotoRole.FRONT, base64 = jpegBase64()),
                        VisionPhotoInput(role = VisionPhotoRole.BACK, base64 = jpegBase64()),
                    ),
                    locale = "ru",
                    categoryHint = "TECH.PC_COMPONENTS",
                ),
            )
        }

        assertNotNull(result)
        assertEquals("used", result.normalizedQuery?.attributes?.get("condition")?.asRawString())
        assertEquals(null, result.normalizedQuery?.attributes?.get("dpi"))
        assertEquals(listOf("condition"), result.bindOutcome?.acceptedAttributeCodes)
        assertEquals(listOf("dpi"), result.bindOutcome?.unresolvedAttributeCodes)
        assertEquals(true, result.warnings.contains("PROFILE_ATTR_UNRESOLVED"))
    }
}

private class FakeAiClient(
    private val responses: List<String>,
) : YandexAiStudioClient {
    val requestedPromptIds = mutableListOf<String?>()
    private var index: Int = 0

    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        requestedPromptIds += request.promptId
        val responseIndex = index.coerceAtMost(responses.lastIndex)
        index += 1
        return YandexAiStudioStructuredResponse.Success(
            responseBody = responses[responseIndex],
            httpStatus = 200,
            invocationMode = if (request.promptId.isNullOrBlank()) {
                YandexAiStudioInvocationMode.CHAT_COMPLETIONS
            } else {
                YandexAiStudioInvocationMode.RESPONSES
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
                    projectId = "project",
                ),
                timeoutMs = 5_000L,
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

private class FakeCatalogReadRepository : CatalogReadRepository {
    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? =
        when {
            categoryCode.equals("TECH.PHONES", ignoreCase = true) -> CatalogCategoryEffectiveSpec(
                category = Category(
                    code = "TECH.PHONES",
                    segment = CategorySegment.TECH,
                    title = localizedTextOf("ru" to "Смартфоны", "en" to "Smartphones"),
                    status = CategoryStatus.ACTIVE,
                ),
                readiness = CatalogCategoryReadiness.READY,
                attributes = commonAttributes(),
            )

            categoryCode.equals("TECH.PC_COMPONENTS", ignoreCase = true) -> CatalogCategoryEffectiveSpec(
                category = Category(
                    code = "TECH.PC_COMPONENTS",
                    segment = CategorySegment.TECH,
                    title = localizedTextOf("ru" to "ПК и комплектующие", "en" to "PC & components"),
                    status = CategoryStatus.ACTIVE,
                ),
                readiness = CatalogCategoryReadiness.READY,
                attributes = commonAttributes(),
            )

            else -> null
        }

    private fun commonAttributes(): List<CatalogAttributeSpec> = listOf(
        CatalogAttributeSpec(
            code = "brand",
            title = "Brand",
            dataType = AttributeDataType.STRING,
            valueType = Stage22ValueType.STRING,
            valueSetType = Stage22ValueSetType.OPEN,
            requiredForOffer = true,
            requiredForCategory = true,
        ),
        CatalogAttributeSpec(
            code = "model",
            title = "Model",
            dataType = AttributeDataType.STRING,
            valueType = Stage22ValueType.STRING,
            valueSetType = Stage22ValueSetType.OPEN,
            requiredForOffer = true,
            requiredForCategory = true,
        ),
        CatalogAttributeSpec(
            code = "condition",
            title = "Condition",
            dataType = AttributeDataType.ENUM,
            valueType = Stage22ValueType.ENUM,
            valueSetType = Stage22ValueSetType.OPEN,
            requiredForOffer = true,
            requiredForCategory = true,
        ),
    )
}

private class FakeCatalogTaxonomyRepository : CatalogTaxonomyRepository {
    private val categories = listOf(
        Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "Смартфоны", "en" to "Smartphones"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
        Category(
            code = "TECH.PC_COMPONENTS",
            segment = CategorySegment.TECH,
            title = localizedTextOf("ru" to "ПК и комплектующие", "en" to "PC & components"),
            parentCode = "TECH",
            status = CategoryStatus.ACTIVE,
        ),
        Category(
            code = "APPL.MAJOR",
            segment = CategorySegment.APPL,
            title = localizedTextOf("ru" to "Крупная техника", "en" to "Major appliances"),
            parentCode = "APPL",
            status = CategoryStatus.ACTIVE,
        ),
    )

    override suspend fun listCategories(): List<Category> = categories

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ): CategoryRedirectResolution? {
        val normalized = categoryCode.trim().uppercase()
        return categories.firstOrNull { category -> category.code == normalized }?.let { category ->
            CategoryRedirectResolution(
                requestedCode = normalized,
                resolvedCode = category.code,
                redirectChain = listOf(category.code),
                wasRedirected = false,
                cycleDetected = false,
            )
        }
    }
}

private fun jpegBase64(): String =
    Base64.getEncoder().encodeToString(
        byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0x00,
        ),
    )
