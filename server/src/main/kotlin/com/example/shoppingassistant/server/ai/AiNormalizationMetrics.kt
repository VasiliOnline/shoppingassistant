package com.example.shoppingassistant.server.ai

import java.time.Clock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable
data class AiNormalizationMetricsSnapshot(
    val capturedAtMs: Long,
    val flows: List<AiNormalizationFlowMetricsSnapshot> = emptyList(),
)

@Serializable
data class AiNormalizationFlowMetricsSnapshot(
    val flow: String,
    val attempts: Long,
    val successes: Long,
    val parseMisses: Long,
    val failures: Long,
    val skippedOpenCircuit: Long,
    val recentLatencyP50Ms: Long? = null,
    val recentLatencyP95Ms: Long? = null,
    val lastAttemptAtMs: Long? = null,
    val targets: List<AiNormalizationTargetMetricsSnapshot> = emptyList(),
)

@Serializable
data class AiNormalizationTargetMetricsSnapshot(
    val targetKey: String,
    val attempts: Long,
    val successes: Long,
    val parseMisses: Long,
    val failures: Long,
    val skippedOpenCircuit: Long,
    val recentLatencyP50Ms: Long? = null,
    val recentLatencyP95Ms: Long? = null,
    val lastAttemptAtMs: Long? = null,
    val lastReason: String? = null,
    val lastFailureCode: String? = null,
)

interface AiNormalizationMetricsStore {
    fun snapshot(): AiNormalizationMetricsSnapshot
}

class InMemoryAiNormalizationMetricsStore(
    private val clock: Clock = Clock.systemUTC(),
    private val latencyWindowSize: Int = 256,
) : AiNormalizationTelemetry, AiNormalizationMetricsStore {

    private val flowStats = ConcurrentHashMap<AiNormalizationFlow, FlowStats>()

    override fun recordAttempt(attempt: AiNormalizationAttempt) {
        val now = clock.millis()
        val stats = flowStats.computeIfAbsent(attempt.flow) { FlowStats(latencyWindowSize) }
        stats.record(attempt, now)
    }

    override fun snapshot(): AiNormalizationMetricsSnapshot {
        val capturedAtMs = clock.millis()
        return AiNormalizationMetricsSnapshot(
            capturedAtMs = capturedAtMs,
            flows = flowStats.entries
                .sortedBy { it.key.name }
                .map { (flow, stats) -> stats.snapshot(flow) },
        )
    }

    private class FlowStats(
        private val latencyWindowSize: Int,
    ) {
        private var attempts: Long = 0
        private var successes: Long = 0
        private var parseMisses: Long = 0
        private var failures: Long = 0
        private var skippedOpenCircuit: Long = 0
        private var lastAttemptAtMs: Long? = null
        private val latencies = ArrayDeque<Long>()
        private val targetStats = linkedMapOf<String, TargetStats>()

        @Synchronized
        fun record(attempt: AiNormalizationAttempt, nowMs: Long) {
            attempts += 1
            lastAttemptAtMs = nowMs
            when (attempt.outcome) {
                AiNormalizationAttemptOutcome.SUCCESS -> successes += 1
                AiNormalizationAttemptOutcome.PARSE_MISS -> parseMisses += 1
                AiNormalizationAttemptOutcome.FAILURE -> failures += 1
                AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT -> skippedOpenCircuit += 1
            }
            appendLatency(latencies, attempt.latencyMs)
            val target = targetStats.getOrPut(attempt.target.key) { TargetStats(latencyWindowSize) }
            target.record(attempt, nowMs)
        }

        @Synchronized
        fun snapshot(flow: AiNormalizationFlow): AiNormalizationFlowMetricsSnapshot =
            AiNormalizationFlowMetricsSnapshot(
                flow = flow.name,
                attempts = attempts,
                successes = successes,
                parseMisses = parseMisses,
                failures = failures,
                skippedOpenCircuit = skippedOpenCircuit,
                recentLatencyP50Ms = percentile(latencies, 0.50),
                recentLatencyP95Ms = percentile(latencies, 0.95),
                lastAttemptAtMs = lastAttemptAtMs,
                targets = targetStats.entries.map { (key, stats) -> stats.snapshot(key) },
            )

        private fun appendLatency(buffer: ArrayDeque<Long>, latencyMs: Long) {
            if (buffer.size >= latencyWindowSize) {
                buffer.removeFirst()
            }
            buffer.addLast(latencyMs)
        }
    }

    private class TargetStats(
        private val latencyWindowSize: Int,
    ) {
        private var attempts: Long = 0
        private var successes: Long = 0
        private var parseMisses: Long = 0
        private var failures: Long = 0
        private var skippedOpenCircuit: Long = 0
        private var lastAttemptAtMs: Long? = null
        private var lastReason: String? = null
        private var lastFailureCode: String? = null
        private val latencies = ArrayDeque<Long>()

        fun record(attempt: AiNormalizationAttempt, nowMs: Long) {
            attempts += 1
            lastAttemptAtMs = nowMs
            lastReason = attempt.reason.name
            lastFailureCode = attempt.failureCode?.name
            when (attempt.outcome) {
                AiNormalizationAttemptOutcome.SUCCESS -> successes += 1
                AiNormalizationAttemptOutcome.PARSE_MISS -> parseMisses += 1
                AiNormalizationAttemptOutcome.FAILURE -> failures += 1
                AiNormalizationAttemptOutcome.SKIPPED_OPEN_CIRCUIT -> skippedOpenCircuit += 1
            }
            if (latencies.size >= latencyWindowSize) {
                latencies.removeFirst()
            }
            latencies.addLast(attempt.latencyMs)
        }

        fun snapshot(targetKey: String): AiNormalizationTargetMetricsSnapshot =
            AiNormalizationTargetMetricsSnapshot(
                targetKey = targetKey,
                attempts = attempts,
                successes = successes,
                parseMisses = parseMisses,
                failures = failures,
                skippedOpenCircuit = skippedOpenCircuit,
                recentLatencyP50Ms = percentile(latencies, 0.50),
                recentLatencyP95Ms = percentile(latencies, 0.95),
                lastAttemptAtMs = lastAttemptAtMs,
                lastReason = lastReason,
                lastFailureCode = lastFailureCode,
            )
    }
}

private fun percentile(values: ArrayDeque<Long>, ratio: Double): Long? {
    if (values.isEmpty()) return null
    val sorted = values.toMutableList().sorted()
    val index = kotlin.math.ceil(sorted.size * ratio).toInt().coerceIn(1, sorted.size) - 1
    return sorted[index]
}
