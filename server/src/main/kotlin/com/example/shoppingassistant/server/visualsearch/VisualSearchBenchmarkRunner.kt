package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEnvelopeStatus
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightCategoryCandidate
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSchemaVersion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectedRegion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.server.ai.AiNormalizationAttempt
import com.example.shoppingassistant.server.ai.AiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.AiTokenUsage
import com.example.shoppingassistant.server.ai.ResourceAiAgentRegistry
import com.example.shoppingassistant.server.ai.AiStructuredContractLoader
import com.example.shoppingassistant.server.ai.yandex.DefaultYandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.catalog.ResourceCatalogRepository
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import com.example.shoppingassistant.server.config.VisualSearchAiProvider
import com.example.shoppingassistant.server.config.VisualSearchConfig
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.system.exitProcess

private val DEFAULT_BENCHMARK_MODELS = listOf(
    "qwen2.5-vl-7b-instruct",
    "deepseek-vl2-tiny",
    "gemma-3-12b-it",
    "gemma-3-27b-it",
)

fun main(args: Array<String>) {
    val manifestPath = args.getOrNull(0)
        ?: error("Usage: VisualSearchBenchmarkRunnerKt <manifest.json> [output.json] [models_csv] [runs]")
    val outputPath = args.getOrNull(1)
    val modelsArg = args.getOrNull(2)
    val runsArg = args.getOrNull(3)?.toIntOrNull()

    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }
    val manifest = json.decodeFromString(
        VisualSearchBenchmarkManifest.serializer(),
        File(manifestPath).readText(Charsets.UTF_8),
    )

    val baseConfig = VisualSearchConfig.fromEnv()
    require(baseConfig.enabled) {
        "Visual search is disabled. Check VISUAL_SEARCH_ENABLED."
    }
    require(baseConfig.providerReady) {
        "Visual search AI is not configured. Check env.local and the selected provider credentials/model."
    }

    val models = parseModels(modelsArg)
        ?: manifest.models.takeIf { it.isNotEmpty() }
        ?: DEFAULT_BENCHMARK_MODELS
    val runs = (runsArg ?: manifest.runs).coerceAtLeast(1)

    val report = runBlocking {
        VisualSearchBenchmarkRunner(
            baseConfig = baseConfig,
            listingVisionConfig = ListingVisionAiConfig.fromEnv(),
        ).run(
            manifest = manifest,
            models = models,
            runs = runs,
        )
    }
    val encoded = json.encodeToString(VisualSearchBenchmarkReport.serializer(), report)
    if (outputPath != null) {
        File(outputPath).writeText(encoded, Charsets.UTF_8)
        println("visual_search_benchmark_written=$outputPath")
    } else {
        println(encoded)
    }
    exitProcess(0)
}

