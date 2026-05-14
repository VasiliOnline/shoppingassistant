package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.i18n.normalizeLocalizedLocale
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class CatalogGovernanceCuratedSeedDocument(
    val schemaVersion: String,
    val packs: List<CatalogGovernanceCuratedSeedPack> = emptyList(),
)

@Serializable
data class CatalogGovernanceCuratedSeedPack(
    val packCode: String,
    val sourceCode: String,
    val displayName: String,
    val tier: CatalogGovernanceSourceTier = CatalogGovernanceSourceTier.MANUAL_EDITORIAL,
    val defaultLocale: String? = null,
    val marketCode: String? = null,
    val sourceVersion: String? = null,
    val sourceUri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val brands: List<CatalogGovernanceCuratedBrandSeed> = emptyList(),
    val families: List<CatalogGovernanceCuratedProductFamilySeed> = emptyList(),
    val models: List<CatalogGovernanceCuratedModelSeed> = emptyList(),
    val canonicalValues: List<CatalogGovernanceCuratedValueSeed> = emptyList(),
) {
    fun toSourceSnapshot(capturedAt: Long): CatalogGovernanceSourceSnapshot =
        CatalogGovernanceSourceSnapshot(
            sourceCode = sourceCode,
            displayName = displayName,
            tier = tier,
            defaultLocale = defaultLocale,
            marketCode = marketCode,
            sourceVersion = sourceVersion,
            sourceUri = sourceUri,
            metadata = metadata + mapOf("seedPackCode" to packCode),
            capturedAt = capturedAt,
        )
}

