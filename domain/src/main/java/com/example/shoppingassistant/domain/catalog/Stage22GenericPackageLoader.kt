package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import kotlinx.serialization.builtins.ListSerializer

internal data class Stage22PackageData(
    val descriptor: Stage22PackageDescriptor,
    val profiles: List<Stage22ResourceProfile>,
    val constraints: List<CatalogConstraints>,
    val valueDicts: List<AttributeValueDict>,
    val sharedProfiles: List<Stage22SharedResourceProfile> = emptyList(),
)

internal object GenericStage22PackageLoader {
    private const val GLOBAL_CONSTRAINTS_FILE = "constraints.global.json"
    private const val GLOBAL_VALUE_DICTS_FILE = "attribute_dicts.global.json"
    private const val GLOBAL_SHARED_PROFILES_FILE = "shared_profiles.global.json"

    private val cache = LinkedHashMap<String, Stage22PackageData>()

    private val registrySnapshot: Stage22RegistrySnapshot by lazy { Stage22RegistryLoader.loadSnapshot() }

    private val globalConstraints: List<CatalogConstraints> by lazy {
        val path = "${CatalogContractPaths.stage22GlobalBase}/$GLOBAL_CONSTRAINTS_FILE"
        if (!CatalogSeedResourceReader.resourceExists(path)) return@lazy emptyList()
        CatalogSeedResourceReader.readJson(
            resourcePath = path,
            deserializer = ListSerializer(CatalogConstraints.serializer()),
        )
    }

    private val globalLegacyDictsByCode: LinkedHashMap<String, AttributeValueDict> by lazy {
        val path = "${CatalogContractPaths.stage22GlobalBase}/$GLOBAL_VALUE_DICTS_FILE"
        if (!CatalogSeedResourceReader.resourceExists(path)) {
            return@lazy linkedMapOf()
        }
        toDictIndex(loadValueDicts(path))
    }

    private val globalSharedProfiles: List<Stage22SharedResourceProfile> by lazy {
        val path = "${CatalogContractPaths.stage22GlobalBase}/$GLOBAL_SHARED_PROFILES_FILE"
        if (!CatalogSeedResourceReader.resourceExists(path)) return@lazy emptyList()
        loadSharedProfiles(path)
    }

    private val registryLegacyDictsByCode: LinkedHashMap<String, AttributeValueDict> by lazy {
        toDictIndex(registrySnapshot.toLegacyDicts())
    }

    @Synchronized
    fun load(descriptor: Stage22PackageDescriptor): Stage22PackageData =
        cache.getOrPut(descriptor.l0Code) { loadInternal(descriptor) }

    fun loadAll(): List<Stage22PackageData> =
        Stage22RegistryLoader.loadPackageDescriptors()
            .sortedBy { it.l0Code }
            .map(::load)

    fun loadGlobalConstraints(): List<CatalogConstraints> = globalConstraints

    fun loadRegistrySnapshot(): Stage22RegistrySnapshot = registrySnapshot

    private fun loadInternal(descriptor: Stage22PackageDescriptor): Stage22PackageData {
        val profilesPath = descriptor.profilesFile
            ?.let { "${descriptor.basePath}/$it" }
            ?.takeIf { CatalogSeedResourceReader.resourceExists(it) }
        val constraintsPath = descriptor.constraintsFile
            ?.let { "${descriptor.basePath}/$it" }
            ?.takeIf { CatalogSeedResourceReader.resourceExists(it) }
        val localDictPath = descriptor.valueDictsFile
            ?.let { "${descriptor.basePath}/$it" }
            ?.takeIf { CatalogSeedResourceReader.resourceExists(it) }
        val sharedProfilesPath = descriptor.sharedProfilesFile
            ?.let { "${descriptor.basePath}/$it" }
            ?.takeIf { CatalogSeedResourceReader.resourceExists(it) }

        val localDicts = localDictPath?.let(::loadValueDicts).orEmpty()
        val sharedProfiles = globalSharedProfiles + sharedProfilesPath?.let(::loadSharedProfiles).orEmpty()
        val dictByCode = LinkedHashMap<String, AttributeValueDict>()
        dictByCode.putAll(globalLegacyDictsByCode)
        dictByCode.putAll(registryLegacyDictsByCode)
        toDictIndex(sharedProfiles.flatMap { it.valueDictionaries }).forEach { (key, dict) -> dictByCode[key] = dict }
        toDictIndex(localDicts).forEach { (key, dict) -> dictByCode[key] = dict }

        val profiles = profilesPath?.let { path ->
            val rawProfiles = CatalogSeedResourceReader.readJson(
                resourcePath = path,
                deserializer = ListSerializer(Stage22ResourceProfile.serializer()),
            )
            val expandedProfiles = expandInheritedProfiles(
                rawProfiles = rawProfiles,
                sharedProfiles = sharedProfiles,
                sourcePath = path,
            )
            expandedProfiles.map { profile ->
                profile.copy(
                    valueDictionaries = resolveProfileDicts(
                        profile = profile,
                        dictByCode = dictByCode,
                        sourcePath = path,
                    ),
                )
            }
        }.orEmpty()

        val constraints = constraintsPath?.let { path ->
            CatalogSeedResourceReader.readJson(
                resourcePath = path,
                deserializer = ListSerializer(CatalogConstraints.serializer()),
            )
        }.orEmpty()

        return Stage22PackageData(
            descriptor = descriptor,
            profiles = profiles,
            constraints = constraints,
            valueDicts = dictByCode.values.toList(),
            sharedProfiles = sharedProfiles,
        )
    }

