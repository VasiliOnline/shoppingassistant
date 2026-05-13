package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsFacetBranchPolicySnapshotTest {

    @Test
    fun tech_phones_snapshot_orders_main_and_secondary_typed_facets() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition("model", "model", "TECH.PHONES"),
                definition("memory_gb", "memory_gb", "TECH.PHONES"),
                definition("color", "color", "TECH.PHONES"),
                definition("network_type", "network_type", "TECH.PHONES"),
                definition("ram_gb", "ram_gb", "TECH.PHONES"),
                definition("refresh_rate_hz", "refresh_rate_hz", "TECH.PHONES"),
                definition("chipset_family", "chipset_family", "TECH.PHONES"),
                definition("release_year", "release_year", "TECH.PHONES", FacetDataType.RANGE),
                definition("screen_size_inch", "screen_size_inch", "TECH.PHONES", FacetDataType.RANGE),
                definition("wireless_charging", "wireless_charging", "TECH.PHONES", FacetDataType.BOOL),
                definition("esim_support", "esim_support", "TECH.PHONES", FacetDataType.BOOL),
                definition("os_family", "os_family", "TECH.PHONES"),
            ),
            categoryCode = "TECH.PHONES",
        )

        assertEquals(
            listOf(
                "brand",
                "price",
                "condition",
                "model",
                "memory_gb",
                "color",
                "network_type",
                "ram_gb",
                "refresh_rate_hz",
                "chipset_family",
                "release_year",
                "screen_size_inch",
                "wireless_charging",
                "esim_support",
            ),
            filters.map { if (it.type == ResultsFacetFilterType.TypedAttribute) it.runtimeKey else it.facetKey },
        )
    }

    @Test
    fun food_ready_meals_snapshot_hides_brand_and_condition_and_prioritizes_cuisine() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition("cuisine_type", "cuisine_type", "FOOD.READY_MEALS"),
                definition("spiciness_level", "spiciness_level", "FOOD.READY_MEALS"),
                definition("allergen_profile", "allergen_profile", "FOOD.READY_MEALS"),
                definition("storage_regime", "storage_regime", "FOOD.READY_MEALS"),
                definition("calories_kcal_per_serving", "calories_kcal_per_serving", "FOOD.READY_MEALS"),
                definition("protein_per_serving_gram", "protein_per_serving_gram", "FOOD.READY_MEALS"),
            ),
            categoryCode = "FOOD.READY_MEALS",
        )

        assertEquals(
            listOf(
                "price",
                "cuisine_type",
                "spiciness_level",
                "allergen_profile",
                "storage_regime",
                "calories_kcal_per_serving",
                "protein_per_serving_gram",
            ),
            filters.map { if (it.type == ResultsFacetFilterType.TypedAttribute) it.runtimeKey else it.facetKey },
        )
    }

    @Test
    fun beauty_devices_snapshot_keeps_condition_and_surfaces_model_before_color() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition("model", "model", "BEAUTY.DEVICES"),
                definition("color", "color", "BEAUTY.DEVICES"),
            ),
            categoryCode = "BEAUTY.DEVICES",
        )

        assertEquals(
            listOf(
                "brand",
                "price",
                "condition",
                "model",
                "color",
            ),
            filters.map { if (it.type == ResultsFacetFilterType.TypedAttribute) it.runtimeKey else it.facetKey },
        )
    }

    @Test
    fun auto_parts_snapshot_prioritizes_model_material_and_usage() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition("model", "model", "AUTO.PARTS"),
                definition("material", "material", "AUTO.PARTS"),
                definition("used_for", "used_for", "AUTO.PARTS"),
                definition("color", "color", "AUTO.PARTS"),
            ),
            categoryCode = "AUTO.PARTS",
        )

        assertEquals(
            listOf(
                "brand",
                "price",
                "condition",
                "model",
                "material",
                "used_for",
                "color",
            ),
            filters.map { if (it.type == ResultsFacetFilterType.TypedAttribute) it.runtimeKey else it.facetKey },
        )
    }

    private fun definition(
        facetKey: String,
        attributeCode: String,
        categoryCode: String,
        valueType: FacetDataType = FacetDataType.ENUM,
    ): FacetDefinition = FacetDefinition(
        facetKey = facetKey,
        title = localizedTextOf("ru" to facetKey),
        valueType = valueType,
        appliesToCategoryCodes = listOf(categoryCode),
        attributeCode = attributeCode,
    )
}