class VisualSearchBenchmarkRunner(
    private val baseConfig: VisualSearchConfig,
    private val listingVisionConfig: ListingVisionAiConfig,
) {
    suspend fun run(
        manifest: VisualSearchBenchmarkManifest,
        models: List<String>,
        runs: Int,
    ): VisualSearchBenchmarkReport {
        require(manifest.samples.isNotEmpty()) { "Manifest must contain at least one sample." }
        val preparedSamples = manifest.samples.map { sample ->
            PreparedBenchmarkSample.from(sample)
        }
        val modelReports = models.map { model ->
            runModel(
                manifest = manifest,
                preparedSamples = preparedSamples,
                model = model,
                runs = runs,
            )
        }
        return VisualSearchBenchmarkReport(
            manifestName = manifest.manifestName,
            runs = runs,
            timeoutMs = baseConfig.activeTimeoutMs,
            contractSnapshot = buildContractSnapshot(baseConfig),
            modelReports = modelReports,
        )
    }

    private suspend fun runModel(
        manifest: VisualSearchBenchmarkManifest,
        preparedSamples: List<PreparedBenchmarkSample>,
        model: String,
        runs: Int,
    ): VisualSearchBenchmarkModelReport {
        val effectiveConfig = when (baseConfig.provider) {
            VisualSearchAiProvider.YANDEX ->
                baseConfig.copy(
                    yandexModel = model,
                    yandexAgentId = null,
                    yandexFallbackAgentIds = emptyList(),
                    yandexAllowModelFallback = false,
                    yandexUseSavedAgent = false,
                )

            VisualSearchAiProvider.GEMINI ->
                baseConfig.copy(
                    geminiModel = model,
                    yandexUseSavedAgent = false,
                    yandexAllowModelFallback = false,
                )

            VisualSearchAiProvider.OPENAI ->
                baseConfig.copy(
                    openAiModel = model,
                    yandexUseSavedAgent = false,
                    yandexAllowModelFallback = false,
                )
        }
        val runReports = mutableListOf<VisualSearchBenchmarkRunReport>()
        repeat(runs) { runIndex ->
            preparedSamples.forEach { sample ->
                runReports += executeSingleRun(
                    config = effectiveConfig,
                    manifest = manifest,
                    sample = sample,
                    runIndex = runIndex + 1,
                )
            }
        }

        val latencies = runReports.mapNotNull { it.endToEndLatencyMs }.sorted()
        val aiLatencies = runReports.mapNotNull { it.aiLatencyMs }.sorted()
        val firstAttemptLatencies = runReports.mapNotNull { it.attemptLatencyMs }.sorted()
        val firstAttemptSuccessCount = runReports.count { it.attemptOutcome == "SUCCESS" }
        val firstAttemptTimeoutCount = runReports.count { it.attemptReason == "TARGET_TIMEOUT" }
        val identityPassCount = runReports.count { it.providerName == "visual_search_identity_openai" }
        val routerOnlyCount = runReports.count { it.providerName == "visual_search_router_openai" }
        val usableRate = runReports.count { it.usableResult }.toDouble() / runReports.size.toDouble()
        val bindSuccessRate = runReports.count { it.bindSuccess }.toDouble() / runReports.size.toDouble()
        val categoryHitRate = runReports.mapNotNull { it.categoryMatch }.averageBooleanOrNull()
        val brandHitRate = runReports.mapNotNull { it.brandMatch }.averageBooleanOrNull()
        val brandAnchorTop1HitRate = runReports.mapNotNull { it.brandAnchorTop1Match }.averageBooleanOrNull()
        val brandAnchorTop2HitRate = runReports.mapNotNull { it.brandAnchorTop2Match }.averageBooleanOrNull()
        val familyHitRate = runReports.mapNotNull { it.familyMatch }.averageBooleanOrNull()
        val familyAnchorTop1HitRate = runReports.mapNotNull { it.familyAnchorTop1Match }.averageBooleanOrNull()
        val familyAnchorTop2HitRate = runReports.mapNotNull { it.familyAnchorTop2Match }.averageBooleanOrNull()
        val richFamilyHitRate = runReports.mapNotNull { it.richFamilyMatch }.averageBooleanOrNull()
        val richFamilyAnchorTop1HitRate = runReports.mapNotNull { it.richFamilyAnchorTop1Match }.averageBooleanOrNull()
        val richFamilyAnchorTop2HitRate = runReports.mapNotNull { it.richFamilyAnchorTop2Match }.averageBooleanOrNull()
        val typeItemAnchorHitRate = runReports.mapNotNull { it.typeItemAnchorMatch }.averageBooleanOrNull()
        val modelHitRate = runReports.mapNotNull { it.modelMatch }.averageBooleanOrNull()
        val modelCandidateTop1HitRate = runReports.mapNotNull { it.modelCandidateTop1Match }.averageBooleanOrNull()
        val modelCandidateTop2HitRate = runReports.mapNotNull { it.modelCandidateTop2Match }.averageBooleanOrNull()
        val coreFillRate = runReports.mapNotNull { it.coreFillRate }.averageOrNull()
        val requiredFillRate = runReports.mapNotNull { it.requiredFillRate }.averageOrNull()
        val avgInputTokens = runReports.mapNotNull { it.inputTokens }.averageLongOrNull()
        val avgOutputTokens = runReports.mapNotNull { it.outputTokens }.averageLongOrNull()
        val avgTotalTokens = runReports.mapNotNull { it.totalTokens }.averageLongOrNull()
        val totalTokensPerSuccessfulBind = runReports
            .filter { it.bindSuccess && it.totalTokens != null }
            .mapNotNull { it.totalTokens }
            .averageDoubleOrNull()

        return VisualSearchBenchmarkModelReport(
            model = model,
            modelUri = effectiveConfig.activeModelRef,
            configSnapshot = buildConfigSnapshot(effectiveConfig),
            invocationCount = runReports.size,
            latencyP50Ms = latencies.percentile(0.50),
            latencyP95Ms = latencies.percentile(0.95),
            aiLatencyP50Ms = aiLatencies.percentile(0.50),
            aiLatencyP95Ms = aiLatencies.percentile(0.95),
            firstAttemptLatencyP50Ms = firstAttemptLatencies.percentile(0.50),
            firstAttemptLatencyP95Ms = firstAttemptLatencies.percentile(0.95),
            firstAttemptSuccessCount = firstAttemptSuccessCount,
            firstAttemptTimeoutCount = firstAttemptTimeoutCount,
            identityPassCount = identityPassCount,
            routerOnlyCount = routerOnlyCount,
            usableRate = usableRate,
            bindSuccessRate = bindSuccessRate,
            categoryHitRate = categoryHitRate,
            brandHitRate = brandHitRate,
            brandAnchorTop1HitRate = brandAnchorTop1HitRate,
            brandAnchorTop2HitRate = brandAnchorTop2HitRate,
            familyHitRate = familyHitRate,
            familyAnchorTop1HitRate = familyAnchorTop1HitRate,
            familyAnchorTop2HitRate = familyAnchorTop2HitRate,
            richFamilyHitRate = richFamilyHitRate,
            richFamilyAnchorTop1HitRate = richFamilyAnchorTop1HitRate,
            richFamilyAnchorTop2HitRate = richFamilyAnchorTop2HitRate,
            typeItemAnchorHitRate = typeItemAnchorHitRate,
            modelHitRate = modelHitRate,
            modelCandidateTop1HitRate = modelCandidateTop1HitRate,
            modelCandidateTop2HitRate = modelCandidateTop2HitRate,
            coreFillRate = coreFillRate,
            requiredFillRate = requiredFillRate,
            avgInputTokens = avgInputTokens,
            avgOutputTokens = avgOutputTokens,
            avgTotalTokens = avgTotalTokens,
            totalTokensPerSuccessfulBind = totalTokensPerSuccessfulBind,
            sampleReports = preparedSamples.map { sample ->
                val sampleRuns = runReports.filter { it.sampleId == sample.sample.sampleId }
                VisualSearchBenchmarkSampleReport(
                    sampleId = sample.sample.sampleId,
                    imagePath = sample.file.absolutePath,
                    contextImagePaths = sample.contextFiles.map { it.absolutePath },
                    expectedCategoryCode = sample.sample.expectedCategoryCode,
                    expectedItemType = sample.sample.expectedItemType,
                    expectedItemTypeAliases = sample.sample.expectedItemTypeAliases,
                    expectedBrand = sample.sample.expectedBrand,
                    expectedFamily = sample.sample.expectedFamily,
                    expectedModel = sample.sample.expectedModel,
                    latencyP50Ms = sampleRuns.mapNotNull { it.endToEndLatencyMs }.sorted().percentile(0.50),
                    latencyP95Ms = sampleRuns.mapNotNull { it.endToEndLatencyMs }.sorted().percentile(0.95),
                    aiLatencyP50Ms = sampleRuns.mapNotNull { it.aiLatencyMs }.sorted().percentile(0.50),
                    aiLatencyP95Ms = sampleRuns.mapNotNull { it.aiLatencyMs }.sorted().percentile(0.95),
                    usableRate = sampleRuns.count { it.usableResult }.toDouble() / sampleRuns.size.toDouble(),
                    bindSuccessRate = sampleRuns.count { it.bindSuccess }.toDouble() / sampleRuns.size.toDouble(),
                    categoryHitRate = sampleRuns.mapNotNull { it.categoryMatch }.averageBooleanOrNull(),
                    brandHitRate = sampleRuns.mapNotNull { it.brandMatch }.averageBooleanOrNull(),
                    brandAnchorTop1HitRate = sampleRuns.mapNotNull { it.brandAnchorTop1Match }.averageBooleanOrNull(),
                    brandAnchorTop2HitRate = sampleRuns.mapNotNull { it.brandAnchorTop2Match }.averageBooleanOrNull(),
                    familyHitRate = sampleRuns.mapNotNull { it.familyMatch }.averageBooleanOrNull(),
                    familyAnchorTop1HitRate = sampleRuns.mapNotNull { it.familyAnchorTop1Match }.averageBooleanOrNull(),
                    familyAnchorTop2HitRate = sampleRuns.mapNotNull { it.familyAnchorTop2Match }.averageBooleanOrNull(),
                    richFamilyHitRate = sampleRuns.mapNotNull { it.richFamilyMatch }.averageBooleanOrNull(),
                    richFamilyAnchorTop1HitRate = sampleRuns.mapNotNull { it.richFamilyAnchorTop1Match }.averageBooleanOrNull(),
                    richFamilyAnchorTop2HitRate = sampleRuns.mapNotNull { it.richFamilyAnchorTop2Match }.averageBooleanOrNull(),
                    typeItemAnchorHitRate = sampleRuns.mapNotNull { it.typeItemAnchorMatch }.averageBooleanOrNull(),
                    modelHitRate = sampleRuns.mapNotNull { it.modelMatch }.averageBooleanOrNull(),
                    modelCandidateTop1HitRate = sampleRuns.mapNotNull { it.modelCandidateTop1Match }.averageBooleanOrNull(),
                    modelCandidateTop2HitRate = sampleRuns.mapNotNull { it.modelCandidateTop2Match }.averageBooleanOrNull(),
                    coreFillRate = sampleRuns.mapNotNull { it.coreFillRate }.averageOrNull(),
                    requiredFillRate = sampleRuns.mapNotNull { it.requiredFillRate }.averageOrNull(),
                    avgInputTokens = sampleRuns.mapNotNull { it.inputTokens }.averageLongOrNull(),
                    avgOutputTokens = sampleRuns.mapNotNull { it.outputTokens }.averageLongOrNull(),
                    avgTotalTokens = sampleRuns.mapNotNull { it.totalTokens }.averageLongOrNull(),
                    totalTokensPerSuccessfulBind = sampleRuns
                        .filter { it.bindSuccess && it.totalTokens != null }
                        .mapNotNull { it.totalTokens }
                        .averageDoubleOrNull(),
                    latestPredictedCategoryCode = sampleRuns.lastOrNull()?.predictedCategoryCode,
                    latestPredictedItemType = sampleRuns.lastOrNull()?.predictedItemType,
                    latestPredictedBrand = sampleRuns.lastOrNull()?.predictedBrand,
                    latestPredictedBrandCandidates = sampleRuns.lastOrNull()?.predictedBrandCandidates.orEmpty(),
                    latestPredictedFamily = sampleRuns.lastOrNull()?.predictedFamily,
                    latestPredictedFamilyCandidates = sampleRuns.lastOrNull()?.predictedFamilyCandidates.orEmpty(),
                    latestPredictedModel = sampleRuns.lastOrNull()?.predictedModel,
                    latestPredictedModelCandidates = sampleRuns.lastOrNull()?.predictedModelCandidates.orEmpty(),
                    latestProviderName = sampleRuns.lastOrNull()?.providerName,
                )
            },
            runs = runReports,
        )
    }

    private suspend fun executeSingleRun(
        config: VisualSearchConfig,
        manifest: VisualSearchBenchmarkManifest,
        sample: PreparedBenchmarkSample,
        runIndex: Int,
    ): VisualSearchBenchmarkRunReport {
        val catalogRepository = ResourceCatalogRepository()
        val contextStore = InMemoryVisualSearchContextStore(config)
        val telemetry = CollectingBenchmarkTelemetry()
        val orchestrator = AiNormalizationOrchestrator(
            agentRegistry = ResourceAiAgentRegistry(
                visualSearchConfig = config,
                listingVisionAiConfig = listingVisionConfig,
            ),
            aiClient = DefaultYandexAiStudioClient(
                retry429MaxAttempts = config.aiRetry429MaxAttempts,
                retry5xxMaxAttempts = config.aiRetry5xxMaxAttempts,
                retryBaseDelayMs = config.aiRetryBaseDelayMs,
                retryMaxDelayMs = config.aiRetryMaxDelayMs,
            ),
            telemetry = telemetry,
        )
        val aiNormalizer = YandexVisualSearchAiDraftNormalizer(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            config = config,
            orchestrator = orchestrator,
        )
        val service = VisualSearchServiceImpl(
            catalogRepository = catalogRepository,
            catalogTaxonomyRepository = catalogRepository,
            contextStore = contextStore,
            config = config,
            aiDraftNormalizer = aiNormalizer,
        )

        val metadata = VisualSearchTransportMetadata(
            visualSessionId = "vs-bench-${UUID.randomUUID()}",
            clientSchemaVersion = VisualSearchSchemaVersion.current,
            catalogDataVersion = CatalogDataVersion.current,
            idempotencyKey = "vs-bench-${sample.asset.sha256}-${config.activeModel}-$runIndex",
        )
        val request = VisualSearchNormalizeDraftRequest(
            asset = sample.asset,
            contextAssets = sample.contextAssets,
            source = sample.sample.source,
            entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
            selectionMode = sample.sample.selectionMode,
            intent = sample.sample.intent,
            selectedRegion = sample.sample.selectedRegion,
            preflightSignals = VisualSearchPreflightSignals(
                reasonCodes = listOf("OFFLINE_BENCHMARK"),
                cheapProjectionReady = true,
                admitServerAi = true,
                captureMode = sample.sample.captureMode,
                exactCategoryCode = sample.sample.preflightExactCategoryCode,
                ocrTextHints = sample.sample.preflightOcrTextHints,
                imageLabelHints = sample.sample.preflightImageLabelHints,
                objectLabel = sample.sample.preflightObjectLabel,
                barcodeValue = sample.sample.preflightBarcodeValue,
                categoryCandidates = sample.sample.preflightCategoryCandidates,
            ),
            manualCategoryCode = sample.sample.manualCategoryCode,
            locale = sample.sample.locale ?: manifest.locale,
        )
        val startedAt = System.nanoTime()
        val normalizeResponse = service.normalizeDraft(
            metadata = metadata,
            request = request,
        )
        val bindResponse = normalizeResponse.draft?.let { draft ->
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = request.intent,
                    source = request.source,
                    selectionMode = request.selectionMode,
                    preflightSignals = request.preflightSignals,
                    normalizationDraft = draft,
                    fingerprint = sample.asset.sha256,
                    manualCategoryCode = sample.sample.manualCategoryCode,
                    locale = request.locale,
                ),
            )
        }
        val endToEndLatencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attempts = telemetry.snapshot()
        val firstAttempt = attempts.firstOrNull()
        val usage = attempts.aggregateUsage()
        val predictedCategoryCode = normalizeResponse.draft?.projection?.categoryCode
            ?: bindResponse?.boundQuery?.categoryCode
        val predictedItemType = normalizeResponse.draft?.projection?.itemType?.text
            ?: bindResponse?.boundQuery?.itemType?.text
        val predictedBrand = normalizeResponse.draft?.projection?.brand?.text
            ?: bindResponse?.boundQuery?.normalizedQuery?.brand?.trim()?.takeIf { it.isNotEmpty() }
        val predictedBrandCandidates = normalizeResponse.draft?.projection?.brandCandidates
            ?.map { candidate -> candidate.text }
            .orEmpty()
        val predictedFamily = normalizeResponse.draft?.projection?.family?.text
        val predictedFamilyCandidates = normalizeResponse.draft?.projection?.familyCandidates
            ?.map { candidate -> candidate.text }
            .orEmpty()
        val predictedModel = normalizeResponse.draft?.projection?.model?.text
            ?: bindResponse?.boundQuery?.normalizedQuery?.model?.trim()?.takeIf { it.isNotEmpty() }
        val predictedModelCandidates = normalizeResponse.draft?.projection?.modelCandidates
            ?.map { candidate -> candidate.text }
            .orEmpty()
        val predictedBrandAnchors = (listOfNotNull(predictedBrand) + predictedBrandCandidates)
            .distinctBy { value -> Normalization.key(value) }
        val predictedFamilyAnchors = (listOfNotNull(predictedFamily) + predictedFamilyCandidates)
            .distinctBy { value -> Normalization.key(value) }
        val expectedFamily = sample.sample.expectedFamily?.trim()?.takeIf { it.isNotEmpty() }
        val hasRichFamilyExpectation = expectedFamily != null &&
            (!sample.sample.expectedBrand.isNullOrBlank() || !sample.sample.expectedModel.isNullOrBlank())
        val expectedTypeItemAnchors = (
            listOfNotNull(sample.sample.expectedItemType?.trim()?.takeIf { it.isNotEmpty() }) +
                sample.sample.expectedItemTypeAliases.mapNotNull { alias -> alias.trim().takeIf { it.isNotEmpty() } }
            )
            .distinctBy(::normalizeBenchmarkText)
        val schemaMetrics = computeSchemaMetrics(
            catalogRepository = catalogRepository,
            normalizeResponse = normalizeResponse,
            bindResponse = bindResponse,
        )
        return VisualSearchBenchmarkRunReport(
            runIndex = runIndex,
            sampleId = sample.sample.sampleId,
            imagePath = sample.file.absolutePath,
            contextImagePaths = sample.contextFiles.map { it.absolutePath },
            endToEndLatencyMs = endToEndLatencyMs,
            attemptLatencyMs = firstAttempt?.latencyMs,
            aiLatencyMs = attempts.sumOf { it.latencyMs }.takeIf { attempts.isNotEmpty() },
            attemptOutcome = firstAttempt?.outcome,
            attemptReason = firstAttempt?.reason,
            attemptFailureCode = firstAttempt?.failureCode,
            httpStatus = firstAttempt?.httpStatus,
            invocationMode = firstAttempt?.invocationMode,
            usableResult = normalizeResponse.status == VisualSearchEnvelopeStatus.OK &&
                (predictedCategoryCode != null || bindResponse?.status == VisualSearchEnvelopeStatus.OK),
            bindSuccess = bindResponse?.status == VisualSearchEnvelopeStatus.OK,
            categoryMatch = sample.sample.expectedCategoryCode
                ?.let { expected -> predictedCategoryCode?.equals(expected, ignoreCase = true) == true },
            brandMatch = sample.sample.expectedBrand
                ?.let { expected -> predictedBrand?.equals(expected, ignoreCase = true) == true },
            brandAnchorTop1Match = sample.sample.expectedBrand
                ?.let { expected -> predictedBrandAnchors.firstOrNull()?.let { matchesExpectedText(it, expected) } == true },
            brandAnchorTop2Match = sample.sample.expectedBrand
                ?.let { expected -> predictedBrandAnchors.take(2).any { candidate -> matchesExpectedText(candidate, expected) } },
            familyMatch = sample.sample.expectedFamily
                ?.let { expected -> matchesExpectedText(predictedFamily, expected) },
            familyAnchorTop1Match = sample.sample.expectedFamily
                ?.let { expected -> predictedFamilyAnchors.firstOrNull()?.let { matchesExpectedText(it, expected) } == true },
            familyAnchorTop2Match = sample.sample.expectedFamily
                ?.let { expected -> predictedFamilyAnchors.take(2).any { candidate -> matchesExpectedText(candidate, expected) } },
            richFamilyMatch = expectedFamily
                ?.takeIf { hasRichFamilyExpectation }
                ?.let { expected -> matchesExpectedText(predictedFamily, expected) },
            richFamilyAnchorTop1Match = expectedFamily
                ?.takeIf { hasRichFamilyExpectation }
                ?.let { expected -> predictedFamilyAnchors.firstOrNull()?.let { matchesExpectedText(it, expected) } == true },
            richFamilyAnchorTop2Match = expectedFamily
                ?.takeIf { hasRichFamilyExpectation }
                ?.let { expected -> predictedFamilyAnchors.take(2).any { candidate -> matchesExpectedText(candidate, expected) } },
            typeItemAnchorMatch = expectedTypeItemAnchors
                .takeIf { it.isNotEmpty() }
                ?.let { expectedValues -> expectedValues.any { expected -> matchesExpectedText(predictedItemType, expected) } },
            modelMatch = sample.sample.expectedModel
                ?.let { expected -> matchesExpectedText(predictedModel, expected) },
            modelCandidateTop1Match = sample.sample.expectedModel
                ?.let { expected -> predictedModelCandidates.firstOrNull()?.let { matchesExpectedText(it, expected) } == true },
            modelCandidateTop2Match = sample.sample.expectedModel
                ?.let { expected -> predictedModelCandidates.take(2).any { candidate -> matchesExpectedText(candidate, expected) } },
            predictedCategoryCode = predictedCategoryCode,
            predictedItemType = predictedItemType,
            predictedBrand = predictedBrand,
            predictedBrandCandidates = predictedBrandCandidates,
            predictedFamily = predictedFamily,
            predictedFamilyCandidates = predictedFamilyCandidates,
            predictedModel = predictedModel,
            predictedModelCandidates = predictedModelCandidates,
            binderStatus = bindResponse?.boundQuery?.binderStatus?.name?.lowercase(),
            providerName = normalizeResponse.draft?.providerName,
            providerSchemaVersion = normalizeResponse.draft?.providerSchemaVersion,
            reasonCodes = normalizeResponse.draft?.reasonCodes.orEmpty(),
            coreFillRate = schemaMetrics.coreFillRate,
            requiredFillRate = schemaMetrics.requiredFillRate,
            requiredFieldCount = schemaMetrics.requiredFieldCount,
            filledRequiredFieldCount = schemaMetrics.filledRequiredFieldCount,
            inputTokens = usage?.inputTokens,
            outputTokens = usage?.outputTokens,
            totalTokens = usage?.totalTokens,
            normalize = normalizeResponse,
            bind = bindResponse,
        )
    }

    private suspend fun computeSchemaMetrics(
        catalogRepository: ResourceCatalogRepository,
        normalizeResponse: VisualSearchNormalizeDraftResponse,
        bindResponse: VisualSearchBindQueryResponse?,
    ): BenchmarkSchemaMetrics {
        val projection = normalizeResponse.draft?.projection
        val boundQuery = bindResponse?.boundQuery
        val brand = boundQuery?.normalizedQuery?.brand
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: projection?.brand?.text?.trim()?.takeIf { it.isNotEmpty() }
        val model = boundQuery?.normalizedQuery?.model
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: projection?.model?.text?.trim()?.takeIf { it.isNotEmpty() }
        val categoryCode = boundQuery?.categoryCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: projection?.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val coreFieldsPresent = listOf(
            categoryCode != null,
            brand != null,
            model != null,
            projection?.title?.trim()?.isNotEmpty() == true,
        )
        val coreFillRate = coreFieldsPresent.count { it }.toDouble() / coreFieldsPresent.size.toDouble()
        if (categoryCode == null) {
            return BenchmarkSchemaMetrics(coreFillRate = coreFillRate)
        }

        val acceptedAttributes = LinkedHashMap<String, TypedAttributeValue>()
        boundQuery?.normalizedQuery?.attributes.orEmpty().forEach { (code, value) ->
            val normalizedCode = Normalization.attributeKey(code)
            if (normalizedCode.isNotEmpty()) {
                acceptedAttributes[normalizedCode] = value
            }
        }
        if (acceptedAttributes.isEmpty()) {
            projection?.attributes.orEmpty().forEach { attribute ->
                val normalizedCode = Normalization.attributeKey(attribute.code)
                if (normalizedCode.isNotEmpty()) {
                    acceptedAttributes[normalizedCode] = attribute.value
                }
            }
        }

        val spec = catalogRepository.getCategoryEffectiveSpec(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
        val requiredCodes = spec?.let {
            collectRequiredCodes(
                spec = it,
                brand = brand,
                model = model,
                acceptedAttributes = acceptedAttributes,
            )
        }.orEmpty()
        if (requiredCodes.isEmpty()) {
            return BenchmarkSchemaMetrics(
                coreFillRate = coreFillRate,
                requiredFillRate = 1.0,
            )
        }
        val presentCodes = acceptedAttributes.keys.toMutableSet()
        if (brand != null) presentCodes += "brand"
        if (model != null) presentCodes += "model"
        val filledRequiredFieldCount = requiredCodes.count { it in presentCodes }
        return BenchmarkSchemaMetrics(
            coreFillRate = coreFillRate,
            requiredFillRate = filledRequiredFieldCount.toDouble() / requiredCodes.size.toDouble(),
            requiredFieldCount = requiredCodes.size,
            filledRequiredFieldCount = filledRequiredFieldCount,
        )
    }

    private fun collectRequiredCodes(
        spec: CatalogCategoryEffectiveSpec,
        brand: String?,
        model: String?,
        acceptedAttributes: Map<String, TypedAttributeValue>,
    ): List<String> {
        val baseRequired = spec.allAttributes()
            .asSequence()
            .filter { attribute -> attribute.requiredForCategory || attribute.requiredForSearch }
            .map { attribute -> Normalization.attributeKey(attribute.code) }
            .filter { code -> code.isNotEmpty() }
        val conditionalRequired = spec.requiredIfRules
            .asSequence()
            .filter { rule -> rule.whenAll.all { conditionSatisfied(it, brand, model, acceptedAttributes) } }
            .map { rule -> Normalization.attributeKey(rule.requiredAttributeCode) }
            .filter { code -> code.isNotEmpty() }
        return (baseRequired + conditionalRequired)
            .distinct()
            .sorted()
            .toList()
    }

    private fun conditionSatisfied(
        condition: AttributeCondition,
        brand: String?,
        model: String?,
        acceptedAttributes: Map<String, TypedAttributeValue>,
    ): Boolean {
        val actual = when (Normalization.attributeKey(condition.attributeCode)) {
            "brand" -> brand
            "model" -> model
            else -> acceptedAttributes[Normalization.attributeKey(condition.attributeCode)]?.asRawString()
        }?.trim()?.lowercase(Locale.ROOT) ?: return false
        val expected = condition.values
            .map { value -> value.trim().lowercase(Locale.ROOT) }
            .filter { value -> value.isNotEmpty() }
        return when (condition.op.name) {
            "STARTS_WITH_ANY" -> expected.any { prefix -> actual.startsWith(prefix) }
            else -> expected.any { candidate -> actual == candidate }
        }
    }
}

