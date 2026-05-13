package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.config.ListingVisionAiConfig
import com.example.shoppingassistant.server.config.VisualSearchConfig

data class AiAgentTargetDescriptor(
    val key: String,
    val modelUri: String,
    val usesSavedAgent: Boolean,
    val promptConfigured: Boolean,
)

data class AiAgentFlowDescriptor(
    val flow: AiNormalizationFlow,
    val enabled: Boolean,
    val ready: Boolean,
    val registryVersion: String? = null,
    val routeVersion: String? = null,
    val contractName: String? = null,
    val contractVersion: String? = null,
    val killSwitchReason: String? = null,
    val circuitBreaker: AiCircuitBreakerConfig? = null,
    val targets: List<AiAgentTargetDescriptor> = emptyList(),
)

data class AiAgentRoute(
    val flow: AiNormalizationFlow,
    val transport: AiTransportConfig,
    val timeoutMs: Long,
    val targets: List<AiExecutionTarget>,
    val circuitBreaker: AiCircuitBreakerConfig = AiCircuitBreakerConfig(),
    val registryVersion: String? = null,
    val routeVersion: String? = null,
    val contractName: String? = null,
    val contractVersion: String? = null,
)

interface AiAgentRegistry {
    fun routeFor(flow: AiNormalizationFlow): AiAgentRoute?

    fun describe(flow: AiNormalizationFlow): AiAgentFlowDescriptor
}

class EnvAiAgentRegistry(
    private val visualSearchConfig: VisualSearchConfig,
    private val listingVisionAiConfig: ListingVisionAiConfig,
) : AiAgentRegistry {
    override fun routeFor(flow: AiNormalizationFlow): AiAgentRoute? = when (flow) {
        AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toRoute(flow, policy = null)
        AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toRoute(flow, policy = null)
    }

    override fun describe(flow: AiNormalizationFlow): AiAgentFlowDescriptor = when (flow) {
        AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toDescriptor(flow, policy = null)
        AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toDescriptor(flow, policy = null)
    }
}

class ResourceAiAgentRegistry(
    private val visualSearchConfig: VisualSearchConfig,
    private val listingVisionAiConfig: ListingVisionAiConfig,
    private val manifest: AiAgentRegistryManifest = AiAgentRegistryManifestLoader.load(),
) : AiAgentRegistry {
    override fun routeFor(flow: AiNormalizationFlow): AiAgentRoute? {
        val policy = manifest.flows[flow] ?: return null
        return when (flow) {
            AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toRoute(flow, policy)
            AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toRoute(flow, policy)
        }
    }

    override fun describe(flow: AiNormalizationFlow): AiAgentFlowDescriptor {
        val policy = manifest.flows[flow]
            ?: return when (flow) {
                AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toMissingDescriptor(flow)
                AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toMissingDescriptor(flow)
            }
        return when (flow) {
            AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toDescriptor(flow, policy)
            AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toDescriptor(flow, policy)
        }
    }
}

class PersistentAiAgentRegistry(
    private val visualSearchConfig: VisualSearchConfig,
    private val listingVisionAiConfig: ListingVisionAiConfig,
    private val overrideStore: PersistentAiAgentRegistryOverrideStore,
) : AiAgentRegistry {
    override fun routeFor(flow: AiNormalizationFlow): AiAgentRoute? {
        val policy = overrideStore.effectiveManifest().flows[flow] ?: return null
        return when (flow) {
            AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toRoute(flow, policy)
            AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toRoute(flow, policy)
        }
    }

    override fun describe(flow: AiNormalizationFlow): AiAgentFlowDescriptor {
        val policy = overrideStore.effectiveManifest().flows[flow]
            ?: return when (flow) {
                AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toMissingDescriptor(flow)
                AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toMissingDescriptor(flow)
            }
        return when (flow) {
            AiNormalizationFlow.VISUAL_SEARCH -> visualSearchConfig.toDescriptor(flow, policy)
            AiNormalizationFlow.LISTING_OFFER -> listingVisionAiConfig.toDescriptor(flow, policy)
        }
    }
}

private fun VisualSearchConfig.toRoute(
    flow: AiNormalizationFlow,
    policy: AiAgentFlowPolicy?,
): AiAgentRoute? {
    if (!providerReady) return null
    if (policy != null && !policy.enabled) return null
    return AiAgentRoute(
        flow = flow,
        transport = AiTransportConfig(
            baseUrl = activeBaseUrl,
            responsesBaseUrl = activeResponsesBaseUrl,
            apiKey = activeApiKey.orEmpty(),
            projectId = activeProjectId,
        ),
        timeoutMs = activeTimeoutMs,
        targets = executionTargets,
        circuitBreaker = AiCircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = circuitBreakerFailureThreshold,
            cooldownMs = circuitBreakerCooldownMs,
        ),
        registryVersion = policy?.registryVersion,
        routeVersion = policy?.routeVersion,
        contractName = policy?.contractName,
        contractVersion = policy?.contractVersion,
    )
}

