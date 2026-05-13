package com.example.shoppingassistant.server.plugins

import com.example.shoppingassistant.server.config.envValue
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
private data class SearchFeatureFlagsResponse(
    val fetchedAtMs: Long = System.currentTimeMillis(),
    val source: String,
    val values: Map<String, Boolean>,
)

fun Route.searchFeatureFlagsRoutes() {
    route("/api/config") {
        get("/search-feature-flags") {
            val defaults = defaultSearchFeatureFlags()
            val resolved = LinkedHashMap<String, Boolean>(defaults.size)
            var hasEnvOverrides = false

            defaults.forEach { (flagKey, defaultValue) ->
                val overrideValue = resolveSearchFeatureFlagOverride(flagKey)
                if (overrideValue != null) hasEnvOverrides = true
                resolved[flagKey] = overrideValue ?: defaultValue
            }

            call.respond(
                SearchFeatureFlagsResponse(
                    source = if (hasEnvOverrides) "ENV" else "DEFAULT",
                    values = resolved,
                ),
            )
        }
    }
}

private fun defaultSearchFeatureFlags(): Map<String, Boolean> = linkedMapOf(
    "VISUAL_SEARCH_ENTRY_ENABLED" to false,
    "VISUAL_SEARCH_SERVER_AI_ENABLED" to false,
    "VISUAL_SEARCH_BARCODE_LANE_ENABLED" to true,
    "VISUAL_SEARCH_RESULTS_RAIL_ENABLED" to false,
    "SEARCH_MAP_ENABLED" to false,
    "SEARCH_SORT_SHEET_EXPLICIT_APPLY" to false,
    "SEARCH_VIEW_TOGGLE_SEGMENTED_TABLET" to false,
    "FILTERS_SINGLE_SELECT_INSTANT" to false,
    "FILTERS_GEO_RADIUS_SLIDER" to false,
    "RESULTS_LOADING_PROGRESSIVE" to true,
    "RESULTS_SKELETON_SHIMMER" to false,
)

private fun resolveSearchFeatureFlagOverride(flagKey: String): Boolean? {
    val directOverride = parseBooleanFlag(envValue("SEARCH_FLAG_$flagKey"))
    if (directOverride != null) return directOverride
    return when (flagKey) {
        "VISUAL_SEARCH_ENTRY_ENABLED" -> parseBooleanFlag(envValue("VISUAL_SEARCH_ENABLED"))
        "VISUAL_SEARCH_SERVER_AI_ENABLED" -> parseBooleanFlag(envValue("VISUAL_SEARCH_SERVER_AI_ENABLED"))
        else -> null
    }
}

private fun parseBooleanFlag(raw: String?): Boolean? {
    val normalized = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
    return when (normalized) {
        "1",
        "true",
        "yes",
        "y",
        "on",
        -> true

        "0",
        "false",
        "no",
        "n",
        "off",
        -> false

        else -> null
    }
}
