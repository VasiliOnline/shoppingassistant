package com.example.shoppingassistant.server.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AiAgentRegistryOverrideStoreTest {

    @Test
    fun persistent_override_updates_effective_manifest_and_admin_snapshot() = runBlocking {
        val repository = InMemoryAiAgentRegistryOverrideRepository()
        val baseManifest = AiAgentRegistryManifest(
            registryVersion = "registry-base-v1",
            flows = mapOf(
                AiNormalizationFlow.VISUAL_SEARCH to AiAgentFlowPolicy(
                    enabled = true,
                    registryVersion = "registry-base-v1",
                    routeVersion = "visual-search/route/1",
                    contractName = "visual_search",
                    contractVersion = "visual-search/v1",
                ),
                AiNormalizationFlow.LISTING_OFFER to AiAgentFlowPolicy(
                    enabled = true,
                    registryVersion = "registry-base-v1",
                    routeVersion = "listing-offer/route/1",
                    contractName = "listing_offer",
                    contractVersion = "listing-offer/v1",
                ),
            ),
        )
        val store = PersistentAiAgentRegistryOverrideStore(
            repository = repository,
            baseManifestLoader = { baseManifest },
            runtimeOverrideApplier = { it },
        )
        val service = AiAgentRegistryAdminService(
            overrideStore = store,
            clock = FixedAiRegistryClock(50_000L),
        )

        val override = service.upsert(
            flow = AiNormalizationFlow.VISUAL_SEARCH,
            request = AiAgentRegistryOverrideUpdateRequest(
                enabled = false,
                routeVersion = "visual-search/route/2",
                contractVersion = "visual-search/v2",
                killSwitchReason = "manual-disable",
                updatedBy = "ops",
                note = "hotfix rollout stop",
            ),
        )
        val snapshot = service.snapshot()
        val searchFlow = snapshot.flows.first { it.flow == AiNormalizationFlow.VISUAL_SEARCH.name }
        val listingFlow = snapshot.flows.first { it.flow == AiNormalizationFlow.LISTING_OFFER.name }

        assertTrue(override.updatedAtMs > 0L)
        assertNotNull(searchFlow.persistentOverride)
        assertEquals("visual-search/route/2", searchFlow.effectivePolicy?.routeVersion)
        assertEquals("manual-disable", searchFlow.effectivePolicy?.killSwitchReason)
        assertTrue(snapshot.effectiveRegistryVersion.startsWith("registry-base-v1+admin:"))
        assertEquals(snapshot.effectiveRegistryVersion, searchFlow.effectivePolicy?.registryVersion)
        assertEquals(snapshot.effectiveRegistryVersion, listingFlow.effectivePolicy?.registryVersion)
    }

    @Test
    fun delete_override_restores_base_policy() = runBlocking {
        val repository = InMemoryAiAgentRegistryOverrideRepository(
            initial = listOf(
                AiAgentRegistryPersistentOverride(
                    flow = AiNormalizationFlow.LISTING_OFFER,
                    enabled = false,
                    routeVersion = "listing-offer/route/2",
                    contractName = "listing_offer",
                    contractVersion = "listing-offer/v2",
                    killSwitchReason = "incident",
                    updatedAtMs = 100L,
                    updatedBy = "ops",
                ),
            ),
        )
        val baseManifest = AiAgentRegistryManifest(
            registryVersion = "registry-base-v1",
            flows = mapOf(
                AiNormalizationFlow.LISTING_OFFER to AiAgentFlowPolicy(
                    enabled = true,
                    registryVersion = "registry-base-v1",
                    routeVersion = "listing-offer/route/1",
                    contractName = "listing_offer",
                    contractVersion = "listing-offer/v1",
                ),
            ),
        )
        val store = PersistentAiAgentRegistryOverrideStore(
            repository = repository,
            baseManifestLoader = { baseManifest },
            runtimeOverrideApplier = { it },
        )
        val service = AiAgentRegistryAdminService(
            overrideStore = store,
            clock = FixedAiRegistryClock(60_000L),
        )

        val deleted = service.delete(AiNormalizationFlow.LISTING_OFFER)
        val snapshot = service.snapshot()
        val listingFlow = snapshot.flows.first { it.flow == AiNormalizationFlow.LISTING_OFFER.name }

        assertTrue(deleted)
        assertEquals("registry-base-v1", snapshot.effectiveRegistryVersion)
        assertNull(listingFlow.persistentOverride)
        assertEquals("listing-offer/route/1", listingFlow.effectivePolicy?.routeVersion)
    }
}

private class InMemoryAiAgentRegistryOverrideRepository(
    initial: List<AiAgentRegistryPersistentOverride> = emptyList(),
) : AiAgentRegistryOverrideRepository {
    private val rows = linkedMapOf<AiNormalizationFlow, AiAgentRegistryPersistentOverride>()

    init {
        initial.forEach { rows[it.flow] = it }
    }

    override suspend fun listAll(): List<AiAgentRegistryPersistentOverride> = rows.values.toList()

    override suspend fun upsert(
        override: AiAgentRegistryPersistentOverride,
    ): AiAgentRegistryPersistentOverride {
        rows[override.flow] = override
        return override
    }

    override suspend fun delete(flow: AiNormalizationFlow): Boolean = rows.remove(flow) != null
}

private class FixedAiRegistryClock(
    private val nowMs: Long,
) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this
}
