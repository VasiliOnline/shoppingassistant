package com.example.shoppingassistant.server.ai

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class AiAgentRegistryManifestLoaderTest {

    @Test
    fun loads_default_registry_manifest() {
        val manifest = AiAgentRegistryManifestLoader.load()

        assertEquals("2026-04-02.2", manifest.registryVersion)
        assertEquals("visual-search/route/1", manifest.flows[AiNormalizationFlow.VISUAL_SEARCH]?.routeVersion)
        assertEquals("listing_offer", manifest.flows[AiNormalizationFlow.LISTING_OFFER]?.contractName)
    }

    @Test
    fun parses_disabled_flow_and_contract_versions() {
        val manifest = AiAgentRegistryManifestLoader.parse(
            """
            {
              "registry_version": "test-registry-1",
              "flows": {
                "visual_search": {
                  "enabled": false,
                  "route_version": "visual-search/route/test",
                  "contract_name": "visual_search",
                  "contract_version": "visual-search/test",
                  "kill_switch_reason": "manual-disable"
                }
              }
            }
            """.trimIndent(),
        )

        val policy = manifest.flows[AiNormalizationFlow.VISUAL_SEARCH]
        assertNotNull(policy)
        assertEquals("test-registry-1", policy.registryVersion)
        assertTrue(!policy.enabled)
        assertEquals("manual-disable", policy.killSwitchReason)
        assertEquals("visual-search/test", policy.contractVersion)
    }

    @Test
    fun merges_override_manifest_over_base_flow_policy() {
        val base = AiAgentRegistryManifestLoader.parse(
            """
            {
              "registry_version": "base-v1",
              "flows": {
                "visual_search": {
                  "enabled": true,
                  "route_version": "visual-search/route/1",
                  "contract_name": "visual_search",
                  "contract_version": "visual-search/v1"
                }
              }
            }
            """.trimIndent(),
        )
        val override = AiAgentRegistryManifestLoader.parse(
            """
            {
              "registry_version": "override-v2",
              "flows": {
                "visual_search": {
                  "enabled": false,
                  "route_version": "visual-search/route/2",
                  "contract_name": "visual_search",
                  "contract_version": "visual-search/v2",
                  "kill_switch_reason": "hotfix"
                }
              }
            }
            """.trimIndent(),
        )

        val merged = AiAgentRegistryManifestLoader.merge(base, override)
        val policy = merged.flows[AiNormalizationFlow.VISUAL_SEARCH]

        assertNotNull(policy)
        assertEquals("override-v2", merged.registryVersion)
        assertTrue(!policy.enabled)
        assertEquals("visual-search/route/2", policy.routeVersion)
        assertEquals("visual-search/v2", policy.contractVersion)
        assertEquals("hotfix", policy.killSwitchReason)
    }
}
