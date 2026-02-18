package com.example.shoppingassistant.core.rank

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Загрузчик Weights из weights.json без Android-зависимостей.
 * Если файл недоступен, возвращает DEFAULT.
 */
object WeightsLoader {

    private const val CLASSPATH = "/assets/weights.json"
    private const val RELATIVE = "core/src/main/java/com/example/shoppingassistant/core/main/assets/weights.json"

    private val DEFAULT = Weights(
        price = 0.6f, delivery = 0.25f, rating = 0.15f, attrPenalty = 0.2f
    )

    fun load(): Weights {
        val text = loadFromClasspath() ?: loadFromFile(RELATIVE)
        return if (text != null) parse(text) else DEFAULT
    }

    private fun loadFromClasspath(): String? = runCatching {
        val stream = WeightsLoader::class.java.getResourceAsStream(CLASSPATH) ?: return null
        BufferedReader(InputStreamReader(stream, Charset.forName("UTF-8"))).use { it.readText() }
    }.getOrNull()

    private fun loadFromFile(path: String): String? = runCatching {
        val p = Paths.get(path); if (!Files.exists(p)) return null
        Files.newBufferedReader(p, Charset.forName("UTF-8")).use { it.readText() }
    }.getOrNull()

    private fun parse(json: String): Weights = try {
        fun num(key: String, def: Float): Float {
            val rx = Regex(""""$key"\s*:\s*([-+]?\d*\.?\d+)""")
            return rx.find(json)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: def
        }
        fun bool(key: String, def: Boolean): Boolean {
            val rx = Regex(""""$key"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
            return when (rx.find(json)?.groupValues?.getOrNull(1)?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> def
            }
        }

        val p = num("price", DEFAULT.price)
        val d = num("delivery", DEFAULT.delivery)
        val rRaw = if (Regex(""""rating"\s*:""").containsMatchIn(json))
            num("rating", DEFAULT.rating) else num("seller", DEFAULT.rating)
        val norm = bool("normalized", true)
        val attr = num("attrPenalty", DEFAULT.attrPenalty)

        val (pn, dn, rn) = if (norm) normalizeToOne(p, d, rRaw) else Triple(p, d, rRaw)
        Weights(price = pn, delivery = dn, rating = rn, attrPenalty = attr)
    } catch (_: Throwable) { DEFAULT }

    private fun normalizeToOne(p: Float, d: Float, r: Float): Triple<Float, Float, Float> {
        val pp = p.coerceAtLeast(0f); val dd = d.coerceAtLeast(0f); val rr = r.coerceAtLeast(0f)
        val sum = pp + dd + rr
        return if (sum == 0f) Triple(DEFAULT.price, DEFAULT.delivery, DEFAULT.rating)
        else Triple(pp / sum, dd / sum, rr / sum)
    }
}
