package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage2BrowseRootsContractTest {
    private val requiredBrowseRoots = setOf(
        "B.TECH",
        "B.APPL",
        "B.HOME",
        "B.FASH",
        "B.BEAUTY",
        "B.KIDS",
        "B.FOOD",
        "B.PETS",
        "B.SPORT",
        "B.AUTO",
    )

    @Test
    fun browseSeed_containsAllRequiredRoots() {
        val rootCodes = browseRootCodes()
        val missing = (requiredBrowseRoots - rootCodes).sorted()
        assertTrue("Missing required browse roots: ${missing.joinToString(", ")}", missing.isEmpty())
    }

    @Test
    fun stage21FallbackBrowseRoots_arePresentInBrowseSeed() = runBlocking {
        val rootCodes = browseRootCodes()
        val scenarios = listOf(
            FallbackScenario(
                name = "tech",
                result = Stage21TechQueryRouter().route(query = "абракадабра qwerty", locale = "ru-RU"),
                expectedBrowseRoot = "B.TECH",
            ),
            FallbackScenario(
                name = "home",
                result = Stage21HomeQueryRouter().route(query = "скачать обои", locale = "ru-RU"),
                expectedBrowseRoot = "B.HOME",
            ),
            FallbackScenario(
                name = "beauty",
                result = Stage21BeautyQueryRouter().route(query = "как сделать макияж", locale = "ru-RU"),
                expectedBrowseRoot = "B.BEAUTY",
            ),
            FallbackScenario(
                name = "kids",
                result = Stage21KidsQueryRouter().route(query = "сказки", locale = "ru-RU"),
                expectedBrowseRoot = "B.KIDS",
            ),
            FallbackScenario(
                name = "food",
                result = Stage21FoodQueryRouter().route(query = "как приготовить борщ", locale = "ru-RU"),
                expectedBrowseRoot = "B.FOOD",
            ),
            FallbackScenario(
                name = "pets",
                result = Stage21PetsQueryRouter().route(query = "как дрессировать собаку", locale = "ru-RU"),
                expectedBrowseRoot = "B.PETS",
            ),
            FallbackScenario(
                name = "sport",
                result = Stage21SportQueryRouter().route(query = "как накачать пресс", locale = "ru-RU"),
                expectedBrowseRoot = "B.SPORT",
            ),
            FallbackScenario(
                name = "auto",
                result = Stage21AutoQueryRouter().route(query = "каталог запчастей pdf", locale = "ru-RU"),
                expectedBrowseRoot = "B.AUTO",
            ),
            FallbackScenario(
                name = "hybrid",
                result = Stage21HybridQueryRouter().route(query = "абракадабра qwerty", locale = "ru-RU"),
                expectedBrowseRoot = "B.TECH",
            ),
        )

        scenarios.forEach { scenario ->
            assertEquals("${scenario.name}: route type", QueryRouteType.OPEN_BROWSE, scenario.result.routeType)
            assertEquals("${scenario.name}: target code", scenario.expectedBrowseRoot, scenario.result.primaryTargetCode)
            assertTrue(
                "${scenario.name}: fallback root '${scenario.expectedBrowseRoot}' is missing in browse seed roots",
                scenario.expectedBrowseRoot in rootCodes,
            )
        }

        val usedRoots = scenarios.map { it.expectedBrowseRoot }.toSet()
        val nonRequiredRoots = (usedRoots - requiredBrowseRoots).sorted()
        assertTrue(
            "Fallback roots are outside required browse roots: ${nonRequiredRoots.joinToString(", ")}",
            nonRequiredRoots.isEmpty(),
        )
    }

    private fun browseRootCodes(): Set<String> = CatalogSeed.browseNodes
        .asSequence()
        .filter { it.parentBrowseCode.isNullOrBlank() }
        .map { it.browseCode.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private data class FallbackScenario(
        val name: String,
        val result: QueryRoutingResult,
        val expectedBrowseRoot: String,
    )
}
