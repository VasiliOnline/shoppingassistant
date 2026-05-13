package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioClient
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioContentPart
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredRequest
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioTransportConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class AiNormalizationOrchestratorTest {

    @Test
    fun execute_structured_json_falls_back_after_transport_failure_and_parse_miss() = runBlocking {
        val attempts = mutableListOf<AiNormalizationAttempt>()
        val orchestrator = AiNormalizationOrchestrator(
            agentRegistry = StaticRegistry(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://folder/gemma/latest",
                        promptId = "agent-primary",
                    ),
                    YandexAiExecutionTarget(
                        key = "fallback_1",
                        modelUri = "gpt://folder/gemma/latest",
                        promptId = "agent-fallback-1",
                    ),
                    YandexAiExecutionTarget(
                        key = "model_fallback",
                        modelUri = "gpt://folder/gemma/latest",
                    ),
                ),
            ),
            aiClient = SequenceClient(
                responses = listOf(
                    YandexAiStudioStructuredResponse.Failure(
                        code = com.example.shoppingassistant.server.ai.yandex.YandexAiStudioFailureCode.UPSTREAM_5XX,
                        httpStatus = 500,
                        invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                    ),
                    YandexAiStudioStructuredResponse.Success(
                        responseBody = """{"ignored":true}""",
                        httpStatus = 200,
                        invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                    ),
                    YandexAiStudioStructuredResponse.Success(
                        responseBody = """{"ok":true}""",
                        httpStatus = 200,
                        invocationMode = YandexAiStudioInvocationMode.CHAT_COMPLETIONS,
                    ),
                ),
            ),
            telemetry = object : AiNormalizationTelemetry {
                override fun recordAttempt(attempt: AiNormalizationAttempt) {
                    attempts += attempt
                }
            },
        )

        val execution = orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            systemInstruction = "system",
            contentParts = listOf(YandexAiStudioContentPart.Text("hello")),
            schemaName = "test_schema",
            schema = buildJsonObject { put("type", "object") },
        ) { responseBody, _ ->
            if (responseBody.contains("\"ok\":true")) "parsed" else null
        }

        assertNotNull(execution)
        assertEquals("parsed", execution.value)
        assertEquals("model_fallback", execution.target.key)
        assertEquals(
            listOf(
                AiNormalizationAttemptOutcome.FAILURE,
                AiNormalizationAttemptOutcome.PARSE_MISS,
                AiNormalizationAttemptOutcome.SUCCESS,
            ),
            attempts.map { it.outcome },
        )
    }

    @Test
    fun execute_structured_json_opens_circuit_after_repeated_failures_then_retries_after_cooldown() = runBlocking {
        val clock = MutableClock(1_000L)
        val client = PromptAwareClient()
        val orchestrator = AiNormalizationOrchestrator(
            agentRegistry = StaticRegistry(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://folder/gemma/latest",
                        promptId = "agent-primary",
                    ),
                    YandexAiExecutionTarget(
                        key = "fallback_1",
                        modelUri = "gpt://folder/gemma/latest",
                        promptId = "agent-fallback",
                    ),
                ),
                circuitBreaker = AiCircuitBreakerConfig(
                    enabled = true,
                    failureThreshold = 2,
                    cooldownMs = 60_000L,
                ),
            ),
            aiClient = client,
            telemetry = NoopAiNormalizationTelemetry,
            healthPolicy = InMemoryAiTargetHealthPolicy(clock),
        )

        repeat(2) {
            val execution = orchestrator.executeStructuredJson(
                flow = AiNormalizationFlow.VISUAL_SEARCH,
                systemInstruction = "system",
                contentParts = listOf(YandexAiStudioContentPart.Text("hello")),
                schemaName = "test_schema",
                schema = buildJsonObject { put("type", "object") },
            ) { responseBody, _ ->
                if (responseBody.contains("\"ok\":true")) "parsed" else null
            }
            assertNotNull(execution)
            assertEquals("fallback_1", execution.target.key)
        }

        val skippedExecution = orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            systemInstruction = "system",
            contentParts = listOf(YandexAiStudioContentPart.Text("hello")),
            schemaName = "test_schema",
            schema = buildJsonObject { put("type", "object") },
        ) { responseBody, _ ->
            if (responseBody.contains("\"ok\":true")) "parsed" else null
        }

        assertNotNull(skippedExecution)
        assertEquals("fallback_1", skippedExecution.target.key)
        assertEquals(
            listOf(
                AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT,
                AiNormalizationAttemptOutcome.SUCCESS,
            ),
            skippedExecution.attempts.map { it.outcome },
        )
        assertEquals(2, client.primaryCalls)

        clock.advanceBy(60_001L)
        val retriedExecution = orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            systemInstruction = "system",
            contentParts = listOf(YandexAiStudioContentPart.Text("hello")),
            schemaName = "test_schema",
            schema = buildJsonObject { put("type", "object") },
        ) { responseBody, _ ->
            if (responseBody.contains("\"ok\":true")) "parsed" else null
        }

        assertNotNull(retriedExecution)
        assertEquals(3, client.primaryCalls)
        assertEquals("fallback_1", retriedExecution.target.key)
    }

    @Test
    fun execute_structured_json_uses_per_request_timeout_override_capped_by_route_timeout() = runBlocking {
        val client = CapturingClient()
        val orchestrator = AiNormalizationOrchestrator(
            agentRegistry = StaticRegistry(
                targets = listOf(
                    YandexAiExecutionTarget(
                        key = "primary",
                        modelUri = "gpt://folder/gemma/latest",
                    ),
                ),
            ),
            aiClient = client,
        )

        val execution = orchestrator.executeStructuredJson(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            systemInstruction = "system",
            contentParts = listOf(YandexAiStudioContentPart.Text("hello")),
            schemaName = "test_schema",
            schema = buildJsonObject { put("type", "object") },
            timeoutMsOverride = 8_000L,
        ) { responseBody, _ ->
            if (responseBody.contains("\"ok\":true")) "parsed" else null
        }

        assertNotNull(execution)
        assertEquals(5_000L, client.requests.single().timeoutMs)
    }
}