@Serializable
data class VisualSearchBenchmarkManifest(
    val manifestName: String = "visual_search_benchmark",
    val locale: String = "ru",
    val runs: Int = 3,
    val models: List<String> = emptyList(),
    val samples: List<VisualSearchBenchmarkSample> = emptyList(),
)

@Serializable
data class VisualSearchBenchmarkSample(
    val sampleId: String,
    val imagePath: String,
    val contextImagePaths: List<String> = emptyList(),
    val expectedCategoryCode: String? = null,
    val expectedItemType: String? = null,
    val expectedItemTypeAliases: List<String> = emptyList(),
    val expectedBrand: String? = null,
    val expectedFamily: String? = null,
    val expectedModel: String? = null,
    val source: VisualSearchSource = VisualSearchSource.GALLERY,
    val captureMode: VisualSearchCaptureMode = VisualSearchCaptureMode.IMAGE,
    val selectionMode: VisualSearchSelectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
    val selectedRegion: VisualSearchSelectedRegion? = null,
    val intent: VisualSearchIntent = VisualSearchIntent.IDENTIFY_FIRST,
    val manualCategoryCode: String? = null,
    val preflightExactCategoryCode: String? = null,
    val preflightOcrTextHints: List<String> = emptyList(),
    val preflightImageLabelHints: List<String> = emptyList(),
    val preflightObjectLabel: String? = null,
    val preflightBarcodeValue: String? = null,
    val preflightCategoryCandidates: List<VisualSearchPreflightCategoryCandidate> = emptyList(),
    val locale: String? = null,
)

