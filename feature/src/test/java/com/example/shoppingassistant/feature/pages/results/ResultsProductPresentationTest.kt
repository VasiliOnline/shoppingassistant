package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.core.rank.ScoreBreakdown
import com.example.shoppingassistant.domain.model.OfferSort
import com.example.shoppingassistant.domain.model.ProductDto
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsProductPresentationTest {

    @Test
    fun buildSortOptions_shows_model_freshness_only_for_supported_branch() {
        val items = listOf(
            explainedItem(
                id = "1",
                attributes = mapOf("release_year" to TypedAttributeValue.Number(2026.0)),
            ),
            explainedItem(
                id = "2",
                attributes = mapOf("release_year" to TypedAttributeValue.Number(2025.0)),
            ),
        )

        val phoneOptions = buildSortOptions(
            items = items,
            location = null,
            categoryCode = "TECH.PHONES",
        )
        val foodOptions = buildSortOptions(
            items = items,
            location = null,
            categoryCode = "FOOD.GROCERY",
        )

        assertTrue(phoneOptions.any { it.sort == OfferSort.MODEL_FRESHNESS_DESC && it.enabled })
        assertFalse(foodOptions.any { it.sort == OfferSort.MODEL_FRESHNESS_DESC })
    }

    @Test
    fun buildSortOptions_disables_model_freshness_when_release_year_coverage_is_low() {
        val items = listOf(
            explainedItem(
                id = "1",
                attributes = mapOf("release_year" to TypedAttributeValue.Number(2026.0)),
            ),
            explainedItem(
                id = "2",
                attributes = emptyMap(),
            ),
        )

        val sortOption = buildSortOptions(
            items = items,
            location = null,
            categoryCode = "TECH.PHONES",
        ).first { it.sort == OfferSort.MODEL_FRESHNESS_DESC }

        assertFalse(sortOption.enabled)
        assertEquals("Недостаточно данных о релизе модели", sortOption.disabledReason)
    }

    @Test
    fun buildResultsProductBadges_prioritizes_new_and_esim_for_phones() {
        val badges = buildResultsProductBadges(
            dto = productDto(
                attributes = mapOf(
                    "release_year" to TypedAttributeValue.Number(2026.0),
                    "esim_support" to TypedAttributeValue.Bool(true),
                    "ip_rating" to TypedAttributeValue.Text("IP68"),
                    "wired_charging_w" to TypedAttributeValue.Number(45.0),
                    "wireless_charging" to TypedAttributeValue.Bool(true),
                    "battery_mah" to TypedAttributeValue.Number(5000.0),
                ),
            ),
            categoryCode = "TECH.PHONES",
            nowYear = 2026,
        )

        assertEquals(listOf("Новинка", "eSIM"), badges.map { it.label })
    }

    @Test
    fun buildResultsProductBadges_ignores_non_phone_categories() {
        val badges = buildResultsProductBadges(
            dto = productDto(
                attributes = mapOf("release_year" to TypedAttributeValue.Number(2026.0)),
            ),
            categoryCode = "FOOD.GROCERY",
            nowYear = 2026,
        )

        assertTrue(badges.isEmpty())
    }

    @Test
    fun buildResultsProductBadges_surfaces_richer_phone_signals_for_next_wave_models() {
        val nothingBadges = buildResultsProductBadges(
            dto = productDto(
                attributes = mapOf(
                    "release_year" to TypedAttributeValue.Number(2024.0),
                    "ip_rating" to TypedAttributeValue.Text("IP54"),
                    "wired_charging_w" to TypedAttributeValue.Number(45.0),
                    "battery_mah" to TypedAttributeValue.Number(5000.0),
                ),
            ),
            categoryCode = "TECH.PHONES",
            nowYear = 2026,
        )
        assertEquals(listOf("IP54", "45W+"), nothingBadges.map { it.label })

        val oneplusBadges = buildResultsProductBadges(
            dto = productDto(
                attributes = mapOf(
                    "release_year" to TypedAttributeValue.Number(2024.0),
                    "esim_support" to TypedAttributeValue.Bool(true),
                    "wireless_charging" to TypedAttributeValue.Bool(true),
                    "battery_mah" to TypedAttributeValue.Number(5400.0),
                ),
            ),
            categoryCode = "TECH.PHONES",
            nowYear = 2026,
        )
        assertEquals(listOf("eSIM", "Беспроводная"), oneplusBadges.map { it.label })
    }

    @Test
    fun supportsResultsModelFreshnessSort_uses_branch_profile() {
        assertTrue(supportsResultsModelFreshnessSort("TECH.PHONES"))
        assertFalse(supportsResultsModelFreshnessSort("FOOD.READY_MEALS"))
        assertFalse(supportsResultsModelFreshnessSort(null))
    }

    private fun explainedItem(
        id: String,
        attributes: Map<String, TypedAttributeValue>,
    ): ExplainedItem = ExplainedItem(
        dto = productDto(id = id, attributes = attributes),
        breakdown = ScoreBreakdown(
            price = 0f,
            delivery = 0f,
            rating = 0f,
            penalties = 0f,
            score = 0f,
        ),
        reasons = emptyList(),
    )

    private fun productDto(
        id: String = "1",
        attributes: Map<String, TypedAttributeValue>,
    ): ProductDto = ProductDto(
        id = id,
        title = "Phone",
        brand = "Apple",
        model = "iPhone",
        price = 1000.0,
        deliveryTime = null,
        sellerRating = 4.9,
        attributes = attributes,
    )
}
