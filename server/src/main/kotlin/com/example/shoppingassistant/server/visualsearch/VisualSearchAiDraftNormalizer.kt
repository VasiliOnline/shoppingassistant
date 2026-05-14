package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.CatalogCanonicalModelRegistry
import com.example.shoppingassistant.domain.catalog.CatalogCanonicalProductFamilyRegistry
import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.i18n.displayTitle
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateAttribute
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateProjection
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCandidateValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchHypothesis
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizationDraft
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectedRegion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.server.ai.AiNormalizationFlow
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.AiStructuredContractLoader
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioContentPart
import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioFocusRegion
import com.example.shoppingassistant.server.catalog.CatalogAiCategoryHintResolver
import com.example.shoppingassistant.server.config.VisualSearchAiProvider
import com.example.shoppingassistant.server.config.VisualSearchConfig
import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

interface VisualSearchAiDraftNormalizer {
    suspend fun normalize(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
    ): VisualSearchNormalizationDraft?
}

private const val MAX_REQUEST_IMAGES: Int = 3
private const val ROUTER_MAX_COMPLETION_TOKENS: Int = 180
private val BROAD_FALLBACK_CATEGORY_CODES: List<String> = listOf(
    "TECH.PHONES",
    "TECH.COMPUTERS",
    "TECH.PC_COMPONENTS",
    "TECH.PHONE_ACCESSORIES",
    "TECH.TV_HOME_THEATER",
    "TECH.AUDIO",
    "TECH.GAMING",
    "TECH.CAMERAS_DRONES",
    "TECH.SMART_HOME_SECURITY",
    "APPL.MAJOR",
    "APPL.SMALL",
    "APPL.CLIMATE",
    "HOME.GARDEN",
    "HOME.KITCHEN_DINING",
    "HOME.REPAIR_TOOLS",
    "KIDS.TOYS_GAMES",
    "FASH.WOMEN",
    "FASH.MEN",
    "FASH.KIDS",
    "FASH.SHOES",
    "FASH.ACCESSORIES",
    "FASH.BAGS",
    "FOOD.GROCERIES",
    "FOOD.READY_MEALS",
    "FOOD.DRINKS",
    "FOOD.SNACKS",
    "BEAUTY.HEALTH",
    "BEAUTY.DEVICES",
    "AUTO.PARTS",
    "AUTO.ACCESSORIES",
)
private val BROAD_FALLBACK_CATEGORY_LABELS: Map<String, String> = mapOf(
    "AUTO.PARTS" to "vehicle batteries, bearings, hubs, replacement parts",
    "AUTO.ACCESSORIES" to "vehicle add-ons, handlebar gauges, thermometers, clocks",
    "BEAUTY.HEALTH" to "supplements, herbs, roots, cosmetics, personal care",
    "FOOD.GROCERIES" to "raw food, tea, seafood, pantry grocery",
    "FOOD.SNACKS" to "candy, sweets, chocolate, snacks, food gift sets",
    "FASH.WOMEN" to "women or unisex clothing, sweaters, dresses, tops",
    "FASH.MEN" to "men clothing, shirts, sweaters, hoodies",
    "FASH.KIDS" to "children clothing, baby apparel, school uniform, kids sizes",
    "FASH.SHOES" to "shoes, sneakers, boots, footwear",
    "TECH.PHONE_ACCESSORIES" to "cases, chargers, cables, phone screens/modules",
    "TECH.GAMING" to "game consoles, PlayStation, Xbox, Nintendo, controllers",
    "HOME.GARDEN" to "plants, flowers, bouquets, garden items",
)
private const val MULTI_IMAGE_CONTEXT_INSTRUCTION: String =
    "Multiple images are extra views of the same product. Use the first image as primary; use later images only to confirm or reject category, brand, family, and exact model. If views conflict, keep the safest category or family and leave weak identity fields empty."

