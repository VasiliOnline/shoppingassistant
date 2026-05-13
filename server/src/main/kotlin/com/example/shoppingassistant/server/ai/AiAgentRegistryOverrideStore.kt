package com.example.shoppingassistant.server.ai

import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

data class AiAgentRegistryEffectiveState(
    val baseManifest: AiAgentRegistryManifest,
    val effectiveManifest: AiAgentRegistryManifest,
    val persistentOverrides: List<AiAgentRegistryPersistentOverride>,
)

class PersistentAiAgentRegistryOverrideStore(
    private val repository: AiAgentRegistryOverrideRepository,
    private val baseManifestLoader: () -> AiAgentRegistryManifest = {
        AiAgentRegistryManifestLoader.loadBase()
    },
    private val runtimeOverrideApplier: (AiAgentRegistryManifest) -> AiAgentRegistryManifest = {
        AiAgentRegistryManifestLoader.applyRuntimeOverrides(it)
    },
) {
    private val state = AtomicReference(runBlocking { loadState() })

    fun baseManifest(): AiAgentRegistryManifest = state.get().baseManifest

    fun effectiveManifest(): AiAgentRegistryManifest = state.get().effectiveManifest

    fun persistentOverrides(): List<AiAgentRegistryPersistentOverride> = state.get().persistentOverrides

    fun persistentOverride(flow: AiNormalizationFlow): AiAgentRegistryPersistentOverride? =
        persistentOverrides().firstOrNull { it.flow == flow }

    suspend fun refresh(): AiAgentRegistryEffectiveState {
        val next = loadState()
        state.set(next)
        return next
    }

    suspend fun upsertOverride(
        override: AiAgentRegistryPersistentOverride,
    ): AiAgentRegistryPersistentOverride {
        repository.upsert(override)
        refresh()
        return persistentOverride(override.flow) ?: override
    }

    suspend fun deleteOverride(flow: AiNormalizationFlow): Boolean {
        val deleted = repository.delete(flow)
        if (deleted) {
            refresh()
        }
        return deleted
    }

    private suspend fun loadState(): AiAgentRegistryEffectiveState {
        val base = baseManifestLoader()
        val persistentOverrides = repository.listAll().sortedBy { it.flow.name }
        val manifestWithPersistentOverrides = buildPersistentOverrideManifest(
            base = base,
            overrides = persistentOverrides,
        )?.let { overrideManifest ->
            AiAgentRegistryManifestLoader.merge(base, overrideManifest)
        } ?: base
        val effective = runtimeOverrideApplier(manifestWithPersistentOverrides)
        return AiAgentRegistryEffectiveState(
            baseManifest = base,
            effectiveManifest = effective,
            persistentOverrides = persistentOverrides,
        )
    }

    private fun buildPersistentOverrideManifest(
        base: AiAgentRegistryManifest,
        overrides: List<AiAgentRegistryPersistentOverride>,
    ): AiAgentRegistryManifest? {
        if (overrides.isEmpty()) return null
        val registryVersion = "${base.registryVersion}+admin:${overrides.maxOf { it.updatedAtMs }}"
        val flows = overrides.associate { override ->
            override.flow to AiAgentFlowPolicy(
                enabled = override.enabled,
                registryVersion = registryVersion,
                routeVersion = override.routeVersion,
                contractName = override.contractName,
                contractVersion = override.contractVersion,
                killSwitchReason = override.killSwitchReason,
            )
        }
        return AiAgentRegistryManifest(
            registryVersion = registryVersion,
            flows = flows,
        )
    }
}

@Serializable
data class AiAgentRegistryOverrideUpdateRequest(
    val enabled: Boolean? = null,
    val routeVersion: String? = null,
    val contractName: String? = null,
    val contractVersion: String? = null,
    val killSwitchReason: String? = null,
    val updatedBy: String? = null,
    val note: String? = null,
)

@Serializable
data class AiAgentRegistryPolicySnapshot(
    val enabled: Boolean,
    val registryVersion: String,
    val routeVersion: String,
    val contractName: String,
    val contractVersion: String,
    val killSwitchReason: String? = null,
)

@Serializable
data class AiAgentRegistryPersistentOverrideSnapshot(
    val flow: String,
    val enabled: Boolean,
    val routeVersion: String,
    val contractName: String,
    val contractVersion: String,
    val killSwitchReason: String? = null,
    val updatedAtMs: Long,
    val updatedBy: String? = null,
    val note: String? = null,
)

