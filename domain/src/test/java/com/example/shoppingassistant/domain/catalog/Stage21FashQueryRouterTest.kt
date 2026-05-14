package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21FashQueryRouterTest {
    private val router = Stage21FashQueryRouter()

    @Test
    fun route_women_query_to_fash_women() = runBlocking {
        val result = router.route(query = "женская одежда", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FASH.WOMEN", result.primaryTargetCode)
    }

    @Test
    fun route_shoes_query_to_fash_shoes() = runBlocking {
        val result = router.route(query = "кроссовки nike", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FASH.SHOES", result.primaryTargetCode)
    }

    @Test
    fun route_fash_surface_queries_to_expected_public_branches() = runBlocking {
        val cases = linkedMapOf(
            "штаны мужские красные" to "FASH.MEN",
            "мужские спортивные брюки" to "FASH.MEN",
            "женские брюки красные" to "FASH.WOMEN",
            "тренировочные штаны женские" to "FASH.WOMEN",
            "детская куртка зимняя" to "FASH.KIDS",
            "шапка детская" to "FASH.KIDS",
            "детская обувь кроссовки" to "FASH.SHOES",
            "ботинки зимние" to "FASH.SHOES",
            "рюкзак nike городской" to "FASH.BAGS",
            "кошелек женский красный кожаный" to "FASH.BAGS",
            "ремень мужской" to "FASH.ACCESSORIES",
            "шарф кашемировый" to "FASH.ACCESSORIES",
        )

        cases.forEach { (query, expectedCode) ->
            val result = router.route(query = query, locale = "ru-RU")
            assertEquals("$query routeType", QueryRouteType.OPEN_CATEGORY, result.routeType)
            assertEquals("$query category", expectedCode, result.primaryTargetCode)
        }
    }

    @Test
    fun route_household_gloves_to_home_cleaning_disambiguation() = runBlocking {
        val result = router.route(query = "перчатки хозяйственные для уборки", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.CLEANING", result.primaryTargetCode)
    }

    @Test
    fun route_laptop_without_bag_tokens_to_tech_laptops() = runBlocking {
        val result = router.route(query = "ноутбук lenovo ideapad", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.COMPUTERS", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_fash_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FASH", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21FashPackageLoader.goldenQueries
        val expectedRate = Stage21FashPackageLoader.coverageGate.gates.goldenSetPassRate

        val misses = mutableListOf<String>()
        val hits = golden.count { query ->
            val result = router.route(query = query.queryText, locale = query.locale)
            val matched = when (query.expectedTargetKind) {
                GoldenTargetKind.CATEGORY_LEAF ->
                    result.routeType == QueryRouteType.OPEN_CATEGORY && result.primaryTargetCode == query.expectedCode

                GoldenTargetKind.BROWSE_NODE ->
                    result.routeType == QueryRouteType.OPEN_BROWSE && result.primaryTargetCode == query.expectedCode
            }
            if (!matched && misses.size < 20) {
                misses += "${query.queryId}:${query.queryText} -> expected=${query.expectedCode}, actual=${result.primaryTargetCode}/${result.routeType}"
            }
            matched
        }

        val total = golden.size.coerceAtLeast(1)
        val passRate = hits / total.toDouble()
        assertTrue(
            "Golden pass rate=$passRate expected>=$expectedRate; misses=${misses.joinToString("; ")}",
            passRate + 1e-9 >= expectedRate,
        )
    }
}
