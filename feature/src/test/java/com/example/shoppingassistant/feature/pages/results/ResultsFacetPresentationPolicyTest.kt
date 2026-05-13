package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsFacetPresentationPolicyTest {

    @Test
    fun explicit_active_facet_surfaces_in_main_list() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "color",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 10,
            valueType = FacetDataType.ENUM,
            explicitActive = true,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
        )

        assertEquals(ResultsTypedFacetPlacement.Main, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("main:explicit_active", policy.reason)
    }

    @Test
    fun notice_facet_surfaces_in_main_list() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "color",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 5,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = true,
            hasBrandContext = true,
        )

        assertEquals(ResultsTypedFacetPlacement.Main, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("main:needs_attention", policy.reason)
    }

    @Test
    fun profile_primary_facet_surfaces_in_main_list() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "memory_gb",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 5,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
            mainTypedFacetKeys = listOf("memory_gb"),
        )

        assertEquals(ResultsTypedFacetPlacement.Main, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("main:profile_primary", policy.reason)
    }

    @Test
    fun profile_secondary_facet_stays_in_additional_list() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "network_type",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 3,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
            additionalTypedFacetKeys = listOf("network_type"),
        )

        assertEquals(ResultsTypedFacetPlacement.Additional, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("additional:profile_secondary", policy.reason)
    }

    @Test
    fun recoverable_live_facet_moves_to_additional_with_inline_values() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "color",
            availableValueCount = 3,
            inlineValueCount = 3,
            knownValueCount = 8,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
        )

        assertEquals(ResultsTypedFacetPlacement.Main, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("main:promoted_global", policy.reason)
    }

    @Test
    fun canonical_only_facet_stays_in_additional_without_inline_values() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "color",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 12,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
        )

        assertEquals(ResultsTypedFacetPlacement.Main, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("main:promoted_global", policy.reason)
    }

    @Test
    fun manual_input_facet_stays_visible_even_without_known_values() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "price",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 0,
            valueType = FacetDataType.RANGE,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = false,
        )

        assertEquals(ResultsTypedFacetPlacement.Additional, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("additional:manual_input_facet", policy.reason)
    }

    @Test
    fun empty_enum_facet_is_hidden() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "color",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 0,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = false,
        )

        assertEquals(ResultsTypedFacetPlacement.Hidden, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("hidden:no_live_or_canonical_values", policy.reason)
    }

    @Test
    fun model_facet_is_hidden_without_brand_context() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "model",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 12,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = false,
            requiresBrandContextTypedFacetKeys = setOf("model"),
        )

        assertEquals(ResultsTypedFacetPlacement.Hidden, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("hidden:requires_brand_context", policy.reason)
    }

    @Test
    fun profile_hidden_facet_is_hidden_without_manual_overrides() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "condition",
            availableValueCount = 5,
            inlineValueCount = 0,
            knownValueCount = 5,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = false,
            hiddenTypedFacetKeys = setOf("condition"),
        )

        assertEquals(ResultsTypedFacetPlacement.Hidden, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("hidden:profile_hidden", policy.reason)
    }

    @Test
    fun live_only_facet_is_hidden_without_live_values() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "network_type",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 0,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
            liveOnlyTypedFacetKeys = setOf("network_type"),
        )

        assertEquals(ResultsTypedFacetPlacement.Hidden, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("hidden:live_only_without_live_values", policy.reason)
    }

    @Test
    fun profile_secondary_live_only_facet_stays_visible_with_canonical_universe() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "network_type",
            availableValueCount = 0,
            inlineValueCount = 0,
            knownValueCount = 2,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = false,
            hasNotice = false,
            hasBrandContext = true,
            additionalTypedFacetKeys = listOf("network_type"),
            liveOnlyTypedFacetKeys = setOf("network_type"),
        )

        assertEquals(ResultsTypedFacetPlacement.Additional, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("additional:profile_secondary", policy.reason)
    }

    @Test
    fun suppressed_auto_applied_facet_stays_hidden() {
        val policy = resolveResultsTypedFacetPresentationPolicy(
            runtimeKey = "os_family",
            availableValueCount = 1,
            inlineValueCount = 0,
            knownValueCount = 2,
            valueType = FacetDataType.ENUM,
            explicitActive = false,
            autoApplied = true,
            hasNotice = false,
            hasBrandContext = true,
            suppressedAutoAppliedTypedFacetKeys = setOf("os_family"),
        )

        assertEquals(ResultsTypedFacetPlacement.Hidden, policy.placement)
        assertFalse(policy.showInlineValues)
        assertEquals("hidden:auto_applied_suppressed", policy.reason)
    }
}
