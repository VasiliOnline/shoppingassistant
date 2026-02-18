package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21AutoQueryRouterTest {
    private val router = Stage21AutoQueryRouter()

    @Test
    fun route_accessories_query_to_auto_accessories() = runBlocking {
        val result = router.route(query = "видеорегистратор в машину", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("AUTO.ACCESSORIES", result.primaryTargetCode)
    }

    @Test
    fun route_parts_query_to_auto_parts() = runBlocking {
        val result = router.route(query = "тормозные колодки", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("AUTO.PARTS", result.primaryTargetCode)
    }

    @Test
    fun route_tires_query_to_auto_tires_wheels() = runBlocking {
        val result = router.route(query = "зимняя резина r17", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("AUTO.TIRES_WHEELS", result.primaryTargetCode)
    }

    @Test
    fun route_tools_query_to_auto_tools_garage() = runBlocking {
        val result = router.route(query = "obd2 сканер", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("AUTO.TOOLS_GARAGE", result.primaryTargetCode)
    }

    @Test
    fun route_kids_disambiguation_to_kids_strollers() = runBlocking {
        val result = router.route(query = "детское автокресло isofix", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("KIDS.STROLLERS_CARSEATS", result.primaryTargetCode)
    }

    @Test
    fun route_home_tools_disambiguation_to_home_repair_tools() = runBlocking {
        val result = router.route(query = "шуруповерт аккумуляторный", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.REPAIR_TOOLS", result.primaryTargetCode)
    }

    @Test
    fun route_phone_accessory_disambiguation_to_tech_phones() = runBlocking {
        val result = router.route(query = "зарядка usb c", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun route_blocked_noise_query_to_auto_root_browse() = runBlocking {
        val result = router.route(query = "каталог запчастей pdf", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.AUTO", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_auto_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("AUTO", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21AutoPackageLoader.goldenQueries
        val expectedRate = Stage21AutoPackageLoader.coverageGate.gates.goldenSetPassRate

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