class YandexVisualSearchAiDraftNormalizer(
    private val catalogRepository: CatalogReadRepository,
    private val catalogTaxonomyRepository: CatalogTaxonomyRepository,
    private val config: VisualSearchConfig,
    private val orchestrator: AiNormalizationOrchestrator,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    },
) : VisualSearchAiDraftNormalizer {

    private val logger = LoggerFactory.getLogger(YandexVisualSearchAiDraftNormalizer::class.java)
    private val aiContract = AiStructuredContractLoader.load("visual_search")
    private val routerContract = AiStructuredContractLoader.load("visual_search_router")
    private val identityContract = AiStructuredContractLoader.load("visual_search_identity")
    private val categoryHintResolver = CatalogAiCategoryHintResolver(catalogTaxonomyRepository)

    override suspend fun normalize(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
    ): VisualSearchNormalizationDraft? {
        if (!config.providerReady || !request.preflightSignals.admitServerAi) return null
        val requestAssets = buildRequestAssets(request)
        val primaryAsset = requestAssets.firstOrNull() ?: return null
        val inlineBase64 = primaryAsset.inlineBase64?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val fullGroundingPacket = buildGroundingPacket(
            request = request,
            requestedCategoryCode = requestedCategoryCode,
        )

        val allContextAssets = requestAssets.drop(1)
        val routerMode = isRouterMode()
        val firstPassGroundingPacket = if (routerMode) {
            fullGroundingPacket.toRouterFirstPassPacket()
        } else {
            fullGroundingPacket
        }
        val firstPassContextAssets = if (routerMode) {
            emptyList()
        } else {
            allContextAssets
        }
        val hasFirstPassContextImages = firstPassContextAssets.any { asset ->
            asset.inlineBase64?.trim()?.isNotEmpty() == true
        }
        val firstPassModelOverride = config.activeModelOverrideForContextImages(hasFirstPassContextImages)
        val contentParts = buildContentParts(
            request = request,
            primaryAsset = primaryAsset,
            primaryInlineBase64 = inlineBase64,
            contextAssets = firstPassContextAssets,
            groundingPacket = firstPassGroundingPacket,
            routerMode = routerMode,
            imageDetailOverride = if (routerMode) "low" else null,
        )
        val firstExecution = orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            systemInstruction = buildSystemPrompt(routerMode),
            contentParts = contentParts,
            schemaName = if (routerMode) "visual_search_route" else "visual_search_normalize_draft",
            schema = if (routerMode) routerContract.schema else aiContract.schema,
            temperature = config.activeTemperature,
            maxCompletionTokens = maxCompletionTokensForFirstPass(routerMode),
            reasoningEffort = config.activeReasoningEffortForModel(firstPassModelOverride),
            strictJsonSchema = true,
            modelOverride = firstPassModelOverride,
            modelOverrideKeySuffix = firstPassModelOverride?.let { "_multi_image" },
            timeoutMsOverride = if (routerMode) config.aiRouterTimeoutMs else null,
        ) { responseBody, target ->
            if (routerMode) {
                parseRouterPayload(
                    metadata = metadata,
                    responseBody = responseBody,
                    request = request,
                    requestedCategoryCode = requestedCategoryCode,
                    groundingPacket = firstPassGroundingPacket,
                )?.withProvider(target)
            } else {
                parseDefaultPayload(
                    metadata = metadata,
                    responseBody = responseBody,
                    request = request,
                    requestedCategoryCode = requestedCategoryCode,
                    groundingPacket = fullGroundingPacket,
                )?.withProvider(target)
                    ?: if (target.usesSavedAgent) {
                        parseAgentPayload(
                            metadata = metadata,
                            responseBody = responseBody,
                            request = request,
                            requestedCategoryCode = requestedCategoryCode,
                            groundingPacket = fullGroundingPacket,
                        )?.withProvider(target)
                    } else {
                        null
                    }
            }
        } ?: return null
        val firstDraft = firstExecution.value
        if (!routerMode) {
            return firstDraft
        }
        if (!shouldRunIdentityEnrichment(firstDraft)) {
            return firstDraft
        }
        if (!config.aiBlockingIdentityEnrichmentEnabled) {
            return firstDraft.withAddedReasonCode("AI_IDENTITY_DEFERRED")
        }
        val hasIdentityContextImages = allContextAssets.any { asset ->
            asset.inlineBase64?.trim()?.isNotEmpty() == true
        }
        val identityModelOverride = config.activeModelOverrideForContextImages(hasIdentityContextImages)
        return runIdentityEnrichment(
            metadata = metadata,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            primaryAsset = primaryAsset,
            primaryInlineBase64 = inlineBase64,
            contextAssets = allContextAssets,
            groundingPacket = fullGroundingPacket,
            routerDraft = firstDraft,
            modelOverride = identityModelOverride,
        ) ?: firstDraft
    }

    private fun VisualSearchNormalizationDraft.withAddedReasonCode(
        reasonCode: String,
    ): VisualSearchNormalizationDraft {
        val normalizedReasonCode = reasonCode.trim().takeIf { it.isNotEmpty() } ?: return this
        val nextReasonCodes = (reasonCodes + normalizedReasonCode).distinct()
        return copy(
            projection = projection.copy(reasonCodes = (projection.reasonCodes + normalizedReasonCode).distinct()),
            reasonCodes = nextReasonCodes,
        )
    }

    private suspend fun runIdentityEnrichment(
        metadata: VisualSearchTransportMetadata,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        primaryAsset: VisualSearchImageAsset,
        primaryInlineBase64: String,
        contextAssets: List<VisualSearchImageAsset>,
        groundingPacket: GroundingPacket,
        routerDraft: VisualSearchNormalizationDraft,
        modelOverride: String?,
    ): VisualSearchNormalizationDraft? {
        val contentParts = buildContentParts(
            request = request,
            primaryAsset = primaryAsset,
            primaryInlineBase64 = primaryInlineBase64,
            contextAssets = contextAssets,
            groundingPacket = groundingPacket,
            routerMode = false,
            userPromptOverride = buildIdentityUserPrompt(routerDraft, groundingPacket),
            imageDetailOverride = identityImageDetailOverride(),
            preprocessMaxSidePxOverride = identityPreprocessMaxSidePxOverride(),
            preprocessJpegQualityOverride = identityPreprocessJpegQualityOverride(),
        )
        val identityExecution = orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            systemInstruction = buildIdentitySystemPrompt(),
            contentParts = contentParts,
            schemaName = "visual_search_identity_enrich",
            schema = identityContract.schema,
            temperature = config.activeTemperature,
            maxCompletionTokens = maxCompletionTokensForIdentity(),
            reasoningEffort = config.activeReasoningEffortForModel(modelOverride),
            strictJsonSchema = true,
            modelOverride = modelOverride,
            modelOverrideKeySuffix = modelOverride?.let { "_identity_multi_image" },
        ) { responseBody, target ->
            parseIdentityPayload(
                metadata = metadata,
                responseBody = responseBody,
                request = request,
                requestedCategoryCode = requestedCategoryCode,
                groundingPacket = groundingPacket,
                target = target,
            )
        } ?: return null
        return mergeRouterAndIdentityDrafts(
            routerDraft = routerDraft,
            identityDraft = identityExecution.value,
        )
    }

    private fun maxCompletionTokensForFirstPass(routerMode: Boolean): Int =
        if (routerMode) {
            config.aiRouterMaxCompletionTokens ?: minOf(config.activeMaxCompletionTokens, ROUTER_MAX_COMPLETION_TOKENS)
        } else {
            config.activeMaxCompletionTokens
        }

    private fun maxCompletionTokensForIdentity(): Int =
        config.aiIdentityMaxCompletionTokens ?: config.activeMaxCompletionTokens

    private fun identityImageDetailOverride(): String? =
        if (config.provider == VisualSearchAiProvider.OPENAI && config.openAiImageDetail == "low") {
            "high"
        } else {
            null
        }

    private fun identityPreprocessMaxSidePxOverride(): Int? =
        if (config.provider == VisualSearchAiProvider.OPENAI && config.openAiImageDetail == "low") {
            maxOf(config.openAiImageMaxSidePx, 768).coerceAtMost(1280)
        } else {
            null
        }

    private fun identityPreprocessJpegQualityOverride(): Float? =
        if (config.provider == VisualSearchAiProvider.OPENAI && config.openAiImageDetail == "low") {
            maxOf(config.openAiImageJpegQuality, 0.74f).coerceAtMost(0.92f)
        } else {
            null
        }

    private suspend fun parseDefaultPayload(
        metadata: VisualSearchTransportMetadata,
        responseBody: String,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): VisualSearchNormalizationDraft? {
        val payload = decodeWithRepair(
            serializer = YandexDraftPayload.serializer(),
            responseBody = responseBody,
            sessionId = metadata.visualSessionId,
            logCode = "default_schema_miss",
        ) ?: return null
        if (!payload.needsRetake &&
            !payload.needsMorePhotos &&
            payload.missingEvidence.isEmpty() &&
            !payload.hasDirectSignal() &&
            payload.hypotheses.isEmpty()
        ) {
            return null
        }

        val directProjection = buildDraftProjection(
            rawCategoryHint = payload.categoryCode,
            categoryConfidence = payload.categoryConfidence,
            primaryObject = payload.primaryObject,
            primaryObjectConfidence = payload.primaryObjectConfidence ?: payload.confidence,
            itemType = payload.itemType,
            itemTypeLabel = payload.itemTypeLabel,
            itemTypeConfidence = payload.itemTypeConfidence,
            brand = payload.brand,
            brandConfidence = payload.brandConfidence,
            brandSource = payload.brandSource,
            rawBrandCandidates = emptyList(),
            family = payload.family,
            familyConfidence = payload.familyConfidence,
            familySource = payload.familySource,
            rawFamilyCandidates = emptyList(),
            model = payload.model,
            modelConfidence = payload.modelConfidence,
            modelSource = payload.modelSource,
            rawModelCandidates = payload.modelCandidates,
            color = payload.color,
            colorConfidence = payload.colorConfidence,
            condition = payload.condition,
            conditionConfidence = payload.conditionConfidence,
            title = payload.title,
            rawAttributes = payload.attributes,
            rawReasonCodes = payload.reasonCodes,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            groundingPacket = groundingPacket,
        )
        val hypothesisEntries = payload.hypotheses.mapIndexedNotNull { index, hypothesis ->
            buildDraftHypothesis(
                hypothesis = hypothesis,
                fallbackRank = index + 1,
                request = request,
                requestedCategoryCode = requestedCategoryCode,
                groundingPacket = groundingPacket,
            )
        }
        val rankedHypotheses = buildRankedHypotheses(
            directProjection = directProjection,
            directConfidence = payload.confidence,
            directReasonCodes = payload.reasonCodes,
            directMissingEvidence = payload.missingEvidence,
            hypotheses = hypothesisEntries,
        )
        val primaryProjection = rankedHypotheses.firstOrNull()?.projection ?: directProjection
        if (primaryProjection == null && !payload.needsRetake && !payload.needsMorePhotos) {
            return null
        }

        val missingEvidence = sanitizeEvidenceCodes(
            payload.missingEvidence +
                rankedHypotheses.firstOrNull().orEmptyList { it.missingEvidence },
        )
        val reasonCodes = buildList {
            addAll(payload.reasonCodes.map { it.trim() }.filter { it.isNotEmpty() })
            addAll(primaryProjection?.reasonCodes.orEmpty())
            add(aiDraftReasonCode())
            if (payload.needsRetake) add("AI_RETAKE_RECOMMENDED")
            if (payload.needsMorePhotos || missingEvidence.isNotEmpty()) add("AI_MORE_PHOTOS_NEEDED")
        }.distinct()

        return VisualSearchNormalizationDraft(
            projection = (primaryProjection ?: VisualSearchCandidateProjection()).copy(reasonCodes = reasonCodes),
            confidence = payload.confidence?.coerceIn(0f, 1f) ?: rankedHypotheses.firstOrNull()?.confidence,
            hypotheses = rankedHypotheses,
            needsMorePhotos = payload.needsMorePhotos || missingEvidence.isNotEmpty(),
            missingEvidence = missingEvidence,
            reasonCodes = reasonCodes,
            providerName = "yandex_ai_studio",
            providerSchemaVersion = "visual-search/yandex-draft/2",
        )
    }

    private suspend fun parseRouterPayload(
        metadata: VisualSearchTransportMetadata,
        responseBody: String,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): VisualSearchNormalizationDraft? {
        val payload = decodeWithRepair(
            serializer = VisualSearchRouterPayload.serializer(),
            responseBody = responseBody,
            sessionId = metadata.visualSessionId,
            logCode = "router_schema_miss",
        ) ?: return null

        val routeStatus = payload.routeStatus.trim().uppercase(Locale.ROOT)
        val sanitizedMissingEvidence = sanitizeEvidenceCodes(payload.missingEvidence)
        val baseReasonCodes = buildList {
            addAll(payload.reasonCodes.map { it.trim() }.filter { it.isNotEmpty() })
            add("AI_ROUTER_DRAFT")
            when (payload.identityMode.trim().uppercase(Locale.ROOT)) {
                "RICH_IDENTITY" -> add("AI_RICH_IDENTITY")
                "TYPE_ONLY" -> add("AI_TYPE_ONLY")
            }
            when (routeStatus) {
                "UNRESOLVED" -> add("AI_ROUTE_UNRESOLVED")
                "TYPE_OTHER" -> add("AI_TYPE_OTHER")
                "CATEGORY_ONLY" -> add("AI_CATEGORY_ONLY")
            }
        }.distinct()

        if (routeStatus == "UNRESOLVED") {
            return VisualSearchNormalizationDraft(
                projection = VisualSearchCandidateProjection(reasonCodes = baseReasonCodes),
                confidence = payload.confidence?.coerceIn(0f, 1f),
                hypotheses = emptyList(),
                needsMorePhotos = false,
                missingEvidence = sanitizedMissingEvidence,
                reasonCodes = baseReasonCodes,
                providerName = "visual_search_router",
                providerSchemaVersion = "visual-search/router/1",
            )
        }

        val normalizedItemType = payload.itemType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless(::isOtherItemTypeText)
        val normalizedItemTypeLabel = payload.itemTypeLabel
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless(::isOtherItemTypeText)
        val normalizedFreeTextType = payload.freeTextType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeUnless(::isOtherItemTypeText)
        val routeItemType = when (routeStatus) {
            "TYPE_OTHER" -> normalizedFreeTextType ?: normalizedItemType
            "CATEGORY_ONLY" -> normalizedItemType
            else -> normalizedItemType
        }
        val routeItemTypeLabel = when (routeStatus) {
            "TYPE_OTHER" -> normalizedFreeTextType ?: normalizedItemTypeLabel
            "CATEGORY_ONLY" -> normalizedItemTypeLabel
            else -> normalizedItemTypeLabel
        }
        val routerBrandSource = mapRouterVisibleBrandSource(payload.visibleBrandSource)
        val routeConfidence = listOfNotNull(payload.categoryConfidence, payload.confidence)
            .maxOrNull()
        val projection = buildDraftProjection(
            rawCategoryHint = payload.categoryCode,
            categoryConfidence = routeConfidence,
            primaryObject = payload.primaryObject,
            primaryObjectConfidence = payload.primaryObjectConfidence ?: payload.confidence,
            itemType = routeItemType,
            itemTypeLabel = routeItemTypeLabel,
            itemTypeConfidence = payload.itemTypeConfidence,
            brand = payload.visibleBrand,
            brandConfidence = payload.visibleBrandConfidence ?: payload.confidence,
            brandSource = routerBrandSource,
            rawBrandCandidates = payload.brandCandidates.mapNotNull { candidate ->
                candidate.brand.toCandidateValue(candidate.confidence, mapRouterVisibleBrandSource(candidate.source))
            },
            family = null,
            familyConfidence = null,
            familySource = null,
            rawFamilyCandidates = emptyList(),
            model = null,
            modelConfidence = null,
            modelSource = null,
            rawModelCandidates = emptyList(),
            color = null,
            colorConfidence = null,
            condition = null,
            conditionConfidence = null,
            title = routeItemTypeLabel ?: payload.primaryObject ?: normalizedFreeTextType,
            rawAttributes = payload.visualAttributes,
            rawReasonCodes = baseReasonCodes,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            groundingPacket = groundingPacket,
        )
        val rankedHypotheses = buildRankedHypotheses(
            directProjection = projection,
            directConfidence = payload.confidence,
            directReasonCodes = baseReasonCodes,
            directMissingEvidence = sanitizedMissingEvidence,
            hypotheses = emptyList(),
        )
        val primaryProjection = rankedHypotheses.firstOrNull()?.projection ?: projection
        if (primaryProjection == null) return null
        val reasonCodes = (baseReasonCodes + primaryProjection.reasonCodes).distinct()
        return VisualSearchNormalizationDraft(
            projection = primaryProjection.copy(reasonCodes = reasonCodes),
            confidence = payload.confidence?.coerceIn(0f, 1f) ?: rankedHypotheses.firstOrNull()?.confidence,
            hypotheses = rankedHypotheses,
            needsMorePhotos = false,
            missingEvidence = sanitizedMissingEvidence,
            reasonCodes = reasonCodes,
            providerName = "visual_search_router",
            providerSchemaVersion = "visual-search/router/1",
        )
    }

    private suspend fun parseIdentityPayload(
        metadata: VisualSearchTransportMetadata,
        responseBody: String,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
        target: YandexAiExecutionTarget,
    ): VisualSearchNormalizationDraft? {
        val payload = decodeWithRepair(
            serializer = VisualSearchIdentityPayload.serializer(),
            responseBody = responseBody,
            sessionId = metadata.visualSessionId,
            logCode = "identity_schema_miss",
        ) ?: return null

        val identityStatus = payload.identityStatus.trim().uppercase(Locale.ROOT)
        val reasonCodes = buildList {
            addAll(payload.reasonCodes.map { it.trim() }.filter { it.isNotEmpty() })
            add("AI_IDENTITY_DRAFT")
            when (identityStatus) {
                "ENRICHED" -> add("AI_IDENTITY_ENRICHED")
                "PARTIAL" -> add("AI_IDENTITY_PARTIAL")
                "TYPE_ONLY_COMPLETE" -> add("AI_IDENTITY_TYPE_ONLY_COMPLETE")
                "NO_IDENTITY" -> add("AI_IDENTITY_NONE")
                "ROUTE_CONFLICT" -> add("AI_IDENTITY_ROUTE_CONFLICT")
            }
            if (payload.routeConflict) add("AI_IDENTITY_ROUTE_CONFLICT")
        }.distinct()
        val brand = payload.brand ?: payload.visibleBrand
        val brandConfidence = payload.brandConfidence ?: payload.visibleBrandConfidence
        val brandSource = payload.brandSource ?: mapRouterVisibleBrandSource(payload.visibleBrandSource)
        val projection = buildDraftProjection(
            rawCategoryHint = payload.categoryCode,
            categoryConfidence = payload.categoryConfidence ?: payload.confidence,
            primaryObject = payload.primaryObject,
            primaryObjectConfidence = payload.primaryObjectConfidence ?: payload.confidence,
            itemType = payload.itemType,
            itemTypeLabel = payload.itemTypeLabel,
            itemTypeConfidence = payload.itemTypeConfidence,
            brand = brand,
            brandConfidence = brandConfidence,
            brandSource = brandSource,
            rawBrandCandidates = payload.brandCandidates.mapNotNull { candidate ->
                candidate.brand.toCandidateValue(candidate.confidence, candidate.source)
            },
            family = payload.family,
            familyConfidence = payload.familyConfidence,
            familySource = payload.familySource,
            rawFamilyCandidates = payload.familyCandidates.mapNotNull { candidate ->
                candidate.family.toCandidateValue(candidate.confidence, candidate.source)
            },
            model = payload.model,
            modelConfidence = payload.modelConfidence,
            modelSource = payload.modelSource,
            rawModelCandidates = payload.modelCandidates.map { candidate ->
                YandexDraftModelCandidate(
                    model = candidate.model,
                    confidence = candidate.confidence,
                    source = candidate.source,
                )
            },
            color = payload.color,
            colorConfidence = payload.colorConfidence,
            condition = null,
            conditionConfidence = null,
            title = payload.searchTitle ?: payload.itemTypeLabel ?: payload.primaryObject,
            rawAttributes = payload.visualAttributes,
            rawReasonCodes = reasonCodes,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            groundingPacket = groundingPacket,
        ) ?: return null
        val hypotheses = buildRankedHypotheses(
            directProjection = projection,
            directConfidence = payload.confidence,
            directReasonCodes = reasonCodes,
            directMissingEvidence = payload.missingEvidence,
            hypotheses = emptyList(),
        )
        val providerName = when (config.provider) {
            VisualSearchAiProvider.GEMINI -> "visual_search_identity_gemini"
            VisualSearchAiProvider.OPENAI -> "visual_search_identity_openai"
            VisualSearchAiProvider.YANDEX -> "visual_search_identity_yandex"
        }
        val providerSchemaVersion = when (config.provider) {
            VisualSearchAiProvider.GEMINI -> "visual-search-identity/gemini/${target.key}"
            VisualSearchAiProvider.OPENAI -> "visual-search-identity/openai/${target.key}"
            VisualSearchAiProvider.YANDEX -> "visual-search-identity/yandex/${target.key}"
        }
        return VisualSearchNormalizationDraft(
            projection = projection,
            confidence = payload.confidence?.coerceIn(0f, 1f) ?: hypotheses.firstOrNull()?.confidence,
            hypotheses = hypotheses,
            needsMorePhotos = payload.needsMorePhotos,
            missingEvidence = sanitizeEvidenceCodes(payload.missingEvidence),
            reasonCodes = reasonCodes,
            providerName = providerName,
            providerSchemaVersion = providerSchemaVersion,
        )
    }

    private suspend fun parseAgentPayload(
        metadata: VisualSearchTransportMetadata,
        responseBody: String,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): VisualSearchNormalizationDraft? {
        val payload = decodeWithRepair(
            serializer = YandexAgentVisionPayload.serializer(),
            responseBody = responseBody,
            sessionId = metadata.visualSessionId,
            logCode = "agent_schema_parse_failed",
        ) ?: return null

        val hypothesisEntries = if (payload.hypotheses.isNotEmpty()) {
            payload.hypotheses.mapIndexedNotNull { index, hypothesis ->
                buildAgentHypothesis(
                    hypothesis = hypothesis,
                    fallbackRank = index + 1,
                    request = request,
                    requestedCategoryCode = requestedCategoryCode,
                    groundingPacket = groundingPacket,
                )
            }
        } else {
            payload.saleObjectCandidates
                .sortedByDescending { it.confidence ?: 0f }
                .mapIndexedNotNull { index, candidate ->
                    buildAgentHypothesis(
                        hypothesis = candidate.toHypothesis(),
                        fallbackRank = index + 1,
                        request = request,
                        requestedCategoryCode = requestedCategoryCode,
                        groundingPacket = groundingPacket,
                    )
                }
        }
        val primaryHypothesis = hypothesisEntries.firstOrNull() ?: return null
        val missingEvidence = sanitizeEvidenceCodes(
            payload.missingEvidence +
                primaryHypothesis.missingEvidence,
        )
        val reasonCodes = buildList {
            add("YANDEX_AI_AGENT_DRAFT")
            if (payload.ambiguities.isNotEmpty()) add("AI_AMBIGUITIES_PRESENT")
            if (payload.needsMorePhotos || missingEvidence.isNotEmpty()) add("AI_MORE_PHOTOS_NEEDED")
            addAll(primaryHypothesis.reasonCodes)
        }.distinct()

        return VisualSearchNormalizationDraft(
            projection = primaryHypothesis.projection.copy(reasonCodes = reasonCodes),
            confidence = primaryHypothesis.confidence,
            hypotheses = hypothesisEntries,
            needsMorePhotos = payload.needsMorePhotos || missingEvidence.isNotEmpty(),
            missingEvidence = missingEvidence,
            reasonCodes = reasonCodes,
            providerName = "yandex_ai_studio_agent",
            providerSchemaVersion = "visual-search/yandex-agent/2",
        )
    }

    private suspend fun buildGroundingPacket(
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
    ): GroundingPacket {
        val locale = request.locale
        val categoryLimit = groundingCategoryLimit()
        val familyLimit = groundingFamilyLimit()
        val modelLimit = groundingModelLimit()
        val allCategories = catalogTaxonomyRepository.listCategories()
            .filter { category -> category.status == CategoryStatus.ACTIVE }
        val hintTexts = buildHintTexts(request)
        val seedText = hintTexts.joinToString(" ")
        val preflightCategoryEntries = buildPreflightCategoryEntries(
            request = request,
            allCategories = allCategories,
        )
        val modelMatches = hintTexts.asSequence()
            .mapNotNull(CatalogCanonicalModelRegistry::matchQuery)
            .distinctBy { it.modelCode }
            .take(modelLimit)
            .toList()
        val familyMatches = hintTexts.asSequence()
            .mapNotNull(CatalogCanonicalProductFamilyRegistry::matchQuery)
            .distinctBy { it.familyCode }
            .take(familyLimit)
            .toList()

        val explicitCategoryCodes = buildSet {
            requestedCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase(Locale.ROOT)) }
            request.preflightSignals.exactCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it.uppercase(Locale.ROOT)) }
            modelMatches.forEach { add(it.defaultCategoryCode.uppercase(Locale.ROOT)) }
            familyMatches.forEach { add(it.defaultCategoryCode.uppercase(Locale.ROOT)) }
        }

        val resolvedCategoryCodes = explicitCategoryCodes.mapNotNull { categoryCode ->
            catalogTaxonomyRepository.resolveCategoryCode(categoryCode)?.resolvedCode?.uppercase(Locale.ROOT)
        }.toSet()

        val hasGroundingSignals = resolvedCategoryCodes.isNotEmpty() ||
            preflightCategoryEntries.isNotEmpty() ||
            hintTexts.isNotEmpty() ||
            modelMatches.isNotEmpty() ||
            familyMatches.isNotEmpty() ||
            !request.preflightSignals.barcodeValue.isNullOrBlank() ||
            !request.preflightSignals.objectLabel.isNullOrBlank()

        val scoredCategoryEntries = if (!hasGroundingSignals) {
            emptyList()
        } else {
            allCategories
                .map { category ->
                    val title = category.displayTitle(locale)
                    val normalizedTitle = SearchTextNormalizer.normalizeKey(title)
                    val titleTokens = SearchTextNormalizer.tokens(title)
                        .map { token -> token.lowercase(Locale.ROOT) }
                        .toSet()
                    val hintTokens = SearchTextNormalizer.tokens(seedText)
                        .map { token -> token.lowercase(Locale.ROOT) }
                        .toSet()
                    val overlap = titleTokens.intersect(hintTokens).size
                    val explicitBoost = when (category.code.uppercase(Locale.ROOT)) {
                        in resolvedCategoryCodes -> 100
                        else -> 0
                    }
                    val modelBoost = modelMatches.count { it.defaultCategoryCode.equals(category.code, ignoreCase = true) } * 16
                    val familyBoost = familyMatches.count { it.defaultCategoryCode.equals(category.code, ignoreCase = true) } * 12
                    val score = explicitBoost + modelBoost + familyBoost + overlap * 4 + if (normalizedTitle in hintTexts) 8 else 0
                    category to score
                }
                .filter { (_, score) -> score > 0 }
                .sortedWith(compareByDescending<Pair<Category, Int>> { it.second }.thenBy { it.first.code })
                .take(categoryLimit)
        }

        val fallbackBroadCategories = if (resolvedCategoryCodes.isEmpty() && scoredCategoryEntries.isEmpty()) {
            buildBroadFallbackCategoryCards(
                allCategories = allCategories,
                locale = locale,
            )
        } else {
            emptyList()
        }

        val candidateCategories = buildList {
            resolvedCategoryCodes.mapNotNullTo(this) { code ->
                allCategories.firstOrNull { category -> category.code.equals(code, ignoreCase = true) }
                    ?.let { buildCategoryCard(it, locale) }
            }
            preflightCategoryEntries.forEach { entry ->
                add(
                    buildCategoryCard(
                        category = entry.category,
                        locale = locale,
                        confidence = entry.confidence,
                        source = entry.source,
                        label = entry.label,
                    ),
                )
            }
            scoredCategoryEntries.forEach { (category, _) ->
                add(buildCategoryCard(category, locale))
            }
            if (isEmpty()) {
                addAll(fallbackBroadCategories)
            }
        }
            .distinctBy { it.code }
            .take(
                if (fallbackBroadCategories.isNotEmpty()) {
                    maxOf(categoryLimit, fallbackBroadCategories.size)
                } else {
                    categoryLimit
                },
            )

        val fallbackFamilyCards = CatalogCanonicalProductFamilyRegistry.families()
            .asSequence()
            .filter { family ->
                candidateCategories.any { category ->
                    category.code.equals(family.defaultCategoryCode, ignoreCase = true)
                }
            }
            .map { family ->
                GroundingFamilyCard(
                    familyCode = family.familyCode,
                    defaultCategoryCode = family.defaultCategoryCode,
                    brand = family.brandCanonical,
                    family = family.prettyModelPrefix,
                    confidence = 0.42f,
                    normalizedModelText = family.prettyModelPrefix,
                )
            }
            .take(familyLimit)
            .toList()

        val candidateFamilies = buildList {
            familyMatches.forEach { match ->
                add(
                    GroundingFamilyCard(
                        familyCode = match.familyCode,
                        defaultCategoryCode = match.defaultCategoryCode,
                        brand = match.brandCanonical,
                        family = match.prettyModelPrefix,
                        confidence = match.confidence.toFloat(),
                        normalizedModelText = match.modelText,
                    ),
                )
            }
            fallbackFamilyCards.forEach { add(it) }
        }
            .distinctBy { it.familyCode }
            .take(familyLimit)

        val candidateModels = modelMatches.map { match ->
            GroundingModelCard(
                modelCode = match.modelCode,
                defaultCategoryCode = match.defaultCategoryCode,
                brand = match.brandCanonical,
                familyCode = match.familyCode,
                model = match.modelText,
                confidence = match.confidence.toFloat(),
            )
        }

        return GroundingPacket(
            locale = locale?.trim()?.ifEmpty { null } ?: "ru",
            intent = request.intent.name.lowercase(Locale.ROOT),
            captureMode = request.preflightSignals.captureMode?.name?.lowercase(Locale.ROOT),
            requestedCategoryCode = requestedCategoryCode?.trim()?.takeIf { it.isNotEmpty() },
            hintTexts = hintTexts,
            barcodeValue = request.preflightSignals.barcodeValue,
            objectLabel = request.preflightSignals.objectLabel,
            candidateCategories = candidateCategories,
            broadFallbackCategories = fallbackBroadCategories.isNotEmpty(),
            candidateFamilies = candidateFamilies,
            candidateModels = candidateModels,
        )
    }

    private fun groundingCategoryLimit(): Int =
        if (config.provider == VisualSearchAiProvider.OPENAI) {
            if (isRouterMode()) {
                minOf(config.aiShortlistCategoryLimit, 6)
            } else {
                minOf(config.aiShortlistCategoryLimit, 6)
            }
        } else {
            config.aiShortlistCategoryLimit
        }

    private fun groundingFamilyLimit(): Int =
        config.aiShortlistFamilyLimit

    private fun groundingModelLimit(): Int =
        config.aiShortlistModelLimit

    private fun groundingAttributeLimit(): Int =
        if (config.provider == VisualSearchAiProvider.OPENAI) minOf(config.aiGroundingAttributeLimit, 1) else config.aiGroundingAttributeLimit

    private fun groundingAllowedValueLimit(): Int =
        if (config.provider == VisualSearchAiProvider.OPENAI) minOf(config.aiGroundingAllowedValueLimit, 1) else config.aiGroundingAllowedValueLimit

    private suspend fun buildPreflightCategoryEntries(
        request: VisualSearchNormalizeDraftRequest,
        allCategories: List<Category>,
    ): List<PreflightCategoryEntry> {
        val categoriesByCode = allCategories.associateBy { category -> category.code.uppercase(Locale.ROOT) }
        val entries = mutableListOf<PreflightCategoryEntry>()
        request.preflightSignals.categoryCandidates.forEach { candidate ->
            val rawCode = candidate.categoryCode.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val resolvedCode = catalogTaxonomyRepository.resolveCategoryCode(rawCode)
                ?.resolvedCode
                ?.uppercase(Locale.ROOT)
                ?: rawCode.uppercase(Locale.ROOT)
            val category = categoriesByCode[resolvedCode] ?: return@forEach
            entries += PreflightCategoryEntry(
                category = category,
                confidence = candidate.confidence?.coerceIn(0f, 1f),
                source = candidate.source?.trim()?.takeIf { it.isNotEmpty() }?.take(32),
                label = candidate.label?.trim()?.takeIf { it.isNotEmpty() }?.take(80),
            )
        }
        return entries
            .sortedWith(
                compareByDescending<PreflightCategoryEntry> { entry -> entry.confidence ?: 0f }
                    .thenBy { entry -> entry.category.code },
            )
            .distinctBy { entry -> entry.category.code.uppercase(Locale.ROOT) }
            .take(config.aiShortlistCategoryLimit)
            .toList()
    }

    private suspend fun buildCategoryCard(
        category: Category,
        locale: String?,
        confidence: Float? = null,
        source: String? = null,
        label: String? = null,
    ): GroundingCategoryCard {
        val effectiveSpec = catalogRepository.getCategoryEffectiveSpec(category.code)
        val attributeLimit = groundingAttributeLimit()
        val allowedValueLimit = groundingAllowedValueLimit()
        val attributes = effectiveSpec?.allAttributes()
            ?.sortedWith(
                compareByDescending<CatalogAttributeSpec> { it.isIdentity }
                    .thenByDescending { it.requiredForCategory || it.requiredForSearch }
                    .thenBy { it.uiOrder },
            )
            ?.take(attributeLimit)
            ?.map { spec ->
                GroundingAttributeCard(
                    code = spec.code,
                    title = spec.labels.resolve(locale, spec.title) ?: spec.title,
                    dataType = spec.dataType.name.lowercase(Locale.ROOT),
                    allowedValues = spec.options
                        .take(allowedValueLimit)
                        .map { option -> option.labels.resolve(locale) ?: option.valueCode }
                        .filter { value -> value.isNotBlank() },
                )
            }
            .orEmpty()
        return GroundingCategoryCard(
            code = category.code.uppercase(Locale.ROOT),
            title = category.displayTitle(locale),
            segment = category.segment.name,
            parentCode = category.parentCode,
            confidence = confidence,
            source = source,
            label = label,
            attributes = attributes,
        )
    }

    private suspend fun buildBroadFallbackCategoryCards(
        allCategories: List<Category>,
        locale: String?,
    ): List<GroundingCategoryCard> {
        val rootCodes = allCategories
            .asSequence()
            .filter { category -> category.status == CategoryStatus.ACTIVE && category.parentCode == null }
            .map { category -> category.code.uppercase(Locale.ROOT) }
            .toSet()
        val directChildren = allCategories
            .asSequence()
            .filter { category ->
                category.status == CategoryStatus.ACTIVE &&
                    category.parentCode?.uppercase(Locale.ROOT) in rootCodes
            }
            .take(groundingCategoryLimit())
            .toList()
        val categoriesByCode = allCategories.associateBy { category -> category.code.uppercase(Locale.ROOT) }
        return buildList {
            BROAD_FALLBACK_CATEGORY_CODES.mapNotNullTo(this) { code ->
                categoriesByCode[code]?.let { category ->
                    val normalizedCode = category.code.uppercase(Locale.ROOT)
                    GroundingCategoryCard(
                        code = normalizedCode,
                        title = category.displayTitle(locale),
                        segment = category.segment.name,
                        parentCode = category.parentCode,
                        label = BROAD_FALLBACK_CATEGORY_LABELS[normalizedCode],
                        attributes = emptyList(),
                    )
                }
            }
            directChildren.forEach { category ->
                add(
                    GroundingCategoryCard(
                        code = category.code.uppercase(Locale.ROOT),
                        title = category.displayTitle(locale),
                        segment = category.segment.name,
                        parentCode = category.parentCode,
                        attributes = emptyList(),
                    ),
                )
            }
        }
            .distinctBy { card -> card.code }
            .take(maxOf(groundingCategoryLimit(), BROAD_FALLBACK_CATEGORY_CODES.size))
    }

    private fun buildHintTexts(request: VisualSearchNormalizeDraftRequest): List<String> =
        buildList {
            request.preflightSignals.ocrTextHints.forEach { add(normalizeHint(it)) }
            request.preflightSignals.imageLabelHints.forEach { add(normalizeHint(it)) }
            request.preflightSignals.objectLabel?.let { add(normalizeHint(it)) }
            request.preflightSignals.barcodeValue?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            request.manualCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            request.preflightSignals.exactCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(config.aiHintLimit)

    private fun normalizeHint(raw: String?): String =
        raw?.let(SearchTextNormalizer::normalizeKey).orEmpty()

    private fun buildRequestAssets(
        request: VisualSearchNormalizeDraftRequest,
    ): List<VisualSearchImageAsset> = buildList {
        add(request.asset)
        addAll(request.contextAssets)
    }
        .distinctBy { asset ->
            asset.sha256.trim().ifEmpty { asset.storageKey?.trim().orEmpty() }
        }
        .take(MAX_REQUEST_IMAGES)

    private fun buildContentParts(
        request: VisualSearchNormalizeDraftRequest,
        primaryAsset: VisualSearchImageAsset,
        primaryInlineBase64: String,
        contextAssets: List<VisualSearchImageAsset>,
        groundingPacket: GroundingPacket,
        routerMode: Boolean,
        userPromptOverride: String? = null,
        imageDetailOverride: String? = null,
        preprocessMaxSidePxOverride: Int? = null,
        preprocessJpegQualityOverride: Float? = null,
    ): List<YandexAiStudioContentPart> = buildList {
        val hasContextImages = contextAssets.any { asset ->
            !asset.inlineBase64.isNullOrBlank()
        }
        add(
            YandexAiStudioContentPart.Text(
                userPromptOverride ?: buildUserPrompt(groundingPacket, routerMode),
            ),
        )
        if (hasContextImages) {
            add(YandexAiStudioContentPart.Text(MULTI_IMAGE_CONTEXT_INSTRUCTION))
        }
        val useOpenAiContextQuality = config.provider == VisualSearchAiProvider.OPENAI &&
            hasContextImages &&
            config.openAiImageDetail == "low"
        val baseOpenAiImageDetail = imageDetailOverride ?: config.openAiImageDetail
        val baseOpenAiMaxSidePx = preprocessMaxSidePxOverride ?: config.openAiImageMaxSidePx
        val baseOpenAiJpegQuality = preprocessJpegQualityOverride ?: config.openAiImageJpegQuality
        val openAiImageDetail = when {
            config.provider != VisualSearchAiProvider.OPENAI -> null
            imageDetailOverride != null -> imageDetailOverride
            useOpenAiContextQuality -> null
            else -> baseOpenAiImageDetail
        }
        val openAiMaxSidePx = when {
            config.provider != VisualSearchAiProvider.OPENAI -> null
            preprocessMaxSidePxOverride != null -> preprocessMaxSidePxOverride
            useOpenAiContextQuality -> 768
            else -> baseOpenAiMaxSidePx
        }
        val openAiJpegQuality = when {
            config.provider != VisualSearchAiProvider.OPENAI -> null
            preprocessJpegQualityOverride != null -> preprocessJpegQualityOverride
            useOpenAiContextQuality -> 0.74f
            else -> baseOpenAiJpegQuality
        }
        add(
            YandexAiStudioContentPart.ImageBase64(
                mimeType = primaryAsset.mimeType,
                base64 = primaryInlineBase64,
                focusRegion = request.selectedRegion?.toFocusRegionOrNull(),
                imageDetail = openAiImageDetail,
                preprocessMaxSidePx = openAiMaxSidePx,
                preprocessJpegQuality = openAiJpegQuality,
            ),
        )
        contextAssets.forEach { asset ->
            asset.inlineBase64
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { inlineBase64 ->
                    add(
                        YandexAiStudioContentPart.ImageBase64(
                            mimeType = asset.mimeType,
                            base64 = inlineBase64,
                            focusRegion = null,
                            imageDetail = openAiImageDetail,
                            preprocessMaxSidePx = openAiMaxSidePx,
                            preprocessJpegQuality = openAiJpegQuality,
                        ),
                    )
                }
        }
    }

    private fun buildIdentityUserPrompt(
        routerDraft: VisualSearchNormalizationDraft,
        groundingPacket: GroundingPacket,
    ): String =
        AiStructuredContractLoader.renderTemplate(
            template = identityContract.userPromptTemplate,
            bindings = mapOf(
                "router_result" to json.encodeToString(
                    RouterResultPacket.serializer(),
                    routerDraft.toRouterResultPacket(),
                ),
                "grounding_packet" to json.encodeToString(GroundingPacket.serializer(), groundingPacket),
            ),
        )

    private fun buildUserPrompt(groundingPacket: GroundingPacket, routerMode: Boolean): String =
        if (routerMode) {
            AiStructuredContractLoader.renderTemplate(
                template = routerContract.userPromptTemplate,
                bindings = mapOf(
                    "grounding_packet" to json.encodeToString(GroundingPacket.serializer(), groundingPacket),
                ),
            )
        } else {
            when (config.aiPromptProfile) {
            "simple" -> """
            Analyze the product image for catalog search.
            First identify primary_object: the central/foreground sale item. All returned fields must describe it only.
            Use candidateCategories as the allowed category options when they are present.
            Return category_code whenever the product type is visible.
            Return brand and family as anchors when supported by distinctive visible design, OCR, label, or a strong catalog match.
            If exact model is uncertain, leave model empty and return 1-2 model_candidates.
            Identify the foreground product, not the table, drinkware, kitchenware, or room around it.
            Set source for identity fields: TEXT_EXACT, LOGO_EXACT, BARCODE_EXACT, USER_HINT, CATALOG_SHORTLIST, VISUAL_DISTINCTIVE, VISUAL_PATTERN, VISUAL_WEAK.
            Never use generic object names as brand.
            Return at most 2 hypotheses for real ambiguity.
            Output JSON only.

            Grounding packet:
            ${json.encodeToString(GroundingPacket.serializer(), groundingPacket)}
            """.trimIndent()

            "identity" -> """
            Identify the sale product for catalog retrieval.
            First identify primary_object: the central/foreground sale item. Category and identity must describe it only.
            Choose the safest category_code first. If exact identity is weak, category_code plus brand or family is still useful.
            Fill identity in this order: brand anchor, family anchor, model candidates, exact model.
            Brand/family may come from distinctive visual patterns. Use exact model only when visible text, packaging, label, or very distinctive design supports it.
            If model is plausible but not exact, return 1-2 model_candidates instead of model.
            Do not describe or classify from the room, background, hands, table, drinkware, kitchenware, or unrelated objects.
            Use 1 or 2 hypotheses only for real ambiguity. Keep auxiliary attributes sparse.
            Set needs_more_photos=true when another angle, back side, label, or packaging would materially improve identification.
            Output JSON only.

            Grounding packet:
            ${json.encodeToString(GroundingPacket.serializer(), groundingPacket)}
            """.trimIndent()

            else -> AiStructuredContractLoader.renderTemplate(
                template = aiContract.userPromptTemplate,
                bindings = mapOf(
                    "grounding_packet" to json.encodeToString(GroundingPacket.serializer(), groundingPacket),
                ),
            )
        }
        }

    private fun buildSystemPrompt(routerMode: Boolean): String =
        buildString {
            append(baseSystemPrompt(routerMode))
            providerSystemPromptOverlay(routerMode)
                ?.let { overlay ->
                    appendLine()
                    appendLine("Provider execution rules:")
                    append(overlay)
                }
            config.activeSystemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { customPrompt ->
                    appendLine()
                    appendLine("Project-specific rules:")
                    append(customPrompt)
            }
        }.trim()

    private fun buildIdentitySystemPrompt(): String =
        buildString {
            append(identityContract.systemPrompt.trim())
            providerSystemPromptOverlay(routerMode = false)
                ?.let { overlay ->
                    appendLine()
                    appendLine("Provider execution rules:")
                    append(overlay)
                }
            config.activeSystemPrompt
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { customPrompt ->
                    appendLine()
                    appendLine("Project-specific rules:")
                    append(customPrompt)
                }
        }.trim()

    private suspend fun shouldRunIdentityEnrichment(
        routerDraft: VisualSearchNormalizationDraft,
    ): Boolean {
        if (!config.aiIdentityEnrichmentEnabled) return false
        val reasonCodes = routerDraft.reasonCodes.map { it.uppercase(Locale.ROOT) }.toSet()
        val categoryCode = routerDraft.projection.categoryCode?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!categorySupportsRichIdentity(categoryCode)) return false
        if (!shouldSpendIdentityPass(categoryCode = categoryCode, projection = routerDraft.projection)) return false
        return "AI_RICH_IDENTITY" in reasonCodes || shouldForceIdentityPass(categoryCode, routerDraft.projection)
    }

    private fun shouldForceIdentityPass(
        categoryCode: String,
        projection: VisualSearchCandidateProjection,
    ): Boolean {
        val normalizedCategory = categoryCode.trim().uppercase(Locale.ROOT)
        val itemText = SearchTextNormalizer.normalizeKey(
            listOfNotNull(projection.itemType?.text, projection.title).joinToString(" "),
        )
        return when (normalizedCategory) {
            "TECH.PHONES",
            "TECH.GAMING",
            "AUTO.PARTS" -> true

            "TECH.SMART_HOME_SECURITY" ->
                itemText.contains("watch") ||
                    itemText.contains("smartwatch") ||
                    itemText.contains("wearable")

            "FASH.ACCESSORIES" ->
                itemText.contains("watch") ||
                    itemText.contains("chronograph") ||
                    itemText.contains("часы")

            "FASH.SHOES" ->
                itemText.contains("shoe") ||
                    itemText.contains("sneaker") ||
                    itemText.contains("trainer") ||
                    itemText.contains("boot") ||
                    itemText.contains("кроссов") ||
                    itemText.contains("ботин")

            else -> false
        }
    }

    private fun shouldSpendIdentityPass(
        categoryCode: String,
        projection: VisualSearchCandidateProjection,
    ): Boolean {
        val normalizedCategory = categoryCode.trim().uppercase(Locale.ROOT)
        val itemText = SearchTextNormalizer.normalizeKey(
            listOfNotNull(projection.itemType?.text, projection.title).joinToString(" "),
        )
        val hasVisibleBrand = !projection.brand?.text.isNullOrBlank()
        val hasModelSignal = !projection.model?.text.isNullOrBlank() || projection.modelCandidates.isNotEmpty()
        fun itemContains(vararg tokens: String): Boolean =
            tokens.any { token -> itemText.contains(token) }

        return when (normalizedCategory) {
            "TECH.PHONES",
            "TECH.GAMING",
            "AUTO.PARTS"
            -> true

            "TECH.SMART_HOME_SECURITY" ->
                itemContains("watch", "smartwatch", "wearable", "smart watch")

            "TECH.AUDIO" ->
                hasVisibleBrand && itemContains("speaker", "headphone", "earbud", "earphone", "portable speaker")

            "FASH.SHOES" ->
                hasVisibleBrand || hasModelSignal || itemContains("shoe", "sneaker", "trainer", "boot", "кроссов", "ботин")

            "FASH.ACCESSORIES" ->
                itemContains("watch", "chronograph", "smartwatch", "wrist watch", "часы")

            else -> false
        }
    }

    private suspend fun categorySupportsRichIdentity(categoryCode: String): Boolean {
        val spec = catalogRepository.getCategoryEffectiveSpec(categoryCode) ?: return false
        val attributeCodes = spec.allAttributes()
            .map { attribute -> normalizeAttributeCode(attribute.code) }
            .toSet()
        return attributeCodes.any { code ->
            code == "model" ||
                code == "model_name" ||
                code == "family" ||
                code == "product_family" ||
                code == "model_line" ||
                code == "product_line" ||
                code == "series"
        }
    }

    private fun normalizeAttributeCode(raw: String): String =
        raw.trim()
            .lowercase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_')

    private fun mergeRouterAndIdentityDrafts(
        routerDraft: VisualSearchNormalizationDraft,
        identityDraft: VisualSearchNormalizationDraft,
    ): VisualSearchNormalizationDraft {
        val routerProjection = routerDraft.projection
        val identityProjection = identityDraft.projection
        val mergedProjection = routerProjection.copy(
            categoryCode = identityProjection.categoryCode ?: routerProjection.categoryCode,
            categoryConfidence = listOfNotNull(routerProjection.categoryConfidence, identityProjection.categoryConfidence)
                .maxOrNull(),
            itemType = identityProjection.itemType ?: routerProjection.itemType,
            brand = identityProjection.brand ?: routerProjection.brand,
            brandCandidates = (identityProjection.brandCandidates + routerProjection.brandCandidates)
                .distinctBy { candidate -> SearchTextNormalizer.normalizeKey(candidate.text) }
                .take(2),
            family = identityProjection.family ?: routerProjection.family,
            familyCandidates = (identityProjection.familyCandidates + routerProjection.familyCandidates)
                .distinctBy { candidate -> SearchTextNormalizer.normalizeKey(candidate.text) }
                .take(2),
            model = identityProjection.model ?: routerProjection.model,
            modelCandidates = (identityProjection.modelCandidates + routerProjection.modelCandidates)
                .distinctBy { candidate -> SearchTextNormalizer.normalizeKey(candidate.text) }
                .take(2),
            color = identityProjection.color ?: routerProjection.color,
            condition = identityProjection.condition ?: routerProjection.condition,
            attributes = (routerProjection.attributes + identityProjection.attributes)
                .distinctBy { attribute -> attribute.code.lowercase(Locale.ROOT) },
            title = identityProjection.title ?: routerProjection.title,
            reasonCodes = (routerProjection.reasonCodes + identityProjection.reasonCodes + "AI_STAGED_IDENTITY")
                .distinct(),
        )
        val reasonCodes = (routerDraft.reasonCodes + identityDraft.reasonCodes + "AI_STAGED_IDENTITY").distinct()
        return VisualSearchNormalizationDraft(
            projection = mergedProjection,
            confidence = listOfNotNull(routerDraft.confidence, identityDraft.confidence).maxOrNull(),
            hypotheses = identityDraft.hypotheses.ifEmpty { routerDraft.hypotheses },
            needsMorePhotos = routerDraft.needsMorePhotos || identityDraft.needsMorePhotos,
            missingEvidence = sanitizeEvidenceCodes(routerDraft.missingEvidence + identityDraft.missingEvidence),
            reasonCodes = reasonCodes,
            providerName = identityDraft.providerName ?: routerDraft.providerName,
            providerSchemaVersion = identityDraft.providerSchemaVersion ?: routerDraft.providerSchemaVersion,
        )
    }

    private fun baseSystemPrompt(routerMode: Boolean): String =
        if (routerMode) {
            routerContract.systemPrompt.trim()
        } else {
            when (config.aiPromptProfile) {
            "simple" -> """
            Normalize one product photo into compact JSON for catalog search.
            First identify primary_object: the central/foreground sale item.
            First choose category_code. Then add brand/family anchors and model_candidates.
            Prefer useful visual anchors over an empty result, but keep exact model null unless strongly supported.
            Identify the primary foreground product, not background tableware or kitchen context.
            Never invent values. Never use generic object names as brand.
            Output valid JSON only.
            """.trimIndent()

            "identity" -> """
            You normalize product photos into compact JSON for catalog search.
            First identify primary_object: the central/foreground sale item.
            Focus on search anchors: category_code, brand, family, model_candidates, and only high-signal attributes.
            Identify the primary foreground product, not background tableware, drinkware, furniture, or room context.
            Prefer category + brand/family anchors over a speculative exact model.
            Never invent text that is not visible or strongly implied by distinctive product design.
            Return valid raw JSON only.
            """.trimIndent()

            else -> aiContract.systemPrompt.trim()
        }
        }

    private fun providerSystemPromptOverlay(routerMode: Boolean): String? = if (routerMode) {
        """
        Return raw JSON only, with no markdown fences, prose, or comments.
        Do not return alternatives, family, exact model, or model candidates.
        visible_brand is allowed only when visible on the primary product, its own tag/label, or its own primary packaging.
        Use TYPE_OTHER only for taxonomy gaps, not uncertainty.
        If the primary product class is visible, route it to the safest compatible category.
        Unknown brand, family, model, or exact subtype must not reduce category confidence.
        """.trimIndent()
    } else when (config.provider) {
        VisualSearchAiProvider.YANDEX -> """
            Prefer binding-safe category selection when candidateCategories are present.
            If the photo supports category plus visual brand/family anchors, return those anchors even without OCR.
            If only category is supported, still return category_code and keep identity sparse.
            Prioritize brand, family, and model over color, condition, and auxiliary attributes.
            Omit non-identity fields unless they clearly improve retrieval.
            Use alternative hypotheses for nearby catalog branches instead of dropping category_code.
        """.trimIndent()

        VisualSearchAiProvider.GEMINI -> """
            Return raw JSON only, with no markdown fences, prose, or comments.
            Never emit trailing commas.
            Omit unsupported or uncertain fields instead of using placeholders.
            Keep the JSON compact and schema-valid; category_code is more valuable than speculative identity text.
        """.trimIndent()

        VisualSearchAiProvider.OPENAI -> """
            Return raw JSON only, with no markdown fences, prose, or comments.
            Use minimal reasoning and compact output: identify the sale object, not the scene.
            Always fill item_type for the visible product type; it is the product-type anchor inside broad category leaves.
            Always return either a safe category_code or visible category/brand/family anchors when evidence is sufficient.
            Brand and family are retrieval anchors: infer them from distinctive product design when useful, even without OCR.
            For rich-identity products, run a second identity pass after category/item_type and return supported brand, family, and model_candidates.
            Do not invent identity from generic object labels or background objects; keep weak exact model empty and use model_candidates.
            Prefer category_code plus brand/family anchors over a speculative exact model.
        """.trimIndent()
    }

    private fun isRouterMode(): Boolean =
        config.aiPromptProfile.equals("router", ignoreCase = true)

    private fun aiDraftReasonCode(): String = when (config.provider) {
        VisualSearchAiProvider.YANDEX -> "YANDEX_AI_DRAFT"
        VisualSearchAiProvider.GEMINI -> "GEMINI_AI_DRAFT"
        VisualSearchAiProvider.OPENAI -> "OPENAI_AI_DRAFT"
    }

    private fun VisualSearchSelectedRegion.toFocusRegionOrNull(): YandexAiStudioFocusRegion? {
        val normalizedWidth = width.coerceIn(0.05f, 1f)
        val normalizedHeight = height.coerceIn(0.05f, 1f)
        val normalizedLeft = left.coerceIn(0f, 1f - normalizedWidth)
        val normalizedTop = top.coerceIn(0f, 1f - normalizedHeight)
        return YandexAiStudioFocusRegion(
            left = normalizedLeft,
            top = normalizedTop,
            width = normalizedWidth,
            height = normalizedHeight,
        )
    }

    private suspend fun buildDraftProjection(
        rawCategoryHint: String?,
        categoryConfidence: Float?,
        primaryObject: String?,
        primaryObjectConfidence: Float?,
        itemType: String?,
        itemTypeLabel: String?,
        itemTypeConfidence: Float?,
        brand: String?,
        brandConfidence: Float?,
        brandSource: String?,
        rawBrandCandidates: List<VisualSearchCandidateValue>,
        family: String?,
        familyConfidence: Float?,
        familySource: String?,
        rawFamilyCandidates: List<VisualSearchCandidateValue>,
        model: String?,
        modelConfidence: Float?,
        modelSource: String?,
        rawModelCandidates: List<YandexDraftModelCandidate>,
        color: String?,
        colorConfidence: Float?,
        condition: String?,
        conditionConfidence: Float?,
        title: String?,
        rawAttributes: List<YandexDraftAttribute>,
        rawReasonCodes: List<String>,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): VisualSearchCandidateProjection? {
        if (
            title.isNullOrBlank() &&
            primaryObject.isNullOrBlank() &&
            rawCategoryHint.isNullOrBlank() &&
            brand.isNullOrBlank() &&
            rawBrandCandidates.isEmpty() &&
            family.isNullOrBlank() &&
            rawFamilyCandidates.isEmpty() &&
            model.isNullOrBlank() &&
            itemType.isNullOrBlank() &&
            itemTypeLabel.isNullOrBlank() &&
            color.isNullOrBlank() &&
            condition.isNullOrBlank() &&
            rawAttributes.isEmpty() &&
            rawReasonCodes.isEmpty()
        ) {
            return null
        }
        val chosenCategoryCode = resolveChosenCategoryCode(
            rawHint = rawCategoryHint,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            groundingPacket = groundingPacket,
        )
            ?.takeUnless { categoryCode ->
                shouldDropSceneCategory(
                    categoryCode = categoryCode,
                    rawCategoryHint = rawCategoryHint,
                    title = title,
                    primaryObject = primaryObject,
                    request = request,
                )
            }
        val effectiveSpec = chosenCategoryCode?.let { categoryCode ->
            catalogRepository.getCategoryEffectiveSpec(categoryCode = categoryCode)
        }
        val attributes = rawAttributes
            .mapNotNull { candidate -> candidate.toDomainAttribute(effectiveSpec?.allAttributes().orEmpty()) }
        val evidenceGatedBrand = brand.takeIfIdentitySupported(
            groundingPacket = groundingPacket,
            confidence = brandConfidence,
            source = brandSource,
            field = IdentityField.BRAND,
            reasonCodes = rawReasonCodes,
        )
        val evidenceGatedFamily = family.takeIfIdentitySupported(
            groundingPacket = groundingPacket,
            confidence = familyConfidence,
            source = familySource,
            field = IdentityField.FAMILY,
            reasonCodes = rawReasonCodes,
        )
        val evidenceGatedModel = model.takeIfIdentitySupported(
            groundingPacket = groundingPacket,
            confidence = modelConfidence,
            source = modelSource,
            field = IdentityField.MODEL,
            reasonCodes = rawReasonCodes,
        )
        val brandCandidates = rawBrandCandidates.filterIdentityCandidates(
            groundingPacket = groundingPacket,
            field = IdentityField.BRAND,
            reasonCodes = rawReasonCodes,
            acceptedValue = evidenceGatedBrand,
        )
        val familyCandidates = rawFamilyCandidates.filterIdentityCandidates(
            groundingPacket = groundingPacket,
            field = IdentityField.FAMILY,
            reasonCodes = rawReasonCodes,
            acceptedValue = evidenceGatedFamily,
        )
        val modelCandidates = buildModelCandidates(
            primaryModel = model,
            primaryConfidence = modelConfidence,
            primarySource = modelSource,
            rawCandidates = rawModelCandidates,
            acceptedModel = evidenceGatedModel,
        )
        return VisualSearchCandidateProjection(
            categoryCode = chosenCategoryCode,
            categoryConfidence = chosenCategoryCode?.let { categoryConfidence?.coerceIn(0f, 1f) },
            itemType = buildItemTypeCandidate(
                itemType = itemType,
                itemTypeLabel = itemTypeLabel,
                confidence = itemTypeConfidence ?: primaryObjectConfidence ?: categoryConfidence,
                title = title,
                primaryObject = primaryObject,
            ),
            brand = evidenceGatedBrand.toCandidateValue(brandConfidence, brandSource),
            brandCandidates = brandCandidates,
            family = evidenceGatedFamily.toCandidateValue(familyConfidence, familySource),
            familyCandidates = familyCandidates,
            model = evidenceGatedModel.toCandidateValue(modelConfidence, modelSource),
            modelCandidates = modelCandidates,
            color = color.toCandidateValue(colorConfidence),
            condition = condition.toCandidateValue(conditionConfidence),
            attributes = attributes,
            title = sanitizeDraftTitle(
                title = title,
                primaryObject = primaryObject,
                primaryObjectConfidence = primaryObjectConfidence,
                categoryCode = chosenCategoryCode,
            ),
            reasonCodes = rawReasonCodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )
    }

    private suspend fun buildDraftHypothesis(
        hypothesis: YandexDraftHypothesis,
        fallbackRank: Int,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): VisualSearchHypothesis? {
        val projection = buildDraftProjection(
            rawCategoryHint = hypothesis.categoryCode,
            categoryConfidence = hypothesis.categoryConfidence ?: hypothesis.confidence,
            primaryObject = hypothesis.primaryObject,
            primaryObjectConfidence = hypothesis.primaryObjectConfidence ?: hypothesis.confidence,
            itemType = hypothesis.itemType,
            itemTypeLabel = hypothesis.itemTypeLabel,
            itemTypeConfidence = hypothesis.itemTypeConfidence ?: hypothesis.confidence,
            brand = hypothesis.brand,
            brandConfidence = hypothesis.brandConfidence ?: hypothesis.confidence,
            brandSource = hypothesis.brandSource,
            rawBrandCandidates = emptyList(),
            family = hypothesis.family,
            familyConfidence = hypothesis.familyConfidence ?: hypothesis.confidence,
            familySource = hypothesis.familySource,
            rawFamilyCandidates = emptyList(),
            model = hypothesis.model,
            modelConfidence = hypothesis.modelConfidence ?: hypothesis.confidence,
            modelSource = hypothesis.modelSource,
            rawModelCandidates = hypothesis.modelCandidates,
            color = hypothesis.color,
            colorConfidence = hypothesis.colorConfidence ?: hypothesis.confidence,
            condition = hypothesis.condition,
            conditionConfidence = hypothesis.conditionConfidence ?: hypothesis.confidence,
            title = hypothesis.title,
            rawAttributes = hypothesis.attributes,
            rawReasonCodes = hypothesis.reasonCodes,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            groundingPacket = groundingPacket,
        ) ?: return null
        return VisualSearchHypothesis(
            rank = hypothesis.rank ?: fallbackRank,
            confidence = hypothesis.confidence?.coerceIn(0f, 1f) ?: projection.categoryConfidence,
            projection = projection,
            missingEvidence = sanitizeEvidenceCodes(hypothesis.missingEvidence),
            reasonCodes = projection.reasonCodes,
        )
    }

    private suspend fun buildAgentHypothesis(
        hypothesis: YandexAgentHypothesis,
        fallbackRank: Int,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): VisualSearchHypothesis? {
        val chosenCategoryCode = resolveChosenCategoryCode(
            rawHint = hypothesis.categoryGuess,
            request = request,
            requestedCategoryCode = requestedCategoryCode,
            groundingPacket = groundingPacket,
        )
            ?.takeUnless { categoryCode ->
                shouldDropSceneCategory(
                    categoryCode = categoryCode,
                    rawCategoryHint = hypothesis.categoryGuess,
                    title = hypothesis.titleGuess,
                    primaryObject = null,
                    request = request,
                )
            }
        val effectiveSpec = chosenCategoryCode?.let { categoryCode ->
            catalogRepository.getCategoryEffectiveSpec(categoryCode = categoryCode)
        }
        val attributes = hypothesis.attributes
            .mapNotNull { attribute -> attribute.toDomainAttribute(effectiveSpec?.allAttributes().orEmpty()) }
        val reasonCodes = buildList {
            addAll(hypothesis.reasonCodes.map { it.trim() }.filter { it.isNotEmpty() })
            hypothesis.familyGuess
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add("AI_FAMILY_HINT_PRESENT") }
        }.distinct()
        val projection = VisualSearchCandidateProjection(
            categoryCode = chosenCategoryCode,
            categoryConfidence = chosenCategoryCode?.let { hypothesis.confidence?.coerceIn(0f, 1f) },
            brand = hypothesis.brandGuess.toCandidateValue(hypothesis.confidence, source = "VISUAL_PATTERN"),
            family = hypothesis.familyGuess.toCandidateValue(hypothesis.confidence, source = "VISUAL_PATTERN"),
            model = hypothesis.modelGuess.toCandidateValue(hypothesis.confidence, source = "VISUAL_PATTERN"),
        modelCandidates = hypothesis.modelGuess
            .toCandidateValue(hypothesis.confidence, source = "VISUAL_PATTERN")
            ?.let(::listOf)
            .orEmpty(),
        color = hypothesis.colorGuess.toCandidateValue(hypothesis.confidence),
            condition = hypothesis.conditionGuess.toCandidateValue(hypothesis.confidence),
            attributes = attributes,
            title = sanitizeDraftTitle(
                title = hypothesis.titleGuess,
                primaryObject = null,
                primaryObjectConfidence = hypothesis.confidence,
                categoryCode = chosenCategoryCode,
            ),
            reasonCodes = reasonCodes,
        )
        return VisualSearchHypothesis(
            rank = hypothesis.rank ?: fallbackRank,
            confidence = hypothesis.confidence?.coerceIn(0f, 1f),
            projection = projection,
            missingEvidence = sanitizeEvidenceCodes(hypothesis.missingEvidence),
            reasonCodes = reasonCodes,
        )
    }

    private fun buildRankedHypotheses(
        directProjection: VisualSearchCandidateProjection?,
        directConfidence: Float?,
        directReasonCodes: List<String>,
        directMissingEvidence: List<String>,
        hypotheses: List<VisualSearchHypothesis>,
    ): List<VisualSearchHypothesis> = buildList {
        addAll(hypotheses)
        if (hypotheses.isEmpty()) {
            directProjection?.let { projection ->
                add(
                    VisualSearchHypothesis(
                        rank = 1,
                        confidence = directConfidence?.coerceIn(0f, 1f) ?: projection.categoryConfidence,
                        projection = projection,
                        missingEvidence = sanitizeEvidenceCodes(directMissingEvidence),
                        reasonCodes = (directReasonCodes + projection.reasonCodes).distinct(),
                    ),
                )
            }
        }
    }
        .distinctBy { hypothesis -> hypothesis.signature() }
        .sortedWith(compareBy<VisualSearchHypothesis> { it.rank ?: Int.MAX_VALUE }.thenByDescending { it.confidence ?: 0f })
        .take(2)

    private suspend fun resolveChosenCategoryCode(
        rawHint: String?,
        request: VisualSearchNormalizeDraftRequest,
        requestedCategoryCode: String?,
        groundingPacket: GroundingPacket,
    ): String? {
        val allowedCategoryCodes = groundingPacket.candidateCategories
            .map { it.code.uppercase(Locale.ROOT) }
            .toSet()
        val constrainedMatch = resolveCategoryCodeFromHint(
            rawHint = rawHint,
            locale = request.locale,
            allowedCodes = allowedCategoryCodes,
        )
        if (constrainedMatch != null) return constrainedMatch
        if (groundingPacket.broadFallbackCategories) {
            resolveCategoryCodeFromHint(
                rawHint = rawHint,
                locale = request.locale,
                allowedCodes = emptySet(),
            )?.let { return it }
        }
        return requestedCategoryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { allowedCategoryCodes.isEmpty() || it in allowedCategoryCodes }
    }

    private fun sanitizeEvidenceCodes(rawCodes: List<String>): List<String> =
        rawCodes.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun sanitizeDraftTitle(
        title: String?,
        primaryObject: String?,
        primaryObjectConfidence: Float?,
        categoryCode: String?,
    ): String? {
        val safeTitle = title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { text -> isSafePrimaryObjectText(text) }
        if (safeTitle != null) return safeTitle
        return primaryObject
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { text -> isSafePrimaryObjectText(text) }
            ?.takeIf { (primaryObjectConfidence ?: 0f) >= 0.45f || !categoryCode.isNullOrBlank() }
    }

    private fun shouldDropSceneCategory(
        categoryCode: String,
        rawCategoryHint: String?,
        title: String?,
        primaryObject: String?,
        request: VisualSearchNormalizeDraftRequest,
    ): Boolean {
        if (!categoryCode.equals("HOME.KITCHEN_DINING", ignoreCase = true)) return false
        if (!request.manualCategoryCode.isNullOrBlank()) return false
        val evidence = buildList {
            add(rawCategoryHint.orEmpty())
            add(title.orEmpty())
            add(primaryObject.orEmpty())
            add(request.preflightSignals.objectLabel.orEmpty())
            addAll(request.preflightSignals.ocrTextHints)
        }
        if (evidence.any(::containsSpecificKitchenObject)) return false
        return evidence.any(::isWeakSceneObjectText) ||
            request.preflightSignals.imageLabelHints.any(::isWeakSceneObjectText) ||
            !primaryObject.isNullOrBlank()
    }

    private fun isSafePrimaryObjectText(raw: String): Boolean =
        !isWeakSceneObjectText(raw)

    private fun isWeakSceneObjectText(raw: String): Boolean {
        val normalized = normalizeVisualSearchObjectText(raw)
        if (normalized.isBlank()) return true
        if (normalized in weakSceneObjectTitles) return true
        return weakSceneObjectTokens.any { token -> normalized.contains(token) }
    }

    private fun containsSpecificKitchenObject(raw: String): Boolean {
        val normalized = normalizeVisualSearchObjectText(raw)
        return specificKitchenObjectTerms.any { term -> normalized.contains(term) }
    }

    private fun normalizeVisualSearchObjectText(raw: String): String =
        SearchTextNormalizer.normalizeKey(raw)
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()

    private fun String?.toCandidateValue(
        confidence: Float?,
        source: String? = null,
    ): VisualSearchCandidateValue? =
        this?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { text ->
                VisualSearchCandidateValue(
                    text = text,
                    confidence = confidence?.coerceIn(0f, 1f),
                    source = normalizeIdentitySource(source),
                )
            }

    private fun mapRouterVisibleBrandSource(raw: String?): String? =
        when (normalizeIdentitySource(raw)) {
            "TEXT_ON_PRODUCT", "TAG_LABEL", "PRIMARY_PACKAGING" -> "TEXT_EXACT"
            "LOGO_ON_PRODUCT" -> "LOGO_EXACT"
            "USER_HINT" -> "USER_HINT"
            else -> normalizeIdentitySource(raw)
        }

    private fun isOtherItemTypeText(raw: String): Boolean =
        SearchTextNormalizer.normalizeKey(raw)
            .replace('-', '_')
            .replace(' ', '_')
            .let { normalized -> normalized == "other" || normalized == "unknown" || normalized == "n_a" }

    private fun buildItemTypeCandidate(
        itemType: String?,
        itemTypeLabel: String?,
        confidence: Float?,
        title: String?,
        primaryObject: String?,
    ): VisualSearchCandidateValue? {
        val label = listOf(itemTypeLabel, itemType, title, primaryObject)
            .firstNotNullOfOrNull { raw ->
                raw?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.takeIf(::isSafePrimaryObjectText)
            }
            ?: return null
        val score = confidence?.coerceIn(0f, 1f)
        if ((score ?: 0.6f) < 0.42f) return null
        return VisualSearchCandidateValue(
            text = label.take(80),
            confidence = score,
            source = "ITEM_TYPE",
        )
    }

    private fun buildModelCandidates(
        primaryModel: String?,
        primaryConfidence: Float?,
        primarySource: String?,
        rawCandidates: List<YandexDraftModelCandidate>,
        acceptedModel: String?,
    ): List<VisualSearchCandidateValue> = buildList {
        rawCandidates.forEach { candidate ->
            candidate.model
                .toCandidateValue(candidate.confidence, candidate.source)
                ?.let(::add)
        }
        primaryModel
            ?.takeIf { raw -> !raw.equals(acceptedModel, ignoreCase = true) }
            .toCandidateValue(primaryConfidence, primarySource)
            ?.let(::add)
    }
        .filterNot { candidate -> candidate.text.equals(acceptedModel, ignoreCase = true) }
        .distinctBy { candidate -> SearchTextNormalizer.normalizeKey(candidate.text) }
        .sortedByDescending { candidate -> candidate.confidence ?: 0f }
        .take(2)

    private fun List<VisualSearchCandidateValue>.filterIdentityCandidates(
        groundingPacket: GroundingPacket,
        field: IdentityField,
        reasonCodes: List<String>,
        acceptedValue: String?,
    ): List<VisualSearchCandidateValue> =
        mapNotNull { candidate ->
            val accepted = candidate.text.takeIfIdentitySupported(
                groundingPacket = groundingPacket,
                confidence = candidate.confidence,
                source = candidate.source,
                field = field,
                reasonCodes = reasonCodes,
            ) ?: return@mapNotNull null
            candidate.copy(text = accepted)
        }
            .filterNot { candidate -> candidate.text.equals(acceptedValue, ignoreCase = true) }
            .distinctBy { candidate -> SearchTextNormalizer.normalizeKey(candidate.text) }
            .sortedByDescending { candidate -> candidate.confidence ?: 0f }
            .take(2)

    private fun String?.takeIfIdentitySupported(
        groundingPacket: GroundingPacket,
        confidence: Float?,
        source: String?,
        field: IdentityField,
        reasonCodes: List<String>,
    ): String? {
        val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (isGenericIdentityAnchor(value)) return null
        if (!config.aiRequireHintEvidenceForIdentity) return value
        val normalizedSource = normalizeIdentitySource(source)
        return value.takeIf {
            groundingPacket.supportsIdentityValue(it) ||
                visualIdentityAnchorAllowed(
                    field = field,
                    source = normalizedSource,
                    confidence = confidence,
                ) ||
                strongVisibleIdentityEvidence(
                    confidence = confidence,
                    reasonCodes = reasonCodes,
                )
        }
    }

    private fun visualIdentityAnchorAllowed(
        field: IdentityField,
        source: String?,
        confidence: Float?,
    ): Boolean {
        val normalizedSource = normalizeIdentitySource(source) ?: return false
        val score = confidence ?: 0f
        if (normalizedSource in exactIdentitySources) return true
        return when (field) {
            IdentityField.BRAND,
            IdentityField.FAMILY,
                -> normalizedSource in visualAnchorSources && score >= 0.55f

            IdentityField.MODEL ->
                normalizedSource == "VISUAL_DISTINCTIVE" && score >= 0.88f
        }
    }

    private fun strongVisibleIdentityEvidence(
        confidence: Float?,
        reasonCodes: List<String>,
    ): Boolean {
        if ((confidence ?: 0f) < 0.88f) return false
        val normalizedReasonText = reasonCodes
            .joinToString(" ")
            .let(SearchTextNormalizer::normalizeKey)
        if (normalizedReasonText.isBlank()) return false
        val negativeSignals = listOf(
            "possible",
            "suspected",
            "unclear",
            "insufficient",
            "not readable",
            "no readable",
            "lack",
            "weak",
        )
        if (negativeSignals.any { signal -> normalizedReasonText.contains(signal) }) return false
        val positiveSignals = listOf(
            "visible",
            "readable",
            "logo",
            "label",
            "packaging",
            "text",
            "ocr",
            "model name",
            "brand visible",
            "family name visible",
        )
        return positiveSignals.any { signal -> normalizedReasonText.contains(signal) }
    }

    private fun GroundingPacket.supportsIdentityValue(rawValue: String): Boolean {
        val normalizedValue = SearchTextNormalizer.normalizeKey(rawValue)
        if (normalizedValue.isEmpty()) return false
        val evidenceTexts = buildList {
            addAll(hintTexts)
            barcodeValue?.let(::add)
            objectLabel?.let(::add)
            candidateFamilies
                .filter { card -> card.confidence > 0.5f }
                .forEach { card ->
                    add(card.brand)
                    add(card.family)
                    add(card.normalizedModelText)
                }
            candidateModels
                .filter { card -> card.confidence > 0.5f }
                .forEach { card ->
                    add(card.brand)
                    card.familyCode?.let(::add)
                    add(card.model)
                }
        }
            .map(SearchTextNormalizer::normalizeKey)
            .filter { it.isNotEmpty() }
        return evidenceTexts.any { evidence ->
            evidence == normalizedValue ||
                evidence.contains(normalizedValue)
        }
    }

    private fun YandexDraftPayload.hasDirectSignal(): Boolean =
        !primaryObject.isNullOrBlank() ||
            !itemType.isNullOrBlank() ||
            !itemTypeLabel.isNullOrBlank() ||
            !title.isNullOrBlank() ||
            !categoryCode.isNullOrBlank() ||
            !brand.isNullOrBlank() ||
            !family.isNullOrBlank() ||
            !model.isNullOrBlank() ||
            modelCandidates.isNotEmpty() ||
            !color.isNullOrBlank() ||
            !condition.isNullOrBlank() ||
            attributes.isNotEmpty() ||
            reasonCodes.isNotEmpty()

    private fun VisualSearchHypothesis.signature(): String = buildString {
        append(projection.categoryCode.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.itemType?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.brand?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.family?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.model?.text.orEmpty().trim().lowercase(Locale.ROOT))
        append('|')
        append(projection.title.orEmpty().trim().lowercase(Locale.ROOT))
    }

    private fun <T> T?.orEmptyList(selector: (T) -> List<String>): List<String> =
        this?.let(selector).orEmpty()

    private suspend fun resolveCategoryCodeFromHint(
        rawHint: String?,
        locale: String?,
        allowedCodes: Set<String> = emptySet(),
    ): String? = categoryHintResolver.resolveCategoryCode(
        rawHint = rawHint,
        locale = locale,
        allowedCodes = allowedCodes,
    )

    private fun VisualSearchNormalizationDraft.withProvider(
        target: YandexAiExecutionTarget,
    ): VisualSearchNormalizationDraft = copy(
        providerName = when {
            isRouterMode() && config.provider == VisualSearchAiProvider.GEMINI -> "visual_search_router_gemini"
            isRouterMode() && config.provider == VisualSearchAiProvider.OPENAI -> "visual_search_router_openai"
            isRouterMode() -> "visual_search_router"
            target.usesSavedAgent -> "yandex_ai_studio_agent"
            config.provider == VisualSearchAiProvider.GEMINI -> "gemini_openai_compat"
            config.provider == VisualSearchAiProvider.OPENAI -> "openai_chat_completions"
            else -> "yandex_ai_studio"
        },
        providerSchemaVersion = when {
            isRouterMode() && config.provider == VisualSearchAiProvider.GEMINI -> "visual-search-router/gemini/${target.key}"
            isRouterMode() && config.provider == VisualSearchAiProvider.OPENAI -> "visual-search-router/openai/${target.key}"
            isRouterMode() -> "visual-search-router/yandex/${target.key}"
            target.usesSavedAgent -> "visual-search/yandex-agent/${target.key}"
            config.provider == VisualSearchAiProvider.GEMINI -> "visual-search/gemini/${target.key}"
            config.provider == VisualSearchAiProvider.OPENAI -> "visual-search/openai/${target.key}"
            else -> "visual-search/yandex-draft/${target.key}"
        },
    )

    private fun <T> decodeWithRepair(
        serializer: KSerializer<T>,
        responseBody: String,
        sessionId: String,
        logCode: String,
    ): T? {
        decodeCandidates(responseBody).forEach { candidate ->
            runCatching {
                json.decodeFromString(serializer, candidate)
            }.getOrNull()?.let { return it }
        }
        logger.info(
            "visualsearch.normalize.{} session={} message={}",
            logCode,
            sessionId,
            "unparseable_json_after_repair",
        )
        return null
    }

    private fun decodeCandidates(responseBody: String): List<String> {
        val normalized = responseBody
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        val candidates = linkedSetOf<String>()
        candidates += normalized
        extractJsonObject(normalized)?.let(candidates::add)
        closeJsonDelimiters(normalized)?.let(candidates::add)
        extractJsonObject(normalized)?.let { extracted ->
            closeJsonDelimiters(extracted)?.let(candidates::add)
        }
        return candidates
            .map(::stripTrailingCommas)
            .map { candidate -> candidate.trim() }
            .filter { candidate -> candidate.startsWith("{") && candidate.endsWith("}") }
            .distinct()
    }

    private fun extractJsonObject(text: String): String? {
        val firstBrace = text.indexOf('{')
        if (firstBrace < 0) return null
        val lastBrace = text.lastIndexOf('}')
        if (lastBrace <= firstBrace) return text.substring(firstBrace).trim().takeIf { it.isNotEmpty() }
        return text.substring(firstBrace, lastBrace + 1).trim()
    }

    private fun closeJsonDelimiters(text: String): String? {
        val source = extractJsonObject(text) ?: text.trim()
        if (!source.startsWith("{")) return null
        val result = StringBuilder(source.length + 8).append(source)
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaping = false
        source.forEach { ch ->
            if (escaping) {
                escaping = false
                return@forEach
            }
            if (ch == '\\') {
                escaping = true
                return@forEach
            }
            if (ch == '"') {
                inString = !inString
                return@forEach
            }
            if (inString) return@forEach
            when (ch) {
                '{', '[' -> stack.addLast(ch)
                '}' -> if (stack.lastOrNull() == '{') stack.removeLast()
                ']' -> if (stack.lastOrNull() == '[') stack.removeLast()
            }
        }
        if (inString) {
            result.append('"')
        }
        while (stack.isNotEmpty()) {
            result.append(
                when (stack.removeLast()) {
                    '{' -> '}'
                    '[' -> ']'
                    else -> ""
                },
            )
        }
        return result.toString().trim()
    }

    private fun stripTrailingCommas(text: String): String =
        text.replace(Regex(",(\\s*[}\\]])"), "$1")

    private fun YandexDraftAttribute.toDomainAttribute(attributeSpecs: List<CatalogAttributeSpec>): VisualSearchCandidateAttribute? {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) return null
        val spec = attributeSpecs.firstOrNull { it.code.equals(normalizedCode, ignoreCase = true) }
        val typedValue = when (kind?.trim()?.lowercase(Locale.ROOT)) {
            "bool", "boolean" -> bool?.let(TypedAttributeValue::Bool)
            "number", "int", "decimal" -> number?.let(TypedAttributeValue::Number)
            else -> when {
                bool != null -> TypedAttributeValue.Bool(bool)
                number != null -> TypedAttributeValue.Number(number)
                !text.isNullOrBlank() -> TypedAttributeValue.Text(text.trim())
                else -> null
            }
        } ?: return null
        return VisualSearchCandidateAttribute(
            code = spec?.code ?: normalizedCode,
            value = typedValue,
            confidence = confidence?.coerceIn(0f, 1f),
        )
    }

    private fun YandexAgentAttribute.toDomainAttribute(attributeSpecs: List<CatalogAttributeSpec>): VisualSearchCandidateAttribute? {
        val attributeName = name?.trim().orEmpty()
        val attributeValue = value?.trim().orEmpty()
        if (attributeName.isEmpty() || attributeValue.isEmpty()) return null
        val spec = attributeSpecs.firstOrNull { candidate ->
            candidate.code.equals(attributeName, ignoreCase = true) ||
                candidate.title.equals(attributeName, ignoreCase = true)
        }
        return VisualSearchCandidateAttribute(
            code = spec?.code ?: attributeName,
            value = TypedAttributeValue.Text(attributeValue),
            confidence = confidence?.coerceIn(0f, 1f),
        )
    }
}

