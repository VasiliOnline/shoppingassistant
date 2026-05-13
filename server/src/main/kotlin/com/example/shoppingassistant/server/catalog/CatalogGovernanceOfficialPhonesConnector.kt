package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedSeedPack
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedModelSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceCuratedValueSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshEndpoint
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshParserType
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSource
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSources
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshValueSeed
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceScope
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

interface CatalogGovernanceOfficialPageFetcher {
    suspend fun fetchText(uri: String): String
}

class HttpCatalogGovernanceOfficialPageFetcher(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(20))
        .build(),
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 1_250L,
) : CatalogGovernanceOfficialPageFetcher {

    override suspend fun fetchText(uri: String): String {
        var lastFailure: Throwable? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            try {
                val response = withContext(Dispatchers.IO) {
                    val request = HttpRequest.newBuilder(URI.create(uri))
                        .timeout(Duration.ofSeconds(30))
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0 Safari/537.36",
                        )
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Cache-Control", "no-cache")
                        .GET()
                        .build()
                    httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                    )
                }
                if (response.statusCode() !in 200..299) {
                    check(response.statusCode() !in setOf(408, 425, 429, 500, 502, 503, 504)) {
                        "Official source '$uri' returned HTTP ${response.statusCode()}."
                    }
                    error("Official source '$uri' returned HTTP ${response.statusCode()}.")
                }
                val content = response.body()
                check(!content.contains("Before you continue", ignoreCase = true)) {
                    "Official source '$uri' returned a bot/interstitial page instead of specs content."
                }
                return content
            } catch (throwable: Throwable) {
                lastFailure = throwable
                val shouldRetry = attempt < maxAttempts - 1 && isRetriableOfficialFetchFailure(throwable)
                if (!shouldRetry) {
                    throw throwable
                }
                delay(retryDelayMs * (attempt + 1))
            }
        }
        throw lastFailure ?: error("Official source '$uri' failed without an error.")
    }

    private fun isRetriableOfficialFetchFailure(throwable: Throwable): Boolean {
        val message = throwable.message.orEmpty()
        return message.contains("Connection reset", ignoreCase = true) ||
            message.contains("GOAWAY", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ||
            message.contains("HTTP 408", ignoreCase = true) ||
            message.contains("HTTP 425", ignoreCase = true) ||
            message.contains("HTTP 429", ignoreCase = true) ||
            message.contains("HTTP 500", ignoreCase = true) ||
            message.contains("HTTP 502", ignoreCase = true) ||
            message.contains("HTTP 503", ignoreCase = true) ||
            message.contains("HTTP 504", ignoreCase = true)
    }
}

