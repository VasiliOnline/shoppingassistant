package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage3TopPresetsGateTest {
    @Test
    fun top_leaf_categories_must_have_at_least_three_working_presets() {
        val topLeafCategories = CatalogSeedResourceReader.readJson(
            resourcePath = TOP_LEAF_CATEGORIES_PATH,
            deserializer = ListSerializer(String.serializer()),
        )
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        assertTrue("Top leaf categories list must not be empty.", topLeafCategories.isNotEmpty())

        val leafCodes = leafCategoryCodes(CatalogSeed.categories)
        val nonLeafInTop = topLeafCategories.filterNot { it in leafCodes }
        assertTrue(
            "Top leaf categories must only contain leaf category codes: ${nonLeafInTop.joinToString()}",
            nonLeafInTop.isEmpty(),
        )

        val presetsByCategory = CatalogSeed.facetPresets.groupBy { it.categoryCode.trim() }
        val underCovered = mutableListOf<String>()
        val noVariant = mutableListOf<String>()
        val emptyRules = mutableListOf<String>()

        topLeafCategories.forEach { categoryCode ->
            val presets = presetsByCategory[categoryCode].orEmpty()
            if (presets.size < 3) {
                underCovered += "$categoryCode:${presets.size}"
            }
            val nonDefault = presets.count { !it.presetCode.endsWith(".DEFAULT") }
            if (nonDefault < 2) {
                noVariant += "$categoryCode:$nonDefault"
            }
            presets.filter { it.rules.isEmpty() }.forEach { preset ->
                emptyRules += preset.presetCode
            }
        }

        assertTrue(
            "Top categories must have >=3 presets each. Under-covered: ${underCovered.joinToString()}",
            underCovered.isEmpty(),
        )
        assertTrue(
            "Top categories must have at least 2 non-default presets. Missing variants: ${noVariant.joinToString()}",
            noVariant.isEmpty(),
        )
        assertTrue(
            "Presets must contain at least one rule. Empty presets: ${emptyRules.joinToString()}",
            emptyRules.isEmpty(),
        )
    }

    private fun leafCategoryCodes(categories: List<Category>): Set<String> {
        val categoryCodes = categories
            .asSequence()
            .map { it.code.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val parentCodes = categories
            .asSequence()
            .mapNotNull { it.parentCode?.trim()?.takeIf(String::isNotBlank) }
            .toSet()
        return categoryCodes - parentCodes
    }

    private companion object {
        private const val TOP_LEAF_CATEGORIES_PATH = "taxonomy/stage3/3.0/top_leaf_categories.json"
    }
}
