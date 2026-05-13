package com.example.shoppingassistant.server.visualsearch

import com.example.shoppingassistant.domain.catalog.CatalogDataVersion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchBindQueryResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchCaptureMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchEntryPoint
import com.example.shoppingassistant.domain.visualsearch.VisualSearchIntent
import com.example.shoppingassistant.domain.visualsearch.VisualSearchImageAsset
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftRequest
import com.example.shoppingassistant.domain.visualsearch.VisualSearchNormalizeDraftResponse
import com.example.shoppingassistant.domain.visualsearch.VisualSearchPreflightSignals
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSchemaVersion
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSelectionMode
import com.example.shoppingassistant.domain.visualsearch.VisualSearchSource
import com.example.shoppingassistant.domain.visualsearch.VisualSearchTransportMetadata
import com.example.shoppingassistant.server.ai.AiNormalizationAttempt
import com.example.shoppingassistant.server.ai.AiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.ResourceAiAgentRegistry
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.ai.yandex.DefaultYandexAiStudioClient
import com.example.shoppingassistant.server.catalog.ResourceCatalogRepository
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import com.example.shoppingassistant.server.config.VisualSearchConfig
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val imagePath = args.getOrNull(0)
        ?: error("Usage: VisualSearchEvalRunnerKt <image-path> [output.json]")
    val outputPath = args.getOrNull(1)

    val imageFile = File(imagePath)
    require(imageFile.isFile) { "Image file not found: $imagePath" }

    val config = VisualSearchConfig.fromEnv()
    require(config.enabled) {
        "Visual search is disabled. Check VISUAL_SEARCH_ENABLED."
    }
    require(config.providerReady) {
        "Visual search AI is not configured. Check env.local and the selected provider credentials/model."
    }

    val catalogRepository = ResourceCatalogRepository()
    val contextStore = InMemoryVisualSearchContextStore(config)
    val telemetry = CollectingAiNormalizationTelemetry()
    val orchestrator = AiNormalizationOrchestrator(
        agentRegistry = ResourceAiAgentRegistry(
            visualSearchConfig = config,
            listingVisionAiConfig = ListingVisionAiConfig.fromEnv(),
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

    val fileBytes = imageFile.readBytes()
    val image = ImageIO.read(imageFile)
    val widthPx = image?.width
    val heightPx = image?.height
    val sha256 = MessageDigest.getInstance("SHA-256")
        .digest(fileBytes)
        .joinToString("") { byte -> "%02x".format(byte) }
    val mimeType = detectMimeType(imageFile)
    val source = detectSource(imageFile)
    val metadata = VisualSearchTransportMetadata(
        visualSessionId = "vs-eval-${UUID.randomUUID()}",
        clientSchemaVersion = VisualSearchSchemaVersion.current,
        catalogDataVersion = CatalogDataVersion.current,
        idempotencyKey = "vs-eval-$sha256",
    )
    val request = VisualSearchNormalizeDraftRequest(
        asset = VisualSearchImageAsset(
            sha256 = sha256,
            mimeType = mimeType,
            widthPx = widthPx,
            heightPx = heightPx,
            byteSize = fileBytes.size.toLong(),
            inlineBase64 = Base64.getEncoder().encodeToString(fileBytes),
        ),
        source = source,
        entryPoint = VisualSearchEntryPoint.SEARCH_HUB_PHOTO_CARD,
        selectionMode = VisualSearchSelectionMode.WHOLE_FRAME,
        intent = VisualSearchIntent.IDENTIFY_FIRST,
        preflightSignals = VisualSearchPreflightSignals(
            reasonCodes = listOf("OFFLINE_EVAL"),
            cheapProjectionReady = true,
            admitServerAi = true,
            captureMode = VisualSearchCaptureMode.IMAGE,
        ),
        locale = "ru",
    )

    val normalizeResponse = runBlocking {
        service.normalizeDraft(
            metadata = metadata,
            request = request,
        )
    }

    val bindResponse = normalizeResponse.draft?.let { draft ->
        runBlocking {
            service.bindQuery(
                metadata = metadata,
                request = VisualSearchBindQueryRequest(
                    intent = request.intent,
                    source = request.source,
                    selectionMode = request.selectionMode,
                    preflightSignals = request.preflightSignals,
                    normalizationDraft = draft,
                    fingerprint = sha256,
                    locale = request.locale,
                ),
            )
        }
    }

    val report = VisualSearchEvalReport(
        imagePath = imageFile.absolutePath,
        source = source,
        widthPx = widthPx,
        heightPx = heightPx,
        configEnabled = config.enabled,
        serverAiEnabled = config.serverAiEnabled,
        yandexReady = config.providerReady,
        model = config.activeModel,
        modelUri = config.activeModelRef,
        aiAttempts = telemetry.snapshot(),
        normalize = normalizeResponse,
        bind = bindResponse,
    )
    val json = Json {
        prettyPrint = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
    val encoded = json.encodeToString(VisualSearchEvalReport.serializer(), report)
    if (outputPath != null) {
        File(outputPath).writeText(encoded, Charsets.UTF_8)
        println("visual_search_eval_written=$outputPath")
    } else {
        println(encoded)
    }
    // Java HttpClient may keep non-daemon worker threads alive in standalone eval mode.
    exitProcess(0)
}

@Serializable
data class VisualSearchEvalReport(
    val imagePath: String,
    val source: VisualSearchSource,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val configEnabled: Boolean,
    val serverAiEnabled: Boolean,
    val yandexReady: Boolean,
    val model: String,
    val modelUri: String,
    val aiAttempts: List<VisualSearchAiAttemptReport> = emptyList(),
    val normalize: VisualSearchNormalizeDraftResponse,
    val bind: VisualSearchBindQueryResponse? = null,
)

@Serializable
data class VisualSearchAiAttemptReport(
    val flow: String,
    val targetKey: String,
    val outcome: String,
    val reason: String,
    val latencyMs: Long,
    val httpStatus: Int? = null,
    val failureCode: String? = null,
    val invocationMode: String? = null,
    val message: String? = null,
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

private fun detectSource(file: File): VisualSearchSource {
    val normalizedPath = file.absolutePath.lowercase()
    return when {
        "screenshot" in normalizedPath || "снимок экрана" in normalizedPath -> VisualSearchSource.SCREENSHOT
        else -> VisualSearchSource.GALLERY
    }
}

private class CollectingAiNormalizationTelemetry : AiNormalizationTelemetry {
    private val attempts = mutableListOf<AiNormalizationAttempt>()

    override fun recordAttempt(attempt: AiNormalizationAttempt) {
        synchronized(attempts) {
            attempts += attempt
        }
    }

    fun snapshot(): List<VisualSearchAiAttemptReport> = synchronized(attempts) {
        attempts.map { attempt ->
            val response = attempt.response
            VisualSearchAiAttemptReport(
                flow = attempt.flow.name,
                targetKey = attempt.target.key,
                outcome = attempt.outcome.name,
                reason = attempt.reason.name,
                latencyMs = attempt.latencyMs,
                httpStatus = when (response) {
                    is YandexAiStudioStructuredResponse.Success -> response.httpStatus
                    is YandexAiStudioStructuredResponse.Failure -> response.httpStatus
                    null -> null
                },
                failureCode = attempt.failureCode?.name,
                invocationMode = when (response) {
                    is YandexAiStudioStructuredResponse.Success -> response.invocationMode.name
                    is YandexAiStudioStructuredResponse.Failure -> response.invocationMode.name
                    null -> null
                },
                message = (response as? YandexAiStudioStructuredResponse.Failure)?.message,
            )
        }
    }
}