private enum class IdentityField {
    BRAND,
    FAMILY,
    MODEL,
}

private val exactIdentitySources = setOf(
    "TEXT_EXACT",
    "LOGO_EXACT",
    "BARCODE_EXACT",
    "USER_HINT",
    "CATALOG_SHORTLIST",
)

private val visualAnchorSources = setOf(
    "VISUAL_DISTINCTIVE",
    "VISUAL_PATTERN",
)

private val genericIdentityAnchors = setOf(
    "accessory",
    "battery",
    "bracelet",
    "camera",
    "candy",
    "charger",
    "chocolate",
    "coffee",
    "console",
    "cutlery",
    "drink",
    "grocery",
    "headphones",
    "honey",
    "hoodie",
    "jacket",
    "jeans",
    "laptop",
    "mouse",
    "pants",
    "phone",
    "product",
    "shoe",
    "shoes",
    "shirt",
    "smartphone",
    "sneakers",
    "speaker",
    "suit",
    "sweater",
    "tea",
    "thermometer",
    "watch",
    "wrist watch",
)

private val weakSceneObjectTitles = setOf(
    "tableware",
    "cutlery",
    "dishware",
    "kitchenware",
    "serveware",
    "flatware",
    "silverware",
    "utensil",
    "utensils",
    "посуда",
    "посуда и кухня",
    "кухня",
    "wall",
    "room",
    "home",
    "interior",
    "furniture",
    "screen",
    "display",
)

