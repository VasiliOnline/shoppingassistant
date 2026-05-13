package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import java.util.Locale

internal enum class ResultsTypedFacetPlacement {
    Main,
    Additional,
    Hidden,
}

internal data class ResultsTypedFacetPresentationPolicy(
    val placement: ResultsTypedFacetPlacement,
    val showInlineValues: Boolean,
    val reason: String,
)

private val promotedGlobalTypedFacetKeys: Set<String> = setOf(
    "color",
)

internal fun resolveResultsTypedFacetPresentationPolicy(
    runtimeKey: String,
    availableValueCount: Int,
    inlineValueCount: Int,
    knownValueCount: Int,
    valueType: FacetDataType?,
    explicitActive: Boolean,
    autoApplied: Boolean,
    hasNotice: Boolean,
    hasBrandContext: Boolean,
    hiddenTypedFacetKeys: Set<String> = emptySet(),
    mainTypedFacetKeys: List<String> = emptyList(),
    additionalTypedFacetKeys: List<String> = emptyList(),
    liveOnlyTypedFacetKeys: Set<String> = emptySet(),
    suppressedAutoAppliedTypedFacetKeys: Set<String> = emptySet(),
    requiresBrandContextTypedFacetKeys: Set<String> = emptySet(),
): ResultsTypedFacetPresentationPolicy {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    val hasAnyValueUniverse = availableValueCount > 0 || knownValueCount > 0
    val isProfileMainFacet = normalizedRuntimeKey in mainTypedFacetKeys
    val isProfileAdditionalFacet = normalizedRuntimeKey in additionalTypedFacetKeys
    return when {
        normalizedRuntimeKey in hiddenTypedFacetKeys &&
            !explicitActive &&
            !hasNotice -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Hidden,
            showInlineValues = false,
            reason = "hidden:profile_hidden",
        )

        normalizedRuntimeKey in requiresBrandContextTypedFacetKeys &&
            !hasBrandContext &&
            !explicitActive &&
            !autoApplied &&
            !hasNotice -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Hidden,
            showInlineValues = false,
            reason = "hidden:requires_brand_context",
        )

        normalizedRuntimeKey in suppressedAutoAppliedTypedFacetKeys &&
            autoApplied &&
            !explicitActive &&
            !hasNotice -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Hidden,
            showInlineValues = false,
            reason = "hidden:auto_applied_suppressed",
        )

        normalizedRuntimeKey in liveOnlyTypedFacetKeys &&
            availableValueCount == 0 &&
            knownValueCount == 0 &&
            !isProfileMainFacet &&
            !isProfileAdditionalFacet &&
            !explicitActive &&
            !autoApplied &&
            !hasNotice -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Hidden,
            showInlineValues = false,
            reason = "hidden:live_only_without_live_values",
        )

        explicitActive -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Main,
            showInlineValues = false,
            reason = "main:explicit_active",
        )

        autoApplied -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Main,
            showInlineValues = false,
            reason = "main:auto_applied",
        )

        hasNotice -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Main,
            showInlineValues = false,
            reason = "main:needs_attention",
        )

        normalizedRuntimeKey in promotedGlobalTypedFacetKeys &&
            (hasAnyValueUniverse || isProfileMainFacet || isProfileAdditionalFacet) -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Main,
            showInlineValues = false,
            reason = "main:promoted_global",
        )

        isProfileMainFacet -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Main,
            showInlineValues = false,
            reason = "main:profile_primary",
        )

        isProfileAdditionalFacet -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Additional,
            showInlineValues = false,
            reason = "additional:profile_secondary",
        )

        availableValueCount > 0 -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Additional,
            showInlineValues = inlineValueCount in 1..6,
            reason = "additional:recoverable_live_values",
        )

        knownValueCount > 0 -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Additional,
            showInlineValues = false,
            reason = "additional:canonical_universe_only",
        )

        valueType == FacetDataType.TEXT || valueType == FacetDataType.RANGE -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Additional,
            showInlineValues = false,
            reason = "additional:manual_input_facet",
        )

        else -> ResultsTypedFacetPresentationPolicy(
            placement = ResultsTypedFacetPlacement.Hidden,
            showInlineValues = false,
            reason = "hidden:no_live_or_canonical_values",
        )
    }
}