@Serializable
data class VisualSearchBenchmarkReport(
    val manifestName: String,
    val runs: Int,
    val timeoutMs: Long,
    val contractSnapshot: VisualSearchBenchmarkContractSnapshot? = null,
    val modelReports: List<VisualSearchBenchmarkModelReport> = emptyList(),
)

@Serializable
data class VisualSearchBenchmarkModelReport(
    val model: String,
    val modelUri: String,
    val configSnapshot: VisualSearchBenchmarkConfigSnapshot? = null,
    val invocationCount: Int,
    val latencyP50Ms: Long? = null,
    val latencyP95Ms: Long? = null,
    val aiLatencyP50Ms: Long? = null,
    val aiLatencyP95Ms: Long? = null,
    val firstAttemptLatencyP50Ms: Long? = null,
    val firstAttemptLatencyP95Ms: Long? = null,
    val firstAttemptSuccessCount: Int? = null,
    val firstAttemptTimeoutCount: Int? = null,
    val identityPassCount: Int? = null,
    val routerOnlyCount: Int? = null,
    val usableRate: Double,
    val bindSuccessRate: Double,
    val categoryHitRate: Double? = null,
    val brandHitRate: Double? = null,
    val brandAnchorTop1HitRate: Double? = null,
    val brandAnchorTop2HitRate: Double? = null,
    val familyHitRate: Double? = null,
    val familyAnchorTop1HitRate: Double? = null,
    val familyAnchorTop2HitRate: Double? = null,
    val richFamilyHitRate: Double? = null,
    val richFamilyAnchorTop1HitRate: Double? = null,
    val richFamilyAnchorTop2HitRate: Double? = null,
    val typeItemAnchorHitRate: Double? = null,
    val modelHitRate: Double? = null,
    val modelCandidateTop1HitRate: Double? = null,
    val modelCandidateTop2HitRate: Double? = null,
    val coreFillRate: Double? = null,
    val requiredFillRate: Double? = null,
    val avgInputTokens: Long? = null,
    val avgOutputTokens: Long? = null,
    val avgTotalTokens: Long? = null,
    val totalTokensPerSuccessfulBind: Double? = null,
    val sampleReports: List<VisualSearchBenchmarkSampleReport> = emptyList(),
    val runs: List<VisualSearchBenchmarkRunReport> = emptyList(),
)

