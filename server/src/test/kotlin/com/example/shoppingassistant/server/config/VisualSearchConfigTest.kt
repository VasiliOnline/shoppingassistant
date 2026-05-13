package com.example.shoppingassistant.server.config

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class VisualSearchConfigTest {

    @Test
    fun constructor_defaults_to_openai_provider() {
        val config = VisualSearchConfig(
            enabled = true,
            serverAiEnabled = true,
            contextReuseTtlSeconds = 900,
            yandexBaseUrl = "https://example.test/v1",
            yandexResponsesBaseUrl = "https://example.test/responses",
            yandexApiKey = "yandex-token",
            yandexProjectId = "project-id",
            yandexModel = "gemma-3-27b-it",
            yandexAgentId = "saved-agent-id",
            yandexFallbackAgentIds = emptyList(),
            yandexAllowModelFallback = true,
            yandexSystemPrompt = null,
            yandexTimeoutMs = 30_000,
            aiShortlistCategoryLimit = 12,
            aiShortlistFamilyLimit = 6,
            aiShortlistModelLimit = 6,
            openAiApiKey = "openai-token",
            openAiModel = "gpt-5-mini",
        )

        assertEquals(VisualSearchAiProvider.OPENAI, config.provider)
        assertTrue(config.providerReady)
        assertEquals("gpt-5-mini", config.activeModelRef)
        assertEquals(listOf("model_primary"), config.executionTargets.map { it.key })
    }

    @Test
    fun executionTargets_ignore_saved_agent_when_disabled_even_if_agent_id_is_present() {
        val config = baseConfig(
            yandexAgentId = "saved-agent-id",
            yandexUseSavedAgent = false,
            yandexAllowModelFallback = true,
        )

        assertFalse(config.usesSavedAgent)
        assertEquals(1, config.executionTargets.size)
        assertEquals("model_primary", config.executionTargets.single().key)
        assertFalse(config.executionTargets.single().usesSavedAgent)
    }

    @Test
    fun executionTargets_include_saved_agent_when_enabled() {
        val config = baseConfig(
            yandexAgentId = "saved-agent-id",
            yandexUseSavedAgent = true,
            yandexAllowModelFallback = true,
        )

        assertTrue(config.usesSavedAgent)
        assertEquals(listOf("primary", "model_fallback"), config.executionTargets.map { it.key })
        assertTrue(config.executionTargets.first().usesSavedAgent)
        assertFalse(config.executionTargets.last().usesSavedAgent)
    }

    @Test
    fun gemini_provider_exposes_direct_model_target_without_saved_agent() {
        val config = baseConfig(
            yandexAgentId = "saved-agent-id",
            yandexUseSavedAgent = true,
            yandexAllowModelFallback = true,
        ).copy(
            provider = VisualSearchAiProvider.GEMINI,
            geminiApiKey = "gemini-token",
            geminiModel = "gemini-2.5-flash",
            geminiTemperature = 0.02,
            geminiMaxCompletionTokens = 420,
        )

        assertTrue(config.providerReady)
        assertFalse(config.usesSavedAgent)
        assertEquals("gemini-2.5-flash", config.activeModelRef)
        assertEquals(0.02, config.activeTemperature)
        assertEquals(420, config.activeMaxCompletionTokens)
        assertEquals(listOf("model_primary"), config.executionTargets.map { it.key })
        assertFalse(config.executionTargets.single().usesSavedAgent)
    }

    @Test
    fun openai_provider_exposes_direct_model_target_with_minimal_reasoning() {
        val config = baseConfig(
            yandexAgentId = "saved-agent-id",
            yandexUseSavedAgent = true,
            yandexAllowModelFallback = true,
        ).copy(
            provider = VisualSearchAiProvider.OPENAI,
            openAiApiKey = "openai-token",
            openAiModel = "gpt-5-mini",
            openAiTemperature = null,
            openAiMaxCompletionTokens = 360,
            openAiReasoningEffort = "minimal",
        )

        assertTrue(config.providerReady)
        assertFalse(config.usesSavedAgent)
        assertEquals("https://api.openai.com/v1", config.activeBaseUrl)
        assertEquals("gpt-5-mini", config.activeModelRef)
        assertEquals(null, config.activeTemperature)
        assertEquals(360, config.activeMaxCompletionTokens)
        assertEquals("minimal", config.activeReasoningEffort)
        assertEquals(null, config.openAiImageDetail)
        assertEquals(768, config.openAiImageMaxSidePx)
        assertEquals(0.74f, config.openAiImageJpegQuality)
        assertEquals(listOf("model_primary"), config.executionTargets.map { it.key })
        assertFalse(config.executionTargets.single().usesSavedAgent)
    }

    private fun baseConfig(
        yandexAgentId: String?,
        yandexUseSavedAgent: Boolean,
        yandexAllowModelFallback: Boolean,
    ): VisualSearchConfig = VisualSearchConfig(
        enabled = true,
        serverAiEnabled = true,
        contextReuseTtlSeconds = 900,
        yandexBaseUrl = "https://example.test/v1",
        yandexResponsesBaseUrl = "https://example.test/responses",
        yandexApiKey = "token",
        yandexProjectId = "project-id",
        yandexModel = "gemma-3-27b-it",
        yandexAgentId = yandexAgentId,
        yandexFallbackAgentIds = emptyList(),
        yandexAllowModelFallback = yandexAllowModelFallback,
        yandexSystemPrompt = null,
        yandexTimeoutMs = 30_000,
        aiShortlistCategoryLimit = 12,
        aiShortlistFamilyLimit = 6,
        aiShortlistModelLimit = 6,
        yandexUseSavedAgent = yandexUseSavedAgent,
        provider = VisualSearchAiProvider.YANDEX,
    )
}
