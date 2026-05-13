package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsFacetUiProfilesTest {

    @Test
    fun food_branch_hides_condition_system_facet() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "FOOD.GROCERIES",
        )

        assertFalse(filters.any { it.facetKey == "condition" })
    }

    @Test
    fun tech_phones_branch_keeps_condition_system_facet() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "TECH.PHONES",
        )

        assertTrue(filters.any { it.facetKey == "condition" })
    }

    @Test
    fun hidden_typed_facet_is_removed_from_branch_list() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition(
                    facetKey = "condition",
                    attributeCode = "condition",
                    appliesTo = listOf("FOOD.GROCERIES"),
                ),
                definition(
                    facetKey = "flavor",
                    attributeCode = "flavor",
                    appliesTo = listOf("FOOD.GROCERIES"),
                ),
            ),
            categoryCode = "FOOD.GROCERIES",
        )

        assertFalse(filters.any { it.runtimeKey == "condition" })
        assertTrue(filters.any { it.runtimeKey == "flavor" })
    }

    @Test
    fun tech_phones_branch_hides_os_typed_facet() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition(
                    facetKey = "os_family",
                    attributeCode = "os_family",
                    appliesTo = listOf("TECH.PHONES"),
                ),
            ),
            categoryCode = "TECH.PHONES",
        )

        assertFalse(filters.any { it.runtimeKey == "os_family" })
    }

    @Test
    fun beauty_skincare_hides_condition_system_facet() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "BEAUTY.SKINCARE",
        )

        assertFalse(filters.any { it.facetKey == "condition" })
        assertTrue(filters.any { it.facetKey == "brand" })
    }

    @Test
    fun beauty_health_hides_brand_and_condition_system_facets() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "BEAUTY.HEALTH",
        )

        assertFalse(filters.any { it.facetKey == "brand" })
        assertFalse(filters.any { it.facetKey == "condition" })
    }

    @Test
    fun beauty_devices_keep_condition_system_facet() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "BEAUTY.DEVICES",
        )

        assertTrue(filters.any { it.facetKey == "condition" })
        assertTrue(filters.any { it.facetKey == "brand" })
    }

    @Test
    fun food_ready_meals_hide_brand_and_condition_system_facets() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "FOOD.READY_MEALS",
        )

        assertFalse(filters.any { it.facetKey == "brand" })
        assertFalse(filters.any { it.facetKey == "condition" })
    }

    @Test
    fun auto_branch_keeps_brand_and_condition_system_facets() {
        val filters = buildFacetUiFilters(
            definitions = emptyList(),
            categoryCode = "AUTO.PARTS",
        )

        assertTrue(filters.any { it.facetKey == "brand" })
        assertTrue(filters.any { it.facetKey == "condition" })
    }

    @Test
    fun delivery_and_seller_trust_definitions_stay_out_of_filter_hub_list() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition(
                    facetKey = "delivery_channel",
                    attributeCode = "delivery_channel",
                    appliesTo = listOf("TECH.PHONES"),
                ),
                definition(
                    facetKey = "seller_trust",
                    attributeCode = "seller_trust",
                    appliesTo = listOf("TECH.PHONES"),
                ),
                definition(
                    facetKey = "memory_gb",
                    attributeCode = "memory_gb",
                    appliesTo = listOf("TECH.PHONES"),
                ),
            ),
            categoryCode = "TECH.PHONES",
        )

        assertFalse(filters.any { it.runtimeKey == "delivery_channel" })
        assertFalse(filters.any { it.runtimeKey == "seller_trust" })
        assertTrue(filters.any { it.runtimeKey == "memory_gb" })
    }

    private fun definition(
        facetKey: String,
        attributeCode: String,
        appliesTo: List<String>,
    ): FacetDefinition = FacetDefinition(
        facetKey = facetKey,
        title = localizedTextOf("ru" to facetKey),
        valueType = FacetDataType.ENUM,
        appliesToCategoryCodes = appliesTo,
        attributeCode = attributeCode,
    )
}
