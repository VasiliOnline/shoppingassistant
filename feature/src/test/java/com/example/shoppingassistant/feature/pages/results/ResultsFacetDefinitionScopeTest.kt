package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsFacetDefinitionScopeTest {

    @Test
    fun returns_empty_when_category_not_selected() {
        val scoped = scopeFacetDefinitionsForCategory(
            definitions = listOf(
                definition(facetKey = "brand", appliesTo = listOf("KIDS.TOYS_GAMES")),
            ),
            categoryCode = null,
        )

        assertEquals(emptyList<FacetDefinition>(), scoped)
    }

    @Test
    fun keeps_only_definitions_for_selected_category_case_insensitive() {
        val scoped = scopeFacetDefinitionsForCategory(
            definitions = listOf(
                definition(facetKey = "brand", appliesTo = listOf("KIDS.TOYS_GAMES")),
                definition(facetKey = "material", appliesTo = listOf("kids.toys_games")),
                definition(facetKey = "storage_gb", appliesTo = listOf("TECH.COMPUTERS")),
            ),
            categoryCode = " kids.toys_games ",
        )

        assertEquals(
            listOf("brand", "material"),
            scoped.map { it.facetKey },
        )
    }

    @Test
    fun deduplicates_keys_after_normalization() {
        val scoped = scopeFacetDefinitionsForCategory(
            definitions = listOf(
                definition(facetKey = " Color ", appliesTo = listOf("KIDS.TOYS_GAMES")),
                definition(facetKey = "color", appliesTo = listOf("KIDS.TOYS_GAMES")),
            ),
            categoryCode = "KIDS.TOYS_GAMES",
        )

        assertEquals(1, scoped.size)
        assertEquals(" Color ", scoped.first().facetKey)
    }

    @Test
    fun preset_scope_resolves_target_category_before_filtering_definitions() {
        val scoped = resolveResultsPresetFacetDefinitions(
            definitions = listOf(
                definition(
                    facetKey = "storage",
                    appliesTo = listOf("TECH.PHONES"),
                    attributeCode = "storage_gb",
                ),
                definition(
                    facetKey = "storage",
                    appliesTo = listOf("TECH.OLD_PHONES"),
                    attributeCode = "legacy_storage",
                ),
            ),
            currentCategoryCode = "TECH.OLD_PHONES",
            collection = FacetCollection(
                collectionCode = "phones_collection",
                categoryCode = "TECH.OLD_PHONES",
                title = localizedTextOf("ru" to "Телефоны"),
            ),
            preset = null,
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

        assertEquals(listOf("storage_gb"), scoped.map { it.attributeCode })
    }

    @Test
    fun preset_scope_prefers_target_category_when_facet_keys_repeat_globally() {
        val scoped = resolveResultsPresetFacetDefinitions(
            definitions = listOf(
                definition(
                    facetKey = "capacity",
                    appliesTo = listOf("TECH.PHONES"),
                    attributeCode = "storage_gb",
                ),
                definition(
                    facetKey = "capacity",
                    appliesTo = listOf("HOME.KITCHEN"),
                    attributeCode = "capacity_l",
                ),
            ),
            currentCategoryCode = "TECH.PHONES",
            collection = FacetCollection(
                collectionCode = "kitchen_collection",
                categoryCode = "HOME.KITCHEN",
                title = localizedTextOf("ru" to "Кухня"),
            ),
            preset = null,
            categories = emptyList(),
        )

        assertEquals(listOf("capacity_l"), scoped.map { it.attributeCode })
    }

    private fun definition(
        facetKey: String,
        appliesTo: List<String>,
        attributeCode: String? = null,
    ): FacetDefinition = FacetDefinition(
        facetKey = facetKey,
        title = localizedTextOf("ru" to facetKey),
        valueType = FacetDataType.ENUM,
        appliesToCategoryCodes = appliesTo,
        attributeCode = attributeCode,
    )
}
