package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshParserType
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSource
import com.example.shoppingassistant.domain.catalog.CatalogGovernanceOfficialRefreshSources
import java.util.Locale

data class CatalogPhoneModelEnrichmentPromotionHints(
    val suggestedSourceUri: String? = null,
    val suggestedParserType: CatalogGovernanceOfficialRefreshParserType? = null,
    val sourceUriSuggestionConfidence: String? = null,
    val sourceUriSuggestionReason: String? = null,
)

object CatalogPhoneModelEnrichmentPromotionHintsResolver {
    private val officialSourcesByCode: Map<String, CatalogGovernanceOfficialRefreshSource> by lazy {
        CatalogGovernanceOfficialRefreshSources.resolve(CATALOG_GOVERNANCE_PHONES_CATEGORY_CODE)
            .associateBy { source -> source.sourceCode }
    }

    fun resolve(candidate: CatalogPhoneModelEnrichmentCandidate): CatalogPhoneModelEnrichmentPromotionHints {
        val sourceCode = candidate.officialSourceCode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return CatalogPhoneModelEnrichmentPromotionHints()
        val source = officialSourcesByCode[sourceCode] ?: return CatalogPhoneModelEnrichmentPromotionHints()
        val modelLabel = candidate.modelRaw.trim().ifEmpty { return CatalogPhoneModelEnrichmentPromotionHints() }
        val parserType = source.endpoints
            .groupingBy { endpoint -> endpoint.parserType }
            .eachCount()
            .maxByOrNull { entry -> entry.value }
            ?.key
            ?: source.endpoints.firstOrNull()?.parserType
            ?: CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE

        return when (source.sourceCode) {
            "APPLE_OFFICIAL_PHONES" -> CatalogPhoneModelEnrichmentPromotionHints(
                suggestedSourceUri = "https://www.apple.com/shop/buy-iphone/${slugifyGeneric(modelLabel)}",
                suggestedParserType = CatalogGovernanceOfficialRefreshParserType.APPLE_BUY_IPHONE_METRICS,
                sourceUriSuggestionConfidence = "HIGH",
                sourceUriSuggestionReason = "apple_buy_iphone_slug",
            )

            "SAMSUNG_OFFICIAL_PHONES" -> CatalogPhoneModelEnrichmentPromotionHints(
                suggestedSourceUri = "https://www.samsung.com/us/smartphones/${slugifyGeneric(modelLabel)}/buy/",
                suggestedParserType = CatalogGovernanceOfficialRefreshParserType.SAMSUNG_DEVICE_BUY_PAGE,
                sourceUriSuggestionConfidence = "HIGH",
                sourceUriSuggestionReason = "samsung_buy_page_slug",
            )

            "GOOGLE_OFFICIAL_PHONES" -> CatalogPhoneModelEnrichmentPromotionHints(
                suggestedSourceUri = source.sourceUri,
                suggestedParserType = CatalogGovernanceOfficialRefreshParserType.GOOGLE_PIXEL_SUPPORT_SPECS,
                sourceUriSuggestionConfidence = "HIGH",
                sourceUriSuggestionReason = "google_shared_support_specs_page",
            )

            "XIAOMI_OFFICIAL_PHONES" -> CatalogPhoneModelEnrichmentPromotionHints(
                suggestedSourceUri = "https://www.mi.com/global/product/${slugifyGeneric(modelLabel)}/specs/",
                suggestedParserType = CatalogGovernanceOfficialRefreshParserType.XIAOMI_GLOBAL_SPECS_PAGE,
                sourceUriSuggestionConfidence = "HIGH",
                sourceUriSuggestionReason = "xiaomi_specs_slug",
            )

            "ONEPLUS_OFFICIAL_PHONES" -> {
                val trimmed = removeBrandPrefix(modelLabel = modelLabel, brand = "oneplus")
                val region = if (trimmed.startsWith("nord ", ignoreCase = true)) "global" else "us"
                CatalogPhoneModelEnrichmentPromotionHints(
                    suggestedSourceUri = "https://www.oneplus.com/$region/${slugifyGeneric(trimmed)}/specs",
                    suggestedParserType = CatalogGovernanceOfficialRefreshParserType.ONEPLUS_SPECS_PAGE,
                    sourceUriSuggestionConfidence = "HIGH",
                    sourceUriSuggestionReason = "oneplus_specs_slug",
                )
            }

            "REALME_OFFICIAL_PHONES" -> CatalogPhoneModelEnrichmentPromotionHints(
                suggestedSourceUri = "https://www.realme.com/global/${slugifyGeneric(modelLabel)}/specs",
                suggestedParserType = CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE,
                sourceUriSuggestionConfidence = "HIGH",
                sourceUriSuggestionReason = "realme_specs_slug",
            )

            "HUAWEI_OFFICIAL_PHONES" -> {
                val trimmed = removeBrandPrefix(modelLabel = modelLabel, brand = "huawei")
                CatalogPhoneModelEnrichmentPromotionHints(
                    suggestedSourceUri = "https://consumer.huawei.com/en/phones/${slugifyHuawei(trimmed)}/specs/",
                    suggestedParserType = CatalogGovernanceOfficialRefreshParserType.GENERIC_PHONE_SPECS_PAGE,
                    sourceUriSuggestionConfidence = "HIGH",
                    sourceUriSuggestionReason = "huawei_specs_slug",
                )
            }

            else -> CatalogPhoneModelEnrichmentPromotionHints(
                suggestedSourceUri = null,
                suggestedParserType = parserType,
                sourceUriSuggestionConfidence = null,
                sourceUriSuggestionReason = null,
            )
        }
    }

    private fun removeBrandPrefix(modelLabel: String, brand: String): String {
        val trimmed = modelLabel.trim()
        val pattern = Regex("^${Regex.escape(brand)}\\s+", RegexOption.IGNORE_CASE)
        return trimmed.replace(pattern, "").trim().ifEmpty { trimmed }
    }

    private fun slugifyGeneric(label: String): String =
        label
            .lowercase(Locale.ROOT)
            .replace("+", " plus ")
            .replace("&", " and ")
            .replace("(", " ")
            .replace(")", " ")
            .replace("/", " ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

    private fun slugifyHuawei(label: String): String {
        val tokens = label
            .trim()
            .replace("+", " plus ")
            .replace(Regex("[()]"), " ")
            .split(Regex("\\s+"))
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }
        if (tokens.isEmpty()) return ""
        if (tokens.size >= 2 && tokens[0].all { it.isLetter() } && tokens[1].all { it.isLetterOrDigit() }) {
            val head = (tokens[0] + tokens[1]).lowercase(Locale.ROOT)
            val tail = tokens.drop(2).joinToString("-") { token -> slugifyGeneric(token) }
            return listOf(head, tail)
                .filter { part -> part.isNotBlank() }
                .joinToString("-")
        }
        return slugifyGeneric(label)
    }
}
