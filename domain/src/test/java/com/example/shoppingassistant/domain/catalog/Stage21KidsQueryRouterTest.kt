package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21KidsQueryRouterTest {
    private val router = Stage21KidsQueryRouter()

    @Test
    fun route_baby_gear_query_to_kids_baby_gear() = runBlocking {
        val result = router.route(query = "подгузники", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("KIDS.BABY_GEAR", result.primaryTargetCode)
    }

    @Test
    fun route_toys_query_to_kids_toys_games() = runBlocking {
        val result = router.route(query = "lego набор", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("KIDS.TOYS_GAMES", result.primaryTargetCode)
    }

    @Test
    fun route_stroller_query_to_kids_strollers_carseats() = runBlocking {
        val result = router.route(query = "автокресло isofix", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("KIDS.STROLLERS_CARSEATS", result.primaryTargetCode)
    }

    @Test
    fun route_clothing_disambiguation_to_fash_kids() = runBlocking {
        val result = router.route(query = "детская куртка", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FASH.KIDS", result.primaryTargetCode)
    }

    @Test
    fun route_phone_disambiguation_to_tech_phones() = runBlocking {
        val result = router.route(query = "iphone 15", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun route_blocked_noise_query_to_kids_root_browse() = runBlocking {
        val result = router.route(query = "сказки", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.KIDS", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_kids_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("KIDS", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21KidsPackageLoader.goldenQueries
        val expectedRate = Stage21KidsPackageLoader.coverageGate.gates.goldenSetPassRate

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
