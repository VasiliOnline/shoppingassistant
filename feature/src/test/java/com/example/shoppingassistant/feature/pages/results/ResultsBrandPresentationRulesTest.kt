package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsBrandPresentationRulesTest {

    @Test
    fun phone_category_brand_options_fall_back_to_canonical_brands() {
        val options = availableBrandOptions(
            items = emptyList(),
            runtimeFacets = emptyList(),
            categoryCode = "TECH.PHONES",
        )

        val names = options.map { option -> option.name }
        assertTrue(names.contains("Apple"))
        assertTrue(names.contains("Samsung"))
        assertTrue(names.contains("Google"))
    }

    @Test
    fun replacement_phone_category_brand_options_fall_back_to_canonical_brands() {
        val options = availableBrandOptions(
            items = emptyList(),
            runtimeFacets = emptyList(),
            categoryCode = "TECH.SMARTPHONES",
            categoriesByCode = mapOf(
                "TECH.SMARTPHONES" to Category(
                    code = "TECH.SMARTPHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.DEPRECATED,
                    replacementCode = "TECH.PHONES",
                ),
                "TECH.PHONES" to Category(
                    code = "TECH.PHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.ACTIVE,
                ),
            ),
        )

        val names = options.map { option -> option.name }
        assertTrue(names.contains("Apple"))
        assertTrue(names.contains("Samsung"))
    }

    @Test
    fun invalid_brand_seed_is_removed_for_phone_category() {
        val sanitized = sanitizeSelectedBrandsForCategory(
            selectedBrands = setOf("Электроника"),
            interpretedBrandSeed = "Электроника",
            categoryCode = "TECH.PHONES",
        )

        assertTrue(sanitized.brands.isEmpty())
        assertEquals(null, sanitized.interpretedBrandSeed)
    }

    @Test
    fun invalid_brand_seed_is_removed_for_replacement_phone_category() {
        val sanitized = sanitizeSelectedBrandsForCategory(
            selectedBrands = setOf("Электроника"),
            interpretedBrandSeed = "Электроника",
            categoryCode = "TECH.SMARTPHONES",
            categoriesByCode = mapOf(
                "TECH.SMARTPHONES" to Category(
                    code = "TECH.SMARTPHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.DEPRECATED,
                    replacementCode = "TECH.PHONES",
                ),
                "TECH.PHONES" to Category(
                    code = "TECH.PHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.ACTIVE,
                ),
            ),
        )

        assertTrue(sanitized.brands.isEmpty())
        assertEquals(null, sanitized.interpretedBrandSeed)
    }

    @Test
    fun valid_brand_seed_is_canonicalized_for_phone_category() {
        val sanitized = sanitizeSelectedBrandsForCategory(
            selectedBrands = setOf("apple"),
            interpretedBrandSeed = "apple",
            categoryCode = "TECH.PHONES",
        )

        assertEquals(setOf("Apple"), sanitized.brands)
        assertEquals("Apple", sanitized.interpretedBrandSeed)
    }

    @Test
    fun category_brand_options_do_not_leak_query_like_values() {
        val options = availableBrandOptions(
            items = emptyList(),
            runtimeFacets = emptyList(),
            categoryCode = "TECH.PHONES",
            selectedBrands = emptySet(),
        )

        assertFalse(options.any { option -> option.name.equals("Электроника", ignoreCase = true) })
    }

    @Test
    fun brand_options_are_scoped_to_selected_models() {
        val options = availableBrandOptions(
            items = emptyList(),
            runtimeFacets = emptyList(),
            categoryCode = "TECH.PHONES",
            selectedBrands = emptySet(),
            typedAttributeFilters = mapOf(
                "model" to TypedAttributeFilterDraft(
                    op = com.example.shoppingassistant.domain.model.TypedAttributeOperator.IN,
                    valuesCsv = "iPhone 13, iPhone 15",
                ),
            ),
        )

        assertEquals(listOf("Apple"), options.map { option -> option.name })
    }
}
