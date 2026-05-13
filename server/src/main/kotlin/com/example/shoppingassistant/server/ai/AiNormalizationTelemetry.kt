package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import org.slf4j.LoggerFactory

enum class AiNormalizationAttemptOutcome {
    SUCCESS,
    PARSE_MISS,
    FAILURE,
    SKIPPED_OPEN_CIRCUIT,
}

enum class AiNormalizationAttemptReason {
    TARGET_SUCCESS,
    TARGET_PARSE_MISS,
    TARGET_OPEN_CIRCUIT,
    TARGET_INVALID_REQUEST,
    TARGET_TIMEOUT,
    TARGET_NETWORK,
    TARGET_AUTH,
    TARGET_RATE_LIMITED,
    TARGET_UPSTREAM_4XX,
    TARGET_UPSTREAM_5XX,
    TARGET_MALFORMED_RESPONSE,
    TARGET_EMPTY_OUTPUT,
}

data class AiNormalizationAttempt(
    val flow: AiNormalizationFlow,
    val target: AiExecutionTarget,
    val registryVersion: String? = null,
    val routeVersion: String? = null,
    val contractName: String? = null,
    val contractVersion: String? = null,
    val outcome: AiNormalizationAttemptOutcome,
    val reason: AiNormalizationAttemptReason,
    val latencyMs: Long,
    val response: AiStructuredResponse? = null,
    val cooldownUntilEpochMs: Long? = null,
) {
    val failureCode: AiFailureCode?
        get() = (response as? YandexAiStudioStructuredResponse.Failure)?.code

    fun affectsCircuitBreaker(): Boolean = when (outcome) {
        AiNormalizationAttemptOutcome.SUCCESS -> false
        AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT -> false
        AiNormalizationAttemptOutcome.PARSE_MISS -> true
        AiNormalizationAttemptOutcome.FAILURE -> failureCode != AiFailureCode.INVALID_REQUEST &&
            failureCode != AiFailureCode.RATE_LIMITED
    }
}

interface AiNormalizationTelemetry {
    fun recordAttempt(attempt: AiNormalizationAttempt)
}

class CompositeAiNormalizationTelemetry(
    private val delegates: List<AiNormalizationTelemetry>,
) : AiNormalizationTelemetry {
    override fun recordAttempt(attempt: AiNormalizationAttempt) {
        delegates.forEach { delegate -> delegate.recordAttempt(attempt) }
    }
}

object NoopAiNormalizationTelemetry : AiNormalizationTelemetry {
    override fun recordAttempt(attempt: AiNormalizationAttempt) = Unit
}

class LoggingAiNormalizationTelemetry : AiNormalizationTelemetry {
    private val logger = LoggerFactory.getLogger("AiNormalization")

    override fun recordAttempt(attempt: AiNormalizationAttempt) {
        val response = attempt.response
        val invocationMode = when (response) {
            is YandexAiStudioStructuredResponse.Success -> response.invocationMode.name
            is YandexAiStudioStructuredResponse.Failure -> response.invocationMode.name
            null -> "LOCAL"
        }
        val httpStatus = when (response) {
            is YandexAiStudioStructuredResponse.Success -> response.httpStatus.toString()
            is YandexAiStudioStructuredResponse.Failure -> response.httpStatus?.toString() ?: "-"
            null -> "-"
        }
        val failureCode = attempt.failureCode?.name ?: "-"
        val cooldownUntil = attempt.cooldownUntilEpochMs?.toString() ?: "-"
        val registryVersion = attempt.registryVersion ?: "-"
        val routeVersion = attempt.routeVersion ?: "-"
        val contractName = attempt.contractName ?: "-"
        val contractVersion = attempt.contractVersion ?: "-"
        val template = "ai.normalize.attempt flow={} target={} registryVersion={} routeVersion={} contract={} contractVersion={} outcome={} reason={} latencyMs={} invocation={} httpStatus={} failureCode={} cooldownUntil={}"
        when (attempt.outcome) {
            AiNormalizationAttemptOutcome.SUCCESS,
            AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT,
            -> logger.info(
                template,
                attempt.flow.name,
                attempt.target.key,
                registryVersion,
                routeVersion,
                contractName,
                contractVersion,
                attempt.outcome.name,
                attempt.reason.name,
                attempt.latencyMs,
                invocationMode,
                httpStatus,
                failureCode,
                cooldownUntil,
            )

            AiNormalizationAttemptOutcome.PARSE_MISS,
            AiNormalizationAttemptOutcome.FAILURE,
            -> logger.warn(
                template,
                attempt.flow.name,
                attempt.target.key,
                registryVersion,
                routeVersion,
                contractName,
                contractVersion,
                attempt.outcome.name,
                attempt.reason.name,
                attempt.latencyMs,
                invocationMode,
                httpStatus,
                failureCode,
                cooldownUntil,
            )
        }
    }
}
