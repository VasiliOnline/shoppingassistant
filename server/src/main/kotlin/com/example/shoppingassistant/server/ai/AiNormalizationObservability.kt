package com.example.shoppingassistant.server.ai

import java.time.Clock
import kotlinx.serialization.Serializable

@Serializable
data class AiNormalizationObservabilitySnapshot(
    val capturedAtMs: Long,
    val registryVersion: String? = null,
    val flows: List<AiNormalizationFlowSnapshot> = emptyList(),
)

@Serializable
data class AiNormalizationFlowSnapshot(
    val flow: String,
    val enabled: Boolean,
    val ready: Boolean,
    val registryVersion: String? = null,
    val routeVersion: String? = null,
    val contractName: String? = null,
    val contractVersion: String? = null,
    val killSwitchReason: String? = null,
    val circuitBreaker: AiCircuitBreakerConfig? = null,
    val targets: List<AiNormalizationTargetSnapshot> = emptyList(),
    val metrics: AiNormalizationFlowMetricsSnapshot? = null,
)

@Serializable
data class AiNormalizationTargetSnapshot(
    val key: String,
    val modelUri: String,
    val usesSavedAgent: Boolean,
    val promptConfigured: Boolean,
    val consecutiveFailures: Int = 0,
    val openUntilEpochMs: Long? = null,
    val metrics: AiNormalizationTargetMetricsSnapshot? = null,
)

class AiNormalizationObservabilityService(
    private val agentRegistry: AiAgentRegistry,
    private val metricsStore: AiNormalizationMetricsStore,
    private val healthPolicy: AiTargetHealthPolicy,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun snapshot(): AiNormalizationObservabilitySnapshot {
        val metrics = metricsStore.snapshot()
        val metricsByFlow = metrics.flows.associateBy { it.flow }
        val flows = AiNormalizationFlow.entries.map { flow ->
            val descriptor = agentRegistry.describe(flow)
            val route = agentRegistry.routeFor(flow)
            val healthByTarget = route?.let { activeRoute ->
                healthPolicy.snapshot(activeRoute).associateBy { it.targetKey }
            }.orEmpty()
            val flowMetrics = metricsByFlow[flow.name]
            AiNormalizationFlowSnapshot(
                flow = flow.name,
                enabled = descriptor.enabled,
                ready = descriptor.ready,
                registryVersion = descriptor.registryVersion,
                routeVersion = descriptor.routeVersion,
                contractName = descriptor.contractName,
                contractVersion = descriptor.contractVersion,
                killSwitchReason = descriptor.killSwitchReason,
                circuitBreaker = descriptor.circuitBreaker,
                targets = descriptor.targets.map { target ->
                    val targetMetrics = flowMetrics?.targets?.firstOrNull { it.targetKey == target.key }
                    val health = healthByTarget[target.key]
                    AiNormalizationTargetSnapshot(
                        key = target.key,
                        modelUri = target.modelUri,
                        usesSavedAgent = target.usesSavedAgent,
                        promptConfigured = target.promptConfigured,
                        consecutiveFailures = health?.consecutiveFailures ?: 0,
                        openUntilEpochMs = health?.openUntilEpochMs,
                        metrics = targetMetrics,
                    )
                },
                metrics = flowMetrics,
            )
        }
        return AiNormalizationObservabilitySnapshot(
            capturedAtMs = clock.millis(),
            registryVersion = flows.firstNotNullOfOrNull { it.registryVersion },
            flows = flows,
        )
    }
}
