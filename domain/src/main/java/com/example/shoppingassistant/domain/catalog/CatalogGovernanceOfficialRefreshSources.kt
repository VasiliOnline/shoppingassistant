package com.example.shoppingassistant.domain.catalog
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class CatalogGovernanceOfficialRefreshSourcesDocument(
    val schemaVersion: String,
    val sources: List<CatalogGovernanceOfficialRefreshSourceSeed> = emptyList(),
)

@Serializable
data class CatalogGovernanceOfficialRefreshSourceSeed(
    val sourceCode: String,
    val categoryCode: String,
    val displayName: String,
    val brandCode: String,
    val tier: CatalogGovernanceSourceTier = CatalogGovernanceSourceTier.AUTHORITATIVE,
    val defaultLocale: String? = null,
    val marketCode: String? = null,
    val sourceUri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val endpoints: List<CatalogGovernanceOfficialRefreshEndpointSeed> = emptyList(),
)

@Serializable
data class CatalogGovernanceOfficialRefreshEndpointSeed(
    val endpointCode: String,
    val parserType: CatalogGovernanceOfficialRefreshParserType,
    val sourceUri: String,
    val familyCode: String,
    val modelCode: String,
    val modelLabel: String,
    val releaseYear: Int? = null,
    val releaseDate: String? = null,
    val aliases: Map<String, List<String>> = emptyMap(),
    val fixedValues: List<CatalogGovernanceOfficialRefreshValueSeed> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogGovernanceOfficialRefreshValueSeed(
    val attributeCode: String,
    val rawValue: String,
    val aliases: Map<String, List<String>> = emptyMap(),
)

@Serializable
enum class CatalogGovernanceOfficialRefreshParserType {
    APPLE_BUY_IPHONE_METRICS,
    SAMSUNG_DEVICE_BUY_PAGE,
    GOOGLE_PIXEL_SUPPORT_SPECS,
    XIAOMI_GLOBAL_SPECS_PAGE,
    ONEPLUS_SPECS_PAGE,
    NOTHING_PRODUCT_PAGE,
    GENERIC_PHONE_SPECS_PAGE,
}

data class CatalogGovernanceOfficialRefreshSource(
    val sourceCode: String,
    val categoryCode: String,
    val displayName: String,
    val brandCode: String,
    val tier: CatalogGovernanceSourceTier,
    val defaultLocale: String?,
    val marketCode: String?,
    val sourceUri: String?,
    val metadata: Map<String, String>,
    val endpoints: List<CatalogGovernanceOfficialRefreshEndpoint>,
)

data class CatalogGovernanceOfficialRefreshEndpoint(
    val endpointCode: String,
    val parserType: CatalogGovernanceOfficialRefreshParserType,
    val sourceUri: String,
    val familyCode: String,
    val modelCode: String,
    val modelLabel: String,
    val releaseYear: Int?,
    val releaseDate: String?,
    val aliases: Map<String, List<String>>,
    val fixedValues: List<CatalogGovernanceOfficialRefreshValueSeed>,
    val metadata: Map<String, String>,
)

object CatalogGovernanceOfficialRefreshSources {
    private val resourcePath: String
        get() = "${CatalogContractPaths.stage40Base}/phones_official_refresh_sources.json"

    val snapshot: CatalogGovernanceOfficialRefreshSourcesDocument by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = resourcePath,
            deserializer = CatalogGovernanceOfficialRefreshSourcesDocument.serializer(),
        )
    }

    fun resolve(categoryCode: String?): List<CatalogGovernanceOfficialRefreshSource> {
        val normalizedCategoryCode = categoryCode
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.takeIf { value -> value.isNotEmpty() }
            ?: return emptyList()
        return snapshot.sources
            .map { it.toNormalizedSource() }
            .filter { source -> source.categoryCode == normalizedCategoryCode }
            .sortedBy { it.displayName }
    }

    fun find(
        sourceCode: String,
        categoryCode: String? = null,
    ): CatalogGovernanceOfficialRefreshSource? {
        val normalizedSourceCode = sourceCode.trim().uppercase(Locale.ROOT)
        if (normalizedSourceCode.isEmpty()) return null
        return resolve(categoryCode)
            .firstOrNull { source -> source.sourceCode == normalizedSourceCode }
    }

    private fun CatalogGovernanceOfficialRefreshSourceSeed.toNormalizedSource():
        CatalogGovernanceOfficialRefreshSource =
        CatalogGovernanceOfficialRefreshSource(
            sourceCode = sourceCode.trim().uppercase(Locale.ROOT),
            categoryCode = categoryCode.trim().uppercase(Locale.ROOT),
            displayName = displayName.trim(),
            brandCode = brandCode.trim().uppercase(Locale.ROOT),
            tier = tier,
            defaultLocale = defaultLocale?.trim()?.ifEmpty { null },
            marketCode = marketCode?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null },
            sourceUri = sourceUri?.trim()?.ifEmpty { null },
            metadata = metadata.mapKeys { (key, _) -> key.trim() }.filterKeys { it.isNotEmpty() },
            endpoints = endpoints.map { endpoint ->
                CatalogGovernanceOfficialRefreshEndpoint(
                    endpointCode = endpoint.endpointCode.trim().uppercase(Locale.ROOT),
                    parserType = endpoint.parserType,
                    sourceUri = endpoint.sourceUri.trim(),
                    familyCode = endpoint.familyCode.trim().uppercase(Locale.ROOT),
                    modelCode = endpoint.modelCode.trim().uppercase(Locale.ROOT),
                    modelLabel = endpoint.modelLabel.trim(),
                    releaseYear = endpoint.releaseYear,
                    releaseDate = endpoint.releaseDate?.trim()?.ifEmpty { null },
                    aliases = normalizeAliases(endpoint.aliases),
                    fixedValues = endpoint.fixedValues
                        .map { value ->
                            CatalogGovernanceOfficialRefreshValueSeed(
                                attributeCode = value.attributeCode.trim().lowercase(Locale.ROOT),
                                rawValue = value.rawValue.trim(),
                                aliases = normalizeAliases(value.aliases),
                            )
                        }
                        .filter { value -> value.attributeCode.isNotBlank() && value.rawValue.isNotBlank() },
                    metadata = endpoint.metadata.mapKeys { (key, _) -> key.trim() }.filterKeys { it.isNotEmpty() },
                )
            },
        )

    private fun normalizeAliases(raw: Map<String, List<String>>): Map<String, List<String>> =
        raw.entries
            .mapNotNull { (locale, values) ->
                val normalizedLocale = locale.trim().lowercase(Locale.ROOT)
                    .takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                val normalizedValues = values
                    .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
                    .distinct()
                if (normalizedValues.isEmpty()) return@mapNotNull null
                normalizedLocale to normalizedValues
            }
            .toMap(LinkedHashMap())
}
