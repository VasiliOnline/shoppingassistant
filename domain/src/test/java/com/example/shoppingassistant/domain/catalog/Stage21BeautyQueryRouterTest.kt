package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21BeautyQueryRouterTest {
    private val router = Stage21BeautyQueryRouter()

    @Test
    fun route_skincare_query_to_beauty_skincare() = runBlocking {
        val result = router.route(query = "крем для лица", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.SKINCARE", result.primaryTargetCode)
    }

    @Test
    fun route_perfume_query_to_beauty_fragrance() = runBlocking {
        val result = router.route(query = "духи женские", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.FRAGRANCE", result.primaryTargetCode)
    }

    @Test
    fun route_phone_query_to_tech_phones_disambiguation() = runBlocking {
        val result = router.route(query = "iphone 15", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun route_vacuum_query_to_appl_small_disambiguation() = runBlocking {
        val result = router.route(query = "робот-пылесос", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("APPL.SMALL", result.primaryTargetCode)
    }

    @Test
    fun route_blocked_noise_query_to_beauty_root_browse() = runBlocking {
        val result = router.route(query = "как сделать макияж", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.BEAUTY", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_beauty_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21BeautyPackageLoader.goldenQueries
        val expectedRate = Stage21BeautyPackageLoader.coverageGate.gates.goldenSetPassRate

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
