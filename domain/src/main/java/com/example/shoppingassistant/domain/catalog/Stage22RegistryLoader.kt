package com.example.shoppingassistant.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class Stage22ValueType {
    STRING,
    NUMBER,
    ENUM,
    BOOLEAN,
}

@Serializable
enum class Stage22ValueSetType {
    CLOSED,
    SEMI_CLOSED,
    OPEN,
}

@Serializable
internal data class Stage22AttributeDef(
    val attributeCode: String,
    val valueType: Stage22ValueType,
    val valueSetType: Stage22ValueSetType,
    val unit: String? = null,
    val isIdentity: Boolean = false,
    val isFacet: Boolean = false,
    val labels: Map<String, String> = emptyMap(),
    val normalization: String? = null,
)

@Serializable
internal data class Stage22ValueDictionaryEntry(
    val valueCode: String,
    val labels: Map<String, String> = emptyMap(),
    val aliases: List<String> = emptyList(),
)

@Serializable
internal data class Stage22ValueDictionary(
    val attributeCode: String,
    val entries: List<Stage22ValueDictionaryEntry> = emptyList(),
)

@Serializable
internal data class Stage22RegistryMeta(
    val schemaVersion: String,
    val dataVersion: String,
    val generatedAt: String,
    val notes: String? = null,
)

internal data class Stage22RegistrySnapshot(
    val attributes: Map<String, Stage22AttributeDef>,
    val dictionaries: Map<String, Stage22ValueDictionary>,
    val meta: Stage22RegistryMeta,
)

@Serializable
internal data class Stage22PackageDescriptor(
    val l0Code: String,
    val basePath: String,
    val profilesFile: String? = null,
    val constraintsFile: String? = null,
    val valueDictsFile: String? = null,
    val sharedProfilesFile: String? = null,
)

@Serializable
internal data class Stage22AttributesDocument(
    val schemaVersion: String,
    val attributes: List<Stage22AttributeDef>,
)

@Serializable
internal data class Stage22ValueDictionariesDocument(
    val schemaVersion: String,
    val dictionaries: List<Stage22ValueDictionary>,
)

@Serializable
internal data class Stage22PackageDescriptorsDocument(
    val schemaVersion: String,
    val descriptors: List<Stage22PackageDescriptor>,
)

internal object Stage22RegistryLoader {
    private const val ATTRIBUTES_FILE = "attributes.json"
    private const val VALUE_DICTIONARIES_FILE = "value_dictionaries.json"
    private const val META_FILE = "registry_meta.json"
    private const val PACKAGE_DESCRIPTORS_FILE = "package_descriptors.json"

    private val snapshotCache: Stage22RegistrySnapshot by lazy { loadSnapshotInternal() }
    private val descriptorsCache: List<Stage22PackageDescriptor> by lazy { loadDescriptorsInternal() }

    fun loadSnapshot(): Stage22RegistrySnapshot = snapshotCache

    fun loadPackageDescriptors(): List<Stage22PackageDescriptor> = descriptorsCache