@Serializable
data class CatalogGovernanceCuratedBrandSeed(
    val code: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val primaryCategoryCode: String? = null,
    val primarySegment: CategorySegment? = null,
    val aliases: Map<String, List<String>> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogGovernanceCuratedProductFamilySeed(
    val code: String,
    val brandCode: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val defaultCategoryCode: String,
    val prettyModelPrefix: String,
    val variantTokens: List<String> = emptyList(),
    val accessoryBlockers: List<String> = emptyList(),
    val aliases: Map<String, List<String>> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogGovernanceCuratedModelSeed(
    val code: String,
    val brandCode: String,
    val familyCode: String? = null,
    val labels: LocalizedText = LocalizedText.Empty,
    val defaultCategoryCode: String? = null,
    val releaseYear: Int? = null,
    val aliases: Map<String, List<String>> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CatalogGovernanceCuratedValueSeed(
    val attributeCode: String,
    val canonicalCode: String,
    val canonicalValue: String? = null,
    val labels: LocalizedText = LocalizedText.Empty,
    val canonicalLocale: String? = null,
    val categoryCode: String? = null,
    val brandCode: String? = null,
    val familyCode: String? = null,
    val modelCode: String? = null,
    val aliases: Map<String, List<String>> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
)

data class CatalogGovernanceScopedCanonicalValue(
    val canonicalCode: String,
    val displayValue: String,
    val aliases: List<String> = emptyList(),
    val scope: CatalogGovernanceScope = CatalogGovernanceScope(),
)

object CatalogGovernanceCuratedSeed {
    private val numericOrderedAttributeCodes = setOf(
        "memory_gb",
        "ram_gb",
        "refresh_rate_hz",
        "release_year",
        "screen_size_inch",
        "battery_mah",
        "wired_charging_w",
        "network_type",
        "diameter_cm",
    )
    private val numericTokenRegex = Regex("""(\d+(?:[.,]\d+)?)""")

    private val resourcePath: String
        get() = "${CatalogContractPaths.stage22RegistryBase}/governance_curated_top_categories.json"

    val snapshot: CatalogGovernanceCuratedSeedDocument by lazy {
        normalizeDocument(
            CatalogSeedResourceReader.readJson(
                resourcePath = resourcePath,
                deserializer = CatalogGovernanceCuratedSeedDocument.serializer(),
            ),
        )
    }

    fun projectedProductFamilies(): List<CatalogCanonicalProductFamilyEntry> {
        val brandsByCode = snapshot.packs
            .flatMap { it.brands }
            .associateBy { it.code }
        return snapshot.packs
            .flatMap { pack ->
                pack.families.map { family ->
                    val brand = brandsByCode[family.brandCode]
                        ?: error("Curated family '${family.code}' references missing brand '${family.brandCode}'.")
                    CatalogCanonicalProductFamilyEntry(
                        familyCode = family.code,
                        defaultCategoryCode = family.defaultCategoryCode,
                        brandCanonical = brand.displayLabel(),
                        brandAliases = brand.allAliasTexts(),
                        familyCanonical = family.displayLabel(),
                        familyAliases = family.allAliasTexts(),
                        prettyModelPrefix = family.prettyModelPrefix.trim(),
                        variantTokens = family.variantTokens.map { Stage21QueryTextNormalizer.normalize(it) }.filter { it.isNotBlank() }.distinct(),
                        accessoryBlockers = family.accessoryBlockers.map { Stage21QueryTextNormalizer.normalize(it) }.filter { it.isNotBlank() }.distinct(),
                    )
                }
            }
            .distinctBy { it.familyCode }
    }

    fun projectedModels(): List<CatalogCanonicalModelEntry> {
        val brandsByCode = snapshot.packs
            .flatMap { it.brands }
            .associateBy { it.code }
        val familiesByCode = snapshot.packs
            .flatMap { it.families }
            .associateBy { it.code }
        return snapshot.packs
            .flatMap { pack ->
                pack.models.map { model ->
                    val brand = brandsByCode[model.brandCode]
                        ?: error("Curated model '${model.code}' references missing brand '${model.brandCode}'.")
                    val family = model.familyCode?.let { familyCode ->
                        familiesByCode[familyCode]
                            ?: error("Curated model '${model.code}' references missing family '$familyCode'.")
                    }
                    CatalogCanonicalModelEntry(
                        modelCode = model.code,
                        defaultCategoryCode = model.defaultCategoryCode
                            ?: family?.defaultCategoryCode
                            ?: brand.primaryCategoryCode
                            ?: "TECH.PHONES",
                        brandCanonical = brand.displayLabel(),
                        familyCode = family?.code,
                        canonicalModel = model.displayLabel(),
                        modelAliases = model.allAliasTexts(),
                        accessoryBlockers = family?.accessoryBlockers
                            ?.map { Stage21QueryTextNormalizer.normalize(it) }
                            ?.filter { it.isNotBlank() }
                            ?.distinct()
                            .orEmpty(),
                        searchWeight = model.metadata["ruEuSearchWeight"]
                            ?.toIntOrNull()
                            ?.coerceIn(0, 100)
                            ?: 0,
                    )
                }
            }
            .mergeProjectedModelsByIdentity()
    }

    private fun List<CatalogCanonicalModelEntry>.mergeProjectedModelsByIdentity(): List<CatalogCanonicalModelEntry> {
        val mergedByIdentity = LinkedHashMap<String, CatalogCanonicalModelEntry>()
        forEach { model ->
            val identityKey = model.defaultCategoryCode.trim().uppercase() +
                "|" + model.canonicalModel.projectedModelIdentityKey()
            val existing = mergedByIdentity[identityKey]
            mergedByIdentity[identityKey] = when (existing) {
                null -> model
                else -> existing.copy(
                    modelAliases = (existing.modelAliases + model.modelAliases).distinct().sorted(),
                    accessoryBlockers = (existing.accessoryBlockers + model.accessoryBlockers).distinct().sorted(),
                    searchWeight = maxOf(existing.searchWeight, model.searchWeight),
                )
            }
        }
        return mergedByIdentity.values.toList()
    }

    private fun String.projectedModelIdentityKey(): String {
        val expanded = replace("+", " plus ")
        return Stage21QueryTextNormalizer.normalize(expanded)
            .replace(Regex("""\b5g\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun projectedValueDictionaries(): List<AttributeValueDict> =
        mergeProjectedDictionaries(
            projectedCanonicalValueDictionaries() + listOfNotNull(projectedPhoneModelLineDictionary()),
        )

    fun resolveBrandCode(brandText: String?): String? {
        val normalizedBrand = Stage21QueryTextNormalizer.normalize(brandText.orEmpty())
        if (normalizedBrand.isBlank()) return null
        return snapshot.packs
            .flatMap { it.brands }
            .firstOrNull { brand ->
                val candidates = buildList {
                    addAll(brand.labels.values)
                    addAll(brand.aliases.normalizedAliases().values.flatten())
                }
                candidates.any { candidate ->
                    Stage21QueryTextNormalizer.normalize(candidate) == normalizedBrand
                }
            }
            ?.code
    }

    private fun projectedCanonicalValueDictionaries(): List<AttributeValueDict> =
        snapshot.packs
            .flatMap { it.canonicalValues }
            .groupBy { it.attributeCode }
            .map { (attributeCode, values) ->
                val entries = LinkedHashMap<String, AttributeValueDictEntry>()
                values.forEach { value ->
                    val canonicalValue = value.displayValue()
                    val synonyms = (value.labels.values + value.allAliasTexts() + listOf(canonicalValue))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .sorted()
                    val existing = entries[value.canonicalCode]
                    entries[value.canonicalCode] = when (existing) {
                        null -> AttributeValueDictEntry(
                            canonicalCode = value.canonicalCode,
                            canonicalValue = canonicalValue,
                            synonyms = synonyms,
                        )

                        else -> existing.copy(
                            canonicalValue = existing.canonicalValue.ifBlank { canonicalValue },
                            synonyms = (existing.synonyms + synonyms)
                                .distinct()
                                .sorted(),
                        )
                    }
                }
                AttributeValueDict(
                    attributeCode = attributeCode,
                    code = attributeCode,
                    entries = entries.values.sortedBy { it.canonicalCode },
                )
            }
            .sortedBy { it.attributeCode }

    private fun projectedPhoneModelLineDictionary(): AttributeValueDict? {
        val brandsByCode = snapshot.packs
            .flatMap { it.brands }
            .associateBy { it.code }
        val familiesByCode = snapshot.packs
            .flatMap { it.families }
            .associateBy { it.code }
        val entries = LinkedHashMap<String, AttributeValueDictEntry>()
        snapshot.packs
            .flatMap { it.models }
            .forEach { model ->
                val brand = brandsByCode[model.brandCode] ?: return@forEach
                val family = model.familyCode?.let(familiesByCode::get) ?: return@forEach
                val categoryCode = model.defaultCategoryCode
                    ?: family.defaultCategoryCode
                    ?: brand.primaryCategoryCode
                    ?: "TECH.PHONES"
                if (!categoryCode.equals("TECH.PHONES", ignoreCase = true)) return@forEach

                val familyLabel = family.displayLabel().trim()
                val modelLine = deriveProjectedPhoneModelLine(
                    modelLabel = model.displayLabel(),
                    familyLabel = familyLabel,
                    familyPrettyModelPrefix = family.prettyModelPrefix,
                    brandLabel = brand.displayLabel(),
                ) ?: return@forEach
                val canonicalCode = modelLineCanonicalCode(modelLine)
                val synonyms = buildPhoneModelLineSynonyms(
                    displayValue = modelLine,
                    familyLabel = familyLabel,
                )
                val existing = entries[canonicalCode]
                entries[canonicalCode] = when (existing) {
                    null -> AttributeValueDictEntry(
                        canonicalCode = canonicalCode,
                        canonicalValue = modelLine,
                        synonyms = synonyms,
                    )

                    else -> existing.copy(
                        synonyms = (existing.synonyms + synonyms).distinct().sorted(),
                    )
                }
            }
        if (entries.isEmpty()) return null
        return AttributeValueDict(
            attributeCode = "model_line",
            code = "model_line",
            entries = entries.values.sortedBy { it.canonicalCode },
        )
    }

    private fun mergeProjectedDictionaries(
        dictionaries: List<AttributeValueDict>,
    ): List<AttributeValueDict> =
        dictionaries
            .groupBy { it.attributeCode }
            .map { (attributeCode, grouped) ->
                val mergedEntries = LinkedHashMap<String, AttributeValueDictEntry>()
                grouped.forEach { dictionary ->
                    dictionary.entries.forEach { entry ->
                        val existing = mergedEntries[entry.canonicalCode]
                        mergedEntries[entry.canonicalCode] = when (existing) {
                            null -> entry
                            else -> existing.copy(
                                canonicalValue = existing.canonicalValue.ifBlank { entry.canonicalValue },
                                synonyms = (existing.synonyms + entry.synonyms).distinct().sorted(),
                                rank = maxOf(existing.rank, entry.rank),
                            )
                        }
                    }
                }
                AttributeValueDict(
                    attributeCode = attributeCode,
                    code = grouped.firstOrNull()?.code ?: attributeCode,
                    entries = mergedEntries.values.sortedBy { it.canonicalCode },
                )
            }
            .sortedBy { it.attributeCode }

    fun resolveScopedCanonicalValues(
        attributeCode: String,
        scope: CatalogGovernanceScope = CatalogGovernanceScope(),
        locale: String? = null,
        includeLessSpecificFallback: Boolean = false,
    ): List<CatalogGovernanceScopedCanonicalValue> {
        val normalizedAttributeCode = attributeCode.trim().lowercase()
        if (normalizedAttributeCode.isBlank()) return emptyList()
        val normalizedScope = scope.normalized()
        val applicableValues = snapshot.packs
            .flatMap { it.canonicalValues }
            .filter { value -> value.attributeCode.equals(normalizedAttributeCode, ignoreCase = true) }
            .filter { value -> value.matchesScope(normalizedScope) }
        val selectedValues = if (includeLessSpecificFallback) {
            applicableValues
        } else {
            selectMostSpecificScopedValues(applicableValues)
        }
        return selectedValues
            .map { value ->
                val displayValue = value.displayValueForLocale(locale)
                CatalogGovernanceScopedCanonicalValue(
                    canonicalCode = value.canonicalCode.trim().uppercase(),
                    displayValue = displayValue,
                    aliases = (value.labels.values + value.allAliasTexts() + listOf(displayValue))
                        .map { alias -> alias.trim() }
                        .filter { alias -> alias.isNotEmpty() }
                        .distinct(),
                    scope = CatalogGovernanceScope(
                        categoryCode = value.categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        brandCode = value.brandCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        familyCode = value.familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        modelCode = value.modelCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                    ),
                )
            }
            .distinctBy { value -> value.canonicalCode }
            .sortForAttribute(normalizedAttributeCode)
    }

    fun CatalogGovernanceCuratedBrandSeed.toBrandCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        now: Long,
    ): CatalogBrandCanon =
        CatalogBrandCanon(
            code = code,
            labels = labels,
            normalizedKey = Stage21QueryTextNormalizer.normalize(displayLabel()),
            primaryCategoryCode = primaryCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            primarySegment = primarySegment,
            metadata = metadata + commonSeedMetadata(pack),
            createdAt = now,
            updatedAt = now,
        )

    fun CatalogGovernanceCuratedProductFamilySeed.toFamilyCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        now: Long,
    ): CatalogProductFamilyCanon =
        CatalogProductFamilyCanon(
            code = code,
            brandCode = brandCode,
            labels = labels,
            normalizedKey = Stage21QueryTextNormalizer.normalize(displayLabel()),
            prettyModelPrefix = prettyModelPrefix.trim(),
            variantTokens = variantTokens.map { Stage21QueryTextNormalizer.normalize(it) }.filter { it.isNotBlank() }.distinct(),
            accessoryBlockers = accessoryBlockers.map { Stage21QueryTextNormalizer.normalize(it) }.filter { it.isNotBlank() }.distinct(),
            defaultCategoryCode = defaultCategoryCode.trim().uppercase(),
            metadata = metadata + commonSeedMetadata(pack),
            createdAt = now,
            updatedAt = now,
        )

    fun CatalogGovernanceCuratedModelSeed.toModelCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        now: Long,
    ): CatalogModelCanon =
        CatalogModelCanon(
            code = code,
            brandCode = brandCode,
            familyCode = familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            labels = labels,
            normalizedKey = Stage21QueryTextNormalizer.normalize(displayLabel()),
            defaultCategoryCode = defaultCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            releaseYear = releaseYear,
            metadata = metadata + commonSeedMetadata(pack),
            createdAt = now,
            updatedAt = now,
        )

    fun CatalogGovernanceCuratedValueSeed.toValueCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        now: Long,
    ): CatalogAttributeValueCanon {
        val canonicalLocale = normalizeLocalizedLocale(canonicalLocale ?: pack.defaultLocale)
            .ifEmpty { "en" }
        val canonicalValue = displayValue()
        return CatalogAttributeValueCanon(
            attributeCode = attributeCode.trim(),
            canonicalCode = canonicalCode.trim().uppercase(),
            canonicalValue = canonicalValue,
            labels = labels.ifBlankThen(canonicalLocale to canonicalValue),
            canonicalLocale = canonicalLocale,
            normalizedValue = Stage21QueryTextNormalizer.normalize(canonicalValue),
            scope = CatalogGovernanceScope(
                categoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                brandCode = brandCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                familyCode = familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                modelCode = modelCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            ),
            metadata = metadata + commonSeedMetadata(pack),
            createdAt = now,
            updatedAt = now,
        )
    }

    fun CatalogGovernanceCuratedBrandSeed.toAliasCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        sourceSnapshotId: Long?,
        now: Long,
    ): List<CatalogAliasCanon> =
        aliases.toAliasCanon(
            targetKind = CatalogGovernanceAliasTargetKind.BRAND,
            targetCode = code,
            pack = pack,
            sourceSnapshotId = sourceSnapshotId,
            now = now,
        )

    fun CatalogGovernanceCuratedProductFamilySeed.toAliasCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        sourceSnapshotId: Long?,
        now: Long,
    ): List<CatalogAliasCanon> =
        aliases.toAliasCanon(
            targetKind = CatalogGovernanceAliasTargetKind.PRODUCT_FAMILY,
            targetCode = code,
            pack = pack,
            sourceSnapshotId = sourceSnapshotId,
            now = now,
        )

    fun CatalogGovernanceCuratedModelSeed.toAliasCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        sourceSnapshotId: Long?,
        now: Long,
    ): List<CatalogAliasCanon> =
        aliases.toAliasCanon(
            targetKind = CatalogGovernanceAliasTargetKind.MODEL,
            targetCode = code,
            pack = pack,
            sourceSnapshotId = sourceSnapshotId,
            now = now,
        )

    fun CatalogGovernanceCuratedValueSeed.toAliasCanon(
        pack: CatalogGovernanceCuratedSeedPack,
        sourceSnapshotId: Long?,
        now: Long,
    ): List<CatalogAliasCanon> =
        aliases.toAliasCanon(
            targetKind = CatalogGovernanceAliasTargetKind.ATTRIBUTE_VALUE,
            targetCode = canonicalCode,
            attributeCode = attributeCode,
            scope = CatalogGovernanceScope(
                categoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                brandCode = brandCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                familyCode = familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                modelCode = modelCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            ),
            pack = pack,
            sourceSnapshotId = sourceSnapshotId,
            now = now,
        )

    private fun Map<String, List<String>>.toAliasCanon(
        targetKind: CatalogGovernanceAliasTargetKind,
        targetCode: String,
        pack: CatalogGovernanceCuratedSeedPack,
        sourceSnapshotId: Long?,
        now: Long,
        attributeCode: String? = null,
        scope: CatalogGovernanceScope = CatalogGovernanceScope(),
    ): List<CatalogAliasCanon> =
        normalizedAliases().flatMap { (locale, values) ->
            values.map { alias ->
                CatalogAliasCanon(
                    locale = locale,
                    marketCode = pack.marketCode,
                    aliasText = alias,
                    normalizedAlias = Stage21QueryTextNormalizer.normalize(alias),
                    targetKind = targetKind,
                    targetCode = targetCode.trim().uppercase(),
                    attributeCode = attributeCode?.trim()?.takeIf { it.isNotEmpty() },
                    scope = scope,
                    sourceSnapshotId = sourceSnapshotId,
                    confidence = 0.98,
                    metadata = commonSeedMetadata(pack),
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }

    private fun CatalogGovernanceCuratedBrandSeed.displayLabel(): String =
        labels.resolve(locale = "en", fallback = code) ?: code

    private fun CatalogGovernanceCuratedProductFamilySeed.displayLabel(): String =
        labels.resolve(locale = "en", fallback = prettyModelPrefix) ?: prettyModelPrefix

    private fun CatalogGovernanceCuratedModelSeed.displayLabel(): String =
        labels.resolve(locale = "en", fallback = code) ?: code

    private fun CatalogGovernanceCuratedValueSeed.displayValue(): String =
        canonicalValue?.trim()?.takeIf { it.isNotEmpty() }
            ?: labels.resolve(locale = canonicalLocale ?: "en", fallback = canonicalCode)
            ?: canonicalCode

    private fun CatalogGovernanceCuratedValueSeed.displayValueForLocale(locale: String?): String =
        labels.resolve(locale = locale, fallback = displayValue()) ?: displayValue()

    private fun deriveProjectedPhoneModelLine(
        modelLabel: String,
        familyLabel: String,
        familyPrettyModelPrefix: String,
        brandLabel: String,
    ): String? {
        val prefix = sequenceOf(
            familyPrettyModelPrefix.trim(),
            familyLabel.trim(),
            brandLabel.trim(),
        )
            .firstOrNull { candidate -> candidate.isNotEmpty() && modelLabel.startsWith(candidate, ignoreCase = true) }
            .orEmpty()
        val remainder = modelLabel
            .removePrefixIgnoreCase(prefix)
            .replace('(', ' ')
            .replace(')', ' ')
            .replace('+', ' ')
            .replace(Regex("""\b(5g|4g)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(
                Regex("""\b(pro|plus|ultra|max|mini|lite|xl|se|fe)\b.*$""", RegexOption.IGNORE_CASE),
                "",
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
        return sequenceOf(
            listOf(familyLabel, remainder).filter { it.isNotBlank() }.joinToString(" ").trim(),
            familyLabel.trim(),
        )
            .map { candidate -> candidate.replace(Regex("""\s+"""), " ").trim() }
            .firstOrNull { candidate -> candidate.isNotBlank() }
    }

    private fun buildPhoneModelLineSynonyms(
        displayValue: String,
        familyLabel: String,
    ): List<String> {
        val normalizedDisplay = Stage21QueryTextNormalizer.normalize(displayValue)
        val compactDisplay = displayValue.replace(Regex("""[^A-Za-z0-9]+"""), "").lowercase(Locale.ROOT)
        val coreRemainder = normalizedDisplay
            .removePrefix(Stage21QueryTextNormalizer.normalize(familyLabel))
            .trim()
        val aliases = buildList {
            add(normalizedDisplay)
            add(compactDisplay)
            if (coreRemainder.any { it.isLetter() }) {
                add(coreRemainder)
                add(coreRemainder.replace(" ", ""))
            }
        }
        return aliases
            .mapNotNull { alias -> alias.trim().takeIf { it.isNotEmpty() } }
            .distinct()
            .sorted()
    }

    private fun modelLineCanonicalCode(displayValue: String): String =
        displayValue.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("""[^A-Z0-9]+"""), "_")
            .trim('_')
            .ifEmpty { "UNKNOWN_MODEL_LINE" }

    private fun String.removePrefixIgnoreCase(prefix: String): String {
        if (prefix.isBlank()) return this
        return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
    }

    private fun CatalogGovernanceCuratedBrandSeed.allAliasTexts(): List<String> =
        (labels.values + aliases.normalizedAliases().values.flatten())
            .map { Stage21QueryTextNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun CatalogGovernanceCuratedProductFamilySeed.allAliasTexts(): List<String> =
        (labels.values + aliases.normalizedAliases().values.flatten())
            .map { Stage21QueryTextNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun CatalogGovernanceCuratedValueSeed.allAliasTexts(): List<String> =
        aliases.normalizedAliases().values
            .flatten()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    private fun CatalogGovernanceCuratedModelSeed.allAliasTexts(): List<String> =
        (labels.values + aliases.normalizedAliases().values.flatten() + listOf(displayLabel()))
            .map { Stage21QueryTextNormalizer.normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    private fun Map<String, List<String>>.normalizedAliases(): Map<String, List<String>> =
        entries
            .mapNotNull { (locale, values) ->
                val normalizedLocale = normalizeLocalizedLocale(locale)
                if (normalizedLocale.isBlank()) return@mapNotNull null
                val normalizedValues = values
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
                if (normalizedValues.isEmpty()) return@mapNotNull null
                normalizedLocale to normalizedValues
            }
            .toMap()

    private fun CatalogGovernanceScope.normalized(): CatalogGovernanceScope =
        CatalogGovernanceScope(
            categoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            brandCode = brandCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            familyCode = familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
            modelCode = modelCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
        )

    private fun CatalogGovernanceCuratedValueSeed.matchesScope(scope: CatalogGovernanceScope): Boolean =
        scopeFieldMatches(categoryCode, scope.categoryCode) &&
            scopeFieldMatches(brandCode, scope.brandCode) &&
            scopeFieldMatches(familyCode, scope.familyCode) &&
            scopeFieldMatches(modelCode, scope.modelCode)

    private fun scopeFieldMatches(
        expected: String?,
        actual: String?,
    ): Boolean {
        val normalizedExpected = expected?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
            ?: return true
        val normalizedActual = actual?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
            ?: return false
        return normalizedExpected == normalizedActual
    }

    private fun selectMostSpecificScopedValues(
        values: List<CatalogGovernanceCuratedValueSeed>,
    ): List<CatalogGovernanceCuratedValueSeed> {
        if (values.isEmpty()) return emptyList()
        val mostSpecificLevel = values.maxOf { value -> value.scopeSpecificityLevel() }
        return values.filter { value -> value.scopeSpecificityLevel() == mostSpecificLevel }
    }

    private fun CatalogGovernanceCuratedValueSeed.scopeSpecificityLevel(): Int = when {
        !modelCode.isNullOrBlank() -> 4
        !familyCode.isNullOrBlank() -> 3
        !brandCode.isNullOrBlank() -> 2
        !categoryCode.isNullOrBlank() -> 1
        else -> 0
    }

    private fun List<CatalogGovernanceScopedCanonicalValue>.sortForAttribute(
        attributeCode: String,
    ): List<CatalogGovernanceScopedCanonicalValue> {
        val normalizedAttributeCode = attributeCode.trim().lowercase(Locale.ROOT)
        return if (normalizedAttributeCode in numericOrderedAttributeCodes) {
            sortedWith(
                compareBy<CatalogGovernanceScopedCanonicalValue> { value ->
                    value.numericSortKey() ?: Double.POSITIVE_INFINITY
                }.thenBy { value ->
                    value.displayValue.lowercase(Locale.ROOT)
                },
            )
        } else {
            this
        }
    }

    private fun CatalogGovernanceScopedCanonicalValue.numericSortKey(): Double? =
        sequenceOf(canonicalCode, displayValue)
            .mapNotNull(::extractNumericSortKey)
            .firstOrNull()

    private fun extractNumericSortKey(text: String): Double? =
        numericTokenRegex
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()

    private fun commonSeedMetadata(pack: CatalogGovernanceCuratedSeedPack): Map<String, String> =
        mapOf(
            "seedPackCode" to pack.packCode,
            "seedSourceCode" to pack.sourceCode,
            "seedSourceVersion" to (pack.sourceVersion ?: "unspecified"),
        )

    private fun LocalizedText.ifBlankThen(entry: Pair<String, String>): LocalizedText =
        if (isBlank()) localizedTextOf(entry) else this

    private fun normalizeDocument(
        raw: CatalogGovernanceCuratedSeedDocument,
    ): CatalogGovernanceCuratedSeedDocument {
        check(raw.schemaVersion.trim().isNotEmpty()) {
            "Curated governance seed schemaVersion must not be blank."
        }
        val normalizedPacks = raw.packs.map { pack ->
            pack.copy(
                packCode = pack.packCode.trim().uppercase(),
                sourceCode = pack.sourceCode.trim().lowercase(),
                displayName = pack.displayName.trim(),
                defaultLocale = normalizeLocalizedLocale(pack.defaultLocale),
                marketCode = pack.marketCode?.trim()?.uppercase(),
                sourceVersion = pack.sourceVersion?.trim(),
                sourceUri = pack.sourceUri?.trim(),
                metadata = pack.metadata.normalizeSeedMetadata(),
                brands = pack.brands.map { brand ->
                    brand.copy(
                        code = brand.code.trim().uppercase(),
                        primaryCategoryCode = brand.primaryCategoryCode?.trim()?.uppercase(),
                        aliases = brand.aliases.normalizeAliasMap(),
                        metadata = brand.metadata.normalizeSeedMetadata(),
                    )
                },
                families = pack.families.map { family ->
                    family.copy(
                        code = family.code.trim().uppercase(),
                        brandCode = family.brandCode.trim().uppercase(),
                        defaultCategoryCode = family.defaultCategoryCode.trim().uppercase(),
                        prettyModelPrefix = family.prettyModelPrefix.trim(),
                        variantTokens = family.variantTokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                        accessoryBlockers = family.accessoryBlockers.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                        aliases = family.aliases.normalizeAliasMap(),
                        metadata = family.metadata.normalizeSeedMetadata(),
                    )
                },
                models = pack.models.map { model ->
                    model.copy(
                        code = model.code.trim().uppercase(),
                        brandCode = model.brandCode.trim().uppercase(),
                        familyCode = model.familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        defaultCategoryCode = model.defaultCategoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        aliases = model.aliases.normalizeAliasMap(),
                        metadata = model.metadata.normalizeSeedMetadata(),
                    )
                },
                canonicalValues = pack.canonicalValues.map { value ->
                    value.copy(
                        attributeCode = value.attributeCode.trim(),
                        canonicalCode = value.canonicalCode.trim().uppercase(),
                        canonicalValue = value.canonicalValue?.trim(),
                        canonicalLocale = normalizeLocalizedLocale(value.canonicalLocale ?: pack.defaultLocale),
                        categoryCode = value.categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        brandCode = value.brandCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        familyCode = value.familyCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        modelCode = value.modelCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                        aliases = value.aliases.normalizeAliasMap(),
                        metadata = value.metadata.normalizeSeedMetadata(),
                    )
                },
            )
        }
        validateCuratedSeed(normalizedPacks)
        return raw.copy(
            schemaVersion = raw.schemaVersion.trim(),
            packs = normalizedPacks,
        )
    }

    private fun validateCuratedSeed(
        packs: List<CatalogGovernanceCuratedSeedPack>,
    ) {
        check(packs.isNotEmpty()) {
            "Curated governance seed must contain at least one pack."
        }
        check(packs.map { it.packCode }.distinct().size == packs.size) {
            "Curated governance seed contains duplicate packCode values."
        }
        val brandsByCode = LinkedHashMap<String, CatalogGovernanceCuratedBrandSeed>()
        val familiesByCode = LinkedHashMap<String, CatalogGovernanceCuratedProductFamilySeed>()
        packs.forEach { pack ->
            pack.brands.forEach { brand ->
                check(brand.code.isNotBlank()) { "Curated brand code must not be blank in pack '${pack.packCode}'." }
                check(brandsByCode.putIfAbsent(brand.code, brand) == null) {
                    "Curated governance seed contains duplicate brand '${brand.code}'."
                }
            }
            pack.families.forEach { family ->
                check(family.code.isNotBlank()) { "Curated family code must not be blank in pack '${pack.packCode}'." }
                check(family.prettyModelPrefix.isNotBlank()) {
                    "Curated family '${family.code}' must define prettyModelPrefix."
                }
                check(familiesByCode.putIfAbsent(family.code, family) == null) {
                    "Curated governance seed contains duplicate family '${family.code}'."
                }
            }
        }
        packs.flatMap { it.families }.forEach { family ->
            check(family.brandCode in brandsByCode) {
                "Curated family '${family.code}' references missing brand '${family.brandCode}'."
            }
        }
        packs.flatMap { it.models }.forEach { model ->
            check(model.brandCode in brandsByCode) {
                "Curated model '${model.code}' references missing brand '${model.brandCode}'."
            }
            model.familyCode?.let { familyCode ->
                check(familyCode in familiesByCode) {
                    "Curated model '${model.code}' references missing family '$familyCode'."
                }
            }
        }
    }

    private fun Map<String, String>.normalizeSeedMetadata(): Map<String, String> =
        entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isEmpty() || normalizedValue.isEmpty()) {
                    null
                } else {
                    normalizedKey to normalizedValue
                }
            }
            .toMap()

    private fun Map<String, List<String>>.normalizeAliasMap(): Map<String, List<String>> =
        entries
            .mapNotNull { (locale, values) ->
                val normalizedLocale = normalizeLocalizedLocale(locale)
                if (normalizedLocale.isEmpty()) return@mapNotNull null
                val normalizedValues = values
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                    .distinct()
                    .sorted()
                if (normalizedValues.isEmpty()) return@mapNotNull null
                normalizedLocale to normalizedValues
            }
            .toMap()
}