private val weakSceneObjectTokens = setOf(
    "tableware",
    "cutlery",
    "dishware",
    "kitchenware",
    "serveware",
    "flatware",
    "silverware",
    "utensil",
    "посуда",
    "кухн",
    "interior",
    "furniture",
)

private val specificKitchenObjectTerms = setOf(
    "knife",
    "fork",
    "spoon",
    "mug",
    "cup",
    "plate",
    "cookware",
    "pan",
    "pot",
    "kettle",
    "нож",
    "вилка",
    "ложка",
    "кружка",
    "чашка",
    "тарелка",
    "сковорода",
    "кастрюля",
    "чайник",
)

private fun normalizeIdentitySource(raw: String?): String? =
    raw
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.replace('-', '_')
        ?.replace(' ', '_')
        ?.takeIf { it.isNotEmpty() }

private fun isGenericIdentityAnchor(raw: String): Boolean {
    val normalized = SearchTextNormalizer.normalizeKey(raw)
    if (normalized.isEmpty()) return true
    return normalized in genericIdentityAnchors
}

@Serializable
private data class GroundingPacket(
    val locale: String,
    val intent: String,
    val captureMode: String? = null,
    val requestedCategoryCode: String? = null,
    val hintTexts: List<String> = emptyList(),
    val barcodeValue: String? = null,
    val objectLabel: String? = null,
    val candidateCategories: List<GroundingCategoryCard> = emptyList(),
    val broadFallbackCategories: Boolean = false,
    val candidateFamilies: List<GroundingFamilyCard> = emptyList(),
    val candidateModels: List<GroundingModelCard> = emptyList(),
)

