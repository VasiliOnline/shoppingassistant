package com.example.shoppingassistant.server.vision

import com.example.shoppingassistant.domain.model.Normalization
import com.example.shoppingassistant.domain.vision.VisionNormalizeRequest
import com.example.shoppingassistant.domain.vision.VisionPhotoInput
import com.example.shoppingassistant.domain.vision.VisionPhotoRole
import com.example.shoppingassistant.server.ai.AiNormalizationOrchestrator
import com.example.shoppingassistant.server.ai.NoopAiNormalizationTelemetry
import com.example.shoppingassistant.server.ai.ResourceAiAgentRegistry
import com.example.shoppingassistant.server.ai.yandex.DefaultYandexAiStudioClient
import com.example.shoppingassistant.server.catalog.CatalogRepositoryImpl
import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import com.example.shoppingassistant.server.config.VisualSearchConfig
import java.io.File
import java.util.Base64
import kotlin.math.ceil
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val manifestPath = args.getOrNull(0)
        ?: error("Usage: ListingVisionEvalRunnerKt <manifest.json> [output.json]")
    val outputPath = args.getOrNull(1)
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }
    val manifest = json.decodeFromString(
        ListingVisionEvalManifest.serializer(),
        File(manifestPath).readText(Charsets.UTF_8),
    )

    val config = ListingVisionAiConfig.fromEnv()
    require(config.yandexReady) {
        "Listing vision AI is not configured. Check VISION_SERVER_AI_ENABLED, VISION_YANDEX_API_KEY, VISION_YANDEX_PROJECT_ID and VISION_YANDEX_MODEL."
    }

    val catalogRepository = CatalogRepositoryImpl()
    val orchestrator = AiNormalizationOrchestrator(
        agentRegistry = ResourceAiAgentRegistry(
            visualSearchConfig = VisualSearchConfig.fromEnv(),
            listingVisionAiConfig = config,
        ),
        aiClient = DefaultYandexAiStudioClient(),
        telemetry = NoopAiNormalizationTelemetry,
    )
    val normalizer = YandexListingVisionAiNormalizer(
        catalogRepository = catalogRepository,
        catalogTaxonomyRepository = catalogRepository,
        config = config,
        orchestrator = orchestrator,
    )
    val runner = ListingVisionEvalRunner(normalizer = normalizer)
    val report = runBlocking { runner.run(manifest) }
    val encoded = json.encodeToString(ListingVisionEvalReport.serializer(), report)
    if (outputPath != null) {
        File(outputPath).writeText(encoded, Charsets.UTF_8)
        println("listing_vision_eval_written=$outputPath")
    } else {
        println(encoded)
    }
}

class ListingVisionEvalRunner(
    private val normalizer: VisionAiPhotoNormalizer,
) {
    suspend fun run(manifest: ListingVisionEvalManifest): ListingVisionEvalReport {
        require(manifest.samples.isNotEmpty()) { "Manifest must contain at least one sample." }
        val samples = manifest.samples.map { sample ->
            val startedAt = System.currentTimeMillis()
            val request = VisionNormalizeRequest(
                photos = sample.photos.map { photo ->
                    VisionPhotoInput(
                        role = photo.role.toVisionPhotoRole(),
                        base64 = Base64.getEncoder().encodeToString(File(photo.path).readBytes()),
                    )
                },
                locale = sample.locale ?: manifest.locale,
                categoryHint = sample.categoryHint,
                hints = sample.hints,
            )
            val result = runCatching { normalizer.normalize(request) }.getOrNull()
            val latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            val predictedCodes = buildPredictedFieldCodes(result)
            val expectedFieldCodes = (sample.expectedFieldCodes + sample.expectedFields.keys)
                .map { code -> Normalization.attributeKey(code) }
                .filter { code -> code.isNotEmpty() }
                .distinct()
            val matchedExpected = expectedFieldCodes.count { code -> code in predictedCodes }
            val fillRate = if (expectedFieldCodes.isEmpty()) {
                null
            } else {
                matchedExpected.toDouble() / expectedFieldCodes.size.toDouble()
            }
            val categoryMatch = sample.expectedCategoryCode
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { expected ->
                    result?.categoryCode?.equals(expected, ignoreCase = true) == true
                }
            ListingVisionEvalSampleReport(
                sampleId = sample.sampleId,
                latencyMs = latencyMs,
                categoryHint = sample.categoryHint,
                expectedCategoryCode = sample.expectedCategoryCode,
                predictedCategoryCode = result?.categoryCode,
                categoryMatch = categoryMatch,
                fillRate = fillRate,
                expectedFieldCodes = expectedFieldCodes,
                predictedFieldCodes = predictedCodes.sorted(),
                warningCodes = result?.warnings.orEmpty(),
                nextAction = result?.nextAction?.name,
                success = result != null,
            )
        }

        val latencies = samples.map { sample -> sample.latencyMs }.sorted()
        val fillRates = samples.mapNotNull { sample -> sample.fillRate }
        return ListingVisionEvalReport(
            manifestName = manifest.manifestName,
            sampleCount = samples.size,
            latencyP50Ms = latencies.percentile(0.50),
            latencyP95Ms = latencies.percentile(0.95),
            averageFillRate = fillRates.averageOrNull(),
            categoryHitRate = samples.mapNotNull { sample -> sample.categoryMatch }.averageBooleanOrNull(),
            samples = samples,
        )
    }

    private fun buildPredictedFieldCodes(
        result: com.example.shoppingassistant.domain.vision.VisionNormalizeResult?,
    ): Set<String> {
        if (result == null) return emptySet()
        return buildSet {
            result.normalizedQuery?.brand?.takeIf { value -> value.isNotBlank() }?.let { add("brand") }
            result.normalizedQuery?.model?.takeIf { value -> value.isNotBlank() }?.let { add("model") }
            result.normalizedQuery?.attributes?.keys?.forEach { key ->
                val normalized = Normalization.attributeKey(key)
                if (normalized.isNotEmpty()) add(normalized)
            }
        }
    }
}

