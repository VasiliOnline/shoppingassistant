package com.example.shoppingassistant.core.data.ingest.avito

import com.example.shoppingassistant.core.data.ingest.ParserResult
import com.example.shoppingassistant.core.data.ingest.SourceParser
import com.example.shoppingassistant.core.data.ingest.replay.IngestReplayPayload
import com.example.shoppingassistant.core.data.ingest.replay.IngestReplayStore
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.cleanText
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.decodeJsonString
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.extractCanonicalUrl
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.extractIconUrl
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.extractJsonLd
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.extractMeta
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.parseJsonObject
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.resolveUrl
import com.example.shoppingassistant.core.data.ingest.util.HtmlExtractors.stripHtml
import com.example.shoppingassistant.domain.ingest.IngestStatus
import com.example.shoppingassistant.domain.ingest.RawLocation
import com.example.shoppingassistant.domain.ingest.RawOffer
import com.example.shoppingassistant.domain.ingest.RawSeller
import com.example.shoppingassistant.domain.ingest.RawSellerType
import com.example.shoppingassistant.domain.ingest.SourceType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * Простой HTTP-парсер Avito без браузера/JS.
 * Забирает HTML, достаёт og/meta-теги, JSON-LD и основные поля объявления.
 */
class AvitoSourceParser(
    private val httpClient: HttpClient,
    private val replayStore: IngestReplayStore,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : SourceParser {

    override val source: SourceType = SourceType.AVITO
    override val parserVersion: String = "1"

    override suspend fun load(url: String): ParserResult {
        val normalizedUrl = url.trim()
        val fetch = fetchHtml(normalizedUrl)
        val status = when (fetch.httpStatus) {
            403, 429 -> IngestStatus.TEMP_BLOCKED
            null -> IngestStatus.NETWORK_ERROR
            else -> if (fetch.html == null) IngestStatus.NETWORK_ERROR else IngestStatus.OK
        }
        maybeRecordReplay(normalizedUrl, status, fetch)
        if (status != IngestStatus.OK) {
            return ParserResult(
                status = status,
                httpStatus = fetch.httpStatus,
                bytes = fetch.bytes,
                message = fetch.error,
            )
        }

        val html = fetch.html ?: return ParserResult(
            status = IngestStatus.NETWORK_ERROR,
            httpStatus = fetch.httpStatus,
            bytes = fetch.bytes,
            message = fetch.error,
        )

        val urlMeta = parseUrlMeta(normalizedUrl)
        val canonicalUrl = extractCanonicalUrl(html) ?: normalizedUrl
        val iconUrl = extractIconUrl(html, canonicalUrl)
        val ogTitle = extractMeta(html, property = "og:title")
        val ogDescription = extractMeta(html, property = "og:description")
        val ogImage = extractMeta(html, property = "og:image")
        val jsonLdObject = extractJsonLd(html)?.let { parseJsonObject(json, it) }

        val priceValue = extractPrice(jsonLdObject, html)
        val priceCurrency = extractCurrency(jsonLdObject, html)
        val description = pickDescription(jsonLdObject, html, ogDescription)
        val images = collectImages(jsonLdObject, html, ogImage)
        val seller = extractSeller(jsonLdObject, html)
        val location = extractLocation(jsonLdObject, html, urlMeta)
        val attributes = extractAttributes(html)
        val createdAtText = extractCreatedAt(html)
        val title = ogTitle ?: jsonLdObject?.get("name")?.jsonPrimitive?.contentOrNull

        val offer = RawOffer(
            source = source,
            url = normalizedUrl,
            listingId = urlMeta.listingId,
            canonicalUrl = canonicalUrl,
            sourceIconUrl = iconUrl,
            citySlug = urlMeta.citySlug,
            categorySlug = urlMeta.categorySlug,
            title = cleanText(title),
            rawTitle = cleanText(ogTitle ?: title),
            description = cleanText(description),
            rawDescription = cleanText(description ?: ogDescription),
            priceValue = priceValue,
            priceCurrency = priceCurrency ?: "RUB",
            images = images,
            attributesRaw = attributes,
            seller = seller,
            location = location,
            createdAtText = createdAtText,
        )

        return ParserResult(
            status = IngestStatus.OK,
            offer = offer,
            httpStatus = fetch.httpStatus,
            bytes = fetch.bytes,
        )
    }

    private suspend fun fetchHtml(url: String): HtmlFetchResult {
        return runCatching {
            val response = httpClient.get(url) {
                header(
                    "User-Agent",
                    "Mozilla/5.0 (compatible; ShoppingAssistantBot/1.0; +https://example.com/bot)",
                )
            }
            val status = response.status.value
            val body = runCatching { response.body<String>() }.getOrNull()
            HtmlFetchResult(
                html = body,
                httpStatus = status,
                bytes = body?.toByteArray()?.size?.toLong(),
                error = null,
            )
        }.getOrElse { err ->
            HtmlFetchResult(
                html = null,
                httpStatus = null,
                bytes = null,
                error = err.message,
            )
        }
    }

    private fun parseUrlMeta(url: String): UrlMeta {
        val regex = Regex("""avito\.ru/([^/?#]+)/([^/?#]+)/[^?\s]*?(\d+)(?:\?|$)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url)
        return if (match != null && match.groupValues.size >= 4) {
            UrlMeta(
                citySlug = match.groupValues[1],
                categorySlug = match.groupValues[2],
                listingId = match.groupValues[3],
            )
        } else {
            UrlMeta()
        }
    }

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

    private fun pickDescription(ld: JsonObject?, html: String, ogDescription: String?): String? {
        val ldDescription = ld?.get("description")?.jsonPrimitive?.contentOrNull
        if (!ldDescription.isNullOrBlank()) return ldDescription

        val bodyDescription = Regex(
            """data-marker=['"]item-description/text['"][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.getOrNull(1)?.let { stripHtml(it) }

        if (!bodyDescription.isNullOrBlank()) return bodyDescription

        val scriptDescription = Regex(
            """"description"\s*:\s*"(.+?)"""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.getOrNull(1)

        if (!scriptDescription.isNullOrBlank()) return decodeJsonString(json, scriptDescription)

        return ogDescription
    }

    private fun collectImages(ld: JsonObject?, html: String, ogImage: String?): List<String> {
        val images = linkedSetOf<String>()

        fun addAll(list: List<String>) {
            list.forEach { img ->
                val cleaned = cleanText(img)
                if (!cleaned.isNullOrBlank()) images.add(cleaned)
            }
        }

        val ldImages = when (val image = ld?.get("image")) {
            is JsonArray -> image.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonElement -> listOfNotNull(image.jsonPrimitive.contentOrNull)
            else -> emptyList()
        }
        addAll(ldImages)

        ogImage?.let { addAll(listOf(it)) }

        val htmlImages = Regex(
            """https?://[^\s"']+\.(?:jpg|jpeg|png|webp)""",
            RegexOption.IGNORE_CASE,
        )
            .findAll(html)
            .map { it.value }
            .filter { it.contains("avito") || it.contains("avatars") || it.contains("static") }
            .toList()
        addAll(htmlImages)

        return images.toList()
    }

    private fun extractSeller(ld: JsonObject?, html: String): RawSeller? {
        val sellerNode = ld?.get("seller")
        val sellerName = when (sellerNode) {
            is JsonObject -> sellerNode["name"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        val sellerType = when ((sellerNode as? JsonObject)?.get("@type")?.jsonPrimitive?.contentOrNull?.lowercase()) {
            "organization" -> RawSellerType.BUSINESS
            "person" -> RawSellerType.PRIVATE
            else -> RawSellerType.UNKNOWN
        }

        val profileUrl = Regex("""https?://(?:www\.)?avito\.ru/user/[^\s"']+""", RegexOption.IGNORE_CASE)
            .find(html)?.value

        val htmlSellerType = Regex("""data-marker=['"]seller-info/label['"][^>]*>(.*?)</""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1)?.let { stripHtml(it)?.lowercase() }

        val resolvedType = when {
            htmlSellerType?.contains("магазин") == true -> RawSellerType.BUSINESS
            htmlSellerType?.contains("частник") == true -> RawSellerType.PRIVATE
            sellerType != RawSellerType.UNKNOWN -> sellerType
            else -> RawSellerType.UNKNOWN
        }

        val finalName = sellerName ?: Regex(
            """data-marker=['"]seller-info/name['"][^>]*>(.*?)</""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.getOrNull(1)?.let(::stripHtml)

        if (finalName.isNullOrBlank() && profileUrl.isNullOrBlank()) return null

        return RawSeller(
            name = cleanText(finalName),
            type = resolvedType,
            profileUrl = cleanText(profileUrl),
        )
    }

    private fun extractLocation(ld: JsonObject?, html: String, meta: UrlMeta): RawLocation? {
        val addressNode = ld?.get("address")
        val addressText = when (addressNode) {
            is JsonObject -> {
                val locality = addressNode["addressLocality"]?.jsonPrimitive?.contentOrNull
                val street = addressNode["streetAddress"]?.jsonPrimitive?.contentOrNull
                listOfNotNull(locality, street).joinToString(", ").ifBlank { null }
            }
            is JsonElement -> addressNode.jsonPrimitive.contentOrNull
            else -> null
        }

        val geo = (addressNode as? JsonObject)?.get("geo")?.jsonObject
        val lat = geo?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val lon = geo?.get("longitude")?.jsonPrimitive?.doubleOrNull

        val htmlLocation = Regex(
            """data-marker=['"]delivery/location-text['"][^>]*>(.*?)</""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.getOrNull(1)?.let(::stripHtml)

        val cityFromHtml = Regex(
            """data-marker=['"]metro-title/metrolist-item['"][^>]*>(.*?)</""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(html)?.groupValues?.getOrNull(1)?.let(::stripHtml)

        val display = cleanText(addressText ?: htmlLocation ?: cityFromHtml ?: meta.citySlug)

        if (display.isNullOrBlank() && lat == null && lon == null) {
            return null
        }

        return RawLocation(
            city = cleanText(cityFromHtml ?: meta.citySlug),
            addressLine = display,
            latitude = lat,
            longitude = lon,
            displayName = display,
        )
    }

    private fun extractAttributes(html: String): List<Pair<String, String>> {
        val attrs = mutableListOf<Pair<String, String>>()
        val listBlock = Regex(
            """data-marker=['"]item-params/list['"][^>]*>(.*?)</ul>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.getOrNull(1)

        if (listBlock != null) {
            val itemRegex = Regex("""<li[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
            itemRegex.findAll(listBlock).forEach { match ->
                val text = stripHtml(match.groupValues[1])
                val parts = text?.split(":")
                if (parts != null && parts.size >= 2) {
                    val key = cleanText(parts[0])
                    val value = cleanText(parts.drop(1).joinToString(":"))
                    if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                        attrs += key to value
                    }
                }
            }
        }

        if (attrs.isEmpty()) {
            val jsonAttrRegex = Regex(
                """"title"\s*:\s*"(.+?)"\s*,\s*"value"\s*:\s*"(.+?)"""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            jsonAttrRegex.findAll(html).forEach { m ->
                val key = decodeJsonString(json, m.groupValues[1])
                val value = decodeJsonString(json, m.groupValues[2])
                val cleanedKey = cleanText(key)
                val cleanedValue = cleanText(value)
                if (!cleanedKey.isNullOrBlank() && !cleanedValue.isNullOrBlank()) {
                    attrs += cleanedKey to cleanedValue
                }
            }
        }

        return attrs
            .fold(mutableListOf<Pair<String, String>>()) { acc, pair ->
                if (acc.none { it.first.equals(pair.first, true) && it.second == pair.second }) {
                    acc.add(pair)
                }
                acc
            }
    }

    private fun extractCreatedAt(html: String): String? {
        val metaDate = Regex(
            """itemprop=['"]datePosted['"]\s+content=['"]([^'"]+)['"]""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)
        if (!metaDate.isNullOrBlank()) return metaDate

        val scriptDate = Regex(
            """"absoluteTime"\s*:\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)
        if (!scriptDate.isNullOrBlank()) return scriptDate

        return null
    }

    private data class HtmlFetchResult(
        val html: String?,
        val httpStatus: Int?,
        val bytes: Long?,
        val error: String?,
    )

    private fun maybeRecordReplay(
        url: String,
        status: IngestStatus,
        fetch: HtmlFetchResult,
    ) {
        val body = fetch.html ?: return
        if (!replayStore.shouldRecord(source, status)) return
        replayStore.record(
            IngestReplayPayload(
                sourceType = source,
                url = url,
                status = status,
                httpStatus = fetch.httpStatus,
                body = body,
                collectedAt = System.currentTimeMillis(),
                parserVersion = parserVersion,
            ),
        )
    }

    private data class UrlMeta(
        val citySlug: String? = null,
        val categorySlug: String? = null,
        val listingId: String? = null,
    )
}