@Serializable
data class VisualSearchBenchmarkContractSnapshot(
    val contractName: String,
    val schemaName: String,
    val promptProfile: String,
    val systemPromptSha256: String,
    val userPromptTemplateSha256: String,
    val schemaSha256: String,
    val systemPrompt: String,
    val userPromptTemplate: String,
    val schemaJson: String,
)

@Serializable
data class VisualSearchBenchmarkConfigSnapshot(
    val provider: String,
    val activeModel: String,
    val activeModelRef: String,
    val timeoutMs: Long,
    val promptProfile: String,
    val openAiImageDetail: String? = null,
    val openAiImageMaxSidePx: Int? = null,
    val openAiImageJpegQuality: Float? = null,
    val openAiReasoningEffort: String? = null,
    val maxCompletionTokens: Int,
    val shortlistCategoryLimit: Int,
    val shortlistFamilyLimit: Int,
    val shortlistModelLimit: Int,
    val groundingAttributeLimit: Int,
    val groundingAllowedValueLimit: Int,
    val hintLimit: Int,
    val requireHintEvidenceForIdentity: Boolean,
)

@Serializable
data class VisualSearchBenchmarkSampleReport(
    val sampleId: String,
    val imagePath: String,
    val contextImagePaths: List<String> = emptyList(),
    val expectedCategoryCode: String? = null,
    val expectedItemType: String? = null,
    val expectedItemTypeAliases: List<String> = emptyList(),
    val expectedBrand: String? = null,
    val expectedFamily: String? = null,
    val expectedModel: String? = null,
    val latencyP50Ms: Long? = null,
    val latencyP95Ms: Long? = null,
    val aiLatencyP50Ms: Long? = null,
    val aiLatencyP95Ms: Long? = null,
    val usableRate: Double,
    val bindSuccessRate: Double,
    val categoryHitRate: Double? = null,
    val brandHitRate: Double? = null,
    val brandAnchorTop1HitRate: Double? = null,
    val brandAnchorTop2HitRate: Double? = null,
    val familyHitRate: Double? = null,
    val familyAnchorTop1HitRate: Double? = null,
    val familyAnchorTop2HitRate: Double? = null,
    val richFamilyHitRate: Double? = null,
    val richFamilyAnchorTop1HitRate: Double? = null,
    val richFamilyAnchorTop2HitRate: Double? = null,
    val typeItemAnchorHitRate: Double? = null,
    val modelHitRate: Double? = null,
    val modelCandidateTop1HitRate: Double? = null,
    val modelCandidateTop2HitRate: Double? = null,
    val coreFillRate: Double? = null,
    val requiredFillRate: Double? = null,
    val avgInputTokens: Long? = null,
    val avgOutputTokens: Long? = null,
    val avgTotalTokens: Long? = null,
    val totalTokensPerSuccessfulBind: Double? = null,
    val latestPredictedCategoryCode: String? = null,
    val latestPredictedItemType: String? = null,
    val latestPredictedBrand: String? = null,
    val latestPredictedBrandCandidates: List<String> = emptyList(),
    val latestPredictedFamily: String? = null,
    val latestPredictedFamilyCandidates: List<String> = emptyList(),
    val latestPredictedModel: String? = null,
    val latestPredictedModelCandidates: List<String> = emptyList(),
    val latestProviderName: String? = null,
)

