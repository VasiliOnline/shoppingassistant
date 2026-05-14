package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.catalog.CatalogSeed
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

    @Test
    fun fash_men_branch_uses_profile_as_typed_facet_allowlist() {
        val filters = buildFacetUiFilters(
            definitions = listOf(
                definition("apparel_type", "apparel_type", listOf("FASH.MEN")),
                definition("size_label", "size_label", listOf("FASH.MEN")),
                definition("color_primary", "color_primary", listOf("FASH.MEN")),
                definition("material_primary", "material_primary", listOf("FASH.MEN")),
                definition("model_line", "model_line", listOf("FASH.MEN")),
                definition("material", "material", listOf("FASH.MEN")),
                definition("size_eu", "size_eu", listOf("FASH.MEN")),
                definition("target_gender", "target_gender", listOf("FASH.MEN")),
            ),
            categoryCode = "FASH.MEN",
        )

        val runtimeKeys = filters.map { it.runtimeKey }.toSet()
        assertTrue(runtimeKeys.contains("apparel_type"))
        assertTrue(runtimeKeys.contains("size_label"))
        assertTrue(runtimeKeys.contains("color_primary"))
        assertTrue(runtimeKeys.contains("material_primary"))
        assertFalse(runtimeKeys.contains("model_line"))
        assertFalse(runtimeKeys.contains("material"))
        assertFalse(runtimeKeys.contains("size_eu"))
        assertFalse(runtimeKeys.contains("target_gender"))
    }

    @Test
    fun fash_men_seed_scope_does_not_include_legacy_facets() {
        val scopedKeys = scopeFacetDefinitionsForCategory(
            definitions = CatalogSeed.facetDefinitions,
            categoryCode = "FASH.MEN",
        ).map { it.facetKey }.toSet()

        assertTrue(scopedKeys.contains("apparel_type"))
        assertTrue(scopedKeys.contains("size_label"))
        assertTrue(scopedKeys.contains("material_primary"))
        assertFalse(scopedKeys.contains("gender"))
        assertFalse(scopedKeys.contains("material"))
        assertFalse(scopedKeys.contains("model_line"))
        assertFalse(scopedKeys.contains("size_eu"))
    }

    @Test
    fun fash_seed_keeps_only_profile_approved_reusable_legacy_facets() {
        val reusableLegacyAllowedByCategory = mapOf(
            "FASH.ACCESSORIES" to setOf("color"),
            "FASH.SHOES" to setOf("size_eu"),
        )
        val legacyKeys = setOf("gender", "material", "model_line", "type_of_item", "color", "size_eu")

        fashCategoryCodes.forEach { categoryCode ->
            val scopedKeys = scopeFacetDefinitionsForCategory(
                definitions = CatalogSeed.facetDefinitions,
                categoryCode = categoryCode,
            ).map { it.facetKey }.toSet()
            val allowedReusableKeys = reusableLegacyAllowedByCategory[categoryCode].orEmpty()
            val forbiddenLegacyKeys = legacyKeys - allowedReusableKeys
            val leakedKeys = scopedKeys.intersect(forbiddenLegacyKeys)

            assertTrue(
                "$categoryCode leaked legacy FASH facet bindings: ${leakedKeys.joinToString(", ")}",
                leakedKeys.isEmpty(),
            )
            allowedReusableKeys.forEach { allowedKey ->
                assertTrue("$categoryCode lost reusable FASH facet '$allowedKey'", allowedKey in scopedKeys)
            }
        }
    }

    @Test
    fun fash_ui_profiles_expose_current_branch_facets_for_every_fash_category() {
        val expectedVisibleKeysByCategory = mapOf(
            "FASH.MEN" to setOf("apparel_type", "size_label", "color_primary", "material_primary"),
            "FASH.WOMEN" to setOf("apparel_type", "size_label", "color_primary", "material_primary"),
            "FASH.KIDS" to setOf("apparel_type", "size_label", "color_primary", "target_gender"),
            "FASH.SHOES" to setOf("shoe_type", "size_eu", "color_primary", "material_upper"),
            "FASH.BAGS" to setOf("bag_type", "color_primary", "material_outer", "size_class"),
            "FASH.ACCESSORIES" to setOf("accessory_type", "color", "material_primary", "target_gender"),
        )
        val alwaysForbiddenLegacyKeys = setOf("gender", "material", "model_line", "type_of_item")

        expectedVisibleKeysByCategory.forEach { (categoryCode, expectedVisibleKeys) ->
            val scopedDefinitions = scopeFacetDefinitionsForCategory(
                definitions = CatalogSeed.facetDefinitions,
                categoryCode = categoryCode,
            )
            val filters = buildFacetUiFilters(
                definitions = scopedDefinitions,
                categoryCode = categoryCode,
            )
            val runtimeKeys = filters.map { it.runtimeKey }.toSet()

            expectedVisibleKeys.forEach { expectedKey ->
                assertTrue("$categoryCode should expose '$expectedKey'", expectedKey in runtimeKeys)
            }
            alwaysForbiddenLegacyKeys.forEach { forbiddenKey ->
                assertFalse("$categoryCode should not expose legacy '$forbiddenKey'", forbiddenKey in runtimeKeys)
            }
        }
    }

    private val fashCategoryCodes = listOf(
        "FASH.MEN",
        "FASH.WOMEN",
        "FASH.KIDS",
        "FASH.SHOES",
        "FASH.BAGS",
        "FASH.ACCESSORIES",
    )

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
