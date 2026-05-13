package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedRunSearchQueryRouterTest {
    @Test
    fun always_routes_to_run_search() = runBlocking {
        val router = SeedRunSearchQueryRouter()

        val result = router.route(query = "iPhone 13 Pro", locale = "ru-RU")

        assertEquals(QueryRouteType.RUN_SEARCH, result.routeType)
        assertTrue(result.extractedTokens.isNotEmpty())
    }
}