    private fun loadSharedProfiles(resourcePath: String): List<Stage22SharedResourceProfile> =
        CatalogSeedResourceReader.readJson(
            resourcePath = resourcePath,
            deserializer = ListSerializer(Stage22SharedResourceProfile.serializer()),
        )

    private fun expandInheritedProfiles(
        rawProfiles: List<Stage22ResourceProfile>,
        sharedProfiles: List<Stage22SharedResourceProfile>,
        sourcePath: String,
    ): List<Stage22ResourceProfile> {
        if (sharedProfiles.isEmpty()) return rawProfiles

        val sharedByCode = LinkedHashMap<String, Stage22SharedResourceProfile>()
        sharedProfiles.forEach { shared ->
            val code = shared.profileCode.trim()
            if (code.isEmpty()) {
                error("Blank shared profile code in '$sourcePath'")
            }
            val previous = sharedByCode.putIfAbsent(code, shared.copy(profileCode = code))
            if (previous != null && previous != shared) {
                error("Duplicate shared profile '$code' with different content in '$sourcePath'")
            }
        }

        return rawProfiles.map { profile ->
            if (profile.extendsProfiles.isEmpty()) return@map profile

            val attributes = LinkedHashMap<String, AttributeDef>()
            val categoryAttributes = LinkedHashMap<String, CategoryAttribute>()
            val valueDictionaries = LinkedHashMap<String, AttributeValueDict>()
            val requiredIfRules = mutableListOf<RequiredIfRule>()

            profile.extendsProfiles.forEach { rawCode ->
                val code = rawCode.trim()
                val shared = sharedByCode[code]
                    ?: error(
                        "Profile '${profile.category.code}' in '$sourcePath' extends unknown shared profile '$code'.",
                    )
                mergeSharedProfileIntoCategory(
                    profile = profile,
                    shared = shared,
                    attributes = attributes,
                    categoryAttributes = categoryAttributes,
                    valueDictionaries = valueDictionaries,
                    requiredIfRules = requiredIfRules,
                )
            }

            profile.attributes.forEach { attribute ->
                val code = attribute.code.trim()
                if (code.isNotEmpty()) attributes[code] = attribute.copy(code = code)
            }
            profile.categoryAttributes.forEach { categoryAttribute ->
                val code = categoryAttribute.attributeCode.trim()
                if (code.isNotEmpty()) {
                    categoryAttributes[code] = categoryAttribute.copy(
                        categoryCode = profile.category.code,
                        attributeCode = code,
                    )
                }
            }
            profile.valueDictionaries.forEach { dictionary ->
                val attributeCode = dictionary.attributeCode.trim()
                if (attributeCode.isNotEmpty()) valueDictionaries[attributeCode] = dictionary
            }
            requiredIfRules += profile.requiredIfRules

            profile.copy(
                attributes = attributes.values.toList(),
                categoryAttributes = categoryAttributes.values.toList(),
                valueDictionaries = valueDictionaries.values.toList(),
                requiredIfRules = requiredIfRules.distinct(),
            )
        }
    }

    private fun mergeSharedProfileIntoCategory(
        profile: Stage22ResourceProfile,
        shared: Stage22SharedResourceProfile,
        attributes: LinkedHashMap<String, AttributeDef>,
        categoryAttributes: LinkedHashMap<String, CategoryAttribute>,
        valueDictionaries: LinkedHashMap<String, AttributeValueDict>,
        requiredIfRules: MutableList<RequiredIfRule>,
    ) {
        shared.attributes.forEach { attribute ->
            val code = attribute.code.trim()
            if (code.isNotEmpty()) attributes.putIfAbsent(code, attribute.copy(code = code))
        }
        shared.categoryAttributes.forEach { categoryAttribute ->
            val code = categoryAttribute.attributeCode.trim()
            if (code.isNotEmpty()) {
                categoryAttributes.putIfAbsent(
                    code,
                    categoryAttribute.copy(
                        categoryCode = profile.category.code,
                        attributeCode = code,
                    ),
                )
            }
        }
        shared.valueDictionaries.forEach { dictionary ->
            val attributeCode = dictionary.attributeCode.trim()
            if (attributeCode.isNotEmpty()) valueDictionaries.putIfAbsent(attributeCode, dictionary)
        }
        requiredIfRules += shared.requiredIfRules
    }