@Serializable
data class AiAgentRegistryAdminFlowSnapshot(
    val flow: String,
    val basePolicy: AiAgentRegistryPolicySnapshot? = null,
    val effectivePolicy: AiAgentRegistryPolicySnapshot? = null,
    val persistentOverride: AiAgentRegistryPersistentOverrideSnapshot? = null,
)

@Serializable
data class AiAgentRegistryAdminSnapshot(
    val capturedAtMs: Long,
    val baseRegistryVersion: String,
    val effectiveRegistryVersion: String,
    val flows: List<AiAgentRegistryAdminFlowSnapshot> = emptyList(),
)

class AiAgentRegistryAdminService(
    private val overrideStore: PersistentAiAgentRegistryOverrideStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun snapshot(): AiAgentRegistryAdminSnapshot {
        val baseManifest = overrideStore.baseManifest()
        val effectiveManifest = overrideStore.effectiveManifest()
        val persistentOverrides = overrideStore.persistentOverrides().associateBy { it.flow }
        return AiAgentRegistryAdminSnapshot(
            capturedAtMs = clock.millis(),
            baseRegistryVersion = baseManifest.registryVersion,
            effectiveRegistryVersion = effectiveManifest.registryVersion,
            flows = AiNormalizationFlow.entries.map { flow ->
                AiAgentRegistryAdminFlowSnapshot(
                    flow = flow.name,
                    basePolicy = baseManifest.flows[flow]?.toSnapshot(),
                    effectivePolicy = effectiveManifest.flows[flow]?.toSnapshot(),
                    persistentOverride = persistentOverrides[flow]?.toSnapshot(),
                )
            },
        )
    }

    suspend fun upsert(
        flow: AiNormalizationFlow,
        request: AiAgentRegistryOverrideUpdateRequest,
    ): AiAgentRegistryPersistentOverride {
        val basePolicy = overrideStore.baseManifest().flows[flow]
            ?: throw NoSuchElementException("unsupported_flow:$flow")
        val currentOverride = overrideStore.persistentOverride(flow)

        val enabled = request.enabled ?: currentOverride?.enabled ?: basePolicy.enabled
        val routeVersion = request.routeVersion.resolveText(currentOverride?.routeVersion ?: basePolicy.routeVersion)
        val contractName = request.contractName.resolveText(currentOverride?.contractName ?: basePolicy.contractName)
        val contractVersion = request.contractVersion.resolveText(
            currentOverride?.contractVersion ?: basePolicy.contractVersion,
        )
        val killSwitchReason = if (enabled) {
            null
        } else {
            request.killSwitchReason.resolveOptionalText(
                currentOverride?.killSwitchReason ?: basePolicy.killSwitchReason,
            ) ?: "disabled_by_admin_override"
        }
        return overrideStore.upsertOverride(
            AiAgentRegistryPersistentOverride(
                flow = flow,
                enabled = enabled,
                routeVersion = routeVersion,
                contractName = contractName,
                contractVersion = contractVersion,
                killSwitchReason = killSwitchReason,
                updatedAtMs = clock.millis(),
                updatedBy = request.updatedBy.resolveOptionalText(currentOverride?.updatedBy),
                note = request.note.resolveOptionalText(currentOverride?.note),
            ),
        )
    }

    suspend fun delete(flow: AiNormalizationFlow): Boolean = overrideStore.deleteOverride(flow)

    private fun AiAgentFlowPolicy.toSnapshot(): AiAgentRegistryPolicySnapshot =
        AiAgentRegistryPolicySnapshot(
            enabled = enabled,
            registryVersion = registryVersion,
            routeVersion = routeVersion,
            contractName = contractName,
            contractVersion = contractVersion,
            killSwitchReason = killSwitchReason,
        )

    private fun AiAgentRegistryPersistentOverride.toSnapshot(): AiAgentRegistryPersistentOverrideSnapshot =
        AiAgentRegistryPersistentOverrideSnapshot(
            flow = flow.name,
            enabled = enabled,
            routeVersion = routeVersion,
            contractName = contractName,
            contractVersion = contractVersion,
            killSwitchReason = killSwitchReason,
            updatedAtMs = updatedAtMs,
            updatedBy = updatedBy,
            note = note,
        )

    private fun String?.resolveText(current: String): String =
        this?.trim()?.takeIf { it.isNotEmpty() } ?: current

    private fun String?.resolveOptionalText(current: String?): String? =
        when (this) {
            null -> current
            else -> trim().takeIf { it.isNotEmpty() }
        }
}
