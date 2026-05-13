package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioFailureCode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioInvocationMode
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioStructuredResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

class AiNormalizationMetricsStoreTest {

    @Test
    fun records_flow_and_target_metrics_with_recent_latency_percentiles() {
        val clock = MutableMetricsClock(10_000L)
        val store = InMemoryAiNormalizationMetricsStore(clock = clock, latencyWindowSize = 16)
        val target = YandexAiExecutionTarget(
            key = "primary",
            modelUri = "gpt://folder/gemma/latest",
            promptId = "agent-primary",
        )

        store.recordAttempt(
            AiNormalizationAttempt(
                flow = AiNormalizationFlow.VISUAL_SEARCH,
                target = target,
                outcome = AiNormalizationAttemptOutcome.SUCCESS,
                reason = AiNormalizationAttemptReason.TARGET_SUCCESS,
                latencyMs = 100,
                response = YandexAiStudioStructuredResponse.Success(
                    responseBody = """{"ok":true}""",
                    httpStatus = 200,
                    invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                ),
            ),
        )
        clock.advanceBy(100L)
        store.recordAttempt(
            AiNormalizationAttempt(
                flow = AiNormalizationFlow.VISUAL_SEARCH,
                target = target,
                outcome = AiNormalizationAttemptOutcome.FAILURE,
                reason = AiNormalizationAttemptReason.TARGET_UPSTREAM_5XX,
                latencyMs = 450,
                response = YandexAiStudioStructuredResponse.Failure(
                    code = YandexAiStudioFailureCode.UPSTREAM_5XX,
                    httpStatus = 500,
                    invocationMode = YandexAiStudioInvocationMode.RESPONSES,
                ),
            ),
        )

        val snapshot = store.snapshot()
        val flow = snapshot.flows.firstOrNull { it.flow == AiNormalizationFlow.VISUAL_SEARCH.name }
        val targetSnapshot = flow?.targets?.firstOrNull { it.targetKey == "primary" }

        assertNotNull(flow)
        assertEquals(2, flow.attempts)
        assertEquals(1, flow.successes)
        assertEquals(1, flow.failures)
        assertEquals(450L, flow.recentLatencyP95Ms)
        assertNotNull(targetSnapshot)
        assertEquals(2, targetSnapshot.attempts)
        assertEquals("TARGET_UPSTREAM_5XX", targetSnapshot.lastReason)
        assertEquals("UPSTREAM_5XX", targetSnapshot.lastFailureCode)
    }
}

private class MutableMetricsClock(
    private var currentMs: Long,
) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(currentMs)

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    fun advanceBy(deltaMs: Long) {
        currentMs += deltaMs
    }
}
