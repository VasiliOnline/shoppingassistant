package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsCategoryResolutionTest {

    @Test
    fun resolves_deprecated_category_to_active_replacement() {
        val resolved = resolveResultsCategoryCode(
            categoryCode = " tech.old_phones ",
            categories = listOf(
                Category(
                    code = "TECH.OLD_PHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.DEPRECATED,
                    replacementCode = "TECH.PHONES",
                ),
                Category(
                    code = "TECH.PHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.ACTIVE,
                ),
            ),
        )

        assertEquals("TECH.PHONES", resolved)
    }

    @Test
    fun keeps_original_code_when_replacement_is_unknown() {
        val resolved = resolveResultsCategoryCode(
            categoryCode = "TECH.OLD_PHONES",
            categories = listOf(
                Category(
                    code = "TECH.OLD_PHONES",
                    segment = CategorySegment.TECH,
                    status = CategoryStatus.DEPRECATED,
                    replacementCode = "TECH.UNKNOWN",
                ),
            ),
        )

        assertEquals("TECH.OLD_PHONES", resolved)
    }

    @Test
    fun resolves_category_summary_from_taxonomy_leaf_title() {
        val summary = resolveResultsCategorySummary(
            categoryCode = "TECH.SMARTPHONES",
            categoryPath = emptyList(),
            categoriesByCode = listOf(
                Category(
                    code = "TECH",
                    segment = CategorySegment.TECH,
                    title = localizedTextOf("ru" to "Электроника"),
                ),
                Category(
                    code = "TECH.SMARTPHONES",
                    segment = CategorySegment.TECH,
                    parentCode = "TECH",
                    title = localizedTextOf("ru" to "Смартфоны"),
                ),
            ).associateBy { it.code },
            localeTag = "ru",
        )

        assertEquals("Смартфоны", summary)
    }
}
