package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import kotlinx.serialization.json.JsonObject

data class AiNormalizationExecution<T>(
    val value: T,
    val target: AiExecutionTarget,
    val attempts: List<AiNormalizationAttempt>,
)

class AiNormalizationOrchestrator(
    private val agentRegistry: AiAgentRegistry,
    private val aiClient: AiStructuredClient,
    private val telemetry: AiNormalizationTelemetry = NoopAiNormalizationTelemetry,
    private val healthPolicy: AiTargetHealthPolicy = NoopAiTargetHealthPolicy,
) {
    suspend fun <T> executeStructuredJson(
        flow: AiNormalizationFlow,
        systemInstruction: String,
        contentParts: List<AiContentPart>,
        schemaName: String,
        schema: JsonObject,
        temperature: Double? = 0.1,
        maxCompletionTokens: Int = 900,
        reasoningEffort: String? = null,
        strictJsonSchema: Boolean = true,
        modelOverride: String? = null,
        modelOverrideKeySuffix: String? = null,
        timeoutMsOverride: Long? = null,
        parse: suspend (responseBody: String, target: AiExecutionTarget) -> T?,
    ): AiNormalizationExecution<T>? {
        val route = agentRegistry.routeFor(flow) ?: return null
        if (contentParts.isEmpty()) return null
        val requestTimeoutMs = timeoutMsOverride
            ?.coerceAtLeast(1_000L)
            ?.coerceAtMost(route.timeoutMs)
            ?: route.timeoutMs

        val attempts = mutableListOf<AiNormalizationAttempt>()
        for (target in route.targets) {
            val effectiveTarget = target.withModelOverride(modelOverride, modelOverrideKeySuffix)
            when (val availability = healthPolicy.availabilityFor(route, effectiveTarget)) {
                AiTargetAvailability.Available -> Unit
                is AiTargetAvailability.OpenCircuit -> {
                    val attempt = AiNormalizationAttempt(
                        flow = flow,
                        target = effectiveTarget,
                        registryVersion = route.registryVersion,
                        routeVersion = route.routeVersion,
                        contractName = route.contractName,
                        contractVersion = route.contractVersion,
                        outcome = AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT,
                        reason = AiNormalizationAttemptReason.TARGET_OPEN_CIRCUIT,
                        latencyMs = 0L,
                        cooldownUntilEpochMs = availability.untilEpochMs,
                    )
                    attempts += attempt
                    telemetry.recordAttempt(attempt)
                    continue
                }
            }
            val startedAt = System.nanoTime()
            val response = aiClient.completeStructuredJson(
                AiStructuredRequest(
                    transport = route.transport,
                    model = effectiveTarget.modelUri,
                    timeoutMs = requestTimeoutMs,
                    systemInstruction = systemInstruction,
                    contentParts = contentParts,
                    schemaName = schemaName,
                    schema = schema,
                    promptId = effectiveTarget.promptId,
                    temperature = temperature,
                    maxCompletionTokens = maxCompletionTokens,
                    reasoningEffort = reasoningEffort,
                    strictJsonSchema = strictJsonSchema,
                ),
            )
            val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

            when (response) {
                is YandexAiStudioStructuredResponse.Success -> {
                    val parsed = parse(response.responseBody, effectiveTarget)
                    val outcome = if (parsed != null) {
                        AiNormalizationAttemptOutcome.SUCCESS
                    } else {
                        AiNormalizationAttemptOutcome.PARSE_MISS
                    }
                    val attempt = AiNormalizationAttempt(
                        flow = flow,
                        target = effectiveTarget,
                        registryVersion = route.registryVersion,
                        routeVersion = route.routeVersion,
                        contractName = route.contractName,
                        contractVersion = route.contractVersion,
                        outcome = outcome,
                        reason = if (parsed != null) {
                            AiNormalizationAttemptReason.TARGET_SUCCESS
                        } else {
                            AiNormalizationAttemptReason.TARGET_PARSE_MISS
                        },
                        latencyMs = latencyMs,
                        response = response,
                    )
                    attempts += attempt
                    telemetry.recordAttempt(attempt)
                    healthPolicy.recordAttempt(route, attempt)
                    if (parsed != null) {
                        return AiNormalizationExecution(
                            value = parsed,
                            target = effectiveTarget,
                            attempts = attempts.toList(),
                        )
                    }
                }

                is YandexAiStudioStructuredResponse.Failure -> {
                    val attempt = AiNormalizationAttempt(
                        flow = flow,
                        target = effectiveTarget,
                        registryVersion = route.registryVersion,
                        routeVersion = route.routeVersion,
                        contractName = route.contractName,
                        contractVersion = route.contractVersion,
                        outcome = AiNormalizationAttemptOutcome.FAILURE,
                        reason = response.code.toAttemptReason(),
                        latencyMs = latencyMs,
                        response = response,
                    )
                    attempts += attempt
                    telemetry.recordAttempt(attempt)
                    healthPolicy.recordAttempt(route, attempt)
                }
            }
        }
        return null
    }

    private fun AiExecutionTarget.withModelOverride(
        modelOverride: String?,
        keySuffix: String?,
    ): AiExecutionTarget {
        val normalizedModel = modelOverride?.trim()?.takeIf { it.isNotEmpty() } ?: return this
        val normalizedSuffix = keySuffix?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
        return copy(
            key = if (normalizedSuffix.isNotEmpty()) "$key$normalizedSuffix" else key,
            modelUri = normalizedModel,
        )
    }

    private fun AiFailureCode.toAttemptReason(): AiNormalizationAttemptReason = when (this) {
        AiFailureCode.INVALID_REQUEST -> AiNormalizationAttemptReason.TARGET_INVALID_REQUEST
        AiFailureCode.TIMEOUT -> AiNormalizationAttemptReason.TARGET_TIMEOUT
        AiFailureCode.NETWORK -> AiNormalizationAttemptReason.TARGET_NETWORK
        AiFailureCode.AUTH -> AiNormalizationAttemptReason.TARGET_AUTH
        AiFailureCode.RATE_LIMITED -> AiNormalizationAttemptReason.TARGET_RATE_LIMITED
        AiFailureCode.UPSTREAM_4XX -> AiNormalizationAttemptReason.TARGET_UPSTREAM_4XX
        AiFailureCode.UPSTREAM_5XX -> AiNormalizationAttemptReason.TARGET_UPSTREAM_5XX
        AiFailureCode.MALFORMED_RESPONSE -> AiNormalizationAttemptReason.TARGET_MALFORMED_RESPONSE
        AiFailureCode.EMPTY_OUTPUT -> AiNormalizationAttemptReason.TARGET_EMPTY_OUTPUT
    }
}
