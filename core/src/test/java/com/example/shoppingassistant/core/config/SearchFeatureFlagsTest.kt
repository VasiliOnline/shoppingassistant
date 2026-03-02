package com.example.shoppingassistant.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFeatureFlagsTest {

    @Test
    fun returns_default_fallback_when_remote_flag_disabled() {
        val gate = SearchFeatureGateImpl(
            remoteConfigService = fakeRemoteConfig(
                values = mapOf(SearchFeatureFlagKey.FILTERS_GEO_RADIUS_SLIDER to false),
            ),
        )

        val decision = gate.decide(
            key = SearchFeatureFlagKey.FILTERS_GEO_RADIUS_SLIDER,
            context = testContext(),
        )

        assertEquals(false, decision.effectiveValue)
        assertEquals(SearchFeatureFallbackReason.DEFAULT, decision.fallbackReason)
    }

    @Test
    fun returns_capability_fallback_when_device_capability_missing() {
        val gate = SearchFeatureGateImpl(
            remoteConfigService = fakeRemoteConfig(
                values = mapOf(SearchFeatureFlagKey.SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET to true),
            ),
        )

        val decision = gate.decide(
            key = SearchFeatureFlagKey.SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET,
            context = testContext(isTabletDevice = false),
        )

        assertEquals(true, decision.remoteValue)
        assertEquals(false, decision.capabilityEnabled)
        assertEquals(false, decision.effectiveValue)
        assertEquals(SearchFeatureFallbackReason.CAPABILITY, decision.fallbackReason)
    }

    @Test
    fun returns_policy_fallback_when_policy_rejects_feature() {
        val gate = SearchFeatureGateImpl(
            remoteConfigService = fakeRemoteConfig(
                values = mapOf(SearchFeatureFlagKey.SEARCH_MAP_ENABLED to true),
            ),
        )

        val decision = gate.decide(
            key = SearchFeatureFlagKey.SEARCH_MAP_ENABLED,
            context = testContext(
                hasGeoPermission = true,
                geoContextSupported = false,
            ),
        )

        assertEquals(true, decision.remoteValue)
        assertEquals(true, decision.capabilityEnabled)
        assertEquals(false, decision.policyEnabled)
        assertEquals(false, decision.effectiveValue)
        assertEquals(SearchFeatureFallbackReason.POLICY, decision.fallbackReason)
    }

    @Test
    fun enables_feature_when_remote_capability_and_policy_are_true() {
        val gate = SearchFeatureGateImpl(
            remoteConfigService = fakeRemoteConfig(
                values = mapOf(SearchFeatureFlagKey.SEARCH_MAP_ENABLED to true),
            ),
        )

        val decision = gate.decide(
            key = SearchFeatureFlagKey.SEARCH_MAP_ENABLED,
            context = testContext(
                hasGeoPermission = true,
                geoContextSupported = true,
            ),
        )

        assertEquals(true, decision.effectiveValue)
        assertNull(decision.fallbackReason)
    }

    private fun fakeRemoteConfig(
        values: Map<SearchFeatureFlagKey, Boolean>,
    ): SearchRemoteConfigService = object : SearchRemoteConfigService {
        private val snapshot = SearchFeatureConfigSnapshot(
            fetchedAtMs = 1L,
            source = SearchFeatureConfigSource.REMOTE,
            values = SearchFeatureFlagKey.entries.associateWith { key ->
                values[key] ?: key.defaultValue
            },
        )

        override fun currentSnapshot(): SearchFeatureConfigSnapshot = snapshot

        override suspend fun refreshAsync(reason: String): SearchFeatureConfigSnapshot = snapshot
    }

    private fun testContext(
        hasGeoPermission: Boolean = true,
        geoContextSupported: Boolean = true,
        isTabletDevice: Boolean = true,
    ): SearchFeatureGateContext = SearchFeatureGateContext(
        searchSessionId = "qs-1",
        canonicalCode = "TECH_PHONES",
        hasGeoPermission = hasGeoPermission,
        geoContextSupported = geoContextSupported,
        isTabletDevice = isTabletDevice,
        hasCache = true,
        reducedMotionEnabled = false,
    )
}