private fun GroundingPacket.toRouterFirstPassPacket(): GroundingPacket =
    copy(
        hintTexts = hintTexts.take(3),
        objectLabel = objectLabel?.take(80),
        candidateCategories = candidateCategories
            .map { category -> category.copy(attributes = emptyList()) }
            .take(24),
        candidateFamilies = emptyList(),
        candidateModels = emptyList(),
    )

@Serializable
private data class RouterResultPacket(
    @SerialName("provider_schema_version")
    val providerSchemaVersion: String? = null,
    val confidence: Float? = null,
    @SerialName("category_code")
    val categoryCode: String? = null,
    @SerialName("category_confidence")
    val categoryConfidence: Float? = null,
    @SerialName("item_type")
    val itemType: String? = null,
    @SerialName("item_type_confidence")
    val itemTypeConfidence: Float? = null,
    val brand: String? = null,
    @SerialName("brand_confidence")
    val brandConfidence: Float? = null,
    @SerialName("brand_source")
    val brandSource: String? = null,
    @SerialName("visual_attributes")
    val visualAttributes: List<RouterVisualAttributePacket> = emptyList(),
    val title: String? = null,
    @SerialName("needs_more_photos")
    val needsMorePhotos: Boolean = false,
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
)

private fun VisualSearchNormalizationDraft.toRouterResultPacket(): RouterResultPacket =
    RouterResultPacket(
        providerSchemaVersion = providerSchemaVersion,
        confidence = confidence,
        categoryCode = projection.categoryCode,
        categoryConfidence = projection.categoryConfidence,
        itemType = projection.itemType?.text,
        itemTypeConfidence = projection.itemType?.confidence,
        brand = projection.brand?.text,
        brandConfidence = projection.brand?.confidence,
        brandSource = projection.brand?.source,
        visualAttributes = projection.attributes.map { attribute -> attribute.toRouterVisualAttributePacket() },
        title = projection.title,
        needsMorePhotos = needsMorePhotos,
        missingEvidence = missingEvidence,
        reasonCodes = reasonCodes,
    )

