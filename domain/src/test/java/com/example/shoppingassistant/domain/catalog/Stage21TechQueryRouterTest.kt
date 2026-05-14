package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage21TechQueryRouterTest {
    private val router = Stage21TechQueryRouter()

    @Test
    fun routing_rules_are_loaded() {
        assertTrue("triggers=${router.debugTriggerCount}", router.debugTriggerCount > 0)
        assertTrue("conflicts=${router.debugConflictCount}", router.debugConflictCount > 0)
    }

    @Test
    fun route_smartphone_to_phones_leaf() = runBlocking {
        val result = router.route(query = "смартфон", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
        assertTrue(result.confidence >= 0.7)
    }

    @Test
    fun route_case_to_phone_accessories_leaf() = runBlocking {
        val debug = router.routeWithCandidates(query = "чехол на айфон 13", locale = "ru-RU")
        assertEquals("top=${debug.topCandidates}", QueryRouteType.OPEN_CATEGORY, debug.result.routeType)
        assertEquals("top=${debug.topCandidates}", "TECH.PHONE_ACCESSORIES", debug.result.primaryTargetCode)
    }

    @Test
    fun route_airpods_to_audio_by_conflict_rule() = runBlocking {
        val result = router.route(query = "airpods pro", locale = "en-US")
        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.AUDIO", result.primaryTargetCode)
    }

    @Test
    fun route_ambiguous_electronics_to_browse_root() = runBlocking {
        val result = router.route(query = "электроника", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.TECH", result.primaryTargetCode)
    }

    @Test
    fun route_unknown_query_fallbacks_to_browse_root() = runBlocking {
        val result = router.route(query = "абракадабра qwerty", locale = "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.TECH", result.primaryTargetCode)
    }

    @Test
    fun route_with_candidates_returns_ranked_list() {
        val debug = router.routeWithCandidates(query = "apple tv приставка", locale = "ru-RU")
        assertNotNull(debug.result.primaryTargetCode)
        assertFalse(debug.topCandidates.isEmpty())
        assertTrue(debug.topCandidates.size <= 3)
        assertTrue(debug.topCandidates.any { it.targetCode == "TECH.TV_HOME_THEATER" })
    }
}
