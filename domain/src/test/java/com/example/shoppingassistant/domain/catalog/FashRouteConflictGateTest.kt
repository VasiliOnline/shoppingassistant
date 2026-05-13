package com.example.shoppingassistant.domain.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FashRouteConflictGateTest {
    private val router = Stage21FashQueryRouter()

    @Test
    fun men_positive_queries_route_to_men_branch() = runBlocking {
        listOf(
            "мужская футболка",
            "мужские джинсы",
            "рубашка мужская белая",
            "мужское худи xl",
            "мужской пуховик зимний",
            "мужские брюки классические",
        ).forEach { query ->
            val result = router.route(query = query, locale = "ru-RU")
            assertEquals("query='$query'", QueryRouteType.OPEN_CATEGORY, result.routeType)
            assertEquals("query='$query'", "FASH.MEN", result.primaryTargetCode)
        }
    }

    @Test
    fun men_negative_route_guards_do_not_route_to_men_branch() = runBlocking {
        val cases = mapOf(
            "мужские кроссовки" to "FASH.SHOES",
            "мужская обувь" to "FASH.SHOES",
            "мужская сумка" to "FASH.BAGS",
            "мужской рюкзак" to "FASH.BAGS",
            "мужской ремень" to "FASH.ACCESSORIES",
            "мужские часы" to "FASH.ACCESSORIES",
            "мужской парфюм" to "BEAUTY.FRAGRANCE",
            "мужской шампунь" to "BEAUTY.HAIRCARE",
            "детская куртка" to "FASH.KIDS",
            "женское платье" to "FASH.WOMEN",
        )

        cases.forEach { (query, expected) ->
            val result = router.route(query = query, locale = "ru-RU")
            assertNotEquals("query='$query'", "FASH.MEN", result.primaryTargetCode)
            assertEquals("query='$query'", expected, result.primaryTargetCode)
        }
    }

    @Test
    fun women_positive_queries_route_to_women_branch() = runBlocking {
        listOf(
            "женское платье",
            "женские джинсы",
            "женская блузка",
            "бюстгальтер 75B",
            "женский купальник",
            "женский пуховик зимний",
        ).forEach { query ->
            val result = router.route(query = query, locale = "ru-RU")
            assertEquals("query='$query'", QueryRouteType.OPEN_CATEGORY, result.routeType)
            assertEquals("query='$query'", "FASH.WOMEN", result.primaryTargetCode)
        }
    }

    @Test
    fun women_negative_route_guards_do_not_route_to_women_branch() = runBlocking {
        val cases = mapOf(
            "женские кроссовки nike" to "FASH.SHOES",
            "женская сумка кожаная" to "FASH.BAGS",
            "женский ремень кожаный" to "FASH.ACCESSORIES",
            "женский парфюм dior" to "BEAUTY.FRAGRANCE",
            "помада красная" to "BEAUTY.MAKEUP",
            "детское платье 110" to "FASH.KIDS",
            "мужская рубашка" to "FASH.MEN",
        )

        cases.forEach { (query, expected) ->
            val result = router.route(query = query, locale = "ru-RU")
            assertNotEquals("query='$query'", "FASH.WOMEN", result.primaryTargetCode)
            assertEquals("query='$query'", expected, result.primaryTargetCode)
        }
    }

    @Test
    fun kids_positive_queries_route_to_kids_branch() = runBlocking {
        listOf(
            "детская футболка рост 128",
            "платье для девочки 6 лет",
            "боди для малыша 68",
            "школьная форма для девочки",
        ).forEach { query ->
            val result = router.route(query = query, locale = "ru-RU")
            assertEquals("query='$query'", QueryRouteType.OPEN_CATEGORY, result.routeType)
            assertEquals("query='$query'", "FASH.KIDS", result.primaryTargetCode)
        }
    }

    @Test
    fun kids_negative_route_guards_do_not_route_to_kids_branch() = runBlocking {
        val cases = mapOf(
            "детские кроссовки" to "FASH.SHOES",
            "кроссовки для мальчика" to "FASH.SHOES",
            "детский рюкзак" to "FASH.BAGS",
            "детская коляска" to "KIDS.STROLLERS_CARSEATS",
            "подгузники" to "KIDS.BABY_GEAR",
            "детская игрушка" to "KIDS.TOYS_GAMES",
            "мужская футболка" to "FASH.MEN",
            "женское платье" to "FASH.WOMEN",
        )

        cases.forEach { (query, expected) ->
            val result = router.route(query = query, locale = "ru-RU")
            assertNotEquals("query='$query'", "FASH.KIDS", result.primaryTargetCode)
            assertEquals("query='$query'", expected, result.primaryTargetCode)
        }
    }
}
