package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.config.envValue
import com.example.shoppingassistant.server.config.parseBooleanEnv
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AiAgentFlowPolicy(
    val enabled: Boolean,
    val registryVersion: String,
    val routeVersion: String,
    val contractName: String,
    val contractVersion: String,
    val killSwitchReason: String? = null,
)

data class AiAgentRegistryManifest(
    val registryVersion: String,
    val flows: Map<AiNormalizationFlow, AiAgentFlowPolicy>,
)

@Serializable
private data class AiAgentRegistryManifestPayload(
    val registry_version: String,
    val flows: Map<String, AiAgentFlowPolicyPayload> = emptyMap(),
)

@Serializable
private data class AiAgentFlowPolicyPayload(
    val enabled: Boolean = true,
    val route_version: String,
    val contract_name: String,
    val contract_version: String,
    val kill_switch_reason: String? = null,
)

object AiAgentRegistryManifestLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }
    private val baseCache = ConcurrentHashMap<String, AiAgentRegistryManifest>()

    fun load(resourcePath: String = DEFAULT_RESOURCE_PATH): AiAgentRegistryManifest =
        applyRuntimeOverrides(loadBase(resourcePath))

    fun loadBase(resourcePath: String = DEFAULT_RESOURCE_PATH): AiAgentRegistryManifest =
        baseCache.getOrPut(resourcePath) { parse(readText(resourcePath)) }

    internal fun parse(rawJson: String): AiAgentRegistryManifest {
        val payload = json.decodeFromString(AiAgentRegistryManifestPayload.serializer(), rawJson)
        val flows = payload.flows.entries.associate { (rawFlow, policy) ->
            val flow = runCatching {
                AiNormalizationFlow.valueOf(rawFlow.trim().uppercase())
            }.getOrElse {
                error("Unknown AI normalization flow in registry manifest: $rawFlow")
            }
            flow to AiAgentFlowPolicy(
                enabled = policy.enabled,
                registryVersion = payload.registry_version,
                routeVersion = policy.route_version.trim(),
                contractName = policy.contract_name.trim(),
                contractVersion = policy.contract_version.trim(),
                killSwitchReason = policy.kill_switch_reason?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return AiAgentRegistryManifest(
            registryVersion = payload.registry_version,
            flows = flows,
        )
    }

    internal fun merge(
        base: AiAgentRegistryManifest,
        override: AiAgentRegistryManifest,
    ): AiAgentRegistryManifest {
        val mergedFlows = linkedMapOf<AiNormalizationFlow, AiAgentFlowPolicy>()
        (base.flows.keys + override.flows.keys).forEach { flow ->
            val basePolicy = base.flows[flow]
            val overridePolicy = override.flows[flow]
            when {
                basePolicy == null && overridePolicy != null -> mergedFlows[flow] = overridePolicy.copy(
                    registryVersion = override.registryVersion,
                )
                basePolicy != null && overridePolicy == null -> mergedFlows[flow] = basePolicy.copy(
                    registryVersion = override.registryVersion,
                )
                basePolicy != null && overridePolicy != null -> mergedFlows[flow] = basePolicy.copy(
                    enabled = overridePolicy.enabled,
                    registryVersion = override.registryVersion,
                    routeVersion = overridePolicy.routeVersion,
                    contractName = overridePolicy.contractName,
                    contractVersion = overridePolicy.contractVersion,
                    killSwitchReason = overridePolicy.killSwitchReason,
                )
            }
        }
        return AiAgentRegistryManifest(
            registryVersion = override.registryVersion,
            flows = mergedFlows,
        )
    }

    fun applyRuntimeOverrides(manifest: AiAgentRegistryManifest): AiAgentRegistryManifest {
        val withOverride = loadOverrideManifest()?.let { override -> merge(manifest, override) } ?: manifest
        return applyEnvOverrides(withOverride)
    }

    private fun loadOverrideManifest(): AiAgentRegistryManifest? {
        val inlineOverride = envValue("AI_AGENT_REGISTRY_OVERRIDE_JSON")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (inlineOverride != null) {
            return parse(inlineOverride)
        }
        val overridePath = envValue("AI_AGENT_REGISTRY_OVERRIDE_PATH")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val path = Paths.get(overridePath)
        if (!Files.isRegularFile(path)) return null
        return parse(Files.readString(path))
    }

    private fun applyEnvOverrides(manifest: AiAgentRegistryManifest): AiAgentRegistryManifest {
        val overrides = manifest.flows.mapValues { (flow, policy) ->
            val enabled = when (flow) {
                AiNormalizationFlow.VISUAL_SEARCH -> parseBooleanEnv(
                    key = "VISUAL_SEARCH_AI_ROUTE_ENABLED",
                    defaultValue = policy.enabled,
                )
                AiNormalizationFlow.LISTING_OFFER -> parseBooleanEnv(
                    primaryKey = "VISION_AI_ROUTE_ENABLED",
                    fallbackKey = "LISTING_AI_ROUTE_ENABLED",
                    defaultValue = policy.enabled,
                )
            }
            val routeVersion = when (flow) {
                AiNormalizationFlow.VISUAL_SEARCH -> envValue("VISUAL_SEARCH_AI_ROUTE_VERSION")
                AiNormalizationFlow.LISTING_OFFER -> envValue("VISION_AI_ROUTE_VERSION", "LISTING_AI_ROUTE_VERSION")
            }?.trim()?.takeIf { it.isNotEmpty() } ?: policy.routeVersion
            val contractVersion = when (flow) {
                AiNormalizationFlow.VISUAL_SEARCH -> envValue("VISUAL_SEARCH_AI_CONTRACT_VERSION")
                AiNormalizationFlow.LISTING_OFFER -> envValue("VISION_AI_CONTRACT_VERSION", "LISTING_AI_CONTRACT_VERSION")
            }?.trim()?.takeIf { it.isNotEmpty() } ?: policy.contractVersion
            val killSwitchReason = when (flow) {
                AiNormalizationFlow.VISUAL_SEARCH -> envValue("VISUAL_SEARCH_AI_KILL_SWITCH_REASON")
                AiNormalizationFlow.LISTING_OFFER -> envValue("VISION_AI_KILL_SWITCH_REASON", "LISTING_AI_KILL_SWITCH_REASON")
            }?.trim()?.takeIf { it.isNotEmpty() }

            policy.copy(
                enabled = enabled,
                routeVersion = routeVersion,
                contractVersion = contractVersion,
                killSwitchReason = if (!enabled) {
                    killSwitchReason ?: policy.killSwitchReason ?: "disabled_by_env_override"
                } else {
                    killSwitchReason ?: policy.killSwitchReason
                },
            )
        }
        val registryVersion = envValue("AI_AGENT_REGISTRY_VERSION_OVERRIDE")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: manifest.registryVersion
        return AiAgentRegistryManifest(
            registryVersion = registryVersion,
            flows = overrides,
        )
    }

    private fun readText(resourcePath: String): String =
        javaClass.classLoader.getResourceAsStream(resourcePath)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("AI agent registry resource not found: $resourcePath")

    private const val DEFAULT_RESOURCE_PATH = "ai/agent_registry.json"
}
