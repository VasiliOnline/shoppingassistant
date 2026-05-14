package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.domain.facet.FacetCollection
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.FacetPresetRule
import com.example.shoppingassistant.domain.facet.FacetPreset
import com.example.shoppingassistant.domain.facet.FacetSelectionMode
import com.example.shoppingassistant.domain.facet.FacetUiConfig
import com.example.shoppingassistant.domain.facet.FacetUiWidget
import com.example.shoppingassistant.domain.facet.FacetValueSource
import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.i18n.toLocalizedText
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

internal object CatalogSeedLoader {
    val categories: List<Category> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage20Base}/categories.json",
            deserializer = ListSerializer(SeedCategoryRow.serializer()),
        )
            .map { row -> row.toRuntimeCategory() }
    }

    val categoryAliases: List<CategoryAlias> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage20Base}/category_aliases.json",
            deserializer = ListSerializer(CategoryAlias.serializer()),
        )
    }

    val browseNodes: List<BrowseNode> by lazy {
        val stage20BrowseNodes = CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage20Base}/browse_nodes.json",
            deserializer = ListSerializer(SeedBrowseNodeRow.serializer()),
        )
            .map { row -> row.toRuntimeBrowseNode() }
        mergeBrowseNodes(
            stage20BrowseNodes,
            Stage21FashPackageLoader.browseNodes,
        )
    }

    val aliasEntries: List<AliasEntry> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage20Base}/alias_entries.json",
            deserializer = ListSerializer(AliasEntry.serializer()),
        )
    }

    val googleMappings: List<GoogleTaxonomyMapping> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage20Base}/google_taxonomy_mappings.json",
            deserializer = ListSerializer(GoogleTaxonomyMapping.serializer()),
        )
    }

    val facetDefinitions: List<FacetDefinition> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage30Base}/facet_definitions.json",
            deserializer = ListSerializer(SeedFacetDefinitionRow.serializer()),
        )
            .map { row -> row.toRuntimeFacetDefinition() }
    }

    val facetPresets: List<FacetPreset> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage30Base}/facet_presets.json",
            deserializer = ListSerializer(SeedFacetPresetRow.serializer()),
        )
            .map { row -> row.toRuntimeFacetPreset() }
    }

    val facetCollections: List<FacetCollection> by lazy {
        CatalogSeedResourceReader.readJson(
            resourcePath = "${CatalogContractPaths.stage30Base}/facet_collections.json",
            deserializer = ListSerializer(SeedFacetCollectionRow.serializer()),
        )
            .map { row -> row.toRuntimeFacetCollection() }
    }

    val stage40ImmutableSchema: Stage40ImmutableSchemaDocument by lazy {
        Stage40ContractLoader.loadImmutableSchema()
    }

    val stage40NormalizationContract: Stage40NormalizationContractDocument by lazy {
        Stage40ContractLoader.loadNormalizationContract()
    }

    val stage40DedupKeys: Stage40DedupKeysDocument by lazy {
        Stage40ContractLoader.loadDedupKeys()
    }

    val stage40TypedConstraints: Stage40TypedConstraintsDocument by lazy {
        Stage40ContractLoader.loadTypedConstraints()
    }

    private val stage22Packages: List<Stage22PackageData> by lazy {
        GenericStage22PackageLoader.loadAll()
    }

    private val validatedStage22Packages: List<Stage22PackageData> by lazy {
        Stage22SeedValidator().validateOrThrow(
            categories = categories,
            packages = stage22Packages,
        )
        stage22Packages
    }

    init {
        // Fail fast during seed initialization so invalid stage 2.2 data never reaches runtime.
        validatedStage22Packages
    }

    val categoryWriteSpecs: List<CatalogCategoryWriteSpec> by lazy {
        val canonicalCategoriesByCode = categories.associateBy { category -> category.code.trim() }
        val specByCode = LinkedHashMap<String, CatalogCategoryWriteSpec>()
        validatedStage22Packages
            .flatMap { it.profiles }
            .map { resourceProfile ->
                enrichSpecSearchRequiredAttributes(resourceProfile.toCategoryWriteSpec())
            }
            .map { spec ->
                val canonicalCategory = canonicalCategoriesByCode[spec.category.code.trim()]
                if (canonicalCategory == null || canonicalCategory == spec.category) {
                    spec
                } else {
                    spec.copy(category = canonicalCategory)
                }
            }
            .forEach { spec ->
                specByCode.putIfAbsent(spec.category.code, spec)
            }

        val categoryCodes = categories.map { category -> category.code }
        val missingCategoryCodes = categoryCodes.filterNot { code -> specByCode.containsKey(code) }
        check(missingCategoryCodes.isEmpty()) {
            "Missing Stage 2.2 profiles for categories: ${missingCategoryCodes.take(10).joinToString(", ")}"
        }

        val emptySpecCodes = categoryCodes.filter { code ->
            specByCode.getValue(code).attributes.isEmpty()
        }
        check(emptySpecCodes.isEmpty()) {
            "Stage 2.2 profiles must define attributes for each category. Empty profiles: ${emptySpecCodes.take(10).joinToString(", ")}"
        }

        categories.map { category -> specByCode.getValue(category.code) }
    }

    val valueDictionaries: List<AttributeValueDict> by lazy {
        val byAttributeCode = LinkedHashMap<String, AttributeValueDict>()
        validatedStage22Packages
            .flatMap { pkg -> pkg.valueDicts }
            .map(::normalizeDictionary)
            .forEach { dict ->
                val attributeCode = dict.attributeCode.trim()
                if (attributeCode.isEmpty()) return@forEach

                val existing = byAttributeCode[attributeCode]
                if (existing == null) {
                    byAttributeCode[attributeCode] = dict
                } else {
                    check(existing == dict) {
                        "Conflicting Stage 2.2 value dictionaries for attribute '$attributeCode'."
                    }
                }
            }

        CatalogGovernanceCuratedSeed.projectedValueDictionaries()
            .map(::normalizeDictionary)
            .forEach { projected ->
                val attributeCode = projected.attributeCode.trim()
                if (attributeCode.isEmpty()) return@forEach
                val existing = byAttributeCode[attributeCode]
                byAttributeCode[attributeCode] = mergeDictionary(
                    base = existing,
                    projected = projected,
                )
            }

        byAttributeCode.values.toList()
    }

    private fun enrichSpecSearchRequiredAttributes(spec: CatalogCategoryWriteSpec): CatalogCategoryWriteSpec {
        if (spec.attributes.isEmpty() || spec.categoryAttributes.isEmpty()) return spec

        val requiredByCategory = spec.categoryAttributes
            .asSequence()
            .filter { attribute -> attribute.isRequiredForCategory }
            .map { attribute -> normalizeAttributeCode(attribute.attributeCode) }
            .filter { code -> code.isNotEmpty() }
            .toSet()
        if (requiredByCategory.isEmpty()) return spec

        val patchedAttributes = spec.attributes.map { attribute ->
            val normalizedCode = normalizeAttributeCode(attribute.code)
            if (!attribute.requiredForSearch && normalizedCode in requiredByCategory) {
                attribute.copy(requiredForSearch = true)
            } else {
                attribute
            }
        }
        return spec.copy(attributes = patchedAttributes)
    }

    val constraints: List<CatalogConstraints> by lazy {
        val ordered = buildList {
            addAll(GenericStage22PackageLoader.loadGlobalConstraints())
            addAll(validatedStage22Packages.flatMap { it.constraints })
            addAll(projectedCuratedConstraints())
        }
        dedupeConstraints(ordered)
    }

    private fun projectedCuratedConstraints(): List<CatalogConstraints> {
        val snapshot = CatalogGovernanceCuratedSeed.snapshot
        if (snapshot.packs.isEmpty()) return emptyList()

        val brandsByCode = snapshot.packs
            .flatMap { pack -> pack.brands }
            .associateBy { brand -> brand.code }
        val familiesByCode = snapshot.packs
            .flatMap { pack -> pack.families }
            .associateBy { family -> family.code }
        val phoneModels = snapshot.packs
            .flatMap { pack -> pack.models }
            .mapNotNull { model ->
                projectedPhoneModelConstraintSeed(
                    model = model,
                    brandsByCode = brandsByCode,
                    familiesByCode = familiesByCode,
                )
            }
            .distinctBy { model -> model.modelCode }

        if (phoneModels.isEmpty()) return emptyList()

        val valueTypeByAttribute = stage40ImmutableSchema.attributes
            .associate { attribute ->
                normalizeAttributeCode(attribute.attributeCode) to attribute.valueType
            }
        val canonicalValues = snapshot.packs.flatMap { pack -> pack.canonicalValues }

        return buildList {
            addAll(
                projectedPhoneBrandConstraints(
                    phoneModels = phoneModels,
                    canonicalValues = canonicalValues,
                ),
            )
            addAll(
                projectedPhoneModelConstraints(
                    phoneModels = phoneModels,
                    canonicalValues = canonicalValues,
                    valueTypeByAttribute = valueTypeByAttribute,
                ),
            )
        }
            .sortedWith(
                compareBy<CatalogConstraints> { constraint ->
                    when (constraint.scope) {
                        ConstraintScope.GLOBAL -> 0
                        ConstraintScope.CATEGORY -> 1
                        ConstraintScope.BRAND -> 2
                        ConstraintScope.MODEL -> 3
                    }
                }
                    .thenBy { constraint -> constraint.categoryCode.orEmpty() }
                    .thenBy { constraint -> constraint.brand.orEmpty() }
                    .thenBy { constraint -> constraint.model.orEmpty() },
            )
    }

    private fun projectedPhoneBrandConstraints(
        phoneModels: List<ProjectedPhoneModelConstraintSeed>,
        canonicalValues: List<CatalogGovernanceCuratedValueSeed>,
    ): List<CatalogConstraints> {
        return phoneModels
            .associateBy { model -> model.brandCode }
            .values
            .mapNotNull { brand ->
                val attributeConstraints = mutableListOf<AttributeValueConstraint>()
                val osValues = canonicalValues
                    .filter { value ->
                        normalizeAttributeCode(value.attributeCode) == TECH_PHONES_BRAND_CONSTRAINED_ATTRIBUTE &&
                            value.categoryCode.equals(TECH_PHONES_CATEGORY_CODE, ignoreCase = true) &&
                            value.brandCode.equals(brand.brandCode, ignoreCase = true) &&
                            value.familyCode.isNullOrBlank() &&
                            value.modelCode.isNullOrBlank()
                    }
                    .map { value -> value.canonicalCode.trim() }
                    .filter { value -> value.isNotEmpty() }
                    .distinct()
                    .sorted()
                if (osValues.isNotEmpty()) {
                    attributeConstraints += AttributeValueConstraint(
                        attributeCode = "os_family",
                        allowedValues = osValues,
                        reason = "Допустимая ОС для бренда ${brand.brandLabel} в TECH.PHONES.",
                    )
                }
                val modelLineValues = phoneModels
                    .asSequence()
                    .filter { model -> model.brandCode.equals(brand.brandCode, ignoreCase = true) }
                    .mapNotNull { model -> model.modelLine?.trim()?.takeIf { line -> line.isNotEmpty() } }
                    .distinct()
                    .sortedBy { value -> value.lowercase(Locale.ROOT) }
                    .toList()
                if (modelLineValues.isNotEmpty()) {
                    attributeConstraints += AttributeValueConstraint(
                        attributeCode = "model_line",
                        allowedValues = modelLineValues,
                        reason = "Допустимые линейки для бренда ${brand.brandLabel} в TECH.PHONES.",
                    )
                }
                if (attributeConstraints.isEmpty()) return@mapNotNull null

                CatalogConstraints(
                    scope = ConstraintScope.BRAND,
                    categoryCode = TECH_PHONES_CATEGORY_CODE,
                    brand = brand.brandLabel,
                    attributeConstraints = attributeConstraints,
                )
            }
    }

    private fun projectedPhoneModelConstraints(
        phoneModels: List<ProjectedPhoneModelConstraintSeed>,
        canonicalValues: List<CatalogGovernanceCuratedValueSeed>,
        valueTypeByAttribute: Map<String, Stage22ValueType>,
    ): List<CatalogConstraints> {
        return phoneModels.mapNotNull { model ->
            val attributeConstraints = buildList {
                model.modelLine?.let { modelLine ->
                    add(
                        AttributeValueConstraint(
                            attributeCode = "model_line",
                            allowedValues = listOf(modelLine),
                            reason = "Линейка модели ${model.modelLabel} в TECH.PHONES.",
                        ),
                    )
                }
                model.releaseYear?.let { releaseYear ->
                    add(
                        AttributeValueConstraint(
                            attributeCode = "release_year",
                            allowedValues = listOf(releaseYear.toString()),
                            reason = "Год релиза для модели ${model.modelLabel}.",
                        ),
                    )
                }

                TECH_PHONES_MODEL_CONSTRAINED_ATTRIBUTES.forEach { attributeCode ->
                    val allowedValues = canonicalValues
                        .asSequence()
                        .filter { value ->
                            normalizeAttributeCode(value.attributeCode) == attributeCode &&
                                value.categoryCode.equals(TECH_PHONES_CATEGORY_CODE, ignoreCase = true) &&
                                value.brandCode.equals(model.brandCode, ignoreCase = true) &&
                                value.modelCode.equals(model.modelCode, ignoreCase = true)
                        }
                        .mapNotNull { value ->
                            projectedConstraintAllowedValue(
                                attributeCode = attributeCode,
                                value = value,
                                valueTypeByAttribute = valueTypeByAttribute,
                            )
                        }
                        .filter { value -> value.isNotEmpty() }
                        .distinct()
                        .toList()
                        .sortedProjectedConstraintValues(valueTypeByAttribute[attributeCode])
                    if (allowedValues.isEmpty()) return@forEach

                    add(
                        AttributeValueConstraint(
                            attributeCode = attributeCode,
                            allowedValues = allowedValues,
                            reason = "Curated-профиль модели ${model.modelLabel} ограничивает $attributeCode.",
                        ),
                    )
                }
            }

            if (attributeConstraints.isEmpty()) {
                null
            } else {
                CatalogConstraints(
                    scope = ConstraintScope.MODEL,
                    categoryCode = TECH_PHONES_CATEGORY_CODE,
                    brand = model.brandLabel,
                    model = model.modelLabel,
                    attributeConstraints = attributeConstraints,
                )
            }
        }
    }

    private fun projectedPhoneModelConstraintSeed(
        model: CatalogGovernanceCuratedModelSeed,
        brandsByCode: Map<String, CatalogGovernanceCuratedBrandSeed>,
        familiesByCode: Map<String, CatalogGovernanceCuratedProductFamilySeed>,
    ): ProjectedPhoneModelConstraintSeed? {
        val family = model.familyCode?.let { familyCode -> familiesByCode[familyCode] }
        val categoryCode = model.defaultCategoryCode
            ?: family?.defaultCategoryCode
            ?: brandsByCode[model.brandCode]?.primaryCategoryCode
            ?: TECH_PHONES_CATEGORY_CODE
        if (!categoryCode.equals(TECH_PHONES_CATEGORY_CODE, ignoreCase = true)) return null

        val brand = brandsByCode[model.brandCode] ?: return null
        val brandLabel = curatedPreferredLabel(brand.labels, brand.code)
        val familyLabel = family?.let { curatedPreferredLabel(it.labels, it.prettyModelPrefix) }
        return ProjectedPhoneModelConstraintSeed(
            modelCode = model.code,
            brandCode = model.brandCode,
            brandLabel = brandLabel,
            modelLabel = curatedPreferredLabel(model.labels, model.code),
            releaseYear = model.releaseYear,
            modelLine = familyLabel?.let { resolvedFamilyLabel ->
                deriveProjectedPhoneModelLine(
                    modelLabel = curatedPreferredLabel(model.labels, model.code),
                    familyLabel = resolvedFamilyLabel,
                    familyPrettyModelPrefix = family.prettyModelPrefix,
                    brandLabel = brandLabel,
                )
            },
        )
    }

    private fun projectedConstraintAllowedValue(
        attributeCode: String,
        value: CatalogGovernanceCuratedValueSeed,
        valueTypeByAttribute: Map<String, Stage22ValueType>,
    ): String? {
        val valueType = valueTypeByAttribute[attributeCode] ?: return null
        return when (valueType) {
            Stage22ValueType.NUMBER,
            Stage22ValueType.ENUM,
                -> value.canonicalCode.trim().takeIf { code -> code.isNotEmpty() }

            Stage22ValueType.BOOLEAN ->
                value.canonicalCode.trim().lowercase(Locale.ROOT).takeIf { code -> code.isNotEmpty() }

            Stage22ValueType.STRING ->
                curatedDisplayValue(value).takeIf { displayValue -> displayValue.isNotEmpty() }
        }
    }

    private fun curatedDisplayValue(value: CatalogGovernanceCuratedValueSeed): String =
        value.canonicalValue?.trim()?.takeIf { canonicalValue -> canonicalValue.isNotEmpty() }
            ?: value.labels.resolve(locale = value.canonicalLocale ?: "en", fallback = value.canonicalCode)
            ?: value.canonicalCode

    private fun curatedPreferredLabel(
        labels: LocalizedText,
        fallback: String,
    ): String = labels.resolve(locale = "en", fallback = fallback)?.trim()?.takeIf { label -> label.isNotEmpty() } ?: fallback

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
            .removeProjectedPhonePrefix(prefix)
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

    private fun List<String>.sortedProjectedConstraintValues(
        valueType: Stage22ValueType?,
    ): List<String> = when (valueType) {
        Stage22ValueType.NUMBER -> sortedBy { value -> value.toDoubleOrNull() ?: Double.POSITIVE_INFINITY }
        else -> sortedBy { value -> value.lowercase(Locale.ROOT) }
    }

    private fun String.removeProjectedPhonePrefix(prefix: String): String =
        if (prefix.isNotBlank() && startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private fun dedupeConstraints(constraints: List<CatalogConstraints>): List<CatalogConstraints> {
        val deduped = LinkedHashMap<String, CatalogConstraints>()
        constraints.forEach { rawConstraint ->
            val constraint = rawConstraint.normalizedConstraint()
            val key = constraint.identityKey()
            val existing = deduped[key]
            deduped[key] = if (existing == null) {
                constraint
            } else {
                existing.mergeWith(constraint)
            }
        }
        return deduped.values.toList()
    }

    private fun CatalogConstraints.identityKey(): String = buildString {
        append(scope.name)
        append("|")
        append(categoryCode?.trim()?.uppercase().orEmpty())
        append("|")
        append(brand?.trim()?.lowercase().orEmpty())
        append("|")
        append(model?.trim()?.lowercase().orEmpty())
    }

    private fun CatalogConstraints.normalizedConstraint(): CatalogConstraints = copy(
        categoryCode = categoryCode?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT),
        brand = brand?.trim()?.takeIf { it.isNotEmpty() },
        model = model?.trim()?.takeIf { it.isNotEmpty() },
        effectiveFrom = effectiveFrom?.trim()?.takeIf { it.isNotEmpty() },
        effectiveTo = effectiveTo?.trim()?.takeIf { it.isNotEmpty() },
        attributeConstraints = normalizeAttributeConstraints(attributeConstraints),
        compatibilityRules = normalizeCompatibilityRules(compatibilityRules),
    )

    private fun CatalogConstraints.mergeWith(incoming: CatalogConstraints): CatalogConstraints = CatalogConstraints(
        scope = incoming.scope,
        categoryCode = incoming.categoryCode ?: categoryCode,
        brand = incoming.brand ?: brand,
        model = incoming.model ?: model,
        effectiveFrom = incoming.effectiveFrom ?: effectiveFrom,
        effectiveTo = incoming.effectiveTo ?: effectiveTo,
        attributeConstraints = mergeAttributeConstraints(attributeConstraints, incoming.attributeConstraints),
        compatibilityRules = normalizeCompatibilityRules(compatibilityRules + incoming.compatibilityRules),
    )

    private fun normalizeAttributeConstraints(
        constraints: List<AttributeValueConstraint>,
    ): List<AttributeValueConstraint> =
        mergeAttributeConstraints(emptyList(), constraints)

    private fun mergeAttributeConstraints(
        base: List<AttributeValueConstraint>,
        incoming: List<AttributeValueConstraint>,
    ): List<AttributeValueConstraint> {
        val merged = LinkedHashMap<String, AttributeValueConstraint>()
        (base + incoming).forEach { rawConstraint ->
            val constraint = rawConstraint.normalizedConstraint() ?: return@forEach
            val key = normalizeAttributeCode(constraint.attributeCode)
            val existing = merged[key]
            merged[key] = if (existing == null) {
                constraint
            } else {
                existing.mergeWith(constraint)
            }
        }
        return merged.values.sortedBy { constraint -> normalizeAttributeCode(constraint.attributeCode) }
    }

    private fun AttributeValueConstraint.normalizedConstraint(): AttributeValueConstraint? {
        val attributeCode = attributeCode.trim().takeIf { it.isNotEmpty() } ?: return null
        return copy(
            attributeCode = attributeCode,
            allowedValues = allowedValues
                .map { value -> value.trim() }
                .filter { value -> value.isNotEmpty() }
                .distinct(),
            forbiddenValues = forbiddenValues
                .map { value -> value.trim() }
                .filter { value -> value.isNotEmpty() }
                .distinct(),
            reason = reason?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun AttributeValueConstraint.mergeWith(
        incoming: AttributeValueConstraint,
    ): AttributeValueConstraint = AttributeValueConstraint(
        attributeCode = incoming.attributeCode.ifBlank { attributeCode },
        allowedValues = if (incoming.allowedValues.isNotEmpty()) incoming.allowedValues else allowedValues,
        forbiddenValues = if (incoming.forbiddenValues.isNotEmpty()) incoming.forbiddenValues else forbiddenValues,
        reason = incoming.reason?.takeIf { it.isNotEmpty() } ?: reason,
    ).normalizedConstraint() ?: this

    private fun normalizeCompatibilityRules(
        rules: List<com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule>,
    ): List<com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule> =
        rules
            .mapNotNull { rule -> rule.normalizedRule() }
            .distinctBy { rule -> rule.deterministicKey() }
            .sortedBy { rule -> rule.deterministicKey() }

    private fun com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule.normalizedRule():
        com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule? {
        val normalizedWhenAll = whenAll
            .mapNotNull { condition ->
                val attributeCode = condition.attributeCode.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val values = condition.values
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                    .distinct()
                    .sorted()
                if (values.isEmpty()) {
                    null
                } else {
                    AttributeCondition(
                        attributeCode = attributeCode,
                        op = condition.op,
                        values = values,
                    )
                }
            }
            .sortedBy { condition ->
                ConditionKey(
                    attributeCode = condition.attributeCode,
                    op = condition.op.name,
                    values = condition.values,
                ).sortKey()
            }
        val normalizedApply = normalizeAttributeConstraints(apply)
        if (normalizedWhenAll.isEmpty() || normalizedApply.isEmpty()) return null
        return com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule(
            whenAll = normalizedWhenAll,
            apply = normalizedApply,
        )
    }

    private fun com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule.deterministicKey(): String {
        val whenKey = whenAll.joinToString(",") { condition ->
            ConditionKey(
                attributeCode = condition.attributeCode.trim(),
                op = condition.op.name,
                values = condition.values.map { it.trim() }.filter { it.isNotEmpty() }.sorted(),
            ).sortKey()
        }
        val applyKey = apply.joinToString(",") { rule ->
            RuleKey(
                attributeCode = rule.attributeCode.trim(),
                allowed = rule.allowedValues.map { it.trim() }.filter { it.isNotEmpty() }.sorted(),
                forbidden = rule.forbiddenValues.map { it.trim() }.filter { it.isNotEmpty() }.sorted(),
                reason = rule.reason?.trim().orEmpty(),
            ).sortKey()
        }
        return "$whenKey->$applyKey"
    }

    private data class RuleKey(
        val attributeCode: String,
        val allowed: List<String>,
        val forbidden: List<String>,
        val reason: String,
    ) {
        fun sortKey(): String = "$attributeCode|${allowed.joinToString(",")}|${forbidden.joinToString(",")}|$reason"
    }

    private data class ConditionKey(
        val attributeCode: String,
        val op: String,
        val values: List<String>,
    ) {
        fun sortKey(): String = "$attributeCode|$op|${values.joinToString(",")}"
    }

    private fun normalizeAttributeCode(rawCode: String): String =
        rawCode.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')

    private fun normalizeDictionary(dict: AttributeValueDict): AttributeValueDict {
        val attributeCode = dict.attributeCode.trim()
        if (attributeCode.isEmpty()) return dict

        val normalizedEntriesByCode = LinkedHashMap<String, AttributeValueDictEntry>()
        dict.entries.forEach { rawEntry ->
            val canonicalCode = rawEntry.canonicalCode.trim()
            val canonicalValue = rawEntry.canonicalValue.trim()
            if (canonicalCode.isEmpty() || canonicalValue.isEmpty()) return@forEach

            val normalizedEntry = rawEntry.copy(
                canonicalCode = canonicalCode,
                canonicalValue = canonicalValue,
                synonyms = rawEntry.synonyms
                    .map { synonym -> synonym.trim() }
                    .filter { synonym -> synonym.isNotEmpty() }
                    .distinct()
                    .sorted(),
            )
            val existing = normalizedEntriesByCode[canonicalCode]
            if (existing == null) {
                normalizedEntriesByCode[canonicalCode] = normalizedEntry
            } else {
                check(existing == normalizedEntry) {
                    "Conflicting dictionary entries for attribute '$attributeCode' and canonicalCode '$canonicalCode'."
                }
            }
        }

        return dict.copy(
            attributeCode = attributeCode,
            code = dict.code?.trim()?.takeIf { it.isNotEmpty() } ?: attributeCode,
            entries = normalizedEntriesByCode.values
                .sortedBy { entry -> entry.canonicalCode },
        )
    }

    private fun mergeDictionary(
        base: AttributeValueDict?,
        projected: AttributeValueDict,
    ): AttributeValueDict {
        if (base == null) return projected

        val mergedEntriesByCode = LinkedHashMap<String, AttributeValueDictEntry>()
        base.entries.forEach { entry ->
            mergedEntriesByCode[entry.canonicalCode] = entry
        }
        projected.entries.forEach { entry ->
            val existing = mergedEntriesByCode[entry.canonicalCode]
            mergedEntriesByCode[entry.canonicalCode] = if (existing == null) {
                entry
            } else {
                existing.copy(
                    canonicalValue = entry.canonicalValue.ifBlank { existing.canonicalValue },
                    synonyms = (existing.synonyms + entry.synonyms)
                        .map { synonym -> synonym.trim() }
                        .filter { synonym -> synonym.isNotEmpty() }
                        .distinct()
                        .sorted(),
                    rank = maxOf(existing.rank, entry.rank),
                )
            }
        }
        return base.copy(
            code = base.code?.trim()?.takeIf { it.isNotEmpty() } ?: projected.code ?: projected.attributeCode,
            entries = mergedEntriesByCode.values.sortedBy { entry -> entry.canonicalCode },
        )
    }
}

private data class ProjectedPhoneModelConstraintSeed(
    val modelCode: String,
    val brandCode: String,
    val brandLabel: String,
    val modelLabel: String,
    val releaseYear: Int?,
    val modelLine: String? = null,
)

private const val TECH_PHONES_CATEGORY_CODE = "TECH.PHONES"
private const val TECH_PHONES_BRAND_CONSTRAINED_ATTRIBUTE = "os_family"
private val TECH_PHONES_MODEL_CONSTRAINED_ATTRIBUTES = listOf(
    "memory_gb",
    "ram_gb",
    "refresh_rate_hz",
    "color",
    "network_type",
    "chipset_family",
)

private fun mergeBrowseNodes(
    baseNodes: List<BrowseNode>,
    vararg overrideNodeGroups: List<BrowseNode>,
): List<BrowseNode> {
    val merged = LinkedHashMap<String, BrowseNode>()
    fun put(node: BrowseNode) {
        val code = node.browseCode.trim()
        if (code.isEmpty()) return
        val key = code.uppercase(Locale.ROOT)
        val existing = merged[key]
        val normalizedNode = node.copy(
            browseCode = code,
            order = if (existing?.parentBrowseCode.isNullOrBlank() && node.parentBrowseCode.isNullOrBlank()) {
                existing?.order ?: node.order
            } else {
                node.order
            },
        )
        merged[key] = normalizedNode.withEnglishFallbackTitle()
    }

    baseNodes.forEach(::put)
    overrideNodeGroups.forEach { nodes -> nodes.forEach(::put) }
    return merged.values.toList()
}

private fun BrowseNode.withEnglishFallbackTitle(): BrowseNode {
    if (!title["en"].isNullOrBlank()) return this
    val fallbackTitle = title["ru"]
        ?: title.asMap().values.firstOrNull()
        ?: return this
    return copy(title = (title.asMap() + ("en" to fallbackTitle)).toLocalizedText())
}

@Serializable
private data class SeedCategoryRow(
    val code: String,
    val segment: CategorySegment,
    val title: LocalizedText = LocalizedText.Empty,
    val titleRu: String? = null,
    val titleEn: String? = null,
    val parentCode: String? = null,
    val description: String? = null,
    val status: CategoryStatus = CategoryStatus.ACTIVE,
    val replacementCode: String? = null,
) {
    fun toRuntimeCategory(): Category = Category(
        code = code,
        segment = segment,
        title = title.orLegacy(titleRu = titleRu, titleEn = titleEn),
        parentCode = parentCode,
        description = description,
        status = status,
        replacementCode = replacementCode,
    )
}

@Serializable
private data class SeedBrowseNodeRow(
    val browseCode: String,
    val parentBrowseCode: String? = null,
    val nodeKind: BrowseNodeKind = BrowseNodeKind.GROUP,
    val titleKey: String? = null,
    val title: LocalizedText = LocalizedText.Empty,
    val titleRu: String? = null,
    val titleEn: String? = null,
    val targetCategoryCode: String? = null,
    val targetType: BrowseTargetType? = null,
    val order: Int = 0,
    val availabilityScope: String = "ALL",
    val iconKey: String? = null,
    val analyticsKey: String? = null,
    val searchKeywordsRu: List<String> = emptyList(),
    val status: BrowseNodeStatus = BrowseNodeStatus.ACTIVE,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
) {
    fun toRuntimeBrowseNode(): BrowseNode = BrowseNode(
        browseCode = browseCode,
        parentBrowseCode = parentBrowseCode,
        nodeKind = nodeKind,
        titleKey = titleKey,
        title = title.orLegacy(titleRu = titleRu, titleEn = titleEn),
        targetCategoryCode = targetCategoryCode,
        targetType = targetType,
        order = order,
        availabilityScope = availabilityScope,
        iconKey = iconKey,
        analyticsKey = analyticsKey,
        searchKeywordsRu = searchKeywordsRu,
        status = status,
        tags = tags,
        notes = notes,
    )
}

@Serializable
private data class SeedFacetDefinitionRow(
    val facetKey: String,
    val title: LocalizedText = LocalizedText.Empty,
    val titleRu: String? = null,
    val titleEn: String? = null,
    val valueType: FacetDataType,
    val appliesToCategoryCodes: List<String>,
    val attributeCode: String? = null,
    val dictionaryCode: String? = null,
    val selectionMode: FacetSelectionMode? = null,
    val uiWidget: FacetUiWidget? = null,
    val normalizationRef: String? = null,
    val source: FacetValueSource = FacetValueSource.OFFER,
    val effectiveFrom: String? = null,
    val effectiveTo: String? = null,
    val ui: FacetUiConfig = FacetUiConfig(),
) {
    fun toRuntimeFacetDefinition(): FacetDefinition = FacetDefinition(
        facetKey = facetKey,
        title = title.orLegacy(titleRu = titleRu, titleEn = titleEn),
        valueType = valueType,
        appliesToCategoryCodes = appliesToCategoryCodes,
        attributeCode = attributeCode,
        dictionaryCode = dictionaryCode,
        selectionMode = selectionMode,
        uiWidget = uiWidget,
        normalizationRef = normalizationRef,
        source = source,
        effectiveFrom = effectiveFrom,
        effectiveTo = effectiveTo,
        ui = ui,
    )
}

@Serializable
private data class SeedFacetPresetRow(
    val presetCode: String,
    val categoryCode: String,
    val title: LocalizedText = LocalizedText.Empty,
    val titleRu: String? = null,
    val titleEn: String? = null,
    val order: Int = 0,
    val effectiveFrom: String? = null,
    val effectiveTo: String? = null,
    val rules: List<FacetPresetRule> = emptyList(),
    val notes: String? = null,
) {
    fun toRuntimeFacetPreset(): FacetPreset = FacetPreset(
        presetCode = presetCode,
        categoryCode = categoryCode,
        title = title.orLegacy(titleRu = titleRu, titleEn = titleEn),
        order = order,
        effectiveFrom = effectiveFrom,
        effectiveTo = effectiveTo,
        rules = rules,
        notes = notes,
    )
}

@Serializable
private data class SeedFacetCollectionRow(
    val collectionCode: String,
    val categoryCode: String,
    val title: LocalizedText = LocalizedText.Empty,
    val titleRu: String? = null,
    val titleEn: String? = null,
    val browseCode: String? = null,
    val presetCode: String? = null,
    val order: Int = 0,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
) {
    fun toRuntimeFacetCollection(): FacetCollection = FacetCollection(
        collectionCode = collectionCode,
        categoryCode = categoryCode,
        title = title.orLegacy(titleRu = titleRu, titleEn = titleEn),
        browseCode = browseCode,
        presetCode = presetCode,
        order = order,
        tags = tags,
        notes = notes,
    )
}

private fun LocalizedText.orLegacy(
    titleRu: String?,
    titleEn: String?,
): LocalizedText =
    if (!isBlank()) this else localizedTextOf("ru" to titleRu, "en" to titleEn)