private fun VisualSearchConfig.toDescriptor(
    flow: AiNormalizationFlow,
    policy: AiAgentFlowPolicy?,
): AiAgentFlowDescriptor =
    AiAgentFlowDescriptor(
        flow = flow,
        enabled = (policy?.enabled ?: true) && providerReady,
        ready = providerReady,
        registryVersion = policy?.registryVersion,
        routeVersion = policy?.routeVersion,
        contractName = policy?.contractName,
        contractVersion = policy?.contractVersion,
        killSwitchReason = if (policy?.enabled == false) {
            policy.killSwitchReason ?: "disabled_by_registry_policy"
        } else {
            null
        },
        circuitBreaker = AiCircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = circuitBreakerFailureThreshold,
            cooldownMs = circuitBreakerCooldownMs,
        ),
        targets = executionTargets.map { target ->
            AiAgentTargetDescriptor(
                key = target.key,
                modelUri = target.modelUri,
                usesSavedAgent = target.usesSavedAgent,
                promptConfigured = target.promptId != null,
            )
        },
    )

private fun VisualSearchConfig.toMissingDescriptor(flow: AiNormalizationFlow): AiAgentFlowDescriptor =
    AiAgentFlowDescriptor(
        flow = flow,
        enabled = false,
        ready = providerReady,
        killSwitchReason = "missing_registry_flow_policy",
        circuitBreaker = AiCircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = circuitBreakerFailureThreshold,
            cooldownMs = circuitBreakerCooldownMs,
        ),
        targets = executionTargets.map { target ->
            AiAgentTargetDescriptor(
                key = target.key,
                modelUri = target.modelUri,
                usesSavedAgent = target.usesSavedAgent,
                promptConfigured = target.promptId != null,
            )
        },
    )

private fun ListingVisionAiConfig.toRoute(
    flow: AiNormalizationFlow,
    policy: AiAgentFlowPolicy?,
): AiAgentRoute? {
    if (!yandexReady) return null
    if (policy != null && !policy.enabled) return null
    return AiAgentRoute(
        flow = flow,
        transport = AiTransportConfig(
            baseUrl = yandexBaseUrl,
            responsesBaseUrl = yandexResponsesBaseUrl,
            apiKey = yandexApiKey.orEmpty(),
            projectId = yandexProjectId,
        ),
        timeoutMs = yandexTimeoutMs,
        targets = executionTargets,
        circuitBreaker = AiCircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = circuitBreakerFailureThreshold,
            cooldownMs = circuitBreakerCooldownMs,
        ),
        registryVersion = policy?.registryVersion,
        routeVersion = policy?.routeVersion,
        contractName = policy?.contractName,
        contractVersion = policy?.contractVersion,
    )
}

private fun ListingVisionAiConfig.toDescriptor(
    flow: AiNormalizationFlow,
    policy: AiAgentFlowPolicy?,
): AiAgentFlowDescriptor =
    AiAgentFlowDescriptor(
        flow = flow,
        enabled = (policy?.enabled ?: true) && yandexReady,
        ready = yandexReady,
        registryVersion = policy?.registryVersion,
        routeVersion = policy?.routeVersion,
        contractName = policy?.contractName,
        contractVersion = policy?.contractVersion,
        killSwitchReason = if (policy?.enabled == false) {
            policy.killSwitchReason ?: "disabled_by_registry_policy"
        } else {
            null
        },
        circuitBreaker = AiCircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = circuitBreakerFailureThreshold,
            cooldownMs = circuitBreakerCooldownMs,
        ),
        targets = executionTargets.map { target ->
            AiAgentTargetDescriptor(
                key = target.key,
                modelUri = target.modelUri,
                usesSavedAgent = target.usesSavedAgent,
                promptConfigured = target.promptId != null,
            )
        },
    )

private fun ListingVisionAiConfig.toMissingDescriptor(flow: AiNormalizationFlow): AiAgentFlowDescriptor =
    AiAgentFlowDescriptor(
        flow = flow,
        enabled = false,
        ready = yandexReady,
        killSwitchReason = "missing_registry_flow_policy",
        circuitBreaker = AiCircuitBreakerConfig(
            enabled = circuitBreakerEnabled,
            failureThreshold = circuitBreakerFailureThreshold,
            cooldownMs = circuitBreakerCooldownMs,
        ),
        targets = executionTargets.map { target ->
            AiAgentTargetDescriptor(
                key = target.key,
                modelUri = target.modelUri,
                usesSavedAgent = target.usesSavedAgent,
                promptConfigured = target.promptId != null,
            )
        },
    )