    private fun loadSnapshotInternal(): Stage22RegistrySnapshot {
        val registryBasePath = CatalogContractPaths.stage22RegistryBase
        val attributesDoc = CatalogSeedResourceReader.readJson(
            resourcePath = "$registryBasePath/$ATTRIBUTES_FILE",
            deserializer = Stage22AttributesDocument.serializer(),
        )
        val valueDictionariesDoc = CatalogSeedResourceReader.readJson(
            resourcePath = "$registryBasePath/$VALUE_DICTIONARIES_FILE",
            deserializer = Stage22ValueDictionariesDocument.serializer(),
        )
        val meta = CatalogSeedResourceReader.readJson(
            resourcePath = "$registryBasePath/$META_FILE",
            deserializer = Stage22RegistryMeta.serializer(),
        )

        val attributes = LinkedHashMap<String, Stage22AttributeDef>()
        attributesDoc.attributes.forEach { attribute ->
            val attributeCode = attribute.attributeCode.trim()
            if (attributeCode.isNotEmpty()) {
                if (attributes.containsKey(attributeCode)) {
                    error("Duplicate attributeCode in Stage 2.2 registry: '$attributeCode'")
                }
                attributes[attributeCode] = attribute.copy(
                    attributeCode = attributeCode,
                    unit = attribute.unit?.trim()?.takeIf { it.isNotEmpty() },
                    labels = LinkedHashMap(
                        attribute.labels
                            .mapKeys { (locale, _) -> locale.trim().lowercase() }
                            .mapValues { (_, label) -> label.trim() }
                            .filter { (locale, label) -> locale.isNotEmpty() && label.isNotEmpty() },
                    ),
                    normalization = attribute.normalization?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }

        val dictionaries = LinkedHashMap<String, Stage22ValueDictionary>()
        valueDictionariesDoc.dictionaries.forEach { dictionary ->
            val attributeCode = dictionary.attributeCode.trim()
            if (attributeCode.isNotEmpty()) {
                if (dictionaries.containsKey(attributeCode)) {
                    error("Duplicate dictionary for attributeCode in Stage 2.2 registry: '$attributeCode'")
                }
                val normalizedEntries = dictionary.entries.map { entry ->
                    Stage22ValueDictionaryEntry(
                        valueCode = entry.valueCode.trim(),
                        labels = LinkedHashMap(
                            entry.labels
                                .mapKeys { (locale, _) -> locale.trim().lowercase() }
                                .mapValues { (_, label) -> label.trim() }
                                .filter { (locale, label) -> locale.isNotEmpty() && label.isNotEmpty() },
                        ),
                        aliases = entry.aliases
                            .map { alias -> alias.trim() }
                            .filter { alias -> alias.isNotEmpty() },
                    )
                }
                dictionaries[attributeCode] = Stage22ValueDictionary(
                    attributeCode = attributeCode,
                    entries = normalizedEntries,
                )
            }
        }

        return Stage22RegistrySnapshot(
            attributes = LinkedHashMap(attributes),
            dictionaries = LinkedHashMap(dictionaries),
            meta = meta.copy(
                schemaVersion = meta.schemaVersion.trim(),
                dataVersion = meta.dataVersion.trim(),
                generatedAt = meta.generatedAt.trim(),
                notes = meta.notes?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    private fun loadDescriptorsInternal(): List<Stage22PackageDescriptor> {
        val registryBasePath = CatalogContractPaths.stage22RegistryBase
        val document = CatalogSeedResourceReader.readJson(
            resourcePath = "$registryBasePath/$PACKAGE_DESCRIPTORS_FILE",
            deserializer = Stage22PackageDescriptorsDocument.serializer(),
        )
        val normalized = LinkedHashMap<String, Stage22PackageDescriptor>()
        document.descriptors.forEach { descriptor ->
            val l0Code = descriptor.l0Code.trim().uppercase()
            if (l0Code.isBlank()) return@forEach
            if (normalized.containsKey(l0Code)) {
                error("Duplicate l0Code in Stage 2.2 package descriptor registry: '$l0Code'")
            }
            val basePath = descriptor.basePath.trim().ifEmpty { CatalogContractPaths.stage22DefaultPackageBase(l0Code) }
            check(basePath.startsWith("${CatalogContractPaths.stage22Base}/")) {
                "Stage 2.2 package '$l0Code' basePath '$basePath' must be under '${CatalogContractPaths.stage22Base}/'."
            }
            normalized[l0Code] = descriptor.copy(
                l0Code = l0Code,
                basePath = basePath,
                profilesFile = descriptor.profilesFile?.trim()?.takeIf { it.isNotEmpty() },
                constraintsFile = descriptor.constraintsFile?.trim()?.takeIf { it.isNotEmpty() },
                valueDictsFile = descriptor.valueDictsFile?.trim()?.takeIf { it.isNotEmpty() },
                sharedProfilesFile = descriptor.sharedProfilesFile?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return normalized.values.toList()
    }
}

internal fun Stage22RegistrySnapshot.toLegacyDicts(): List<AttributeValueDict> =
    dictionaries.values.map { dictionary ->
        AttributeValueDict(
            attributeCode = dictionary.attributeCode,
            code = dictionary.attributeCode,
            entries = dictionary.entries.map { entry ->
                val canonicalValue = entry.labels["en"] ?: entry.labels["ru"] ?: entry.valueCode
                AttributeValueDictEntry(
                    canonicalCode = entry.valueCode,
                    canonicalValue = canonicalValue,
                    synonyms = entry.aliases,
                    rank = 0,
                )
            },
        )
    }
