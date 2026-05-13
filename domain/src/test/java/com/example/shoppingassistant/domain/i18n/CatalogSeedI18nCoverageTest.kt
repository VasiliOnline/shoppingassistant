package com.example.shoppingassistant.domain.i18n

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSeedI18nCoverageTest {
    @Test
    fun loadedSeed_hasEnglishTitles_forCatalogDisplayEntities() {
        val missingCategories = CatalogSeed.categories
            .filter { it.title["en"].isNullOrBlank() }
            .map { it.code }
        val missingBrowseNodes = CatalogSeed.browseNodes
            .filter { it.title["en"].isNullOrBlank() }
            .map { it.browseCode }
        val missingFacetDefinitions = CatalogSeed.facetDefinitions
            .filter { it.title["en"].isNullOrBlank() }
            .map { it.facetKey }
        val missingFacetPresets = CatalogSeed.facetPresets
            .filter { it.title["en"].isNullOrBlank() }
            .map { it.presetCode }
        val missingFacetCollections = CatalogSeed.facetCollections
            .filter { it.title["en"].isNullOrBlank() }
            .map { it.collectionCode }

        assertTrue("Missing category titleEn: ${missingCategories.joinToString()}", missingCategories.isEmpty())
        assertTrue("Missing browse node titleEn: ${missingBrowseNodes.joinToString()}", missingBrowseNodes.isEmpty())
        assertTrue(
            "Missing facet definition titleEn: ${missingFacetDefinitions.joinToString()}",
            missingFacetDefinitions.isEmpty(),
        )
        assertTrue(
            "Missing facet preset titleEn: ${missingFacetPresets.joinToString()}",
            missingFacetPresets.isEmpty(),
        )
        assertTrue(
            "Missing facet collection titleEn: ${missingFacetCollections.joinToString()}",
            missingFacetCollections.isEmpty(),
        )
    }
}