@Serializable
data class VisualSearchBenchmarkRunReport(
    val runIndex: Int,
    val sampleId: String,
    val imagePath: String,
    val contextImagePaths: List<String> = emptyList(),
    val endToEndLatencyMs: Long? = null,
    val attemptLatencyMs: Long? = null,
    val aiLatencyMs: Long? = null,
    val attemptOutcome: String? = null,
    val attemptReason: String? = null,
    val attemptFailureCode: String? = null,
    val httpStatus: Int? = null,
    val invocationMode: String? = null,
    val usableResult: Boolean,
    val bindSuccess: Boolean,
    val categoryMatch: Boolean? = null,
    val brandMatch: Boolean? = null,
    val brandAnchorTop1Match: Boolean? = null,
    val brandAnchorTop2Match: Boolean? = null,
    val familyMatch: Boolean? = null,
    val familyAnchorTop1Match: Boolean? = null,
    val familyAnchorTop2Match: Boolean? = null,
    val richFamilyMatch: Boolean? = null,
    val richFamilyAnchorTop1Match: Boolean? = null,
    val richFamilyAnchorTop2Match: Boolean? = null,
    val typeItemAnchorMatch: Boolean? = null,
    val modelMatch: Boolean? = null,
    val modelCandidateTop1Match: Boolean? = null,
    val modelCandidateTop2Match: Boolean? = null,
    val predictedCategoryCode: String? = null,
    val predictedItemType: String? = null,
    val predictedBrand: String? = null,
    val predictedBrandCandidates: List<String> = emptyList(),
    val predictedFamily: String? = null,
    val predictedFamilyCandidates: List<String> = emptyList(),
    val predictedModel: String? = null,
    val predictedModelCandidates: List<String> = emptyList(),
    val binderStatus: String? = null,
    val providerName: String? = null,
    val providerSchemaVersion: String? = null,
    val reasonCodes: List<String> = emptyList(),
    val coreFillRate: Double? = null,
    val requiredFillRate: Double? = null,
    val requiredFieldCount: Int = 0,
    val filledRequiredFieldCount: Int = 0,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val normalize: VisualSearchNormalizeDraftResponse,
    val bind: VisualSearchBindQueryResponse? = null,
)

