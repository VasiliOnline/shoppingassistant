package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21FoodQueryRouterTest {
    private val router = Stage21FoodQueryRouter()

    @Test
    fun route_drinks_query_to_food_drinks() = runBlocking {
        val result = router.route(query = "вода 5л с доставкой", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FOOD.DRINKS", result.primaryTargetCode)
    }

    @Test
    fun route_ready_meals_query_to_food_ready_meals() = runBlocking {
        val result = router.route(query = "пицца и роллы", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FOOD.READY_MEALS", result.primaryTargetCode)
    }

    @Test
    fun route_pet_food_disambiguation_to_pets_food() = runBlocking {
        val result = router.route(query = "корм whiskas для кошек", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("PETS.FOOD", result.primaryTargetCode)
    }

    @Test
    fun route_supplements_disambiguation_to_beauty_health() = runBlocking {
        val result = router.route(query = "витамины омега 3", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.HEALTH", result.primaryTargetCode)
    }

    @Test
    fun route_kitchen_appliance_disambiguation_to_appl_small() = runBlocking {
        val result = router.route(query = "электрочайник", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("APPL.SMALL", result.primaryTargetCode)
    }

    @Test
    fun route_kitchenware_disambiguation_to_home_kitchen_dining() = runBlocking {
        val result = router.route(query = "набор кастрюль и посуды", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.KITCHEN_DINING", result.primaryTargetCode)
    }

    @Test
    fun route_blocked_noise_query_to_food_root_browse() = runBlocking {
        val result = router.route(query = "как приготовить борщ", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.FOOD", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_to_food_canonical_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FOOD", result.primaryTargetCode)
    }

    @Test
    fun golden_set_pass_rate_meets_coverage_gate() = runBlocking {
        val golden = Stage21FoodPackageLoader.goldenQueries
        val expectedRate = Stage21FoodPackageLoader.coverageGate.gates.goldenSetPassRate

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
