package com.example.shoppingassistant.server.config

import com.example.shoppingassistant.server.ai.AiExecutionTarget

data class ListingVisionAiConfig(
    val serverAiEnabled: Boolean,
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
    val shortlistCategoryLimit: Int,
    val categoryAttributeLimit: Int,
    val maxPhotosPerRequest: Int,
    val circuitBreakerEnabled: Boolean = true,
    val circuitBreakerFailureThreshold: Int = 2,
    val circuitBreakerCooldownMs: Long = 90_000L,
) {
    val yandexReady: Boolean
        get() = serverAiEnabled &&
            !yandexApiKey.isNullOrBlank() &&
            !yandexProjectId.isNullOrBlank() &&
            yandexModel.isNotBlank()

    companion object {
        fun fromEnv(): ListingVisionAiConfig = ListingVisionAiConfig(
            serverAiEnabled = parseBooleanEnv(
                primaryKey = "VISION_SERVER_AI_ENABLED",
                fallbackKey = "VISUAL_SEARCH_SERVER_AI_ENABLED",
                defaultValue = false,
            ),
            yandexBaseUrl = envValue("VISION_YANDEX_BASE_URL", "VISUAL_SEARCH_YANDEX_BASE_URL")
                ?: "https://ai.api.cloud.yandex.net/v1",
            yandexResponsesBaseUrl = envValue("VISION_YANDEX_RESPONSES_BASE_URL", "VISUAL_SEARCH_YANDEX_RESPONSES_BASE_URL")
                ?: "https://rest-assistant.api.cloud.yandex.net/v1",
            yandexApiKey = envValue("VISION_YANDEX_API_KEY", "VISUAL_SEARCH_YANDEX_API_KEY"),
            yandexProjectId = envValue("VISION_YANDEX_PROJECT_ID", "VISUAL_SEARCH_YANDEX_PROJECT_ID"),
            yandexModel = envValue("VISION_YANDEX_MODEL", "VISUAL_SEARCH_YANDEX_MODEL")
                ?: "qwen2.5-vl-32b-instruct",
            yandexAgentId = envValue("VISION_YANDEX_AGENT_ID", "VISION_YANDEX_PROMPT_ID"),
            yandexFallbackAgentIds = parseListEnv("VISION_YANDEX_FALLBACK_AGENT_IDS"),
            yandexAllowModelFallback = parseBooleanEnv(
                key = "VISION_YANDEX_ALLOW_MODEL_FALLBACK",
                defaultValue = true,
            ),
            yandexSystemPrompt = envValue("VISION_YANDEX_SYSTEM_PROMPT"),
            yandexTimeoutMs = envValue("VISION_YANDEX_TIMEOUT_MS", "VISUAL_SEARCH_YANDEX_TIMEOUT_MS")
                ?.toLongOrNull()
                ?.coerceIn(3_000L, 90_000L)
                ?: 15_000L,
            shortlistCategoryLimit = envValue("VISION_AI_SHORTLIST_CATEGORIES")
                ?.toIntOrNull()
                ?.coerceIn(4, 16)
                ?: 8,
            categoryAttributeLimit = envValue("VISION_AI_CATEGORY_ATTRIBUTES")
                ?.toIntOrNull()
                ?.coerceIn(4, 12)
                ?: 8,
            maxPhotosPerRequest = envValue("VISION_AI_MAX_PHOTOS")
                ?.toIntOrNull()
                ?.coerceIn(1, 6)
                ?: 4,
            circuitBreakerEnabled = parseBooleanEnv(
                primaryKey = "VISION_AI_CIRCUIT_BREAKER_ENABLED",
                fallbackKey = "VISUAL_SEARCH_AI_CIRCUIT_BREAKER_ENABLED",
                defaultValue = true,
            ),
            circuitBreakerFailureThreshold = envValue(
                "VISION_AI_CIRCUIT_BREAKER_FAILURE_THRESHOLD",
                "VISUAL_SEARCH_AI_CIRCUIT_BREAKER_FAILURE_THRESHOLD",
            )
                ?.trim()
                ?.toIntOrNull()
                ?.coerceIn(1, 10)
                ?: 2,
            circuitBreakerCooldownMs = envValue(
                "VISION_AI_CIRCUIT_BREAKER_COOLDOWN_MS",
                "VISUAL_SEARCH_AI_CIRCUIT_BREAKER_COOLDOWN_MS",
            )
                ?.trim()
                ?.toLongOrNull()
                ?.coerceIn(5_000L, 15 * 60_000L)
                ?: 90_000L,
        )
    }

    val usesSavedAgent: Boolean
        get() = !yandexAgentId.isNullOrBlank()

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

    val executionTargets: List<AiExecutionTarget>
        get() {
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

            appendAgent("primary", yandexAgentId)
            yandexFallbackAgentIds.forEachIndexed { index, promptId ->
                appendAgent("fallback_${index + 1}", promptId)
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