@Serializable
private data class RouterVisualAttributePacket(
    val code: String,
    val kind: String? = null,
    val text: String? = null,
    val number: Double? = null,
    val bool: Boolean? = null,
    val confidence: Float? = null,
)

private fun VisualSearchCandidateAttribute.toRouterVisualAttributePacket(): RouterVisualAttributePacket {
    val typedValue = value
    return when (typedValue) {
        is TypedAttributeValue.Bool -> RouterVisualAttributePacket(
            code = code,
            kind = "bool",
            bool = typedValue.value,
            confidence = confidence,
        )

        is TypedAttributeValue.Number -> RouterVisualAttributePacket(
            code = code,
            kind = "number",
            number = typedValue.value,
            confidence = confidence,
        )

        is TypedAttributeValue.Text -> RouterVisualAttributePacket(
            code = code,
            kind = "text",
            text = typedValue.value,
            confidence = confidence,
        )
    }
}

@Serializable
private data class GroundingCategoryCard(
    val code: String,
    val title: String,
    val segment: String,
    val parentCode: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
    val label: String? = null,
    val attributes: List<GroundingAttributeCard> = emptyList(),
)

private data class PreflightCategoryEntry(
    val category: Category,
    val confidence: Float?,
    val source: String?,
    val label: String?,
)

