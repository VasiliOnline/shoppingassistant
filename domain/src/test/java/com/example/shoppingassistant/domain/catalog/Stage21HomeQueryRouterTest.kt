package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21HomeQueryRouterTest {
    private val router = Stage21HomeQueryRouter()

    @Test
    fun route_furniture_query_to_home_furniture() = runBlocking {
        val result = router.route(query = "диван икеа", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.FURNITURE", result.primaryTargetCode)
    }

    @Test
    fun route_smart_lighting_query_to_tech_smart_home() = runBlocking {
        val result = router.route(query = "умная лампочка zigbee", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.SMART_HOME", result.primaryTargetCode)
    }

    @Test
    fun route_robot_vacuum_to_appl_small() = runBlocking {
        val result = router.route(query = "робот-пылесос", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("APPL.SMALL", result.primaryTargetCode)
    }

    @Test
    fun route_teapot_to_home_kitchen_dining() = runBlocking {
        val result = router.route(query = "чайник заварочный", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.KITCHEN_DINING", result.primaryTargetCode)
    }

    @Test
    fun route_generic_noise_query_to_home_browse_root() = runBlocking {
        val result = router.route(query = "скачать обои", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.HOME", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_home_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21HomePackageLoader.goldenQueries
        val expectedRate = Stage21HomePackageLoader.coverageGate.gates.goldenSetPassRate

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
