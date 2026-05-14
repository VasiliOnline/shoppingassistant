package com.example.shoppingassistant.core.data.ingest.avito

import com.example.shoppingassistant.core.data.ingest.replay.NoopIngestReplayStore
import com.example.shoppingassistant.domain.catalog.QueryRouteType
import com.example.shoppingassistant.domain.catalog.QueryRouter
import com.example.shoppingassistant.domain.catalog.QueryRoutingResult
import com.example.shoppingassistant.domain.ingest.IngestStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AvitoSourceParserTest {
    @Test
    fun load_mapsLegacyTechSlugsToCurrentLeafCategories() = runBlocking {
        val cases = mapOf(
            "noutbuki" to "TECH.COMPUTERS",
            "planshety_i_elektronnye_knigi" to "TECH.TABLETS_E_READERS",
            "tv_i_videotekhnika" to "TECH.TV_HOME_THEATER",
            "fototehnika" to "TECH.CAMERAS_DRONES",
            "umnyy_dom" to "TECH.SMART_HOME_SECURITY",
        )

        cases.forEach { (slug, expectedCategoryCode) ->
            val parser = AvitoSourceParser(
                httpClient = httpClientForListing(),
                replayStore = NoopIngestReplayStore(),
                queryRouter = LowConfidenceRouter,
            )

            val result = parser.load("https://www.avito.ru/moskva/$slug/test_1234567890")

            assertEquals(IngestStatus.OK, result.status)
            assertEquals(slug, expectedCategoryCode, result.offer?.categoryCode)
        }
    }

    private fun httpClientForListing(): HttpClient {
        val engine = MockEngine {
            respond(
                content = """
                    <html>
                      <head>
                        <meta property="og:title" content="Test listing">
                        <meta property="og:description" content="Test description">
                      </head>
                      <body>1 000 ₽</body>
                    </html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        }
        return HttpClient(engine)
    }

    private object LowConfidenceRouter : QueryRouter {
        override suspend fun route(query: String, locale: String): QueryRoutingResult =
            QueryRoutingResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = "TECH.PHONES",
                confidence = 0.1,
            )
    }
}
