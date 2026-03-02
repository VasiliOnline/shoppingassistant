package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.facet.FacetRuntimeFilters
import com.example.shoppingassistant.domain.facet.FacetRuntimeFiltersApplier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage3RuntimeE2ETest {

    @Test
    fun query_alias_to_browse_collection_applies_preset_filters() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = Stage3AliasRepository(
                entries = listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "пицца",
                        normalizedTerm = "пицца",
                        kind = AliasKind.BROWSE,
                        targetCode = "B.FOOD.READY.05",
                        weight = 90,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = Stage3FallbackRouter(),
        )

        val routed = router.route(query = "Пицца", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, routed.routeType)
        assertEquals("B.FOOD.READY.05", routed.facetCollectionCode)

        val collection = CatalogSeed.facetCollections
            .firstOrNull { it.collectionCode == routed.facetCollectionCode }
        assertNotNull("Expected collection for routed browse code", collection)

        val presetCode = collection?.presetCode
        val preset = CatalogSeed.facetPresets.firstOrNull { it.presetCode == presetCode }
        assertNotNull("Expected preset linked from collection", preset)

        val applied = FacetRuntimeFiltersApplier.apply(
            base = FacetRuntimeFilters(),
            collection = collection,
            preset = preset,
        )

        assertEquals("FOOD.READY_MEALS", applied.categoryCode)
        assertEquals("B.FOOD.READY.05", applied.facetCollectionCode)
        assertEquals("FP.FOOD.READY.PIZZA", applied.facetPresetCode)
        assertEquals("пицца", applied.attributes["cuisine"]?.asRawString())
    }

    @Test
    fun query_alias_to_default_ready_meals_applies_price_ceiling() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = Stage3AliasRepository(
                entries = listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "готовая еда",
                        normalizedTerm = "готовая еда",
                        kind = AliasKind.BROWSE,
                        targetCode = "B.FOOD.READY",
                        weight = 80,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = Stage3FallbackRouter(),
        )

        val routed = router.route(query = "готовая еда", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, routed.routeType)
        assertEquals("B.FOOD.READY", routed.facetCollectionCode)

        val collection = CatalogSeed.facetCollections
            .firstOrNull { it.collectionCode == routed.facetCollectionCode }
        val preset = CatalogSeed.facetPresets.firstOrNull { it.presetCode == collection?.presetCode }
        val applied = FacetRuntimeFiltersApplier.apply(
            base = FacetRuntimeFilters(),
            collection = collection,
            preset = preset,
        )

        assertEquals("FOOD.READY_MEALS", applied.categoryCode)
        assertEquals(2500, applied.priceMax)
        assertTrue(applied.priceMin == null)
    }
}

private class Stage3AliasRepository(
    private val entries: List<AliasEntry>,
) : AliasEntryRepository {
    override suspend fun listAliasEntries(locale: String?): List<AliasEntry> {
        if (locale.isNullOrBlank()) return entries
        return entries.filter { entry -> entry.locale.equals(locale, ignoreCase = true) }
    }
}

private class Stage3FallbackRouter : QueryRouter {
    override suspend fun route(query: String, locale: String): QueryRoutingResult =
        QueryRoutingResult(
            routeType = QueryRouteType.RUN_SEARCH,
            primaryTargetCode = null,
            confidence = 0.1,
        )
}
