package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Stage21ApplQueryRouterTest {
    private val router = Stage21ApplQueryRouter()

    @Test
    fun route_refrigerator_to_appl_leaf_node() = runBlocking {
        val result = router.route(query = "холодильник", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.APPL.MAJOR.REFRIGERATORS", result.primaryTargetCode)
    }

    @Test
    fun route_robot_vacuum_to_specific_leaf() = runBlocking {
        val result = router.route(query = "робот пылесос", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.APPL.HOMECARE.ROBOT.VACUUMS", result.primaryTargetCode)
    }

    @Test
    fun route_hair_conditioner_to_beauty_disambiguation() = runBlocking {
        val result = router.route(query = "кондиционер для волос", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("BEAUTY.HAIRCARE", result.primaryTargetCode)
    }

    @Test
    fun route_osb_plate_to_home_repair_disambiguation() = runBlocking {
        val result = router.route(query = "плита osb купить", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("HOME.REPAIR_TOOLS", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_fallbacks_to_appl_root_category() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("APPL", result.primaryTargetCode)
    }
}