@Serializable
private data class GroundingAttributeCard(
    val code: String,
    val title: String,
    val dataType: String,
    val allowedValues: List<String> = emptyList(),
)

@Serializable
private data class GroundingFamilyCard(
    val familyCode: String,
    val defaultCategoryCode: String,
    val brand: String,
    val family: String,
    val confidence: Float,
    val normalizedModelText: String,
)

@Serializable
private data class GroundingModelCard(
    val modelCode: String,
    val defaultCategoryCode: String,
    val brand: String,
    val familyCode: String? = null,
    val model: String,
    val confidence: Float,
)

@Serializable
private data class VisualSearchRouterPayload(
    @SerialName("route_status")
    val routeStatus: String,
    @SerialName("identity_mode")
    val identityMode: String,
    val confidence: Float? = null,
    @SerialName("primary_object")
    val primaryObject: String? = null,
    @SerialName("primary_object_confidence")
    val primaryObjectConfidence: Float? = null,
    @SerialName("category_code")
    val categoryCode: String? = null,
    @SerialName("category_confidence")
    val categoryConfidence: Float? = null,
    @SerialName("browse_node_code")
    val browseNodeCode: String? = null,
    @SerialName("browse_node_confidence")
    val browseNodeConfidence: Float? = null,
    @SerialName("item_type")
    val itemType: String? = null,
    @SerialName("item_type_label")
    val itemTypeLabel: String? = null,
    @SerialName("item_type_confidence")
    val itemTypeConfidence: Float? = null,
    @SerialName("free_text_type")
    val freeTextType: String? = null,
    @SerialName("visible_brand")
    val visibleBrand: String? = null,
    @SerialName("visible_brand_confidence")
    val visibleBrandConfidence: Float? = null,
    @SerialName("visible_brand_source")
    val visibleBrandSource: String? = null,
    @SerialName("brand_candidates")
    val brandCandidates: List<VisualSearchRouterBrandCandidate> = emptyList(),
    @SerialName("visual_attributes")
    val visualAttributes: List<YandexDraftAttribute> = emptyList(),
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
)

