package com.example.shoppingassistant.core.data.catalog

import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogApiDataSourceSeedMergeTest {

    @Test
    fun list_categories_merge_adds_seed_categories_missing_from_remote_runtime() {
        val merged = mergeCategoriesWithSeed(
            remoteCategories = listOf(
                category("TECH"),
                category("TECH.PHONES"),
            ),
            seedCategories = listOf(
                category("TECH"),
                category("TECH.PHONES"),
                category("TECH.CAMERAS_DRONES"),
                category("FASH.MEN"),
            ),
        )

        val codes = merged.map { it.code }
        assertEquals("TECH", codes[0])
        assertEquals("TECH.PHONES", codes[1])
        assertTrue("TECH.CAMERAS_DRONES" in codes)
        assertTrue("FASH.MEN" in codes)
    }

    private fun category(code: String): Category = Category(
        code = code,
        segment = code.substringBefore('.').let { root ->
            runCatching { CategorySegment.valueOf(root) }.getOrDefault(CategorySegment.OTHER)
        },
        title = localizedTextOf("ru" to code),
        parentCode = code.substringBeforeLast('.', missingDelimiterValue = "")
            .takeIf { parent -> parent.isNotBlank() && parent != code },
    )
}