class CatalogGovernanceOfficialPhonesConnector(
    private val fetcher: CatalogGovernanceOfficialPageFetcher = HttpCatalogGovernanceOfficialPageFetcher(),
    private val endpointOverlayRepository: CatalogGovernanceOfficialPhoneEndpointOverlayRepository =
        NoopCatalogGovernanceOfficialPhoneEndpointOverlayRepository,
) : CatalogGovernanceRefreshConnector {

    private val knownPhoneColorAliases: Set<String> by lazy {
        CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = "color",
            scope = CatalogGovernanceScope(categoryCode = CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE),
            locale = "en",
        )
            .flatMap { candidate -> candidate.aliases + candidate.displayValue }
            .map(::normalizeOfficialCanonicalText)
            .filter { alias -> alias.isNotBlank() }
            .toSet()
    }

    private val basePhoneColorTokens: Set<String> = setOf(
        "black",
        "white",
        "blue",
        "green",
        "red",
        "gold",
        "silver",
        "gray",
        "grey",
        "purple",
        "pink",
        "cyan",
        "orange",
        "beige",
        "pearl",
        "jade",
        "yellow",
        "violet",
        "coral",
        "mint",
    )

    override val connectorType: CatalogGovernanceRefreshConnectorType =
        CatalogGovernanceRefreshConnectorType.OFFICIAL_PHONE_WEB_SOURCE

    override suspend fun loadPayload(
        entry: CatalogGovernanceSourceRegistryEntry,
    ): CatalogGovernanceConnectorPayload {
        val declaredSource = CatalogGovernanceOfficialRefreshSources.find(
            sourceCode = entry.sourceCode,
            categoryCode = entry.categoryCode,
        ) ?: error(
            "Official refresh source '${entry.sourceCode}' for '${entry.categoryCode}' is not declared.",
        )
        val overlayEndpoints = endpointOverlayRepository.listRefreshEndpoints(
            categoryCode = entry.categoryCode,
            sourceCode = entry.sourceCode,
        )
        val source = declaredSource.copy(
            endpoints = mergeOfficialEndpoints(
                declaredEndpoints = declaredSource.endpoints,
                overlayEndpoints = overlayEndpoints,
            ),
        )
        val htmlByUri = LinkedHashMap<String, String>()
        source.endpoints.map { endpoint -> endpoint.sourceUri }.distinct().forEach { uri ->
            htmlByUri[uri] = fetcher.fetchText(uri)
        }
        val extractedModels = source.endpoints.map { endpoint ->
            parseEndpoint(
                source = source,
                endpoint = endpoint,
                html = htmlByUri.getValue(endpoint.sourceUri),
            )
        }
        val pack = buildPack(source = source, extractedModels = extractedModels)
        return CatalogGovernanceConnectorPayload(
            packs = listOf(pack),
            metadata = source.metadata + mapOf(
                "officialSourceCode" to source.sourceCode,
                "officialBrandCode" to source.brandCode,
                "endpointCount" to source.endpoints.size.toString(),
                "fetchedUris" to htmlByUri.keys.joinToString(","),
            ),
        )
    }

    private fun buildPack(
        source: CatalogGovernanceOfficialRefreshSource,
        extractedModels: List<ExtractedOfficialPhoneModel>,
    ): CatalogGovernanceCuratedSeedPack {
        val canonicalValues = extractedModels
            .flatMap { model ->
                model.values.map { value ->
                    buildCanonicalValueSeed(
                        source = source,
                        endpoint = model.endpoint,
                        value = value,
                    )
                }
            }
            .sortedWith(
                compareBy<CatalogGovernanceCuratedValueSeed>({ it.attributeCode }, { it.modelCode.orEmpty() }, { it.canonicalCode }),
            )
        val models = extractedModels
            .map { model ->
                CatalogGovernanceCuratedModelSeed(
                    code = model.endpoint.modelCode,
                    brandCode = source.brandCode,
                    familyCode = model.endpoint.familyCode,
                    labels = localizedTextOf("en" to model.endpoint.modelLabel),
                    defaultCategoryCode = source.categoryCode,
                    releaseYear = model.endpoint.releaseYear,
                    aliases = model.endpoint.aliases.ifEmpty {
                        defaultModelAliases(model.endpoint.modelLabel)
                    },
                    metadata = source.metadata + model.endpoint.metadata + mapOf(
                        "officialRefreshSource" to source.sourceCode,
                        "officialEndpointCode" to model.endpoint.endpointCode,
                    ),
                )
            }
            .distinctBy { it.code }
            .sortedBy { it.code }
        return CatalogGovernanceCuratedSeedPack(
            packCode = "OFFICIAL_${source.sourceCode}",
            sourceCode = source.sourceCode,
            displayName = source.displayName,
            tier = source.tier,
            defaultLocale = source.defaultLocale,
            marketCode = source.marketCode,
            sourceVersion = "official-refresh",
            sourceUri = source.sourceUri ?: source.endpoints.firstOrNull()?.sourceUri,
            metadata = source.metadata + mapOf(
                "connectorType" to connectorType.name,
                "endpointCount" to source.endpoints.size.toString(),
                "modelCount" to models.size.toString(),
            ),
            models = models,
            canonicalValues = canonicalValues,
        )
    }

    private fun parseEndpoint(
        source: CatalogGovernanceOfficialRefreshSource,
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): ExtractedOfficialPhoneModel {
        val parsedValues = when (endpoint.parserType) {
            CatalogGovernanceOfficialRefreshParserType.APPLE_BUY_IPHONE_METRICS ->
                parseAppleBuyPage(endpoint = endpoint, html = html)

            CatalogGovernanceOfficialRefreshParserType.SAMSUNG_DEVICE_BUY_PAGE ->
                parseSamsungBuyPage(endpoint = endpoint, html = html)

            CatalogGovernanceOfficialRefreshParserType.GOOGLE_PIXEL_SUPPORT_SPECS ->
                parseGoogleSupportSpecsPage(endpoint = endpoint, html = html)

            CatalogGovernanceOfficialRefreshParserType.XIAOMI_GLOBAL_SPECS_PAGE ->
                parseXiaomiSpecsPage(endpoint = endpoint, html = html)

            CatalogGovernanceOfficialRefreshParserType.ONEPLUS_SPECS_PAGE ->
                parseOnePlusSpecsPage(endpoint = endpoint, html = html)

            CatalogGovernanceOfficialRefreshParserType.NOTHING_PRODUCT_PAGE ->
                parseNothingProductPage(endpoint = endpoint, html = html)

            CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE ->
                parseGenericPhoneSpecsPage(endpoint = endpoint, html = html)
        }
        val extractedRichFacts = extractRichFactsFromHtml(html)
        val inferredReleaseDate = endpoint.releaseDate?.trim()?.takeIf { it.isNotEmpty() }
            ?: extractedRichFacts.firstOrNull { value ->
                value.attributeCode.equals("release_date", ignoreCase = true)
            }?.rawValue
        val inferredReleaseYear = endpoint.releaseYear ?: inferredReleaseDate
            ?.substringBefore('-')
            ?.toIntOrNull()
        val inferredModelLine = endpoint.metadata["modelLine"]?.trim()?.takeIf { modelLine -> modelLine.isNotEmpty() }
            ?: inferModelLine(brandCode = source.brandCode, modelLabel = endpoint.modelLabel)
        val fixedValues = endpoint.fixedValues.map { fixedValue ->
            ExtractedOfficialValue(
                attributeCode = fixedValue.attributeCode,
                rawValue = fixedValue.rawValue,
                aliases = fixedValue.aliases,
            )
        }
        val syntheticValues = listOfNotNull(
            inferredReleaseYear?.let { releaseYear ->
                ExtractedOfficialValue(
                    attributeCode = "release_year",
                    rawValue = releaseYear.toString(),
                    aliases = defaultValueAliases("release_year", releaseYear.toString()),
                )
            },
            endpoint.releaseDate?.let { releaseDate ->
                ExtractedOfficialValue(
                    attributeCode = "release_date",
                    rawValue = releaseDate,
                    aliases = defaultValueAliases("release_date", releaseDate),
                )
            },
            inferredModelLine?.let { modelLine ->
                ExtractedOfficialValue(
                    attributeCode = "model_line",
                    rawValue = modelLine,
                    aliases = defaultValueAliases("model_line", modelLine),
                )
            },
        )
        val mergedValues = (parsedValues + extractedRichFacts + fixedValues + syntheticValues)
            .groupBy { value ->
                value.attributeCode.lowercase(Locale.ROOT) to
                    normalizeOfficialCanonicalText(value.rawValue)
            }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, value ->
                    acc.copy(
                        aliases = mergeAliasMaps(acc.aliases, value.aliases),
                    )
                }
            }
            .sortedWith(compareBy({ it.attributeCode }, { it.rawValue }))
        require(mergedValues.isNotEmpty()) {
            "Official connector '${source.sourceCode}' produced no values for '${endpoint.modelCode}'."
        }
        return ExtractedOfficialPhoneModel(endpoint = endpoint, values = mergedValues)
    }

    private fun parseAppleBuyPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> {
        val names = Regex("""\"name\":\"([^\"]+)\"""")
            .findAll(html)
            .map { match -> decodeEscapedText(match.groupValues[1]) }
            .filter { candidate -> candidate.startsWith(endpoint.modelLabel) }
            .toList()
        require(names.isNotEmpty()) {
            "Apple official page did not expose catalog entries for '${endpoint.modelLabel}'."
        }
        val colors = linkedSetOf<String>()
        val values = mutableListOf<ExtractedOfficialValue>()
        names.forEach { name ->
            val remainder = name.removePrefix(endpoint.modelLabel).trim()
            val match = Regex("""^(\d+(?:\.\d+)?(?:TB|GB))\s+(.+)$""", RegexOption.IGNORE_CASE)
                .find(remainder)
                ?: return@forEach
            values += storageValue(match.groupValues[1])
            colors += match.groupValues[2].trim()
        }
        values += colors.map(::colorValue)
        return values
    }

    private fun parseSamsungBuyPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> {
        val values = mutableListOf<ExtractedOfficialValue>()
        val storageMatches = linkedSetOf<String>()
        storageMatches += Regex(
            """\"productTitle\":\"${Regex.escape(endpoint.modelLabel)}\s+(\d+(?:TB|GB))""",
            RegexOption.IGNORE_CASE,
        )
            .findAll(html)
            .map { match -> match.groupValues[1] }
            .toList()
        storageMatches += Regex(
            """aria-label=\"((?:\d+(?:TB|GB)))\"""",
            RegexOption.IGNORE_CASE,
        )
            .findAll(html)
            .map { match -> match.groupValues[1] }
            .toList()
        storageMatches.forEach { token ->
            values += storageValue(token)
        }

        val colors = Regex("""Color Variant:\s*([^\"]+)""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .map { match -> decodeEscapedText(match.groupValues[1]).trim() }
            .filter { candidate -> candidate.isNotEmpty() }
            .distinct()
            .toMutableList()
        if (colors.isEmpty()) {
            val faqPattern = Regex(
                """What colors do[^?]+\?.*?come in ([^.]+)\.""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            val faqColors = faqPattern.find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.splitColorSentence()
                .orEmpty()
            colors += faqColors
        }
        colors.distinct().forEach { color ->
            values += colorValue(color)
        }
        require(values.isNotEmpty()) {
            "Samsung official page did not expose values for '${endpoint.modelLabel}'."
        }
        return values
    }

    private fun parseGoogleSupportSpecsPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> {
        val section = Regex(
            """${Regex.escape(endpoint.modelLabel)}</a>\s*<div>\s*<table.*?</table>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.value
            ?: error("Google support specs page did not expose section for '${endpoint.modelLabel}'.")
        val values = mutableListOf<ExtractedOfficialValue>()
        extractGoogleListBlock(section, "Storage").forEach { token ->
            Regex("""(\d+(?:\.\d+)?)\s*(TB|GB)""", RegexOption.IGNORE_CASE)
                .findAll(token)
                .forEach { match ->
                    values += storageValue(match.value)
                }
        }
        extractGoogleListBlock(section, "Memory")
            .flatMap { token ->
                Regex("""(\d+)\s*GB\s*RAM""", RegexOption.IGNORE_CASE)
                    .findAll(token)
                    .map { match -> ramValue(match.groupValues[1]) }
                    .toList()
            }
            .forEach(values::add)
        extractGoogleColors(section).forEach { color ->
            values += colorValue(color)
        }
        Regex("""Google\s+Tensor\s+[A-Za-z0-9+ ]+""", RegexOption.IGNORE_CASE)
            .find(section)
            ?.groupValues
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { chipset ->
                values += chipsetValue(chipset)
            }
        extractMaxRefreshHz(section)
            ?.takeIf { hz -> hz > 0 }
            ?.let { hz ->
                values += refreshValue(hz)
            }
        require(values.isNotEmpty()) {
            "Google support specs page did not expose values for '${endpoint.modelLabel}'."
        }
        return values
    }

    private fun parseXiaomiSpecsPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> = parseGenericPhoneSpecsPage(endpoint = endpoint, html = html)

    private fun parseOnePlusSpecsPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> = parseGenericPhoneSpecsPage(endpoint = endpoint, html = html)

    private fun parseNothingProductPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> = parseGenericPhoneSpecsPage(endpoint = endpoint, html = html)

    private fun parseGenericPhoneSpecsPage(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> =
        buildList {
            addAll(extractStorageRamValues(html))
            addAll(extractColors(endpoint = endpoint, html = html))
        }

    private fun extractRichFactsFromHtml(html: String): List<ExtractedOfficialValue> {
        val normalizedHtml = normalizeOfficialExtractionHtml(html)
        val plainText = stripHtml(normalizedHtml)
        return buildList {
            extractReleaseDate(normalizedHtml = normalizedHtml, plainText = plainText)?.let { releaseDate ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "release_date",
                        rawValue = releaseDate,
                    ),
                )
            }
            extractScreenSizeInch(plainText)?.let { screenSize ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "screen_size_inch",
                        rawValue = screenSize,
                    ),
                )
            }
            extractRefreshRateHz(plainText)?.let { refreshRate ->
                add(refreshValue(refreshRate))
            }
            extractBatteryMah(plainText)?.let { batteryMah ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "battery_mah",
                        rawValue = batteryMah,
                    ),
                )
            }
            extractWiredChargingW(plainText)?.let { wiredCharging ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "wired_charging_w",
                        rawValue = wiredCharging,
                    ),
                )
            }
            extractWirelessCharging(plainText)?.let { wirelessCharging ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "wireless_charging",
                        rawValue = wirelessCharging.toString(),
                    ),
                )
            }
            extractEsimSupport(plainText)?.let { esimSupport ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "esim_support",
                        rawValue = esimSupport.toString(),
                    ),
                )
            }
            extractDualSim(plainText)?.let { dualSim ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "dual_sim",
                        rawValue = dualSim.toString(),
                    ),
                )
            }
            extractIpRating(plainText)?.let { ipRating ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "ip_rating",
                        rawValue = ipRating,
                    ),
                )
            }
            extractChipsetFamily(plainText)?.let { chipset ->
                add(chipsetValue(chipset))
            }
            extractNetworkType(plainText)?.let { networkType ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "network_type",
                        rawValue = networkType,
                    ),
                )
            }
            extractOsFamily(plainText)?.let { osFamily ->
                add(
                    ExtractedOfficialValue(
                        attributeCode = "os_family",
                        rawValue = osFamily,
                    ),
                )
            }
        }.distinctBy { value -> value.attributeCode.lowercase(Locale.ROOT) }
    }

    private fun extractGoogleListBlock(
        section: String,
        header: String,
    ): List<String> =
        Regex(
            """<p><strong>${Regex.escape(header)}</strong></p>\s*<ul>(.*?)</ul>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(section)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { block ->
                Regex("""<li>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(block)
                    .map { match -> stripHtml(match.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
            }
            .orEmpty()

    private fun extractGoogleColors(section: String): List<String> =
        Regex(
            """<th><strong>Colors</strong></th>\s*<td>\s*<ul>(.*?)</ul>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(section)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { block ->
                Regex("""<li>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(block)
                    .map { match -> stripHtml(match.groupValues[1]) }
                    .filter { it.isNotBlank() }
                    .toList()
            }
            .orEmpty()

    private fun extractStorageRamValues(html: String): List<ExtractedOfficialValue> {
        val normalizedHtml = normalizeOfficialExtractionHtml(html)
        val values = mutableListOf<ExtractedOfficialValue>()
        val pairPatterns = listOf(
            Regex("""(\d+)\s*GB\s*RAM\s*\+\s*(\d+(?:\.\d+)?)\s*(TB|GB)\s*ROM""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*GB\s*\+\s*(\d+(?:\.\d+)?)\s*(TB|GB)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*\+\s*(\d+(?:\.\d+)?)\s*(TB|GB)""", RegexOption.IGNORE_CASE),
        )
        pairPatterns.forEach { pattern ->
            pattern.findAll(normalizedHtml).forEach { match ->
                values += ramValue(match.groupValues[1])
                values += storageValue("${match.groupValues[2]}${match.groupValues[3]}")
            }
        }
        values += extractLabeledCapacityValues(
            normalizedHtml = normalizedHtml,
            attributeCode = "ram_gb",
            labels = listOf("ram", "memory"),
        )
        values += extractLabeledCapacityValues(
            normalizedHtml = normalizedHtml,
            attributeCode = "memory_gb",
            labels = listOf("rom", "storage"),
        )
        return values.distinctBy { value ->
            value.attributeCode.lowercase(Locale.ROOT) to normalizeOfficialCanonicalText(value.rawValue)
        }
    }

    private fun extractLabeledCapacityValues(
        normalizedHtml: String,
        attributeCode: String,
        labels: List<String>,
    ): List<ExtractedOfficialValue> {
        val values = mutableListOf<ExtractedOfficialValue>()
        labels.forEach { label ->
            Regex("""\b${Regex.escape(label)}\s*[:：]\s*([^\r\n<"]+)""", RegexOption.IGNORE_CASE)
                .findAll(normalizedHtml)
                .forEach { match ->
                    Regex("""(\d+(?:\.\d+)?)\s*(TB|GB)""", RegexOption.IGNORE_CASE)
                        .findAll(match.groupValues[1])
                        .forEach { capacityMatch ->
                            val token = "${capacityMatch.groupValues[1]}${capacityMatch.groupValues[2]}"
                            values += when (attributeCode) {
                                "ram_gb" -> ramValue(capacityMatch.groupValues[1])
                                "memory_gb" -> storageValue(token)
                                else -> ExtractedOfficialValue(attributeCode = attributeCode, rawValue = token)
                            }
                        }
                }
        }
        return values
    }

    private fun extractColors(
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        html: String,
    ): List<ExtractedOfficialValue> {
        val normalizedHtml = normalizeOfficialExtractionHtml(html).replace("+", " ")
        val hintedColors = endpoint.metadata["colorHints"]
            .orEmpty()
            .split(',', '|', ';')
            .mapNotNull { hint -> hint.trim().takeIf { it.isNotEmpty() } }
            .distinct()
            .filter { hint ->
                Regex("""(?<![\p{L}\p{N}])${Regex.escape(hint)}(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(normalizedHtml)
            }
        val extractedColors = extractColorCandidatesFromHtml(normalizedHtml)
            .filter(::isSupportedColorCandidate)
        return (hintedColors + extractedColors)
            .distinct()
            .map(::colorValue)
    }

    private fun isSupportedColorCandidate(candidate: String): Boolean {
        val normalized = normalizeOfficialCanonicalText(candidate)
        if (normalized.isBlank()) return false
        if (knownPhoneColorAliases.contains(normalized)) return true
        return normalized.split(' ').any { token -> token in basePhoneColorTokens }
    }

    private fun extractColorCandidatesFromHtml(normalizedHtml: String): List<String> {
        val candidates = linkedSetOf<String>()
        val rawPatterns = listOf(
            Regex("""(?:[?&](?:colour|color)=)([^&#"'\s]+)""", RegexOption.IGNORE_CASE),
            Regex("""["'](?:color|colour|colorName|colourName)["']\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""color-name-list["']?\s*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:data-color|data-colour|aria-label)\s*=\s*["'](?:Color Variant:\s*)?([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:specs|list|color)[-_]([a-z]+(?:[-_][a-z]+){0,2})\.(?:png|jpe?g|webp|svg)""", RegexOption.IGNORE_CASE),
        )
        rawPatterns.forEach { pattern ->
            pattern.findAll(normalizedHtml).forEach { match ->
                normalizeColorCandidate(match.groupValues[1])?.let(candidates::add)
            }
        }
        Regex("""(?:available\s+in|available\s+colors?|colors?|colours?)\s*[:：]\s*([^\r\n<]{3,120})""", RegexOption.IGNORE_CASE)
            .findAll(normalizedHtml)
            .forEach { match ->
                match.groupValues[1].splitColorSentence().forEach { token ->
                    normalizeColorCandidate(token)?.let(candidates::add)
                }
            }
        return candidates.toList()
    }

    private fun normalizeColorCandidate(raw: String): String? {
        val cleaned = decodeEscapedText(raw)
            .replace('+', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("""\b(?:color|colour|variant|option|swatch|finish)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', ';', '.', ':', '|', '/', '"', '\'')
        if (cleaned.isEmpty()) return null
        if (cleaned.any { char -> char.isDigit() }) return null
        val normalized = normalizeOfficialCanonicalText(cleaned)
        if (normalized.isBlank()) return null
        return cleaned
            .split(Regex("""\s+"""))
            .joinToString(" ") { token ->
                token.lowercase(Locale.ROOT).replaceFirstChar { character -> character.titlecase(Locale.ROOT) }
            }
    }

    private fun extractMaxRefreshHz(section: String): Int? =
        Regex("""(\d+)\s*-\s*(\d+)Hz|(\d+)Hz""", RegexOption.IGNORE_CASE)
            .findAll(section)
            .mapNotNull { match ->
                listOfNotNull(
                    match.groupValues.getOrNull(2)?.toIntOrNull(),
                    match.groupValues.getOrNull(3)?.toIntOrNull(),
                ).maxOrNull()
            }
            .maxOrNull()

    private fun extractReleaseDate(
        normalizedHtml: String,
        plainText: String,
    ): String? {
        val structuredPatterns = listOf(
            Regex("""["'](?:datePublished|releaseDate|release_date|availabilityDate|launchDate)["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:data-release-date|release-date)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        )
        structuredPatterns.forEach { pattern ->
            pattern.findAll(normalizedHtml).forEach { match ->
                normalizeReleaseDateCandidate(match.groupValues[1])?.let { return it }
            }
        }
        val naturalPatterns = listOf(
            Regex(
                """(?i)\b(?:release(?:d)?(?:\s+date)?|launch(?:ed)?(?:\s+date)?|announced(?:\s+on)?|available(?:\s+(?:from|on))?)\b[^A-Za-z0-9]{0,24}([A-Za-z]{3,9}\s+\d{1,2},\s+\d{4}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{4}|\d{4}[-/]\d{2}[-/]\d{2})""",
            ),
        )
        naturalPatterns.forEach { pattern ->
            pattern.findAll(plainText).forEach { match ->
                normalizeReleaseDateCandidate(match.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    private fun normalizeReleaseDateCandidate(raw: String): String? {
        val trimmed = stripHtml(raw)
            .replace(Regex("""(\d)(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE), "$1")
            .trim()
            .trim('"', '\'', '.', ',', ';')
        if (trimmed.isEmpty()) return null
        Regex("""\b(\d{4}-\d{2}-\d{2})\b""").find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        Regex("""\b(\d{4})/(\d{2})/(\d{2})\b""").find(trimmed)?.let { match ->
            return "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
        }
        runCatching { OffsetDateTime.parse(trimmed).toLocalDate().toString() }
            .getOrNull()
            ?.let { return it }
        releaseDateFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(trimmed, formatter).toString() }.getOrNull()
        }?.let { return it }
        return null
    }

    private fun extractBatteryMah(plainText: String): String? =
        Regex("""(?i)\b(\d{4,5})\s*m(?:ah|illiamp(?:ere)?(?:-|\s)?hours?)\b""")
            .findAll(plainText)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .firstOrNull { value -> value in 3_000..9_000 }
            ?.toString()

    private fun extractWiredChargingW(plainText: String): String? {
        val lowerCased = plainText.lowercase(Locale.ROOT)
        return Regex("""(?i)\b(\d{2,3})\s*w\b""")
            .findAll(plainText)
            .mapNotNull { match ->
                val watts = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                if (watts !in 10..150) return@mapNotNull null
                val contextStart = (match.range.first - 24).coerceAtLeast(0)
                val contextEnd = (match.range.last + 24).coerceAtMost(lowerCased.lastIndex)
                val context = lowerCased.substring(contextStart, contextEnd + 1)
                if (context.contains("wireless") || context.contains("qi")) return@mapNotNull null
                val looksLikeCharging = context.contains("charg") ||
                    context.contains("wired") ||
                    context.contains("supervooc") ||
                    context.contains("super charge") ||
                    context.contains("supercharge") ||
                    context.contains("hypercharge") ||
                    context.contains("turbo")
                if (!looksLikeCharging) return@mapNotNull null
                watts
            }
            .firstOrNull()
            ?.toString()
    }

    private fun extractWirelessCharging(plainText: String): Boolean? {
        val normalized = plainText.lowercase(Locale.ROOT)
        if (
            normalized.contains("no wireless charging") ||
            normalized.contains("without wireless charging") ||
            normalized.contains("без беспроводной зарядки")
        ) {
            return false
        }
        return if (
            normalized.contains("wireless charging") ||
            normalized.contains("wireless charge") ||
            normalized.contains("qi2") ||
            normalized.contains("qi wireless") ||
            normalized.contains("qi charging")
        ) {
            true
        } else {
            null
        }
    }

    private fun extractEsimSupport(plainText: String): Boolean? {
        val normalized = plainText.lowercase(Locale.ROOT)
        if (normalized.contains("no esim") || normalized.contains("without esim")) {
            return false
        }
        return if (
            normalized.contains("esim") ||
            normalized.contains("e-sim") ||
            normalized.contains("e sim")
        ) {
            true
        } else {
            null
        }
    }

    private fun extractDualSim(plainText: String): Boolean? {
        val normalized = plainText.lowercase(Locale.ROOT)
        if (normalized.contains("single sim")) return false
        return if (
            normalized.contains("dual sim") ||
            normalized.contains("dual-sim") ||
            normalized.contains("dual nano sim") ||
            normalized.contains("dual nano-sim") ||
            normalized.contains("two sim")
        ) {
            true
        } else {
            null
        }
    }

    private fun extractIpRating(plainText: String): String? =
        Regex("""(?i)\bIP\d{2}[A-Z]?\b""")
            .find(plainText)
            ?.value
            ?.uppercase(Locale.ROOT)

    private fun extractScreenSizeInch(plainText: String): String? =
        Regex("""(?i)\b(\d(?:[.,]\d{1,2})?)\s*(?:-|\s)?(?:inch|inches|["”])\b?""")
            .findAll(plainText)
            .mapNotNull { match ->
                val value = match.groupValues[1].replace(',', '.').toBigDecimalOrNull()
                    ?: return@mapNotNull null
                if (value < BigDecimal("4.0") || value > BigDecimal("8.5")) return@mapNotNull null
                value.stripTrailingZeros().toPlainString()
            }
            .firstOrNull()

    private fun extractRefreshRateHz(plainText: String): Int? =
        Regex("""(?i)\b(?:up to\s*)?(\d{2,3})\s*hz\b""")
            .findAll(plainText)
            .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
            .filter { hz -> hz in 50..240 }
            .maxOrNull()

    private fun extractChipsetFamily(plainText: String): String? {
        val patterns = listOf(
            Regex("""(?i)\bApple\s+A\d{2}(?:\s+Pro|\s+Bionic)?\b"""),
            Regex("""(?i)\bA\d{2}(?:\s+Pro|\s+Bionic)?\b"""),
            Regex("""(?i)\bGoogle\s+Tensor\s+G\d+\b"""),
            Regex("""(?i)\bTensor\s+G\d+\b"""),
            Regex("""(?i)\bSnapdragon\s+\d+[A-Za-z0-9+]*(?:\s+Gen\s*\d+)?(?:\s+Elite)?(?:\s+Ultra)?\b"""),
            Regex("""(?i)\bMediaTek\s+Dimensity\s+\d+(?:\s+Ultra|\s+Pro|\s+Ultimate)?\b"""),
            Regex("""(?i)\bDimensity\s+\d+(?:\s+Ultra|\s+Pro|\s+Ultimate)?\b"""),
            Regex("""(?i)\bExynos\s+\d+[A-Za-z0-9+]*(?:\s+Gen\s*\d+)?\b"""),
            Regex("""(?i)\bKirin\s+\d+[A-Za-z0-9+]*(?:\s+Pro)?\b"""),
        )
        return patterns.asSequence()
            .flatMap { pattern -> pattern.findAll(plainText).map { match -> match.value } }
            .map(::normalizeChipsetCandidate)
            .firstOrNull { candidate -> candidate.isNotBlank() }
    }

    private fun normalizeChipsetCandidate(raw: String): String =
        raw.replace(
            Regex("""\b(?:mobile platform|platform|processor|chipset|soc)\b.*$""", RegexOption.IGNORE_CASE),
            "",
        )
            .replace(Regex("""\bApple\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', ';', '.')

    private fun inferModelLine(
        brandCode: String,
        modelLabel: String,
    ): String? {
        val normalizedBrandCode = brandCode.trim().uppercase(Locale.ROOT)
        val trimmedLabel = modelLabel.trim().takeIf { label -> label.isNotEmpty() } ?: return null
        val brandStripped = trimmedLabel
            .replace(Regex("^${Regex.escape(normalizedBrandCode.lowercase(Locale.ROOT))}\\s+", RegexOption.IGNORE_CASE), "")
            .replace(
                Regex("""^(apple|samsung|google|xiaomi|poco|redmi|oneplus|nothing|huawei|honor|realme)\s+""", RegexOption.IGNORE_CASE),
                "",
            )
            .trim()
            .ifEmpty { trimmedLabel }
        val familySpecific = when (normalizedBrandCode) {
            "APPLE" -> "iPhone"
            "SAMSUNG" -> Regex("""^(Galaxy\s+[A-Za-z]+)""", RegexOption.IGNORE_CASE)
                .find(brandStripped)
                ?.groupValues
                ?.getOrNull(1)
            "GOOGLE" -> if (brandStripped.startsWith("Pixel", ignoreCase = true)) "Pixel" else null
            "XIAOMI" -> when {
                brandStripped.startsWith("Redmi Note", ignoreCase = true) -> "Redmi Note"
                brandStripped.startsWith("Redmi", ignoreCase = true) -> "Redmi"
                brandStripped.startsWith("POCO", ignoreCase = true) ->
                    Regex("""^(POCO\s+[A-Za-z]+)""", RegexOption.IGNORE_CASE)
                        .find(brandStripped)
                        ?.groupValues
                        ?.getOrNull(1)
                else -> Regex("""^([A-Za-z]+)""", RegexOption.IGNORE_CASE)
                    .find(brandStripped)
                    ?.groupValues
                    ?.getOrNull(1)
            }
            "ONEPLUS" -> when {
                brandStripped.startsWith("Nord", ignoreCase = true) -> "Nord"
                trimmedLabel.startsWith("OnePlus Nord", ignoreCase = true) -> "Nord"
                else -> "OnePlus"
            }
            "NOTHING" -> if (brandStripped.startsWith("Phone", ignoreCase = true)) "Phone" else null
            "HUAWEI" -> when {
                brandStripped.startsWith("Pura", ignoreCase = true) -> "Pura"
                Regex("""^P\d+""", RegexOption.IGNORE_CASE).containsMatchIn(brandStripped) -> "P"
                else -> null
            }
            "HONOR" -> when {
                Regex("""^(Magic\d+)""", RegexOption.IGNORE_CASE).containsMatchIn(brandStripped) ->
                    Regex("""^(Magic\d+)""", RegexOption.IGNORE_CASE).find(brandStripped)?.groupValues?.getOrNull(1)
                Regex("""^\d+""").containsMatchIn(brandStripped) ->
                    Regex("""^(\d+)""").find(brandStripped)?.groupValues?.getOrNull(1)
                else -> null
            }
            "REALME" -> when {
                brandStripped.startsWith("GT", ignoreCase = true) -> "GT"
                Regex("""^\d+\s+Pro\+""", RegexOption.IGNORE_CASE).containsMatchIn(brandStripped) ->
                    Regex("""^(\d+\s+Pro\+)""", RegexOption.IGNORE_CASE).find(brandStripped)?.groupValues?.getOrNull(1)
                Regex("""^\d+\s+Pro""", RegexOption.IGNORE_CASE).containsMatchIn(brandStripped) ->
                    Regex("""^(\d+\s+Pro)""", RegexOption.IGNORE_CASE).find(brandStripped)?.groupValues?.getOrNull(1)
                Regex("""^\d+""").containsMatchIn(brandStripped) ->
                    Regex("""^(\d+)""").find(brandStripped)?.groupValues?.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return familySpecific
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { candidate -> candidate.isNotEmpty() }
    }

    private fun extractNetworkType(plainText: String): String? {
        val normalized = plainText.lowercase(Locale.ROOT)
        return when {
            Regex("""(?<![a-z0-9])5\s*g(?![a-z0-9])""").containsMatchIn(normalized) -> "5G"
            Regex("""(?<![a-z0-9])4\s*g(?![a-z0-9])""").containsMatchIn(normalized) ||
                normalized.contains("lte") -> "4G"

            else -> null
        }
    }

    private fun extractOsFamily(plainText: String): String? {
        val normalized = plainText.lowercase(Locale.ROOT)
        return when {
            normalized.contains("iphone os") || Regex("""(?<![a-z])ios(?![a-z])""").containsMatchIn(normalized) -> "iOS"
            normalized.contains("android") -> "Android"
            else -> null
        }
    }

    private fun buildCanonicalValueSeed(
        source: CatalogGovernanceOfficialRefreshSource,
        endpoint: CatalogGovernanceOfficialRefreshEndpoint,
        value: ExtractedOfficialValue,
    ): CatalogGovernanceCuratedValueSeed {
        val scope = CatalogGovernanceScope(
            categoryCode = source.categoryCode,
            brandCode = source.brandCode,
            familyCode = endpoint.familyCode,
            modelCode = endpoint.modelCode,
        )
        val resolvedExisting = CatalogGovernanceCuratedSeed.resolveScopedCanonicalValues(
            attributeCode = value.attributeCode,
            scope = scope,
            locale = "en",
        ).firstOrNull { candidate ->
            matchesCanonicalCandidate(rawValue = value.rawValue, candidate = candidate)
        }
        val canonicalCode = resolvedExisting?.canonicalCode ?: when (value.attributeCode.lowercase(Locale.ROOT)) {
            "model_line" -> if (value.rawValue.any { character -> character.isLetter() }) {
                generateFallbackCanonicalCode(
                    attributeCode = value.attributeCode,
                    rawValue = value.rawValue,
                )
            } else {
                endpoint.familyCode.trim().takeIf { familyCode -> familyCode.isNotEmpty() }
                    ?: generateFallbackCanonicalCode(
                        attributeCode = value.attributeCode,
                        rawValue = value.rawValue,
                    )
            }
            else -> generateFallbackCanonicalCode(
                attributeCode = value.attributeCode,
                rawValue = value.rawValue,
            )
        }
        val canonicalDisplay = resolvedExisting?.displayValue ?: generateFallbackCanonicalDisplay(
            attributeCode = value.attributeCode,
            rawValue = value.rawValue,
        )
        val aliases = mergeAliasMaps(
            base = defaultValueAliases(attributeCode = value.attributeCode, rawValue = value.rawValue),
            overlay = value.aliases,
        )
        return CatalogGovernanceCuratedValueSeed(
            attributeCode = value.attributeCode,
            canonicalCode = canonicalCode,
            canonicalValue = canonicalDisplay,
            labels = localizedTextOf("en" to canonicalDisplay),
            categoryCode = source.categoryCode,
            brandCode = source.brandCode,
            familyCode = endpoint.familyCode,
            modelCode = endpoint.modelCode,
            aliases = aliases,
            metadata = source.metadata + endpoint.metadata + mapOf(
                "officialRefreshSource" to source.sourceCode,
                "officialEndpointCode" to endpoint.endpointCode,
                "officialRawValue" to value.rawValue,
            ),
        )
    }

    private fun matchesCanonicalCandidate(
        rawValue: String,
        candidate: com.example.shoppingassistant.domain.catalog.CatalogGovernanceScopedCanonicalValue,
    ): Boolean {
        val normalizedRawValue = normalizeOfficialCanonicalText(rawValue)
        if (normalizedRawValue.isBlank()) return false
        return normalizeOfficialCanonicalText(candidate.displayValue) == normalizedRawValue ||
            candidate.aliases.any { alias ->
                normalizeOfficialCanonicalText(alias) == normalizedRawValue
            }
    }

    private fun generateFallbackCanonicalCode(
        attributeCode: String,
        rawValue: String,
    ): String = when (attributeCode.lowercase(Locale.ROOT)) {
        "memory_gb", "ram_gb", "refresh_rate_hz", "release_year", "battery_mah", "wired_charging_w" ->
            rawValue.extractNumericDisplay()
        "release_date" -> rawValue.trim()
        "screen_size_inch" -> rawValue.extractDecimalDisplay()
        "network_type" -> rawValue.trim().uppercase(Locale.ROOT)
        "os_family" -> rawValue.filterNot { it.isWhitespace() }.uppercase(Locale.ROOT)
        else -> rawValue.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("""[^A-Z0-9]+"""), "_")
            .trim('_')
            .ifEmpty { "UNKNOWN_VALUE" }
    }

    private fun generateFallbackCanonicalDisplay(
        attributeCode: String,
        rawValue: String,
    ): String = when (attributeCode.lowercase(Locale.ROOT)) {
        "memory_gb", "ram_gb", "refresh_rate_hz", "release_year", "battery_mah", "wired_charging_w" ->
            rawValue.extractNumericDisplay()
        "release_date" -> rawValue.trim()
        "screen_size_inch" -> rawValue.extractDecimalDisplay()
        else -> rawValue.trim()
    }

    private fun defaultModelAliases(label: String): Map<String, List<String>> {
        val compact = label
            .replace(Regex("""[^A-Za-z0-9]+"""), "")
            .lowercase(Locale.ROOT)
        val aliases = listOf(label.lowercase(Locale.ROOT), compact)
            .filter { it.isNotBlank() }
            .distinct()
        return if (aliases.isEmpty()) emptyMap() else mapOf("en" to aliases)
    }

    private fun defaultValueAliases(
        attributeCode: String,
        rawValue: String,
    ): Map<String, List<String>> {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val normalizedAttributeCode = attributeCode.lowercase(Locale.ROOT)
        val aliases = when (normalizedAttributeCode) {
            "memory_gb" -> {
                val number = trimmed.extractNumericDisplay()
                val tbAliases = if (trimmed.contains("TB", ignoreCase = true)) {
                    listOf(trimmed.lowercase(Locale.ROOT))
                } else {
                    emptyList()
                }
                listOf(
                    "${number}gb",
                    "${number} gb",
                    "rom $number",
                    "$number rom",
                ) + tbAliases
            }

            "ram_gb" -> {
                val number = trimmed.extractNumericDisplay()
                listOf(
                    "${number}gb ram",
                    "${number} gb ram",
                    "${number}ram",
                    "$number ram",
                    "ram $number",
                )
            }

            "refresh_rate_hz" -> {
                val number = trimmed.extractNumericDisplay()
                listOf(number, "${number}hz", "${number} hz", "$number refresh")
            }

            "release_year" -> listOf(trimmed.extractNumericDisplay())

            "release_date" -> listOf(
                trimmed,
                trimmed.replace("-", "/"),
                trimmed.replace("-", ""),
            )

            "battery_mah" -> {
                val number = trimmed.extractNumericDisplay()
                listOf(number, "${number}mah", "${number} mah", "${number}ma", "battery $number", "батарея $number")
            }

            "wired_charging_w" -> {
                val number = trimmed.extractNumericDisplay()
                listOf("${number}w", "${number} w", "${number} watt", "charging ${number}w", "зарядка ${number}вт")
            }

            "screen_size_inch" -> {
                val number = trimmed.extractDecimalDisplay()
                val localized = number.replace('.', ',')
                listOf(number, localized, "${number}in", "${number} in", "${number} inch")
            }

            "network_type" -> when (trimmed.uppercase(Locale.ROOT)) {
                "5G" -> listOf("5g", "5 g", "nr", "5g nr")
                "4G" -> listOf("4g", "4 g", "lte")
                else -> listOf(trimmed.lowercase(Locale.ROOT))
            }

            "os_family" -> when (trimmed.lowercase(Locale.ROOT)) {
                "ios" -> listOf("ios", "iphone os")
                "android" -> listOf("android", "android os")
                else -> listOf(trimmed.lowercase(Locale.ROOT))
            }

            "wireless_charging" -> when (trimmed.lowercase(Locale.ROOT)) {
                "true" -> listOf("wireless charging", "wireless charge", "qi", "беспроводная зарядка", "беспроводная")
                "false" -> listOf("no wireless charging", "без беспроводной зарядки")
                else -> listOf(trimmed.lowercase(Locale.ROOT))
            }

            "esim_support" -> when (trimmed.lowercase(Locale.ROOT)) {
                "true" -> listOf("esim", "e-sim", "e sim")
                "false" -> listOf("no esim", "без esim")
                else -> listOf(trimmed.lowercase(Locale.ROOT))
            }

            "dual_sim" -> when (trimmed.lowercase(Locale.ROOT)) {
                "true" -> listOf("dual sim", "dual-sim", "dual nano sim", "dual nano-sim")
                "false" -> listOf("single sim", "одна sim")
                else -> listOf(trimmed.lowercase(Locale.ROOT))
            }

            "ip_rating" -> {
                val compact = trimmed.replace(" ", "")
                listOf(trimmed, compact)
            }

            "chipset_family" -> {
                val compact = trimmed.replace(Regex("""[^A-Za-z0-9]+"""), "").lowercase(Locale.ROOT)
                listOf(trimmed, compact).map { it.lowercase(Locale.ROOT) }
            }

            else -> {
                val compact = trimmed.replace(Regex("""[^A-Za-z0-9]+"""), "").lowercase(Locale.ROOT)
                listOf(trimmed, compact).map { it.lowercase(Locale.ROOT) }
            }
        }
        return aliases
            .mapNotNull { alias -> alias.trim().takeIf { it.isNotEmpty() } }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { mapOf("en" to it) }
            .orEmpty()
    }

    private fun storageValue(token: String): ExtractedOfficialValue {
        val display = token.extractNumericDisplay()
        return ExtractedOfficialValue(
            attributeCode = "memory_gb",
            rawValue = display,
            aliases = defaultValueAliases("memory_gb", token),
        )
    }

    private fun ramValue(number: String): ExtractedOfficialValue =
        ExtractedOfficialValue(
            attributeCode = "ram_gb",
            rawValue = number.extractNumericDisplay(),
            aliases = defaultValueAliases("ram_gb", number),
        )

    private fun refreshValue(hz: Int): ExtractedOfficialValue =
        ExtractedOfficialValue(
            attributeCode = "refresh_rate_hz",
            rawValue = hz.toString(),
            aliases = defaultValueAliases("refresh_rate_hz", hz.toString()),
        )

    private fun colorValue(label: String): ExtractedOfficialValue =
        ExtractedOfficialValue(
            attributeCode = "color",
            rawValue = label.trim(),
            aliases = defaultValueAliases("color", label),
        )

    private fun chipsetValue(label: String): ExtractedOfficialValue =
        ExtractedOfficialValue(
            attributeCode = "chipset_family",
            rawValue = label.trim(),
            aliases = defaultValueAliases("chipset_family", label),
        )

    private fun mergeAliasMaps(
        base: Map<String, List<String>>,
        overlay: Map<String, List<String>>,
    ): Map<String, List<String>> {
        val locales = LinkedHashSet<String>()
        locales += base.keys
        locales += overlay.keys
        return locales.associateWith { locale ->
            (base[locale].orEmpty() + overlay[locale].orEmpty())
                .mapNotNull { alias -> alias.trim().takeIf { it.isNotEmpty() } }
                .distinct()
        }.filterValues { aliases -> aliases.isNotEmpty() }
    }

    private fun String.extractNumericDisplay(): String {
        val match = Regex("""(\d+(?:[.,]\d+)?)""").find(this)
            ?: return trim()
        val numeric = match.groupValues[1].replace(',', '.')
        val asDouble = numeric.toDoubleOrNull()
        val multiplier = when {
            contains("TB", ignoreCase = true) -> 1024
            else -> 1
        }
        val normalized = asDouble?.times(multiplier)?.toInt()
        return normalized?.toString() ?: numeric
    }

    private fun String.extractDecimalDisplay(): String {
        val match = Regex("""(\d+(?:[.,]\d+)?)""").find(this)
            ?: return trim()
        val numeric = match.groupValues[1].replace(',', '.')
        return runCatching { BigDecimal(numeric).stripTrailingZeros().toPlainString() }
            .getOrDefault(numeric)
    }

    private fun String.splitColorSentence(): List<String> =
        replace(" and ", ",")
            .split(',')
            .map { token -> decodeEscapedText(token).trim() }
            .filter { token -> token.isNotEmpty() }

    private fun decodeEscapedText(raw: String): String =
        raw.replace("\\u0026", "&")
            .replace("\\u0027", "'")
            .replace("\\u002B", "+")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\\"", "\"")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    private fun stripHtml(raw: String): String =
        decodeEscapedText(
            raw.replace(Regex("""<[^>]+>"""), " "),
        ).replace(Regex("""\s+"""), " ").trim()

    private fun normalizeOfficialExtractionHtml(raw: String): String =
        decodeEscapedText(raw)
            .replace("%2B", "+", ignoreCase = true)
            .replace("&amp;", "&")
            .replace('\u00A0', ' ')

    private companion object {
        private val releaseDateFormatters: List<DateTimeFormatter> = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMMM d, uuuu")
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMM d, uuuu")
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMMM d uuuu")
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMM d uuuu")
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d MMMM uuuu")
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.SMART),
            DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("d MMM uuuu")
                .toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.SMART),
        )
    }
}

private fun mergeOfficialEndpoints(
    declaredEndpoints: List<CatalogGovernanceOfficialRefreshEndpoint>,
    overlayEndpoints: List<CatalogGovernanceOfficialRefreshEndpoint>,
): List<CatalogGovernanceOfficialRefreshEndpoint> =
    (declaredEndpoints + overlayEndpoints)
        .groupBy { endpoint -> endpoint.endpointCode }
        .values
        .map { duplicates -> duplicates.last() }
        .sortedBy { endpoint -> endpoint.endpointCode }

private data class ExtractedOfficialPhoneModel(
    val endpoint: CatalogGovernanceOfficialRefreshEndpoint,
    val values: List<ExtractedOfficialValue>,
)

private data class ExtractedOfficialValue(
    val attributeCode: String,
    val rawValue: String,
    val aliases: Map<String, List<String>> = emptyMap(),
)

private fun normalizeOfficialCanonicalText(raw: String): String =
    SearchTextNormalizer.normalizeKey(raw)
        .replace('ё', 'е')
        .replace("[-‐‑‒–—]+".toRegex(), " ")
        .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
