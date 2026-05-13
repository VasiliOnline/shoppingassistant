package com.example.shoppingassistant.domain.facet

import com.example.shoppingassistant.domain.i18n.LocalizedText
import kotlinx.serialization.Serializable

@Serializable
enum class FacetDataType {
    ENUM,
    RANGE,
    BOOL,
    TEXT,
}

@Serializable
enum class FacetValueSource {
    OFFER,
    PRODUCT,
    DERIVED,
}

@Serializable
enum class FacetSelectionMode {
    SINGLE,
    MULTI,
    RANGE,
    BOOLEAN,
}

@Serializable
enum class FacetUiWidget {
    CHECKBOX_GROUP,
    RADIO_GROUP,
    RANGE_INPUT,
    TOGGLE,
    TEXT_INPUT,
}

@Serializable
data class FacetUiConfig(
    val order: Int = 0,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val format: String? = null,
)

@Serializable
data class FacetDefinition(
    val facetKey: String,
    val title: LocalizedText = LocalizedText.Empty,
    val valueType: FacetDataType,
    val appliesToCategoryCodes: List<String>,
    val attributeCode: String? = null,
    val dictionaryCode: String? = null,
    val selectionMode: FacetSelectionMode? = null,
    val uiWidget: FacetUiWidget? = null,
    val normalizationRef: String? = null,
    val source: FacetValueSource = FacetValueSource.OFFER,
    val effectiveFrom: String? = null, // ISO date (yyyy-MM-dd), inclusive
    val effectiveTo: String? = null, // ISO date (yyyy-MM-dd), inclusive
    val ui: FacetUiConfig = FacetUiConfig(),
)

@Serializable
data class FacetPresetRule(
    val facetKey: String,
    val includeValues: List<String> = emptyList(),
    val excludeValues: List<String> = emptyList(),
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val boolValue: Boolean? = null,
)

@Serializable
data class FacetPreset(
    val presetCode: String,
    val categoryCode: String,
    val title: LocalizedText = LocalizedText.Empty,
    val order: Int = 0,
    val effectiveFrom: String? = null, // ISO date (yyyy-MM-dd), inclusive
    val effectiveTo: String? = null, // ISO date (yyyy-MM-dd), inclusive
    val rules: List<FacetPresetRule> = emptyList(),
    val notes: String? = null,
)

@Serializable
data class FacetCollection(
    val collectionCode: String,
    val categoryCode: String,
    val title: LocalizedText = LocalizedText.Empty,
    val browseCode: String? = null,
    val presetCode: String? = null,
    val order: Int = 0,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
)

interface FacetDefinitionRepository {
    suspend fun listFacetDefinitions(): List<FacetDefinition>
    suspend fun listFacetDefinitions(categoryCode: String): List<FacetDefinition>
    suspend fun getFacetDefinition(facetKey: String): FacetDefinition?
}

interface FacetPresetRepository {
    suspend fun listFacetPresets(): List<FacetPreset>
    suspend fun listFacetPresets(categoryCode: String): List<FacetPreset>
    suspend fun getFacetPreset(presetCode: String): FacetPreset?
}

interface FacetCollectionRepository {
    suspend fun listFacetCollections(): List<FacetCollection>
    suspend fun listFacetCollections(categoryCode: String): List<FacetCollection>
    suspend fun getFacetCollection(collectionCode: String): FacetCollection?
    suspend fun getFacetCollectionByBrowseCode(browseCode: String): FacetCollection?
}

class GetFacetDefinitionsTask(
    private val repository: FacetDefinitionRepository,
) {
    suspend operator fun invoke(categoryCode: String? = null): List<FacetDefinition> =
        if (categoryCode.isNullOrBlank()) repository.listFacetDefinitions()
        else repository.listFacetDefinitions(categoryCode)
}

class GetFacetPresetsTask(
    private val repository: FacetPresetRepository,
) {
    suspend operator fun invoke(categoryCode: String? = null): List<FacetPreset> =
        if (categoryCode.isNullOrBlank()) repository.listFacetPresets()
        else repository.listFacetPresets(categoryCode)
}

class GetFacetPresetTask(
    private val repository: FacetPresetRepository,
) {
    suspend operator fun invoke(presetCode: String): FacetPreset? = repository.getFacetPreset(presetCode)
}

class GetFacetCollectionsTask(
    private val repository: FacetCollectionRepository,
) {
    suspend operator fun invoke(categoryCode: String? = null): List<FacetCollection> =
        if (categoryCode.isNullOrBlank()) repository.listFacetCollections()
        else repository.listFacetCollections(categoryCode)
}

class GetFacetCollectionTask(
    private val repository: FacetCollectionRepository,
) {
    suspend operator fun invoke(collectionCode: String): FacetCollection? =
        repository.getFacetCollection(collectionCode)
}

class GetFacetCollectionByBrowseCodeTask(
    private val repository: FacetCollectionRepository,
) {
    suspend operator fun invoke(browseCode: String): FacetCollection? =
        repository.getFacetCollectionByBrowseCode(browseCode)
}

fun FacetDefinition.runtimeFilterKey(): String =
    attributeCode
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: facetKey.trim().lowercase()
