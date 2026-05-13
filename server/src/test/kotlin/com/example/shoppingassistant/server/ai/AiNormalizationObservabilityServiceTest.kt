package com.example.shoppingassistant.server.ai

import com.example.shoppingassistant.server.ai.yandex.YandexAiExecutionTarget
import com.example.shoppingassistant.server.ai.yandex.YandexAiStudioTransportConfig
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class AiNormalizationObservabilityServiceTest {

    @Test
    fun snapshot_includes_disabled_flow_policy_and_live_metrics() {
        val registry = object : AiAgentRegistry {
            override fun routeFor(flow: AiNormalizationFlow): AiAgentRoute? =
                if (flow == AiNormalizationFlow.VISUAL_SEARCH) {
                    AiAgentRoute(
                        flow = flow,
                        transport = YandexAiStudioTransportConfig(
                            baseUrl = "https://example.test/v1",
                            responsesBaseUrl = "https://example.test/v1",
                            apiKey = "token",
                            projectId = "folder",
                        ),
                        timeoutMs = 5_000L,
                        targets = listOf(
                            YandexAiExecutionTarget(
                                key = "primary",
                                modelUri = "gpt://folder/gemma/latest",
                                promptId = "agent-primary",
                            ),
                        ),
                        registryVersion = "registry-v1",
                        routeVersion = "visual-search/route/1",
                        contractName = "visual_search",
                        contractVersion = "visual-search/v1",
                    )
                } else {
                    null
                }

            override fun describe(flow: AiNormalizationFlow): AiAgentFlowDescriptor =
                if (flow == AiNormalizationFlow.VISUAL_SEARCH) {
                    AiAgentFlowDescriptor(
                        flow = flow,
                        enabled = true,
                        ready = true,
                        registryVersion = "registry-v1",
                        routeVersion = "visual-search/route/1",
                        contractName = "visual_search",
                        contractVersion = "visual-search/v1",
                        targets = listOf(
                            AiAgentTargetDescriptor(
                                key = "primary",
                                modelUri = "gpt://folder/gemma/latest",
                                usesSavedAgent = true,
                                promptConfigured = true,
                            ),
                        ),
                    )
                } else {
                    AiAgentFlowDescriptor(
                        flow = flow,
                        enabled = false,
                        ready = false,
                        registryVersion = "registry-v1",
                        routeVersion = "listing-offer/route/1",
                        contractName = "listing_offer",
                        contractVersion = "listing-offer/v1",
                        killSwitchReason = "manual-disable",
                    )
                }
        }
        val metricsStore = InMemoryAiNormalizationMetricsStore(clock = FixedSnapshotClock(20_000L))
        metricsStore.recordAttempt(
            AiNormalizationAttempt(
                flow = AiNormalizationFlow.VISUAL_SEARCH,
                target = YandexAiExecutionTarget(
                    key = "primary",
                    modelUri = "gpt://folder/gemma/latest",
                    promptId = "agent-primary",
                ),
                registryVersion = "registry-v1",
                routeVersion = "visual-search/route/1",
                contractName = "visual_search",
                contractVersion = "visual-search/v1",
                outcome = AiNormalizationAttemptOutcome.SUCCESS,
                reason = AiNormalizationAttemptReason.TARGET_SUCCESS,
                latencyMs = 120L,
            ),
        )
        val service = AiNormalizationObservabilityService(
            agentRegistry = registry,
            metricsStore = metricsStore,
            healthPolicy = NoopAiTargetHealthPolicy,
            clock = FixedSnapshotClock(21_000L),
        )

        val snapshot = service.snapshot()
        val searchFlow = snapshot.flows.firstOrNull { it.flow == AiNormalizationFlow.VISUAL_SEARCH.name }
        val listingFlow = snapshot.flows.firstOrNull { it.flow == AiNormalizationFlow.LISTING_OFFER.name }

        assertEquals("registry-v1", snapshot.registryVersion)
        assertNotNull(searchFlow)
        assertTrue(searchFlow.enabled)
        assertEquals(1, searchFlow.metrics?.attempts)
        assertNotNull(listingFlow)
        assertTrue(!listingFlow.enabled)
        assertEquals("manual-disable", listingFlow.killSwitchReason)
    }
}

private class FixedSnapshotClock(
    private val currentMs: Long,
) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(currentMs)

    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this
}