@Serializable
data class ListingVisionEvalManifest(
    val manifestName: String = "listing_vision_eval",
    val locale: String = "ru",
    val samples: List<ListingVisionEvalSample> = emptyList(),
)

@Serializable
data class ListingVisionEvalSample(
    val sampleId: String,
    val categoryHint: String? = null,
    val expectedCategoryCode: String? = null,
    val expectedFieldCodes: List<String> = emptyList(),
    val expectedFields: Map<String, String> = emptyMap(),
    val hints: Map<String, String> = emptyMap(),
    val locale: String? = null,
    val photos: List<ListingVisionEvalPhoto> = emptyList(),
)

@Serializable
data class ListingVisionEvalPhoto(
    val path: String,
    val role: ListingVisionEvalPhotoRole = ListingVisionEvalPhotoRole.FRONT,
)

@Serializable
enum class ListingVisionEvalPhotoRole {
    @SerialName("front")
    FRONT,

    @SerialName("back")
    BACK,

    @SerialName("left")
    LEFT,

    @SerialName("right")
    RIGHT,

    @SerialName("top")
    TOP,

    @SerialName("bottom")
    BOTTOM,

    @SerialName("tech_1")
    TECH_1,

    @SerialName("tech_2")
    TECH_2,
}

@Serializable
data class ListingVisionEvalReport(
    val manifestName: String,
    val sampleCount: Int,
    val latencyP50Ms: Long? = null,
    val latencyP95Ms: Long? = null,
    val averageFillRate: Double? = null,
    val categoryHitRate: Double? = null,
    val samples: List<ListingVisionEvalSampleReport> = emptyList(),
)

@Serializable
data class ListingVisionEvalSampleReport(
    val sampleId: String,
    val latencyMs: Long,
    val categoryHint: String? = null,
    val expectedCategoryCode: String? = null,
    val predictedCategoryCode: String? = null,
    val categoryMatch: Boolean? = null,
    val fillRate: Double? = null,
    val expectedFieldCodes: List<String> = emptyList(),
    val predictedFieldCodes: List<String> = emptyList(),
    val warningCodes: List<String> = emptyList(),
    val nextAction: String? = null,
    val success: Boolean,
)

private fun ListingVisionEvalPhotoRole.toVisionPhotoRole(): VisionPhotoRole = when (this) {
    ListingVisionEvalPhotoRole.FRONT -> VisionPhotoRole.FRONT
    ListingVisionEvalPhotoRole.BACK -> VisionPhotoRole.BACK
    ListingVisionEvalPhotoRole.LEFT -> VisionPhotoRole.LEFT
    ListingVisionEvalPhotoRole.RIGHT -> VisionPhotoRole.RIGHT
    ListingVisionEvalPhotoRole.TOP -> VisionPhotoRole.TOP
    ListingVisionEvalPhotoRole.BOTTOM -> VisionPhotoRole.BOTTOM
    ListingVisionEvalPhotoRole.TECH_1 -> VisionPhotoRole.TECH_1
    ListingVisionEvalPhotoRole.TECH_2 -> VisionPhotoRole.TECH_2
}

private fun List<Long>.percentile(ratio: Double): Long? {
    if (isEmpty()) return null
    val position = ceil(size * ratio).toInt().coerceIn(1, size) - 1
    return this[position]
}

private fun List<Double>.averageOrNull(): Double? =
    if (isEmpty()) null else average()

private fun List<Boolean>.averageBooleanOrNull(): Double? =
    if (isEmpty()) null else count { it }.toDouble() / size.toDouble()
