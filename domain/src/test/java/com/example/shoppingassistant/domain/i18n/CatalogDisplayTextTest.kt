package com.example.shoppingassistant.domain.i18n

import com.example.shoppingassistant.domain.catalog.BrowseNode
import com.example.shoppingassistant.domain.catalog.CatalogValueOption
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogDisplayTextTest {
    @Test
    fun category_prefersEnglishTitle_whenEnglishLocaleRequested() {
        val category = Category(
            code = "TECH.PHONES",
            segment = CategorySegment.TECH,
            title = localizedTextOf(
                "ru" to "Смартфоны",
                "en" to "Smartphones",
            ),
        )

        assertEquals("Smartphones", category.displayTitle(locale = "en-US"))
        assertEquals("Смартфоны", category.displayTitle(locale = "ru-RU"))
    }

    @Test
    fun browseNode_fallsBackToEnglishBeforeCode_whenPrimaryIsBlank() {
        val node = BrowseNode(
            browseCode = "B.TECH.ROOT",
            title = localizedTextOf(
                "ru" to "  ",
                "en" to "Electronics",
            ),
        )

        assertEquals("Electronics", node.displayTitle(locale = "ru-RU"))
    }

    @Test
    fun facetEntities_useLocalizedTitle_whenAvailable() {
        val definition = FacetDefinition(
            facetKey = "brand",
            title = localizedTextOf(
                "ru" to "Бренд",
                "en" to "Brand",
            ),
            valueType = FacetDataType.ENUM,
            appliesToCategoryCodes = listOf("TECH.PHONES"),
        )
        val preset = FacetPreset(
            presetCode = "FP.TECH.DEFAULT",
            categoryCode = "TECH.PHONES",
            title = localizedTextOf(
                "ru" to "Популярные",
                "en" to "Popular",
            ),
        )
        val collection = FacetCollection(
            collectionCode = "FC.TECH.DEFAULT",
            categoryCode = "TECH.PHONES",
            title = localizedTextOf(
                "ru" to "Коллекция",
                "en" to "Collection",
            ),
        )

        assertEquals("Brand", definition.displayTitle(locale = "en"))
        assertEquals("Popular", preset.displayTitle(locale = "en-GB"))
        assertEquals("Collection", collection.displayTitle(locale = "en-US"))
    }

    @Test
    fun catalogValueOption_prefersLocalizedLabel_andFallsBackToUndOrCode() {
        val localized = CatalogValueOption(
            valueCode = "BLACK",
            labels = localizedTextOf("ru" to "Черный", "en" to "Black"),
            aliases = listOf("black", "черный"),
        )
        val undOnly = CatalogValueOption(
            valueCode = "RUB",
            labels = localizedTextOf("und" to "RUB"),
        )
        val missingLabels = CatalogValueOption(
            valueCode = "USED",
            labels = LocalizedText.Empty,
        )

        assertEquals("Черный", localized.displayLabel(locale = "ru-RU"))
        assertEquals("Black", localized.displayLabel(locale = "en-US"))
        assertEquals("RUB", undOnly.displayLabel(locale = "de-DE"))
        assertEquals("USED", missingLabels.displayLabel(locale = "ru-RU"))
    }
}