private class StaticRegistry(
    private val targets: List<YandexAiExecutionTarget>,
    private val circuitBreaker: AiCircuitBreakerConfig = AiCircuitBreakerConfig(),
) : AiAgentRegistry {
    override fun routeFor(flow: AiNormalizationFlow): AiAgentRoute? = AiAgentRoute(
        flow = flow,
        transport = YandexAiStudioTransportConfig(
            baseUrl = "https://example.test/v1",
            responsesBaseUrl = "https://example.test/v1",
            apiKey = "token",
            projectId = "folder",
        ),
        timeoutMs = 5_000,
        targets = targets,
        circuitBreaker = circuitBreaker,
    )

    override fun describe(flow: AiNormalizationFlow): AiAgentFlowDescriptor = AiAgentFlowDescriptor(
        flow = flow,
        enabled = true,
        ready = true,
        circuitBreaker = circuitBreaker,
        targets = targets.map { target ->
            AiAgentTargetDescriptor(
                key = target.key,
                modelUri = target.modelUri,
                usesSavedAgent = target.usesSavedAgent,
                promptConfigured = target.promptId != null,
            )
        },
    )
}

private class SequenceClient(
    private val responses: List<YandexAiStudioStructuredResponse>,
) : YandexAiStudioClient {
    private var index: Int = 0

    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        val responseIndex = index.coerceAtMost(responses.lastIndex)
        index += 1
        return responses[responseIndex]
    }
}

private class PromptAwareClient : YandexAiStudioClient {
    var primaryCalls: Int = 0
        private set

    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse =
        when (request.promptId) {
            "agent-primary" -> {
                primaryCalls += 1
                YandexAiStudioStructuredResponse.Failure(
                    code = com.example.shoppingassistant.server.ai.yandex.YandexAiStudioFailureCode.UPSTREAM_5XX,
                    httpStatus = 500,
                    invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                )
            }

            "agent-fallback" -> YandexAiStudioStructuredResponse.Success(
                responseBody = """{"ok":true}""",
                httpStatus = 200,
                invocationMode = YandexAiStudioInvocationMode.RESPONSES,
            )

            else -> error("Unexpected promptId=${request.promptId}")
        }
}

private class CapturingClient : YandexAiStudioClient {
    val requests = mutableListOf<YandexAiStudioStructuredRequest>()

    override suspend fun completeStructuredJson(request: YandexAiStudioStructuredRequest): YandexAiStudioStructuredResponse {
        requests += request
        return YandexAiStudioStructuredResponse.Success(
            responseBody = """{"ok":true}""",
            httpStatus = 200,
            invocationMode = YandexAiStudioInvocationMode.CHAT_COMPLETIONS,
        )
    }
}

private class MutableClock(
    private var currentMs: Long,
) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(currentMs)

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    fun advanceBy(deltaMs: Long) {
        currentMs += deltaMs
    }
}
