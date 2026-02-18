package com.example.shoppingassistant.server.price.avito

import com.example.shoppingassistant.domain.ingest.SourceType
import com.example.shoppingassistant.server.price.PriceProbe
import com.example.shoppingassistant.server.price.PriceProbeResult
import com.example.shoppingassistant.server.price.PriceProbeStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AvitoPriceProbe(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : PriceProbe {

    override val sourceType: SourceType = SourceType.AVITO
    override val parserVersion: String = "1"

    override suspend fun probe(url: String): PriceProbeResult = withContext(Dispatchers.IO) {
        val normalizedUrl = url.trim()
        val startedAt = System.currentTimeMillis()
        val request = HttpRequest.newBuilder(URI(normalizedUrl))
            .GET()
            .header("User-Agent", "Mozilla/5.0 (compatible; ShoppingAssistantBot/1.0)")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrElse { err ->
            return@withContext PriceProbeResult(
                status = PriceProbeStatus.NETWORK_ERROR,
                message = err.message,
                latencyMs = System.currentTimeMillis() - startedAt,
            )
        }

        val latency = System.currentTimeMillis() - startedAt
        val statusCode = response.statusCode()
        val body = response.body()
        val bytes = body?.toByteArray()?.size?.toLong()

        if (statusCode == 403 || statusCode == 429) {
            return@withContext PriceProbeResult(
                status = PriceProbeStatus.TEMP_BLOCKED,
                httpStatus = statusCode,
                latencyMs = latency,
                bytes = bytes,
            )
        }
        if (statusCode !in 200..299 || body.isNullOrBlank()) {
            return@withContext PriceProbeResult(
                status = PriceProbeStatus.NETWORK_ERROR,
                httpStatus = statusCode,
                latencyMs = latency,
                bytes = bytes,
                message = "HTTP $statusCode",
            )
        }

        val canonicalUrl = extractCanonicalUrl(body)
        val listingId = extractListingId(normalizedUrl)
        val jsonLd = extractJsonLd(body)?.let { parseJsonObject(it) }

        val price = extractPrice(jsonLd, body)
        val currency = extractCurrency(jsonLd, body)

        if (price == null || currency.isNullOrBlank()) {
            return@withContext PriceProbeResult(
                status = PriceProbeStatus.PARSE_ERROR,
                httpStatus = statusCode,
                latencyMs = latency,
                bytes = bytes,
                message = "Price or currency not found",
                canonicalUrl = canonicalUrl,
                listingId = listingId,
            )
        }

        PriceProbeResult(
            status = PriceProbeStatus.OK,
            priceValue = price,
            currency = currency,
            canonicalUrl = canonicalUrl,
            listingId = listingId,
            httpStatus = statusCode,
            latencyMs = latency,
            bytes = bytes,
        )
    }

    private fun extractJsonLd(html: String): String? {
        val scriptRegex = Regex(
            """<script[^>]+application/ld\+json[^>]*>(.*?)</script>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return scriptRegex.find(html)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun parseJsonObject(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()

    private fun extractPrice(ld: JsonObject?, html: String): Double? {
        val priceFromLd = when (val offers = ld?.get("offers")) {
            is JsonObject -> offers["price"]?.jsonPrimitive?.doubleOrNull
            is JsonArray -> offers.firstOrNull()?.jsonObject?.get("price")?.jsonPrimitive?.doubleOrNull
            else -> null
        }
        if (priceFromLd != null) return priceFromLd

        val metaPrice = Regex("""itemprop=['"]price['"]\s+content=['"]([\d.,]+)['"]""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!metaPrice.isNullOrBlank()) return metaPrice.toDoubleOrNull()

        val scriptPrice = Regex(
            """"price"\s*:\s*([\d.]+)""",
            setOf(RegexOption.IGNORE_CASE),
        ).find(html)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        return scriptPrice
    }

    private fun extractCurrency(ld: JsonObject?, html: String): String? {
        val currencyFromLd = when (val offers = ld?.get("offers")) {
            is JsonObject -> offers["priceCurrency"]?.jsonPrimitive?.contentOrNull
            is JsonArray -> offers.firstOrNull()?.jsonObject?.get("priceCurrency")?.jsonPrimitive?.contentOrNull
            else -> null
        }
        if (!currencyFromLd.isNullOrBlank()) return currencyFromLd

        val metaCurrency = Regex("""itemprop=['"]priceCurrency['"]\s+content=['"]([A-Z]+)['"]""")
            .find(html)?.groupValues?.getOrNull(1)
        if (!metaCurrency.isNullOrBlank()) return metaCurrency

        val scriptCurrency = Regex(
            """"currency(?:Code)?"\s*:\s*"([A-Z]+)"""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)

        return scriptCurrency
    }

    private fun extractCanonicalUrl(html: String): String? {
        val canonical = Regex(
            """<link\s+rel=['"]canonical['"]\s+href=['"]([^'"]+)['"]""",
            setOf(RegexOption.IGNORE_CASE),
        ).find(html)?.groupValues?.getOrNull(1)

        val ogUrl = extractMeta(html, property = "og:url")
        return cleanText(canonical ?: ogUrl)
    }

    private fun extractMeta(html: String, property: String? = null, name: String? = null): String? {
        if (property == null && name == null) return null
        val attr = property?.let { "property=['\"]$it['\"]" } ?: "name=['\"]$name['\"]"
        val regex = Regex(
            """<meta[^>]+$attr[^>]+content=['"]([^'"]+)['"]""",
            setOf(RegexOption.IGNORE_CASE),
        )
        return regex.find(html)?.groupValues?.getOrNull(1)?.let(::cleanText)
    }

    private fun extractListingId(url: String): String? {
        val regex = Regex("""avito\.ru/[^/?#]+/[^/?#]+/[^?\s]*?(\d+)(?:\?|$)""", RegexOption.IGNORE_CASE)
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private fun cleanText(raw: String?): String? =
        raw?.replace(Regex("\\s+"), " ")?.trim()?.ifEmpty { null }
}