private data class PreparedBenchmarkSample(
    val sample: VisualSearchBenchmarkSample,
    val file: File,
    val asset: VisualSearchImageAsset,
    val contextFiles: List<File>,
    val contextAssets: List<VisualSearchImageAsset>,
) {
    companion object {
        fun from(sample: VisualSearchBenchmarkSample): PreparedBenchmarkSample {
            val file = File(sample.imagePath)
            require(file.isFile) { "Image file not found: ${sample.imagePath}" }
            val contextFiles = sample.contextImagePaths
                .map { path -> File(path) }
                .filter { contextFile -> contextFile.absolutePath != file.absolutePath }
                .distinctBy { contextFile -> contextFile.absolutePath }
                .take(2)
                .onEach { contextFile ->
                    require(contextFile.isFile) { "Context image file not found: ${contextFile.path}" }
                }
            return PreparedBenchmarkSample(
                sample = sample,
                file = file,
                asset = buildImageAsset(file),
                contextFiles = contextFiles,
                contextAssets = contextFiles.map(::buildImageAsset),
            )
        }

        private fun buildImageAsset(file: File): VisualSearchImageAsset {
            val fileBytes = file.readBytes()
            val image = ImageIO.read(file)
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(fileBytes)
                .joinToString("") { byte -> "%02x".format(byte) }
            return VisualSearchImageAsset(
                sha256 = sha256,
                mimeType = detectMimeType(file),
                widthPx = image?.width,
                heightPx = image?.height,
                byteSize = fileBytes.size.toLong(),
                inlineBase64 = Base64.getEncoder().encodeToString(fileBytes),
            )
        }
    }
}

private fun parseModels(raw: String?): List<String>? =
    raw?.split(',', ';')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }

