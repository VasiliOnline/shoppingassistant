package com.example.shoppingassistant.core.config

import com.example.shoppingassistant.core.network.BackendClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.Locale

enum class SearchFeatureFlagKey(val code: String, val defaultValue: Boolean) {
    VISUAL_SEARCH_ENTRY_ENABLED(code = "VISUAL_SEARCH_ENTRY_ENABLED", defaultValue = false),
    VISUAL_SEARCH_SERVER_AI_ENABLED(code = "VISUAL_SEARCH_SERVER_AI_ENABLED", defaultValue = false),
    VISUAL_SEARCH_BARCODE_LANE_ENABLED(code = "VISUAL_SEARCH_BARCODE_LANE_ENABLED", defaultValue = true),
    VISUAL_SEARCH_RESULTS_RAIL_ENABLED(code = "VISUAL_SEARCH_RESULTS_RAIL_ENABLED", defaultValue = false),
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
 * Получает feature flags для search/results с backend API.
 * При сетевых сбоях остаётся на последнем snapshot (CACHE/DEFAULT fallback).
 */
class SearchRemoteConfigServiceImpl(
    private val backendClient: BackendClient,
) : SearchRemoteConfigService {
    private val mutex = Mutex()
    private val baseUrl get() = BackendConfig.BASE_URL
    @Volatile
    private var snapshot = SearchFeatureConfigSnapshot(
        fetchedAtMs = System.currentTimeMillis(),
        source = SearchFeatureConfigSource.DEFAULT,
        values = SearchFeatureFlagKey.entries.associateWith { key -> key.defaultValue },
    )

    override fun currentSnapshot(): SearchFeatureConfigSnapshot = snapshot

    override suspend fun refreshAsync(reason: String): SearchFeatureConfigSnapshot {
        return mutex.withLock {
            val current = snapshot
            val fetched = runCatching {
                backendClient.client.get("$baseUrl/api/config/search-feature-flags") {
                    parameter("reason", reason)
                }
            }.getOrNull()
            if (fetched != null && fetched.status.isSuccess()) {
                val payload = runCatching { fetched.body<SearchFeatureConfigRemoteResponse>() }.getOrNull()
                if (payload != null) {
                    val valuesByCode = payload.values
                        .entries
                        .associate { (rawCode, value) -> rawCode.trim().uppercase(Locale.ROOT) to value }
                    val mergedValues = SearchFeatureFlagKey.entries.associateWith { key ->
                        valuesByCode[key.code] ?: key.defaultValue
                    }
                    val remoteSnapshot = SearchFeatureConfigSnapshot(
                        fetchedAtMs = payload.fetchedAtMs ?: System.currentTimeMillis(),
                        source = SearchFeatureConfigSource.REMOTE,
                        values = mergedValues,
                    )
                    snapshot = remoteSnapshot
                    return@withLock remoteSnapshot
                }
            }
            val fallbackSnapshot = current.copy(
                fetchedAtMs = System.currentTimeMillis(),
                source = if (current.source == SearchFeatureConfigSource.REMOTE ||
                    current.source == SearchFeatureConfigSource.CACHE
                ) {
                    SearchFeatureConfigSource.CACHE
                } else {
                    SearchFeatureConfigSource.DEFAULT
                },
            )
            snapshot = fallbackSnapshot
            fallbackSnapshot
        }
    }
}

@Serializable
private data class SearchFeatureConfigRemoteResponse(
    val fetchedAtMs: Long? = null,
    val source: String? = null,
    val values: Map<String, Boolean> = emptyMap(),
)

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
