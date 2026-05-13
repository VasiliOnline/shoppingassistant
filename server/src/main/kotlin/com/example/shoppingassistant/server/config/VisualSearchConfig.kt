package com.example.shoppingassistant.server.config

import com.example.shoppingassistant.server.ai.AiExecutionTarget

enum class VisualSearchAiProvider {
    YANDEX,
    GEMINI,
    OPENAI,
}

data class VisualSearchConfig(
    val enabled: Boolean,
    val serverAiEnabled: Boolean,
    val contextReuseTtlSeconds: Long,
    val yandexBaseUrl: String,
    val yandexResponsesBaseUrl: String,
    val yandexApiKey: String?,
    val yandexProjectId: String?,
    val yandexModel: String,
    val yandexAgentId: String?,
    val yandexFallbackAgentIds: List<String>,
    val yandexAllowModelFallback: Boolean,
    val yandexSystemPrompt: String?,
    val yandexTimeoutMs: Long,
    val aiShortlistCategoryLimit: Int,
    val aiShortlistFamilyLimit: Int,
    val aiShortlistModelLimit: Int,
    val circuitBreakerEnabled: Boolean = true,
    val circuitBreakerFailureThreshold: Int = 2,
    val circuitBreakerCooldownMs: Long = 90_000L,
    val yandexUseSavedAgent: Boolean = true,
    val yandexTemperature: Double = 0.1,
    val yandexMaxCompletionTokens: Int = 700,
    val aiGroundingAttributeLimit: Int = 3,
    val aiGroundingAllowedValueLimit: Int = 3,
    val aiHintLimit: Int = 8,
    val aiPromptProfile: String = "default",
    val aiRouterTimeoutMs: Long = 2_800L,
    val aiRouterMaxCompletionTokens: Int? = null,
    val aiIdentityMaxCompletionTokens: Int? = null,
    val aiRequireHintEvidenceForIdentity: Boolean = false,
    val aiIdentityEnrichmentEnabled: Boolean = true,
    val aiBlockingIdentityEnrichmentEnabled: Boolean = false,
    val provider: VisualSearchAiProvider = VisualSearchAiProvider.OPENAI,
    val geminiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta/openai",
    val geminiApiKey: String? = null,
    val geminiModel: String = "gemini-2.5-flash",
    val geminiTimeoutMs: Long = 30_000L,
    val geminiSystemPrompt: String? = null,
    val geminiTemperature: Double = 0.05,
    val geminiMaxCompletionTokens: Int = 500,
    val openAiBaseUrl: String = "https://api.openai.com/v1",
    val openAiApiKey: String? = null,
    val openAiModel: String = "gpt-5-mini",
    val openAiMultiImageModel: String? = null,
    val openAiTimeoutMs: Long = 30_000L,
    val openAiSystemPrompt: String? = null,
    val openAiTemperature: Double? = null,
    val openAiMaxCompletionTokens: Int = 900,
    val openAiReasoningEffort: String? = "minimal",
    val openAiImageDetail: String? = null,
    val openAiImageMaxSidePx: Int = 768,
    val openAiImageJpegQuality: Float = 0.74f,
    val aiRetry429MaxAttempts: Int = 1,
    val aiRetry5xxMaxAttempts: Int = 1,
    val aiRetryBaseDelayMs: Long = 800L,
    val aiRetryMaxDelayMs: Long = 4_000L,
) {
    val yandexReady: Boolean
        get() = serverAiEnabled &&
            !yandexApiKey.isNullOrBlank() &&
            !yandexProjectId.isNullOrBlank() &&
            yandexModel.isNotBlank()

    val geminiReady: Boolean
        get() = serverAiEnabled &&
            !geminiApiKey.isNullOrBlank() &&
            geminiModel.isNotBlank()

    val openAiReady: Boolean
        get() = serverAiEnabled &&
            !openAiApiKey.isNullOrBlank() &&
            openAiModel.isNotBlank()

    companion object {
        fun fromEnv(): VisualSearchConfig = VisualSearchConfig(
            enabled = parseBooleanEnv("VISUAL_SEARCH_ENABLED", defaultValue = true),
            serverAiEnabled = parseBooleanEnv("VISUAL_SEARCH_SERVER_AI_ENABLED", defaultValue = true),
            contextReuseTtlSeconds = envValue("VISUAL_SEARCH_CONTEXT_REUSE_TTL_SECONDS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceAtLeast(60L)
                ?: 15 * 60L,
            yandexBaseUrl = envValue("VISUAL_SEARCH_YANDEX_BASE_URL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "https://ai.api.cloud.yandex.net/v1",
            yandexResponsesBaseUrl = envValue("VISUAL_SEARCH_YANDEX_RESPONSES_BASE_URL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "https://rest-assistant.api.cloud.yandex.net/v1",
            yandexApiKey = envValue("VISUAL_SEARCH_YANDEX_API_KEY")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            yandexProjectId = envValue("VISUAL_SEARCH_YANDEX_PROJECT_ID")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            yandexModel = envValue("VISUAL_SEARCH_YANDEX_MODEL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "qwen2.5-vl-32b-instruct",
            yandexAgentId = envValue("VISUAL_SEARCH_YANDEX_AGENT_ID", "VISUAL_SEARCH_YANDEX_PROMPT_ID")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            yandexFallbackAgentIds = parseListEnv("VISUAL_SEARCH_YANDEX_FALLBACK_AGENT_IDS"),
            yandexAllowModelFallback = parseBooleanEnv(
                key = "VISUAL_SEARCH_YANDEX_ALLOW_MODEL_FALLBACK",
                defaultValue = true,
            ),
            yandexSystemPrompt = envValue("VISUAL_SEARCH_YANDEX_SYSTEM_PROMPT")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            yandexTimeoutMs = envValue("VISUAL_SEARCH_YANDEX_TIMEOUT_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(3_000L, 120_000L)
                ?: 30_000L,
            aiShortlistCategoryLimit = envValue("VISUAL_SEARCH_AI_SHORTLIST_CATEGORIES")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(6, 24)
                ?: 8,
            aiShortlistFamilyLimit = envValue("VISUAL_SEARCH_AI_SHORTLIST_FAMILIES")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(2, 12)
                ?: 4,
            aiShortlistModelLimit = envValue("VISUAL_SEARCH_AI_SHORTLIST_MODELS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(2, 12)
                ?: 4,
            circuitBreakerEnabled = parseBooleanEnv(
                key = "VISUAL_SEARCH_AI_CIRCUIT_BREAKER_ENABLED",
                defaultValue = true,
            ),
            circuitBreakerFailureThreshold = envValue("VISUAL_SEARCH_AI_CIRCUIT_BREAKER_FAILURE_THRESHOLD")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(1, 10)
                ?: 2,
            circuitBreakerCooldownMs = envValue("VISUAL_SEARCH_AI_CIRCUIT_BREAKER_COOLDOWN_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(5_000L, 15 * 60_000L)
                ?: 90_000L,
            yandexUseSavedAgent = parseBooleanEnv(
                key = "VISUAL_SEARCH_YANDEX_USE_SAVED_AGENT",
                defaultValue = false,
            ),
            yandexTemperature = envValue("VISUAL_SEARCH_YANDEX_TEMPERATURE")
                ?.trim()
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, 1.0)
                ?: 0.1,
            yandexMaxCompletionTokens = envValue("VISUAL_SEARCH_YANDEX_MAX_COMPLETION_TOKENS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(128, 2500)
                ?: 700,
            aiGroundingAttributeLimit = envValue("VISUAL_SEARCH_AI_GROUNDING_ATTRIBUTE_LIMIT")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(0, 12)
                ?: 3,
            aiGroundingAllowedValueLimit = envValue("VISUAL_SEARCH_AI_GROUNDING_ALLOWED_VALUE_LIMIT")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(0, 8)
                ?: 3,
            aiHintLimit = envValue("VISUAL_SEARCH_AI_HINT_LIMIT")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(4, 24)
                ?: 8,
            aiPromptProfile = envValue("VISUAL_SEARCH_OPENAI_PROMPT_PROFILE", "VISUAL_SEARCH_AI_PROMPT_PROFILE")
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in setOf("default", "simple", "identity", "router") }
                ?: "default",
            aiRouterTimeoutMs = envValue("VISUAL_SEARCH_AI_ROUTER_TIMEOUT_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(1_500L, 30_000L)
                ?: 2_800L,
            aiRouterMaxCompletionTokens = envValue("VISUAL_SEARCH_AI_ROUTER_MAX_COMPLETION_TOKENS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(128, 900),
            aiIdentityMaxCompletionTokens = envValue("VISUAL_SEARCH_AI_IDENTITY_MAX_COMPLETION_TOKENS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(256, 2500),
            aiRequireHintEvidenceForIdentity = parseBooleanEnv(
                key = "VISUAL_SEARCH_AI_REQUIRE_HINT_EVIDENCE_FOR_IDENTITY",
                defaultValue = false,
            ),
            aiIdentityEnrichmentEnabled = parseBooleanEnv(
                key = "VISUAL_SEARCH_AI_IDENTITY_ENRICHMENT_ENABLED",
                defaultValue = true,
            ),
            aiBlockingIdentityEnrichmentEnabled = parseBooleanEnv(
                key = "VISUAL_SEARCH_AI_BLOCKING_IDENTITY_ENRICHMENT_ENABLED",
                defaultValue = false,
            ),
            provider = envValue("VISUAL_SEARCH_AI_PROVIDER")
                ?.trim()
                ?.uppercase()
                ?.let(::parseProvider)
                ?: VisualSearchAiProvider.OPENAI,
            geminiBaseUrl = envValue("VISUAL_SEARCH_GEMINI_BASE_URL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "https://generativelanguage.googleapis.com/v1beta/openai",
            geminiApiKey = envValue("VISUAL_SEARCH_GEMINI_API_KEY", "GEMINI_API_KEY")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            geminiModel = envValue("VISUAL_SEARCH_GEMINI_MODEL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "gemini-2.5-flash",
            geminiTimeoutMs = envValue("VISUAL_SEARCH_GEMINI_TIMEOUT_MS", "VISUAL_SEARCH_YANDEX_TIMEOUT_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(3_000L, 120_000L)
                ?: 30_000L,
            geminiSystemPrompt = envValue("VISUAL_SEARCH_GEMINI_SYSTEM_PROMPT")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            geminiTemperature = envValue("VISUAL_SEARCH_GEMINI_TEMPERATURE")
                ?.trim()
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, 1.0)
                ?: 0.05,
            geminiMaxCompletionTokens = envValue("VISUAL_SEARCH_GEMINI_MAX_COMPLETION_TOKENS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(128, 2500)
                ?: 500,
            openAiBaseUrl = envValue("VISUAL_SEARCH_OPENAI_BASE_URL", "OPENAI_BASE_URL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "https://api.openai.com/v1",
            openAiApiKey = envValue("VISUAL_SEARCH_OPENAI_API_KEY", "OPENAI_API_KEY")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            openAiModel = envValue("VISUAL_SEARCH_OPENAI_MODEL", "OPENAI_MODEL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "gpt-5-mini",
            openAiMultiImageModel = envValue("VISUAL_SEARCH_OPENAI_MULTI_IMAGE_MODEL", "OPENAI_MULTI_IMAGE_MODEL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            openAiTimeoutMs = envValue("VISUAL_SEARCH_OPENAI_TIMEOUT_MS", "VISUAL_SEARCH_YANDEX_TIMEOUT_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(1_500L, 120_000L)
                ?: 30_000L,
            openAiSystemPrompt = envValue("VISUAL_SEARCH_OPENAI_SYSTEM_PROMPT")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            openAiTemperature = envValue("VISUAL_SEARCH_OPENAI_TEMPERATURE")
                ?.trim()
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, 2.0),
            openAiMaxCompletionTokens = envValue("VISUAL_SEARCH_OPENAI_MAX_COMPLETION_TOKENS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(128, 2500)
                ?: 900,
            openAiReasoningEffort = envValue("VISUAL_SEARCH_OPENAI_REASONING_EFFORT")
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in setOf("none", "minimal", "low", "medium", "high", "xhigh") }
                ?: "minimal",
            openAiImageDetail = parseOpenAiImageDetail(envValue("VISUAL_SEARCH_OPENAI_IMAGE_DETAIL")),
            openAiImageMaxSidePx = envValue("VISUAL_SEARCH_OPENAI_IMAGE_MAX_SIDE_PX")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(256, 1280)
                ?: 768,
            openAiImageJpegQuality = envValue("VISUAL_SEARCH_OPENAI_IMAGE_JPEG_QUALITY")
                ?.trim()
                ?.toFloatOrNull()
                ?.coerceIn(0.45f, 0.92f)
                ?: 0.74f,
            aiRetry429MaxAttempts = envValue("VISUAL_SEARCH_AI_RETRY_429_MAX_ATTEMPTS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(0, 4)
                ?: 1,
            aiRetry5xxMaxAttempts = envValue("VISUAL_SEARCH_AI_RETRY_5XX_MAX_ATTEMPTS")
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(0, 4)
                ?: 1,
            aiRetryBaseDelayMs = envValue("VISUAL_SEARCH_AI_RETRY_BASE_DELAY_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(100L, 10_000L)
                ?: 800L,
            aiRetryMaxDelayMs = envValue("VISUAL_SEARCH_AI_RETRY_MAX_DELAY_MS")
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(500L, 30_000L)
                ?: 4_000L,
        )
    }

    val yandexModelUri: String
        get() {
            val rawModel = yandexModel.trim()
            if (rawModel.startsWith("gpt://", ignoreCase = true)) return rawModel
            val projectId = yandexProjectId?.trim().orEmpty()
            if (projectId.isEmpty()) return rawModel
            val normalizedModel = rawModel.trim('/')
            return if (normalizedModel.endsWith("/latest", ignoreCase = true)) {
                "gpt://$projectId/$normalizedModel"
            } else {
                "gpt://$projectId/$normalizedModel/latest"
            }
        }

    val usesSavedAgent: Boolean
        get() = provider == VisualSearchAiProvider.YANDEX && yandexUseSavedAgent && !yandexAgentId.isNullOrBlank()

    val providerReady: Boolean
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexReady
            VisualSearchAiProvider.GEMINI -> geminiReady
            VisualSearchAiProvider.OPENAI -> openAiReady
        }

    val activeBaseUrl: String
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexBaseUrl
            VisualSearchAiProvider.GEMINI -> geminiBaseUrl
            VisualSearchAiProvider.OPENAI -> openAiBaseUrl
        }

    val activeResponsesBaseUrl: String?
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexResponsesBaseUrl
            VisualSearchAiProvider.GEMINI -> null
            VisualSearchAiProvider.OPENAI -> null
        }

    val activeApiKey: String?
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexApiKey
            VisualSearchAiProvider.GEMINI -> geminiApiKey
            VisualSearchAiProvider.OPENAI -> openAiApiKey
        }

    val activeProjectId: String?
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexProjectId
            VisualSearchAiProvider.GEMINI -> null
            VisualSearchAiProvider.OPENAI -> null
        }

    val activeModel: String
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexModel
            VisualSearchAiProvider.GEMINI -> geminiModel
            VisualSearchAiProvider.OPENAI -> openAiModel
        }

    val activeTimeoutMs: Long
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexTimeoutMs
            VisualSearchAiProvider.GEMINI -> geminiTimeoutMs
            VisualSearchAiProvider.OPENAI -> openAiTimeoutMs
        }

    val activeSystemPrompt: String?
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexSystemPrompt
            VisualSearchAiProvider.GEMINI -> geminiSystemPrompt
            VisualSearchAiProvider.OPENAI -> openAiSystemPrompt
        }

    val activeTemperature: Double?
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexTemperature
            VisualSearchAiProvider.GEMINI -> geminiTemperature
            VisualSearchAiProvider.OPENAI -> openAiTemperature
        }

    val activeMaxCompletionTokens: Int
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexMaxCompletionTokens
            VisualSearchAiProvider.GEMINI -> geminiMaxCompletionTokens
            VisualSearchAiProvider.OPENAI -> openAiMaxCompletionTokens
        }

    val activeReasoningEffort: String?
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> null
            VisualSearchAiProvider.GEMINI -> null
            VisualSearchAiProvider.OPENAI -> openAiReasoningEffort.normalizedOpenAiReasoningEffortFor(openAiModel)
        }

    fun activeReasoningEffortForModel(modelOverride: String?): String? {
        val model = modelOverride?.trim()?.takeIf { it.isNotEmpty() } ?: activeModelRef
        return when (provider) {
            VisualSearchAiProvider.OPENAI -> openAiReasoningEffort.normalizedOpenAiReasoningEffortFor(model)

            else -> activeReasoningEffort
        }
    }

    fun activeModelOverrideForContextImages(hasContextImages: Boolean): String? =
        if (provider == VisualSearchAiProvider.OPENAI && hasContextImages) {
            openAiMultiImageModel
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.takeIf { !it.equals(openAiModel.trim(), ignoreCase = true) }
        } else {
            null
        }

    val activeModelRef: String
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> yandexModelUri
            VisualSearchAiProvider.GEMINI -> geminiModel.trim()
            VisualSearchAiProvider.OPENAI -> openAiModel.trim()
        }

    val providerMissingReasonCodes: List<String>
        get() = when (provider) {
            VisualSearchAiProvider.YANDEX -> buildList {
                if (yandexApiKey.isNullOrBlank()) add("YANDEX_API_KEY_MISSING")
                if (yandexProjectId.isNullOrBlank()) add("YANDEX_PROJECT_ID_MISSING")
                if (yandexModel.isBlank()) add("YANDEX_MODEL_MISSING")
            }

            VisualSearchAiProvider.GEMINI -> buildList {
                if (geminiApiKey.isNullOrBlank()) add("GEMINI_API_KEY_MISSING")
                if (geminiModel.isBlank()) add("GEMINI_MODEL_MISSING")
            }

            VisualSearchAiProvider.OPENAI -> buildList {
                if (openAiApiKey.isNullOrBlank()) add("OPENAI_API_KEY_MISSING")
                if (openAiModel.isBlank()) add("OPENAI_MODEL_MISSING")
            }
        }

    val executionTargets: List<AiExecutionTarget>
        get() {
            if (provider != VisualSearchAiProvider.YANDEX) {
                return listOf(
                    AiExecutionTarget(
                        key = "model_primary",
                        modelUri = activeModelRef,
                    ),
                )
            }
            val targets = mutableListOf<AiExecutionTarget>()
            val seenPromptIds = linkedSetOf<String>()

            fun appendAgent(key: String, promptId: String?) {
                val normalized = promptId?.trim()?.takeIf { it.isNotEmpty() } ?: return
                if (seenPromptIds.add(normalized)) {
                    targets += AiExecutionTarget(
                        key = key,
                        modelUri = yandexModelUri,
                        promptId = normalized,
                    )
                }
            }

            if (yandexUseSavedAgent) {
                appendAgent("primary", yandexAgentId)
                yandexFallbackAgentIds.forEachIndexed { index, promptId ->
                    appendAgent("fallback_${index + 1}", promptId)
                }
            }
            if (targets.isEmpty() || yandexAllowModelFallback) {
                targets += AiExecutionTarget(
                    key = if (targets.isEmpty()) "model_primary" else "model_fallback",
                    modelUri = yandexModelUri,
                )
            }
            return targets
        }
}

private fun parseProvider(raw: String): VisualSearchAiProvider =
    when (val normalized = raw.trim().uppercase()) {
        "GPT", "OPEN_AI", "OPENAI" -> VisualSearchAiProvider.OPENAI
        else -> runCatching { VisualSearchAiProvider.valueOf(normalized) }
            .getOrDefault(VisualSearchAiProvider.OPENAI)
    }

private fun parseOpenAiImageDetail(raw: String?): String? {
    val normalized = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when (normalized) {
        "low", "auto", "high" -> normalized
        "none", "off", "false" -> null
        else -> null
    }
}

private fun String?.normalizedOpenAiReasoningEffortFor(model: String): String? {
    val effort = this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (effort == "none") return null
    if (!model.supportsOpenAiReasoningEffort()) return null
    return if (effort == "minimal" && model.usesPostGpt50ReasoningLevels()) {
        null
    } else {
        effort
    }
}

private fun String.supportsOpenAiReasoningEffort(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("gpt-5") || normalized.startsWith("o")
}

private fun String.usesPostGpt50ReasoningLevels(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("gpt-5.") && !normalized.startsWith("gpt-5.0")
}
