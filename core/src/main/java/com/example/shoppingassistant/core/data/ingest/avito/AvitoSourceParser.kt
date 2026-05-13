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
import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.QueryRouteType
import com.example.shoppingassistant.domain.catalog.QueryRouter
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
import java.util.Locale

/**
 * Простой HTTP-парсер Avito без браузера/JS.
 * Забирает HTML, достаёт og/meta-теги, JSON-LD и основные поля объявления.
 */
class AvitoSourceParser(
    private val httpClient: HttpClient,
    private val replayStore: IngestReplayStore,
    private val queryRouter: QueryRouter,
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
        val categoryClassification = classifyCategory(
            urlMeta = urlMeta,
            title = title,
            attributes = attributes,
            description = description,
        )

        val offer = RawOffer(
            source = source,
            url = normalizedUrl,
            listingId = urlMeta.listingId,
            canonicalUrl = canonicalUrl,
            sourceIconUrl = iconUrl,
            citySlug = urlMeta.citySlug,
            categorySlug = urlMeta.categorySlug,
            categoryCode = categoryClassification.categoryCode,
            categoryConfidence = categoryClassification.confidence,
            parserVersion = parserVersion,
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

    private suspend fun classifyCategory(
        urlMeta: UrlMeta,
        title: String?,
        attributes: List<Pair<String, String>>,
        description: String?,
    ): CategoryClassification {
        val slugBased = classifyBySlug(urlMeta.categorySlug)
        val query = buildRoutingQuery(
            slug = urlMeta.categorySlug,
            title = title,
            description = description,
            attributes = attributes,
        )
        val routed = runCatching { queryRouter.route(query = query, locale = "ru-RU") }.getOrNull()
        val routedLeaf = normalizeRoutedTargetToLeaf(routed?.primaryTargetCode)
        val routedScore = routed?.confidence?.coerceIn(0.0, 1.0) ?: 0.0

        if (routedLeaf != null && routed?.routeType == QueryRouteType.OPEN_CATEGORY && routedScore >= 0.55) {
            if (slugBased == null || routedScore >= slugBased.confidence) {
                return CategoryClassification(
                    categoryCode = routedLeaf,
                    confidence = routedScore,
                )
            }
        }

        if (slugBased != null) {
            return slugBased
        }

        if (routedLeaf != null) {
            return CategoryClassification(
                categoryCode = routedLeaf,
                confidence = routedScore.coerceAtLeast(0.35),
            )
        }

        return CategoryClassification(
            categoryCode = DEFAULT_CATEGORY_CODE,
            confidence = DEFAULT_CATEGORY_CONFIDENCE,
        )
    }

    private fun classifyBySlug(slug: String?): CategoryClassification? {
        val normalizedSlug = normalizeSlug(slug)
        if (normalizedSlug.isEmpty()) return null
        val mappedCategory = SLUG_TO_LEAF.entries.firstOrNull { (token, _) ->
            normalizedSlug.contains(token)
        }?.value
        if (mappedCategory != null) {
            return CategoryClassification(
                categoryCode = mappedCategory,
                confidence = 0.82,
            )
        }
        return null
    }

    private fun buildRoutingQuery(
        slug: String?,
        title: String?,
        description: String?,
        attributes: List<Pair<String, String>>,
    ): String {
        val slugHint = normalizeSlug(slug).replace('_', ' ').trim()
        val attrsHint = attributes
            .take(4)
            .joinToString(" ") { (_, value) -> value }
        return listOfNotNull(
            slugHint.takeIf { it.isNotBlank() },
            cleanText(title),
            cleanText(attrsHint),
            cleanText(description)?.take(120),
        ).joinToString(" ").trim()
    }

    private fun normalizeRoutedTargetToLeaf(rawTargetCode: String?): String? {
        val normalized = rawTargetCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val withoutBrowse = normalized.removePrefix("B.")
        if (withoutBrowse in LEAF_CATEGORY_CODES) {
            return withoutBrowse
        }
        return ROOT_DEFAULT_LEAF[withoutBrowse]
    }

    private fun normalizeSlug(slug: String?): String =
        slug.orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .replace('-', '_')
            .replace('/', '_')
            .replace(Regex("_+"), "_")
            .trim('_')

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

    private data class CategoryClassification(
        val categoryCode: String,
        val confidence: Double,
    )

    private companion object {
        private const val DEFAULT_CATEGORY_CODE = ""
        private const val DEFAULT_CATEGORY_CONFIDENCE = 0.0

        private val LEAF_CATEGORY_CODES: Set<String> by lazy {
            val allCodes = CatalogSeed.categories.map { it.code.trim().uppercase(Locale.ROOT) }.toSet()
            val parentCodes = CatalogSeed.categories
                .mapNotNull { it.parentCode?.trim()?.uppercase(Locale.ROOT)?.takeIf { parent -> parent.isNotEmpty() } }
                .toSet()
            allCodes - parentCodes
        }

        private val ROOT_DEFAULT_LEAF = mapOf(
            "TECH" to "TECH.PHONES",
            "APPL" to "APPL.SMALL",
            "AUTO" to "AUTO.PARTS",
            "FOOD" to "FOOD.GROCERIES",
            "HOME" to "HOME.KITCHEN_DINING",
            "FASH" to "FASH.WOMEN",
            "BEAUTY" to "BEAUTY.SKINCARE",
            "KIDS" to "KIDS.TOYS_GAMES",
            "PETS" to "PETS.FOOD",
            "SPORT" to "SPORT.FITNESS",
        )

        private val SLUG_TO_LEAF = linkedMapOf(
            "telefony" to "TECH.PHONES",
            "aksessuary_dlya_telefonov" to "TECH.PHONE_ACCESSORIES",
            "noutbuki" to "TECH.LAPTOPS",
            "planshety_i_elektronnye_knigi" to "TECH.TABLETS_EBOOKS",
            "komplektuyuschie" to "TECH.PC_COMPONENTS",
            "komplektuyushchie" to "TECH.PC_COMPONENTS",
            "tv_i_videotekhnika" to "TECH.TV_VIDEO",
            "audio_i_video" to "TECH.AUDIO",
            "igry_pristavki_i_programmy" to "TECH.GAMING",
            "fototehnika" to "TECH.CAMERAS",
            "umnyy_dom" to "TECH.SMART_HOME",
            "krupnaya_bytovaya_tekhnika" to "APPL.MAJOR",
            "melkaya_bytovaya_tekhnika" to "APPL.SMALL",
            "klimaticheskoe_oborudovanie" to "APPL.CLIMATE",
            "avtozapchasti" to "AUTO.PARTS",
            "shiny_diski_i_kolesa" to "AUTO.TIRES_WHEELS",
            "instrumenty_i_oborudovanie" to "AUTO.TOOLS_GARAGE",
            "aksessuary_dlya_avto" to "AUTO.ACCESSORIES",
            "gotovaya_eda" to "FOOD.READY_MEALS",
            "bakaleya" to "FOOD.GROCERIES",
            "napitki" to "FOOD.DRINKS",
            "sneki" to "FOOD.SNACKS",
            "tekstil" to "HOME.TEXTILES",
            "osveschenie" to "HOME.LIGHTING",
            "hranenie_i_poryadok" to "HOME.STORAGE",
            "uborka" to "HOME.CLEANING",
            "remont_i_instrumenty" to "HOME.REPAIR_TOOLS",
            "santehnika" to "HOME.PLUMBING",
            "sad_i_ogorod" to "HOME.GARDEN",
            "kuhnya_i_stolovaya" to "HOME.KITCHEN_DINING",
            "mebel" to "HOME.FURNITURE",
            "zhenskaya_odezhda" to "FASH.WOMEN",
            "muzhskaya_odezhda" to "FASH.MEN",
            "detskaya_odezhda" to "FASH.KIDS",
            "obuv" to "FASH.SHOES",
            "sumki_i_ryukzaki" to "FASH.BAGS",
            "aksessuary" to "FASH.ACCESSORIES",
            "uhod_za_kozhey" to "BEAUTY.SKINCARE",
            "uhod_za_volosami" to "BEAUTY.HAIRCARE",
            "uhod_za_telom" to "BEAUTY.BODYCARE",
            "dekorativnaya_kosmetika" to "BEAUTY.MAKEUP",
            "parfyumeriya" to "BEAUTY.FRAGRANCE",
            "beauty_gadzhety" to "BEAUTY.DEVICES",
            "zdorove_i_vitaminy" to "BEAUTY.HEALTH",
            "igrushki_i_igry" to "KIDS.TOYS_GAMES",
            "kolyaski_i_avtokresla" to "KIDS.STROLLERS_CARSEATS",
            "detskaya_mebel_i_bezopasnost" to "KIDS.NURSERY_FURNITURE",
            "tovary_dlya_malyshey" to "KIDS.BABY_GEAR",
            "korm_i_lakomstva" to "PETS.FOOD",
            "gigiena_i_uhod" to "PETS.HYGIENE",
            "aksessuary_dlya_pitomtsev" to "PETS.ACCESSORIES",
            "veterinariya_i_zdorove" to "PETS.HEALTH",
            "fitnes" to "SPORT.FITNESS",
            "turizm_i_outdoor" to "SPORT.OUTDOOR",
            "velosipedy_i_samokaty" to "SPORT.BIKES_SCOOTERS",
            "sportinventar" to "SPORT.EQUIPMENT",
            "hobbi_i_aktivnyj_otdyh" to "SPORT.HOBBY",
        )
    }
}
