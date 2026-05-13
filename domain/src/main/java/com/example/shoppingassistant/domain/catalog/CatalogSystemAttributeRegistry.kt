package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.i18n.LocalizedText
import kotlinx.serialization.Serializable
import java.util.Locale

internal data class CatalogSystemAttributeRegistry(
    val attributesByCode: Map<String, CatalogSystemAttributeRegistryEntry>,
    val ignoredForOfferSignalCodes: Set<String>,
)

@Serializable
internal data class CatalogSystemAttributeRegistryDocument(
    val schemaVersion: String,
    val attributes: List<CatalogSystemAttributeRegistryEntry> = emptyList(),
)

@Serializable
internal data class CatalogSystemAttributeRegistryEntry(
    val code: String,
    val title: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val dataType: AttributeDataType,
    val valueType: Stage22ValueType,
    val valueSetType: Stage22ValueSetType,
    val uiOrderOffset: Int = 0,
    val requiredForSearch: Boolean = false,
    val requiredForOffer: Boolean = false,
    val requiredForExpress: Boolean = false,
    val facetEnabled: Boolean = false,
    val multiValued: Boolean = false,
    val valueDictCode: String? = null,
    val unit: String? = null,
    val normalization: String? = null,
    val isIdentity: Boolean = false,
    val isFacet: Boolean = false,
    val dictionaryRequired: Boolean = false,
    val enumOnly: Boolean = false,
    val regexPattern: String? = null,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val widgetHint: CatalogAttributeWidgetHint? = null,
    val options: List<CatalogValueOption> = emptyList(),
    val ignoreForOfferSignal: Boolean = false,
)

internal object CatalogSystemAttributeRegistryLoader {
    private const val RESOURCE_PATH = "taxonomy/stage2/2.2/_registry/system_attributes.json"

    private val registryCache: CatalogSystemAttributeRegistry by lazy { loadInternal() }

    fun load(): CatalogSystemAttributeRegistry = registryCache

    private fun loadInternal(): CatalogSystemAttributeRegistry {
        val document = CatalogSeedResourceReader.readJson(
            resourcePath = RESOURCE_PATH,
            deserializer = CatalogSystemAttributeRegistryDocument.serializer(),
        )
        val attributesByCode = LinkedHashMap<String, CatalogSystemAttributeRegistryEntry>()
        document.attributes.forEach { attribute ->
            val normalizedCode = attribute.code.trim().lowercase(Locale.ROOT)
            if (normalizedCode.isEmpty()) return@forEach
            check(!attributesByCode.containsKey(normalizedCode)) {
                "Duplicate system attribute code '$normalizedCode' in $RESOURCE_PATH."
            }
            attributesByCode[normalizedCode] = attribute.copy(
                code = attribute.code.trim(),
                title = attribute.title.trim(),
                valueDictCode = attribute.valueDictCode?.trim()?.takeIf { it.isNotEmpty() },
                unit = attribute.unit?.trim()?.takeIf { it.isNotEmpty() },
                normalization = attribute.normalization?.trim()?.takeIf { it.isNotEmpty() },
                regexPattern = attribute.regexPattern?.trim()?.takeIf { it.isNotEmpty() },
                options = attribute.options.map { option ->
                    option.copy(
                        valueCode = option.valueCode.trim(),
                        aliases = option.aliases
                            .map { alias -> alias.trim() }
                            .filter { alias -> alias.isNotEmpty() },
                    )
                },
            )
        }
        return CatalogSystemAttributeRegistry(
            attributesByCode = LinkedHashMap(attributesByCode),
            ignoredForOfferSignalCodes = attributesByCode.values
                .filter { attribute -> attribute.ignoreForOfferSignal }
                .map { attribute -> attribute.code.trim().lowercase(Locale.ROOT) }
                .toSet(),
        )
    }
}