private fun buildContractSnapshot(config: VisualSearchConfig): VisualSearchBenchmarkContractSnapshot {
    val routerMode = config.aiPromptProfile.equals("router", ignoreCase = true)
    val contractName = if (routerMode) "visual_search_router" else "visual_search"
    val schemaName = if (routerMode) "visual_search_route" else "visual_search_normalize_draft"
    val contract = AiStructuredContractLoader.load(contractName)
    val schemaJson = Json.encodeToString(contract.schema)
    return VisualSearchBenchmarkContractSnapshot(
        contractName = contractName,
        schemaName = schemaName,
        promptProfile = config.aiPromptProfile,
        systemPromptSha256 = sha256Hex(contract.systemPrompt),
        userPromptTemplateSha256 = sha256Hex(contract.userPromptTemplate),
        schemaSha256 = sha256Hex(schemaJson),
        systemPrompt = contract.systemPrompt,
        userPromptTemplate = contract.userPromptTemplate,
        schemaJson = schemaJson,
    )
}

private fun buildConfigSnapshot(config: VisualSearchConfig): VisualSearchBenchmarkConfigSnapshot =
    VisualSearchBenchmarkConfigSnapshot(
        provider = config.provider.name,
        activeModel = config.activeModel,
        activeModelRef = config.activeModelRef,
        timeoutMs = config.activeTimeoutMs,
        promptProfile = config.aiPromptProfile,
        openAiImageDetail = config.openAiImageDetail,
        openAiImageMaxSidePx = config.openAiImageMaxSidePx,
        openAiImageJpegQuality = config.openAiImageJpegQuality,
        openAiReasoningEffort = config.openAiReasoningEffort,
        maxCompletionTokens = config.activeMaxCompletionTokens,
        shortlistCategoryLimit = config.aiShortlistCategoryLimit,
        shortlistFamilyLimit = config.aiShortlistFamilyLimit,
        shortlistModelLimit = config.aiShortlistModelLimit,
        groundingAttributeLimit = config.aiGroundingAttributeLimit,
        groundingAllowedValueLimit = config.aiGroundingAllowedValueLimit,
        hintLimit = config.aiHintLimit,
        requireHintEvidenceForIdentity = config.aiRequireHintEvidenceForIdentity,
    )

private fun detectMimeType(file: File): String =
    Files.probeContentType(file.toPath())
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

private fun matchesExpectedText(actual: String?, expected: String): Boolean {
    val normalizedActual = normalizeBenchmarkText(actual)
    val normalizedExpected = normalizeBenchmarkText(expected)
    if (normalizedActual.isEmpty() || normalizedExpected.isEmpty()) return false
    return normalizedActual == normalizedExpected ||
        (normalizedExpected.length >= 5 && normalizedActual.contains(normalizedExpected)) ||
        (normalizedActual.length >= 5 && normalizedExpected.contains(normalizedActual))
}

private fun normalizeBenchmarkText(value: String?): String =
    value.orEmpty()
        .lowercase(Locale.ROOT)
        .filter { char -> char.isLetterOrDigit() }

private class CollectingBenchmarkTelemetry : AiNormalizationTelemetry {
    private val attempts = mutableListOf<AiNormalizationAttempt>()

    override fun recordAttempt(attempt: AiNormalizationAttempt) {
        synchronized(attempts) {
            attempts += attempt
        }
    }

    fun snapshot(): List<BenchmarkAttemptReport> = synchronized(attempts) {
        attempts.map { attempt ->
            val response = attempt.response
            BenchmarkAttemptReport(
                latencyMs = attempt.latencyMs,
                outcome = attempt.outcome.name,
                reason = attempt.reason.name,
                failureCode = attempt.failureCode?.name,
                httpStatus = when (response) {
                    is YandexAiStudioStructuredResponse.Success -> response.httpStatus
                    is YandexAiStudioStructuredResponse.Failure -> response.httpStatus
                    null -> null
                },
                invocationMode = when (response) {
                    is YandexAiStudioStructuredResponse.Success -> response.invocationMode.name
                    is YandexAiStudioStructuredResponse.Failure -> response.invocationMode.name
                    null -> null
                },
                usage = when (response) {
                    is YandexAiStudioStructuredResponse.Success -> response.usage
                    is YandexAiStudioStructuredResponse.Failure -> response.usage
                    null -> null
                },
            )
        }
    }
}

private data class BenchmarkAttemptReport(
    val latencyMs: Long,
    val outcome: String,
    val reason: String,
    val failureCode: String? = null,
    val httpStatus: Int? = null,
    val invocationMode: String? = null,
    val usage: AiTokenUsage? = null,
)

private data class BenchmarkSchemaMetrics(
    val coreFillRate: Double,
    val requiredFillRate: Double? = null,
    val requiredFieldCount: Int = 0,
    val filledRequiredFieldCount: Int = 0,
)

private fun List<BenchmarkAttemptReport>.aggregateUsage(): AiTokenUsage? {
    val populated = mapNotNull { report -> report.usage?.takeUnless { it.isEmpty } }
    if (populated.isEmpty()) return null
    val inputTokens = populated.mapNotNull { usage -> usage.inputTokens }
        .sum()
        .takeIf { populated.any { usage -> usage.inputTokens != null } }
    val outputTokens = populated.mapNotNull { usage -> usage.outputTokens }
        .sum()
        .takeIf { populated.any { usage -> usage.outputTokens != null } }
    val totalTokens = populated.mapNotNull { usage -> usage.totalTokens }
        .sum()
        .takeIf { populated.any { usage -> usage.totalTokens != null } }
    return AiTokenUsage(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        totalTokens = totalTokens,
    ).takeUnless { it.isEmpty }
}

private fun List<Long>.percentile(ratio: Double): Long? {
    if (isEmpty()) return null
    val position = ceil(size * ratio).toInt().coerceIn(1, size) - 1
    return this[position]
}

private fun List<Boolean>.averageBooleanOrNull(): Double? =
    if (isEmpty()) null else count { it }.toDouble() / size.toDouble()

private fun List<Double>.averageOrNull(): Double? =
    if (isEmpty()) null else average()

private fun List<Long>.averageDoubleOrNull(): Double? =
    if (isEmpty()) null else average()

private fun List<Long>.averageLongOrNull(): Long? =
    if (isEmpty()) null else average().roundToLong()
