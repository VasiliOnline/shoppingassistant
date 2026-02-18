package com.example.shoppingassistant.core.config

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Загрузка границ нормализации (bounds) из weights.json:
 * {
 *   "bounds": {
 *     "price": [min, max],
 *     "deliveryDays": [min, max],
 *     "sellerRating": [min, max]
 *   }
 * }
 */
object RankBoundsConfig {

    private const val CLASSPATH = "/assets/weights.json"
    private const val RELATIVE =
        "core/src/main/java/com/example/shoppingassistant/core/main/assets/weights.json"

    data class RankBounds(
        val price: ClosedFloatingPointRange<Float>,
        val deliveryDays: IntRange,
        val sellerRating: ClosedFloatingPointRange<Float>
    )

    fun load(): RankBounds {
        val text = loadFromClasspath() ?: loadFromFile(RELATIVE) ?: return defaults()
        return parse(text) ?: defaults()
    }

    private fun loadFromClasspath(): String? = runCatching {
        val s = RankBoundsConfig::class.java.getResourceAsStream(CLASSPATH) ?: return null
        BufferedReader(InputStreamReader(s, Charset.forName("UTF-8"))).use { it.readText() }
    }.getOrNull()

    private fun loadFromFile(path: String): String? = runCatching {
        val p = Paths.get(path); if (!Files.exists(p)) return null
        Files.newBufferedReader(p, Charset.forName("UTF-8")).use { it.readText() }
    }.getOrNull()

    private fun parse(json: String): RankBounds? = runCatching {
        fun arr2f(key: String, defMin: Float, defMax: Float): ClosedFloatingPointRange<Float> {
            val rx = Regex(""""$key"\s*:\s*\[\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*]""")
            val m = rx.find(json)?.groupValues ?: return defMin..defMax
            val a = m[1].toFloatOrNull() ?: defMin
            val b = m[2].toFloatOrNull() ?: defMax
            return minOf(a, b)..maxOf(a, b)
        }
        fun arr2i(key: String, defMin: Int, defMax: Int): IntRange {
            val rx = Regex(""""$key"\s*:\s*\[\s*(-?\d+)\s*,\s*(-?\d+)\s*]""")
            val m = rx.find(json)?.groupValues ?: return defMin..defMax
            val a = m[1].toIntOrNull() ?: defMin
            val b = m[2].toIntOrNull() ?: defMax
            return minOf(a, b)..maxOf(a, b)
        }

        val price = arr2f("price", 1f, 5000f)
        val delivery = arr2i("deliveryDays", 1, 30)
        val seller = arr2f("sellerRating", 0f, 5f)
        RankBounds(price = price, deliveryDays = delivery, sellerRating = seller)
    }.getOrNull()

    private fun defaults() = RankBounds(
        price = 1f..5000f,
        deliveryDays = 1..30,
        sellerRating = 0f..5f
    )
}
