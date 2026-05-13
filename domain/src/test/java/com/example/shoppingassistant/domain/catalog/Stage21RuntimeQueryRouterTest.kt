package com.example.shoppingassistant.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class Stage21RuntimeQueryRouterTest {

    @Test
    fun picksMostConfidentSegmentCandidate() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter(
            segmentRouters = listOf(
                FakeRouter(
                    QueryRoutingResult(
                        routeType = QueryRouteType.OPEN_CATEGORY,
                        primaryTargetCode = "TECH.PHONES",
                        confidence = 0.60,
                    ),
                ),
                FakeRouter(
                    QueryRoutingResult(
                        routeType = QueryRouteType.OPEN_BROWSE,
                        primaryTargetCode = "B.FOOD",
                        confidence = 0.82,
                    ),
                ),
            ),
            fallbackRouter = FakeRouter(
                QueryRoutingResult(
                    routeType = QueryRouteType.RUN_SEARCH,
                    confidence = 0.25,
                ),
            ),
            minConfidenceToAccept = 0.55,
        )

        val result = router.route("пицца", "ru-RU")
        assertEquals(QueryRouteType.OPEN_BROWSE, result.routeType)
        assertEquals("B.FOOD", result.primaryTargetCode)
    }

    @Test
    fun fallsBackToRunSearch_whenAllCandidatesLowConfidence() = kotlinx.coroutines.runBlocking {
        val fallback = QueryRoutingResult(
            routeType = QueryRouteType.RUN_SEARCH,
            primaryTargetCode = null,
            confidence = 0.3,
        )
        val router = Stage21RuntimeQueryRouter(
            segmentRouters = listOf(
                FakeRouter(
                    QueryRoutingResult(
                        routeType = QueryRouteType.OPEN_CATEGORY,
                        primaryTargetCode = "TECH.PHONES",
                        confidence = 0.40,
                    ),
                ),
            ),
            fallbackRouter = FakeRouter(fallback),
            minConfidenceToAccept = 0.55,
        )

        val result = router.route("непонятный запрос", "ru-RU")
        assertEquals(fallback, result)
    }

    @Test
    fun ignoresSegmentRunSearchResults() = kotlinx.coroutines.runBlocking {
        val fallback = QueryRoutingResult(
            routeType = QueryRouteType.RUN_SEARCH,
            confidence = 0.15,
        )
        val router = Stage21RuntimeQueryRouter(
            segmentRouters = listOf(
                FakeRouter(
                    QueryRoutingResult(
                        routeType = QueryRouteType.RUN_SEARCH,
                        confidence = 0.99,
                    ),
                ),
            ),
            fallbackRouter = FakeRouter(fallback),
            minConfidenceToAccept = 0.55,
        )

        val result = router.route("query", "ru-RU")
        assertEquals(fallback, result)
    }

    @Test
    fun canonical_phone_family_query_overrides_cross_segment_fallbacks() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("айфон 16 про серый 256гб", "ru-RU")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun canonical_laptop_family_query_routes_to_laptops() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("macbook air m3 15", "en-US")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.LAPTOPS", result.primaryTargetCode)
    }

    @Test
    fun canonical_ereader_family_query_routes_to_tablets_and_ebooks() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("киндл paperwhite", "ru-RU")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.TABLETS_EBOOKS", result.primaryTargetCode)
    }

    @Test
    fun canonical_phone_model_query_routes_to_phones_from_short_model_alias() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("s24 ultra 256gb", "en-US")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun compact_phone_family_query_routes_to_phones_without_wrong_segment_fallback() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("iphone17pro256gb", "en-US")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun internal_pixel_phone_alias_routes_to_phones() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("pixel phone", "en-US")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    @Test
    fun compact_long_tail_phone_model_query_routes_to_phones() = kotlinx.coroutines.runBlocking {
        val router = Stage21RuntimeQueryRouter()

        val result = router.route("pocof6pro512gb", "en-US")

        assertEquals(QueryRouteType.OPEN_CATEGORY, result.routeType)
        assertEquals("TECH.PHONES", result.primaryTargetCode)
    }

    private class FakeRouter(
        private val result: QueryRoutingResult,
    ) : QueryRouter {
        override suspend fun route(query: String, locale: String): QueryRoutingResult = result
    }
}
