package com.example.shoppingassistant.core.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SearchFeatureFlagKey(val code: String, val defaultValue: Boolean) {
    SEARCH_MAP_ENABLED(code = "SEARCH_MAP_ENABLED", defaultValue = false),
    SEARCH_SORT_SHEET_EXPLICIT_APPLY(code = "SEARCH_SORT_SHEET_EXPLICIT_APPLY", defaultValue = false),
    SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET(code = "SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET", defaultValue = false),
    FILTERS_SINGLE_SELECT_INSTANT(code = "FILTERS_SINGLE_SELECT_INSTANT", defaultValue = false),
    FILTERS_GEO_RADIUS_SLIDER(code = "FILTERS_GEO_RADIUS_SLIDER", defaultValue = false),
    RESULTS_LOADING_PROGRESSIVE(code = "RESULTS_LOADING_PROGRESSIVE", defaultValue = true),
    RESULTS_SKELETON_SHIMMER(code = "RESULTS_SKELETON_SHIMMER", defaultValue = false),
}

enum class SearchFeatureConfigSource {
    DEFAULT,
    CACHE,
    REMOTE,
}

enum class SearchFeatureFallbackReason(val code: String) {
    DEFAULT("DEFAULT"),
    CAPABILITY("CAPABILITY"),
    POLICY("POLICY"),
}

data class SearchFeatureConfigSnapshot(
    val fetchedAtMs: Long,
    val source: SearchFeatureConfigSource,
    val values: Map<SearchFeatureFlagKey, Boolean>,
)

data class SearchFeatureGateContext(
    val searchSessionId: String,
    val canonicalCode: String?,
    val hasGeoPermission: Boolean,
    val geoContextSupported: Boolean,
    val isTabletDevice: Boolean,
    val hasCache: Boolean,
    val reducedMotionEnabled: Boolean,
)

data class SearchFeatureDecision(
    val key: SearchFeatureFlagKey,
    val remoteValue: Boolean,
    val capabilityEnabled: Boolean,
    val policyEnabled: Boolean,
    val effectiveValue: Boolean,
    val fallbackReason: SearchFeatureFallbackReason?,
)

interface SearchRemoteConfigService {
    fun currentSnapshot(): SearchFeatureConfigSnapshot
    suspend fun refreshAsync(reason: String): SearchFeatureConfigSnapshot
}

interface SearchFeatureGate {
    fun decide(
        key: SearchFeatureFlagKey,
        context: SearchFeatureGateContext,
    ): SearchFeatureDecision
}

/**
 * v1: typed allowlist + deterministic defaults.
 * Remote provider can be connected behind this class without changing UI contracts.
 */
class SearchRemoteConfigServiceImpl : SearchRemoteConfigService {
    private val mutex = Mutex()
    @Volatile
    private var snapshot = SearchFeatureConfigSnapshot(
        fetchedAtMs = System.currentTimeMillis(),
        source = SearchFeatureConfigSource.DEFAULT,
        values = SearchFeatureFlagKey.entries.associateWith { key -> key.defaultValue },
    )

    override fun currentSnapshot(): SearchFeatureConfigSnapshot = snapshot

    override suspend fun refreshAsync(reason: String): SearchFeatureConfigSnapshot {
        // v1 fallback: stale-while-revalidate over deterministic defaults.
        return mutex.withLock {
            val current = snapshot
            val refreshed = current.copy(
                fetchedAtMs = System.currentTimeMillis(),
                source = if (current.source == SearchFeatureConfigSource.DEFAULT) {
                    SearchFeatureConfigSource.DEFAULT
                } else {
                    SearchFeatureConfigSource.CACHE
                },
            )
            snapshot = refreshed
            refreshed
        }
    }
}

class SearchFeatureGateImpl(
    private val remoteConfigService: SearchRemoteConfigService,
) : SearchFeatureGate {
    override fun decide(
        key: SearchFeatureFlagKey,
        context: SearchFeatureGateContext,
    ): SearchFeatureDecision {
        val remoteValue = remoteConfigService.currentSnapshot().values[key] ?: key.defaultValue
        val capabilityEnabled = capabilityGate(key, context)
        val policyEnabled = policyGate(key, context)
        val effectiveValue = remoteValue && capabilityEnabled && policyEnabled
        val fallbackReason = when {
            !remoteValue -> SearchFeatureFallbackReason.DEFAULT
            !capabilityEnabled -> SearchFeatureFallbackReason.CAPABILITY
            !policyEnabled -> SearchFeatureFallbackReason.POLICY
            else -> null
        }
        return SearchFeatureDecision(
            key = key,
            remoteValue = remoteValue,
            capabilityEnabled = capabilityEnabled,
            policyEnabled = policyEnabled,
            effectiveValue = effectiveValue,
            fallbackReason = fallbackReason,
        )
    }

    private fun capabilityGate(
        key: SearchFeatureFlagKey,
        context: SearchFeatureGateContext,
    ): Boolean = when (key) {
        SearchFeatureFlagKey.SEARCH_MAP_ENABLED -> context.hasGeoPermission
        SearchFeatureFlagKey.SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET -> context.isTabletDevice
        SearchFeatureFlagKey.RESULTS_LOADING_PROGRESSIVE -> context.hasCache
        SearchFeatureFlagKey.RESULTS_SKELETON_SHIMMER -> !context.reducedMotionEnabled
        else -> true
    }

    private fun policyGate(
        key: SearchFeatureFlagKey,
        context: SearchFeatureGateContext,
    ): Boolean = when (key) {
        SearchFeatureFlagKey.SEARCH_MAP_ENABLED -> context.geoContextSupported
        else -> true
    }
}
