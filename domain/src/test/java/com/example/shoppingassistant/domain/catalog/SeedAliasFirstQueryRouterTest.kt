package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedAliasFirstQueryRouterTest {

    @Test
    fun exactBrowseAlias_opensBrowseRoute() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "пицца",
                        normalizedTerm = "пицца",
                        kind = AliasKind.BROWSE,
                        targetCode = "B.FOOD.READY.01",
                        weight = 20,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(),
        )

        val result = router.route(query = "Пицца!!", locale = "ru-RU")

        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.FOOD.READY.01", result.primaryTargetCode)
        assertTrue(result.extractedTokens.contains("пицца"))
    }

    @Test
    fun exactCategoryAlias_opensCategoryRoute() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "айфон",
                        normalizedTerm = "айфон",
                        kind = AliasKind.CATEGORY,
                        targetCode = "TECH.PHONES",
                        weight = 85,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(),
        )

        val result = router.route(query = "Айфон", locale = "ru-RU")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun noExactAlias_usesFallbackRouter() = runBlocking {
        val fallback = QueryRoutingResult(
            routeType = QueryRouteType.RUN_SEARCH,
            primaryTargetCode = null,
            extractedTokens = listOf("fallback"),
            confidence = 0.1,
        )
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "айфон",
                        normalizedTerm = "айфон",
                        kind = AliasKind.CATEGORY,
                        targetCode = "TECH.PHONES",
                        weight = 85,
                        matchKind = AliasMatchKind.TOKEN,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(fallback),
        )

        val result = router.route(query = "iphone 13", locale = "ru-RU")

        assertEquals(fallback.routeType, result.routeType)
        assertEquals(fallback.extractedTokens, result.extractedTokens)
    }

    @Test
    fun localeFallback_usesDefaultLocaleWhenRequestedLocaleIsMissing() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "айфон",
                        normalizedTerm = "айфон",
                        kind = AliasKind.CATEGORY,
                        targetCode = "TECH.PHONES",
                        weight = 80,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(),
        )

        val result = router.route(query = "Айфон", locale = "en-US")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun highestWeight_winsForSameNormalizedTermCollision() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "смартфон",
                        normalizedTerm = "смартфон",
                        kind = AliasKind.CATEGORY,
                        targetCode = "TECH.PHONES",
                        weight = 40,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                    AliasEntry(
                        locale = "ru-RU",
                        term = "смартфон",
                        normalizedTerm = "смартфон",
                        kind = AliasKind.CATEGORY,
                        targetCode = "TECH.TV_VIDEO",
                        weight = 90,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(),
        )

        val result = router.route(query = "смартфон", locale = "ru-RU")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.TV_VIDEO", result.primaryTargetCode)
    }

    @Test
    fun browseKind_hasPriorityOverCategoryWhenWeightIsEqual() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "пицца",
                        normalizedTerm = "пицца",
                        kind = AliasKind.CATEGORY,
                        targetCode = "FOOD.READY_MEALS",
                        weight = 60,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                    AliasEntry(
                        locale = "ru-RU",
                        term = "пицца",
                        normalizedTerm = "пицца",
                        kind = AliasKind.BROWSE,
                        targetCode = "B.FOOD.READY.01",
                        weight = 60,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(),
        )

        val result = router.route(query = "пицца", locale = "ru-RU")

        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.FOOD.READY.01", result.primaryTargetCode)
    }

    @Test
    fun blockedAlias_isIgnoredDuringCollisionResolution() = runBlocking {
        val router = SeedAliasFirstQueryRouter(
            aliasEntryRepository = FakeAliasEntryRepository(
                listOf(
                    AliasEntry(
                        locale = "ru-RU",
                        term = "роллы",
                        normalizedTerm = "роллы",
                        kind = AliasKind.BROWSE,
                        targetCode = "B.FOOD.READY.02",
                        weight = 100,
                        matchKind = AliasMatchKind.EXACT,
                        isBlocked = true,
                        source = AliasSource.SEED,
                    ),
                    AliasEntry(
                        locale = "ru-RU",
                        term = "роллы",
                        normalizedTerm = "роллы",
                        kind = AliasKind.CATEGORY,
                        targetCode = "FOOD.READY_MEALS",
                        weight = 40,
                        matchKind = AliasMatchKind.EXACT,
                        source = AliasSource.SEED,
                    ),
                ),
            ),
            fallbackRouter = FakeFallbackRouter(),
        )

        val result = router.route(query = "роллы", locale = "ru-RU")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FOOD.READY_MEALS", result.primaryTargetCode)
    }
}

private class FakeAliasEntryRepository(
    private val entries: List<AliasEntry>,
) : AliasEntryRepository {
    override suspend fun listAliasEntries(locale: String?): List<AliasEntry> {
        if (locale.isNullOrBlank()) return entries
        return entries.filter { it.locale.equals(locale, ignoreCase = true) }
    }
}

private class FakeFallbackRouter(
    private val result: QueryRoutingResult = QueryRoutingResult(
        routeType = QueryRouteType.OPEN_BROWSE,
        primaryTargetCode = "B.TECH",
        extractedTokens = emptyList(),
        confidence = 0.2,
    ),
) : QueryRouter {
    override suspend fun route(query: String, locale: String): QueryRoutingResult = result
}
