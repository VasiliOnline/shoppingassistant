package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21HybridQueryRouterTest {
    private val router = Stage21HybridQueryRouter()

    @Test
    fun route_tech_query_prefers_tech_router() = runBlocking {
        val result = router.route(query = "смартфон", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun route_appl_query_prefers_appl_router() = runBlocking {
        val result = router.route(query = "холодильник", locale = "ru-RU")
        assertTrue(
            "Expected APPL target, got route=${result.routeType}, target=${result.primaryTargetCode}",
            result.primaryTargetCode == "B.APPL.MAJOR.REFRIGERATORS" ||
                result.primaryTargetCode == "APPL.MAJOR" ||
                result.primaryTargetCode?.startsWith("B.APPL") == true ||
                result.primaryTargetCode?.startsWith("APPL.") == true,
        )
    }

    @Test
    fun route_home_query_prefers_home_router() = runBlocking {
        val result = router.route(query = "диван икеа", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.FURNITURE", result.primaryTargetCode)
    }

    @Test
    fun route_fash_query_prefers_fash_router() = runBlocking {
        val result = router.route(query = "кроссовки nike", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("FASH.SHOES", result.primaryTargetCode)
    }

    @Test
    fun route_beauty_query_prefers_beauty_router() = runBlocking {
        val result = router.route(query = "сыворотка для лица", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.SKINCARE", result.primaryTargetCode)
    }

    @Test
    fun route_kids_query_prefers_kids_router() = runBlocking {
        val result = router.route(query = "подгузники", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("KIDS.BABY_GEAR", result.primaryTargetCode)
    }

    @Test
    fun route_food_query_prefers_food_router() = runBlocking {
        val result = router.route(query = "вода 5л с доставкой", locale = "ru-RU")
        assertTrue(
            "Expected FOOD target, got route=${result.routeType}, target=${result.primaryTargetCode}",
            result.primaryTargetCode == "FOOD.DRINKS" ||
                result.primaryTargetCode == "B.FOOD.DRINKS" ||
                result.primaryTargetCode == "B.FOOD" ||
                result.primaryTargetCode?.startsWith("FOOD.") == true ||
                result.primaryTargetCode?.startsWith("B.FOOD") == true,
        )
    }

    @Test
    fun route_pets_query_routes_to_pets_segment() = runBlocking {
        val result = router.route(query = "поводок рулетка для собак", locale = "ru-RU")
        assertTrue(
            "Expected PETS target, got route=${result.routeType}, target=${result.primaryTargetCode}",
            result.primaryTargetCode == "PETS.ACCESSORIES" ||
                result.primaryTargetCode == "B.PETS.ACCESSORIES" ||
                result.primaryTargetCode == "B.PETS" ||
                result.primaryTargetCode?.startsWith("PETS.") == true ||
                result.primaryTargetCode?.startsWith("B.PETS") == true,
        )
    }

    @Test
    fun route_sport_query_routes_to_sport_segment() = runBlocking {
        val result = router.route(query = "велосипед горный Trek", locale = "ru-RU")
        assertTrue(
            "Expected SPORT target, got route=${result.routeType}, target=${result.primaryTargetCode}",
            result.primaryTargetCode == "SPORT.BIKES_SCOOTERS" ||
                result.primaryTargetCode == "B.SPORT.BIKES" ||
                result.primaryTargetCode == "B.SPORT" ||
                result.primaryTargetCode?.startsWith("SPORT.") == true ||
                result.primaryTargetCode?.startsWith("B.SPORT") == true,
        )
    }

    @Test
    fun route_auto_query_routes_to_auto_segment() = runBlocking {
        val result = router.route(query = "зимняя резина r17", locale = "ru-RU")
        assertTrue(
            "Expected AUTO target, got route=${result.routeType}, target=${result.primaryTargetCode}",
            result.primaryTargetCode == "AUTO.TIRES_WHEELS" ||
                result.primaryTargetCode == "B.AUTO.TIRES" ||
                result.primaryTargetCode == "B.AUTO" ||
                result.primaryTargetCode?.startsWith("AUTO.") == true ||
                result.primaryTargetCode?.startsWith("B.AUTO") == true,
        )
    }

    @Test
    fun route_unknown_query_keeps_global_fallback() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.TECH", result.primaryTargetCode)
    }
}
