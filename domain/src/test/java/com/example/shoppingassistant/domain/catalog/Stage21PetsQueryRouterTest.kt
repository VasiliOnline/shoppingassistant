package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21PetsQueryRouterTest {
    private val router = Stage21PetsQueryRouter()

    @Test
    fun route_pet_food_query_to_pets_food() = runBlocking {
        val result = router.route(query = "корм для кошек royal canin", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("PETS.FOOD", result.primaryTargetCode)
    }

    @Test
    fun route_hygiene_query_to_pets_hygiene() = runBlocking {
        val result = router.route(query = "наполнитель для кошачьего туалета", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("PETS.HYGIENE", result.primaryTargetCode)
    }

    @Test
    fun route_accessories_query_to_pets_accessories() = runBlocking {
        val result = router.route(query = "поводок рулетка для собак", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("PETS.ACCESSORIES", result.primaryTargetCode)
    }

    @Test
    fun route_health_query_to_pets_health() = runBlocking {
        val result = router.route(query = "капли от блох для собак", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("PETS.HEALTH", result.primaryTargetCode)
    }

    @Test
    fun route_human_haircare_disambiguation_to_beauty_haircare() = runBlocking {
        val result = router.route(query = "шампунь и бальзам для волос", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.HAIRCARE", result.primaryTargetCode)
    }

    @Test
    fun route_human_supplements_disambiguation_to_beauty_health() = runBlocking {
        val result = router.route(query = "витамины омега 3 магний", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.HEALTH", result.primaryTargetCode)
    }

    @Test
    fun route_human_grocery_disambiguation_to_food_groceries() = runBlocking {
        val result = router.route(query = "хлеб и молоко с доставкой", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FOOD.GROCERIES", result.primaryTargetCode)
    }

    @Test
    fun route_plants_disambiguation_to_home_garden() = runBlocking {
        val result = router.route(query = "грунт и удобрение для цветов", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.GARDEN", result.primaryTargetCode)
    }

    @Test
    fun route_blocked_noise_query_to_pets_root_browse() = runBlocking {
        val result = router.route(query = "как дрессировать собаку", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.PETS", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_pets_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("PETS", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21PetsPackageLoader.goldenQueries
        val expectedRate = Stage21PetsPackageLoader.coverageGate.gates.goldenSetPassRate

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