@Serializable
private data class VisualSearchRouterBrandCandidate(
    val brand: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
)

@Serializable
private data class VisualSearchIdentityPayload(
    @SerialName("identity_status")
    val identityStatus: String,
    val confidence: Float? = null,
    @SerialName("route_conflict")
    val routeConflict: Boolean = false,
    @SerialName("primary_object")
    val primaryObject: String? = null,
    @SerialName("primary_object_confidence")
    val primaryObjectConfidence: Float? = null,
    @SerialName("category_code")
    val categoryCode: String? = null,
    @SerialName("category_confidence")
    val categoryConfidence: Float? = null,
    @SerialName("browse_node_code")
    val browseNodeCode: String? = null,
    @SerialName("item_type")
    val itemType: String? = null,
    @SerialName("item_type_label")
    val itemTypeLabel: String? = null,
    @SerialName("item_type_confidence")
    val itemTypeConfidence: Float? = null,
    @SerialName("visible_brand")
    val visibleBrand: String? = null,
    @SerialName("visible_brand_confidence")
    val visibleBrandConfidence: Float? = null,
    @SerialName("visible_brand_source")
    val visibleBrandSource: String? = null,
    val brand: String? = null,
    @SerialName("brand_confidence")
    val brandConfidence: Float? = null,
    @SerialName("brand_source")
    val brandSource: String? = null,
    @SerialName("brand_candidates")
    val brandCandidates: List<VisualSearchIdentityBrandCandidate> = emptyList(),
    val family: String? = null,
    @SerialName("family_confidence")
    val familyConfidence: Float? = null,
    @SerialName("family_source")
    val familySource: String? = null,
    @SerialName("family_candidates")
    val familyCandidates: List<VisualSearchIdentityFamilyCandidate> = emptyList(),
    val model: String? = null,
    @SerialName("model_confidence")
    val modelConfidence: Float? = null,
    @SerialName("model_source")
    val modelSource: String? = null,
    @SerialName("model_candidates")
    val modelCandidates: List<VisualSearchIdentityModelCandidate> = emptyList(),
    val color: String? = null,
    @SerialName("color_confidence")
    val colorConfidence: Float? = null,
    @SerialName("visual_attributes")
    val visualAttributes: List<YandexDraftAttribute> = emptyList(),
    @SerialName("search_title")
    val searchTitle: String? = null,
    @SerialName("needs_more_photos")
    val needsMorePhotos: Boolean = false,
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
)

@Serializable
private data class VisualSearchIdentityModelCandidate(
    val model: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
)

@Serializable
private data class VisualSearchIdentityBrandCandidate(
    val brand: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
)

@Serializable
private data class VisualSearchIdentityFamilyCandidate(
    val family: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
)

@Serializable
private data class YandexDraftPayload(
    @SerialName("needs_retake")
    val needsRetake: Boolean = false,
    @SerialName("needs_more_photos")
    val needsMorePhotos: Boolean = false,
    @SerialName("retake_reason")
    val retakeReason: String? = null,
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    val confidence: Float? = null,
    @SerialName("primary_object")
    val primaryObject: String? = null,
    @SerialName("primary_object_confidence")
    val primaryObjectConfidence: Float? = null,
    @SerialName("item_type")
    val itemType: String? = null,
    @SerialName("item_type_label")
    val itemTypeLabel: String? = null,
    @SerialName("item_type_confidence")
    val itemTypeConfidence: Float? = null,
    val title: String? = null,
    @SerialName("category_code")
    val categoryCode: String? = null,
    @SerialName("category_confidence")
    val categoryConfidence: Float? = null,
    @SerialName("family_code")
    val familyCode: String? = null,
    val family: String? = null,
    @SerialName("family_confidence")
    val familyConfidence: Float? = null,
    @SerialName("family_source")
    val familySource: String? = null,
    val brand: String? = null,
    @SerialName("brand_confidence")
    val brandConfidence: Float? = null,
    @SerialName("brand_source")
    val brandSource: String? = null,
    val model: String? = null,
    @SerialName("model_confidence")
    val modelConfidence: Float? = null,
    @SerialName("model_source")
    val modelSource: String? = null,
    @SerialName("model_candidates")
    val modelCandidates: List<YandexDraftModelCandidate> = emptyList(),
    val color: String? = null,
    @SerialName("color_confidence")
    val colorConfidence: Float? = null,
    val condition: String? = null,
    @SerialName("condition_confidence")
    val conditionConfidence: Float? = null,
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
    val attributes: List<YandexDraftAttribute> = emptyList(),
    val hypotheses: List<YandexDraftHypothesis> = emptyList(),
)

@Serializable
private data class YandexDraftModelCandidate(
    val model: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
)

@Serializable
private data class YandexDraftAttribute(
    val code: String,
    val kind: String? = null,
    val text: String? = null,
    val number: Double? = null,
    val bool: Boolean? = null,
    val confidence: Float? = null,
)

@Serializable
private data class YandexDraftHypothesis(
    val rank: Int? = null,
    val confidence: Float? = null,
    @SerialName("primary_object")
    val primaryObject: String? = null,
    @SerialName("primary_object_confidence")
    val primaryObjectConfidence: Float? = null,
    @SerialName("item_type")
    val itemType: String? = null,
    @SerialName("item_type_label")
    val itemTypeLabel: String? = null,
    @SerialName("item_type_confidence")
    val itemTypeConfidence: Float? = null,
    val title: String? = null,
    @SerialName("category_code")
    val categoryCode: String? = null,
    @SerialName("category_confidence")
    val categoryConfidence: Float? = null,
    val brand: String? = null,
    @SerialName("brand_confidence")
    val brandConfidence: Float? = null,
    @SerialName("brand_source")
    val brandSource: String? = null,
    val family: String? = null,
    @SerialName("family_confidence")
    val familyConfidence: Float? = null,
    @SerialName("family_source")
    val familySource: String? = null,
    val model: String? = null,
    @SerialName("model_confidence")
    val modelConfidence: Float? = null,
    @SerialName("model_source")
    val modelSource: String? = null,
    @SerialName("model_candidates")
    val modelCandidates: List<YandexDraftModelCandidate> = emptyList(),
    val color: String? = null,
    @SerialName("color_confidence")
    val colorConfidence: Float? = null,
    val condition: String? = null,
    @SerialName("condition_confidence")
    val conditionConfidence: Float? = null,
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    val attributes: List<YandexDraftAttribute> = emptyList(),
)

@Serializable
private data class YandexAgentVisionPayload(
    @SerialName("needs_more_photos")
    val needsMorePhotos: Boolean = false,
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    @SerialName("sale_object_candidates")
    val saleObjectCandidates: List<YandexAgentSaleObjectCandidate> = emptyList(),
    val hypotheses: List<YandexAgentHypothesis> = emptyList(),
    val ambiguities: List<String> = emptyList(),
)

@Serializable
private data class YandexAgentHypothesis(
    val rank: Int? = null,
    @SerialName("title_guess")
    val titleGuess: String? = null,
    @SerialName("brand_guess")
    val brandGuess: String? = null,
    @SerialName("category_guess")
    val categoryGuess: String? = null,
    @SerialName("family_guess")
    val familyGuess: String? = null,
    @SerialName("model_guess")
    val modelGuess: String? = null,
    @SerialName("color_guess")
    val colorGuess: String? = null,
    @SerialName("condition_guess")
    val conditionGuess: String? = null,
    val confidence: Float? = null,
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    val attributes: List<YandexAgentAttribute> = emptyList(),
)

@Serializable
private data class YandexAgentSaleObjectCandidate(
    @SerialName("title_guess")
    val titleGuess: String? = null,
    @SerialName("brand_guess")
    val brandGuess: String? = null,
    @SerialName("category_guess")
    val categoryGuess: String? = null,
    @SerialName("family_guess")
    val familyGuess: String? = null,
    @SerialName("model_guess")
    val modelGuess: String? = null,
    @SerialName("color_guess")
    val colorGuess: String? = null,
    @SerialName("condition_guess")
    val conditionGuess: String? = null,
    val confidence: Float? = null,
    @SerialName("reason_codes")
    val reasonCodes: List<String> = emptyList(),
    @SerialName("missing_evidence")
    val missingEvidence: List<String> = emptyList(),
    val attributes: List<YandexAgentAttribute> = emptyList(),
)

@Serializable
private data class YandexAgentAttribute(
    val name: String? = null,
    val value: String? = null,
    val confidence: Float? = null,
)

private fun YandexAgentSaleObjectCandidate.toHypothesis(): YandexAgentHypothesis = YandexAgentHypothesis(
    titleGuess = titleGuess,
    brandGuess = brandGuess,
    categoryGuess = categoryGuess,
    familyGuess = familyGuess,
    modelGuess = modelGuess,
    colorGuess = colorGuess,
    conditionGuess = conditionGuess,
    confidence = confidence,
    reasonCodes = reasonCodes,
    missingEvidence = missingEvidence,
    attributes = attributes,
)
