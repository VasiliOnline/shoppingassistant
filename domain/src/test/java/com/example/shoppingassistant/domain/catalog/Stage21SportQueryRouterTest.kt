package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21SportQueryRouterTest {
    private val router = Stage21SportQueryRouter()

    @Test
    fun route_bikes_query_to_sport_bikes_scooters() = runBlocking {
        val result = router.route(query = "велосипед горный Trek", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("SPORT.BIKES_SCOOTERS", result.primaryTargetCode)
    }

    @Test
    fun route_fitness_query_to_sport_fitness() = runBlocking {
        val result = router.route(query = "гантели 10 кг", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("SPORT.FITNESS", result.primaryTargetCode)
    }

    @Test
    fun route_outdoor_query_to_sport_outdoor() = runBlocking {
        val result = router.route(query = "палатка кемпинговая", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("SPORT.OUTDOOR", result.primaryTargetCode)
    }

    @Test
    fun route_hobby_query_to_sport_hobby() = runBlocking {
        val result = router.route(query = "мольберт для рисования", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("SPORT.HOBBY", result.primaryTargetCode)
    }

    @Test
    fun route_supplements_disambiguation_to_beauty_health() = runBlocking {
        val result = router.route(query = "витамин d3", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.HEALTH", result.primaryTargetCode)
    }

    @Test
    fun route_tools_disambiguation_to_home_repair_tools() = runBlocking {
        val result = router.route(query = "набор инструментов для велосипеда", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.REPAIR_TOOLS", result.primaryTargetCode)
    }

    @Test
    fun route_blocked_noise_query_to_sport_root_browse() = runBlocking {
        val result = router.route(query = "как накачать пресс", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.SPORT", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_sport_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("SPORT", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21SportPackageLoader.goldenQueries
        val expectedRate = Stage21SportPackageLoader.coverageGate.gates.goldenSetPassRate

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