    private fun resolveProfileDicts(
        profile: Stage22ResourceProfile,
        dictByCode: Map<String, AttributeValueDict>,
        sourcePath: String,
    ): List<AttributeValueDict> {
        val resolved = LinkedHashMap<String, AttributeValueDict>()
        profile.attributes.forEach { attribute ->
            val dictCode = attribute.valueDictCode?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val dictFromRegistry = dictByCode[dictCode]
            val dictFromProfile = profile.valueDictionaries.firstOrNull { dict ->
                dict.code?.equals(dictCode, ignoreCase = false) == true ||
                    dict.attributeCode.equals(dictCode, ignoreCase = false)
            }
            val dict = dictFromRegistry ?: dictFromProfile
                ?: error(
                    "Missing value dictionary '$dictCode' for category '${profile.category.code}' " +
                        "attribute '${attribute.code}' in '$sourcePath'",
                )

            resolved.putIfAbsent(dict.attributeCode, dict)
        }
        return resolved.values.toList()
    }

    private fun toDictIndex(dicts: List<AttributeValueDict>): LinkedHashMap<String, AttributeValueDict> {
        val map = LinkedHashMap<String, AttributeValueDict>()
        dicts.forEach { dict ->
            val code = dict.code?.trim()?.takeIf { it.isNotEmpty() } ?: dict.attributeCode
            map.putIfAbsent(code, dict)
            map.putIfAbsent(dict.attributeCode, dict)
        }
        return map
    }

    private fun loadValueDicts(resourcePath: String): List<AttributeValueDict> = when {
        resourcePath.endsWith(".json", ignoreCase = true) -> {
            CatalogSeedResourceReader.readJson(
                resourcePath = resourcePath,
                deserializer = ListSerializer(AttributeValueDict.serializer()),
            )
        }

        resourcePath.endsWith(".tsv", ignoreCase = true) -> {
            parseDictsTsv(resourcePath, CatalogSeedResourceReader.readText(resourcePath))
        }

        else -> error("Unsupported value dictionaries format: '$resourcePath'")
    }

    private fun parseDictsTsv(
        resourcePath: String,
        content: String,
    ): List<AttributeValueDict> {
        val rows = parseTsvRows(resourcePath, content)
        val grouped = LinkedHashMap<String, MutableList<AttributeValueDictEntry>>()
        val attributeByGroup = LinkedHashMap<String, String>()

        rows.forEach { row ->
            val attributeCode = row.required("attribute_code", resourcePath)
            val dictCode = row.optional("dict_code") ?: attributeCode
            val groupKey = "$dictCode|$attributeCode"
            attributeByGroup.putIfAbsent(groupKey, attributeCode)

            val entry = AttributeValueDictEntry(
                canonicalCode = row.required("canonical_code", resourcePath),
                canonicalValue = row.required("canonical_value", resourcePath),
                synonyms = row.optional("synonyms")
                    ?.split("|", ",", ";")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
                rank = row.optional("rank")?.toIntOrNull() ?: 0,
            )

            grouped.getOrPut(groupKey) { mutableListOf() }.add(entry)
        }

        return grouped.entries.map { (groupKey, entries) ->
            val dictCode = groupKey.substringBefore("|")
            val attributeCode = attributeByGroup[groupKey].orEmpty()
            AttributeValueDict(
                attributeCode = attributeCode,
                code = dictCode,
                entries = entries.toList(),
            )
        }
    }

    private fun parseTsvRows(
        resourcePath: String,
        content: String,
    ): List<Map<String, String>> {
        val lines = content
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val header = lines.first().split('\t').map { it.trim() }.toMutableList()
        if (header.isEmpty()) return emptyList()
        header[0] = header[0].removePrefix("\uFEFF")

        return lines.drop(1).mapIndexed { rowIndex, line ->
            val cells = line.split('\t')
            header.indices.associate { columnIndex ->
                header[columnIndex] to cells.getOrElse(columnIndex) { "" }.trim()
            }.also { row ->
                if (row["attribute_code"].isNullOrBlank()) {
                    error("TSV parse error in '$resourcePath' row ${rowIndex + 2}: missing 'attribute_code'")
                }
            }
        }
    }

    private fun Map<String, String>.required(
        column: String,
        resourcePath: String,
    ): String = this[column]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("TSV parse error in '$resourcePath': missing required column '$column'")

    private fun Map<String, String>.optional(column: String): String? =
        this[column]?.trim()?.takeIf { it.isNotEmpty() }
}
