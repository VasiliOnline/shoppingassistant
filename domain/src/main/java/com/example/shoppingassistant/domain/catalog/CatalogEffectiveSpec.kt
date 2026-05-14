package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.domain.i18n.LocalizedText
import com.example.shoppingassistant.domain.i18n.localizedTextOf
import com.example.shoppingassistant.domain.i18n.toLocalizedText
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
enum class CatalogAttributeWidgetHint {
    INPUT,
    CHIPS,
    DROPDOWN,
    TOGGLE,
    RANGE_INPUT,
}

@Serializable
enum class CatalogCategoryReadiness {
    READY,
    BETA,
    INTERNAL,
}

@Serializable
data class CatalogValueOption(
    val valueCode: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val aliases: List<String> = emptyList(),
    val rank: Int = 0,
)

@Serializable
data class CatalogAttributeSpec(
    val code: String,
    val title: String,
    val labels: LocalizedText = LocalizedText.Empty,
    val role: CatalogAttributeRole = CatalogAttributeRole.T2_ADVANCED,
    val usageScopes: List<CatalogAttributeUsageScope> = emptyList(),
    val facetTemplateCode: String? = null,
    val dataType: AttributeDataType,
    val valueType: Stage22ValueType,
    val valueSetType: Stage22ValueSetType,
    val uiOrder: Int = 0,
    val requiredForCategory: Boolean = false,
    val requiredForSearch: Boolean = false,
    val requiredForOffer: Boolean = false,
    val requiredForExpress: Boolean = false,
    val requiredBy: String? = null,
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
    val widgetHint: CatalogAttributeWidgetHint = CatalogAttributeWidgetHint.INPUT,
    val options: List<CatalogValueOption> = emptyList(),
)

@Serializable
data class CatalogEffectiveSpecMeta(
    val source: String = "profile+registry+stage4+constraints",
    val archetypes: List<CategoryArchetype> = emptyList(),
    val identityAttributeCodes: List<String> = emptyList(),
    val facetAttributeCodes: List<String> = emptyList(),
    val hasTypedConstraints: Boolean = false,
    val hasDictionaries: Boolean = false,
    val completenessGatePassed: Boolean = false,
    val editorialReadiness: CatalogCategoryReadiness = CatalogCategoryReadiness.INTERNAL,
    val operationalReadiness: CatalogCategoryReadiness = CatalogCategoryReadiness.INTERNAL,
    val editorialBlockingIssues: List<String> = emptyList(),
    val operationalBlockingIssues: List<String> = emptyList(),
    val operationalSampleCount: Int = 0,
    val operationalDroppedRate: Double = 0.0,
    val operationalUnknownAttributeRate: Double = 0.0,
    val operationalRequiredMissingRate: Double = 0.0,
    val operationalLowConfidenceRate: Double = 0.0,
    val readinessBlockingIssues: List<String> = emptyList(),
)

@Serializable
data class CatalogCategoryEffectiveSpec(
    val category: Category,
    val readiness: CatalogCategoryReadiness,
    val attributes: List<CatalogAttributeSpec>,
    val systemAttributes: List<CatalogAttributeSpec> = emptyList(),
    val requiredIfRules: List<RequiredIfRule> = emptyList(),
    val constraints: List<CatalogConstraints> = emptyList(),
    val meta: CatalogEffectiveSpecMeta = CatalogEffectiveSpecMeta(),
)

fun CatalogCategoryWriteSpec.toCategoryEffectiveSpec(
    constraints: List<CatalogConstraints> = emptyList(),
): CatalogCategoryEffectiveSpec = buildCategoryEffectiveSpec(
    category = category,
    attributes = attributes,
    categoryAttributes = categoryAttributes,
    valueDictionaries = mergeEffectiveSpecValueDictionaries(
        global = CatalogSeed.valueDictionaries,
        local = valueDictionaries,
    ),
    requiredIfRules = requiredIfRules,
    constraints = constraints,
)

private fun mergeEffectiveSpecValueDictionaries(
    global: List<AttributeValueDict>,
    local: List<AttributeValueDict>,
): List<AttributeValueDict> {
    val mergedByAttribute = LinkedHashMap<String, AttributeValueDict>()
    global.forEach { dictionary ->
        val attributeCode = normalizeCatalogAttributeCode(dictionary.attributeCode)
        if (attributeCode.isNotEmpty()) {
            mergedByAttribute[attributeCode] = dictionary
        }
    }
    local.forEach { dictionary ->
        val attributeCode = normalizeCatalogAttributeCode(dictionary.attributeCode)
        if (attributeCode.isEmpty()) return@forEach
        val existing = mergedByAttribute[attributeCode]
        mergedByAttribute[attributeCode] = when {
            existing == null -> dictionary
            else -> mergeEffectiveSpecValueDictionary(existing, dictionary)
        }
    }
    return mergedByAttribute.values.toList()
}

private fun mergeEffectiveSpecValueDictionary(
    base: AttributeValueDict,
    overlay: AttributeValueDict,
): AttributeValueDict {
    val mergedEntries = LinkedHashMap<String, AttributeValueDictEntry>()
    base.entries.forEach { entry ->
        mergedEntries[entry.canonicalCode] = entry
    }
    overlay.entries.forEach { entry ->
        val existing = mergedEntries[entry.canonicalCode]
        mergedEntries[entry.canonicalCode] = if (existing == null) {
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
    return AttributeValueDict(
        attributeCode = overlay.attributeCode.ifBlank { base.attributeCode },
        code = overlay.code ?: base.code,
        entries = mergedEntries.values.sortedBy { entry -> entry.canonicalCode },
    )
}

private fun buildCategoryEffectiveSpec(
    category: Category,
    attributes: List<AttributeDef>,
    categoryAttributes: List<CategoryAttribute>,
    valueDictionaries: List<AttributeValueDict>,
    requiredIfRules: List<RequiredIfRule>,
    constraints: List<CatalogConstraints>,
): CatalogCategoryEffectiveSpec {
    val registry = Stage22RegistryLoader.loadSnapshot()
    val immutableByCode = stage40ImmutableByCode()
    val typedByCode = stage40TypedByCode()
    val attributeDefsByCode = attributes.associateBy { normalizeCatalogAttributeCode(it.code) }
    val categoryAttrsByCode = categoryAttributes.associateBy { normalizeCatalogAttributeCode(it.attributeCode) }
    val dictByCode = valueDictionaries.associateBy { normalizeCatalogAttributeCode(it.attributeCode) }

    val orderedCodes = buildList {
        categoryAttributes
            .sortedWith(
                compareBy<CategoryAttribute> { it.uiOrder }
                    .thenBy { normalizeCatalogAttributeCode(it.attributeCode) },
            )
            .forEach { add(it.attributeCode) }
        attributes.forEach { def ->
            if (none { existing -> existing.equals(def.code, ignoreCase = true) }) {
                add(def.code)
            }
        }
        defaultSystemAttributeCodes.forEach { systemCode ->
            if (none { existing -> existing.equals(systemCode, ignoreCase = true) }) {
                add(systemCode)
            }
        }
    }
    val systemUiOrderBase = (categoryAttributes.maxOfOrNull { it.uiOrder } ?: 0) + 1000

    val allAttributeSpecs = orderedCodes
        .mapNotNull { rawCode ->
            val normalizedCode = normalizeCatalogAttributeCode(rawCode)
            if (normalizedCode.isEmpty()) return@mapNotNull null

            val categoryAttribute = categoryAttrsByCode[normalizedCode]
            val attributeDef = attributeDefsByCode[normalizedCode]
            val registryDef = registry.attributes[normalizedCode]
            val immutable = immutableByCode[normalizedCode]
            val typed = typedByCode[normalizedCode]
            val dict = dictByCode[normalizedCode]
            val registryDictionary = registry.dictionaries[normalizedCode]
            val systemDefaults = systemAttributeDefaults(
                normalizedCode = normalizedCode,
                uiOrderBase = systemUiOrderBase,
            )
            val options = resolveCatalogValueOptions(dict, registryDictionary).ifEmpty {
                systemDefaults?.options.orEmpty()
            }
            if (
                categoryAttribute == null &&
                attributeDef == null &&
                registryDef == null &&
                immutable == null &&
                typed == null &&
                dict == null &&
                registryDictionary == null &&
                systemDefaults == null
            ) {
                return@mapNotNull null
            }

            val title = attributeDef?.title
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: systemDefaults?.title
                ?: registryDef?.labels?.toLocalizedText()?.let(::preferredCatalogLabel)
                ?: rawCode.trim()
            val labels = systemDefaults?.labels ?: registryDef?.labels?.toLocalizedText() ?: LocalizedText.Empty
            val dataType = attributeDef?.dataType ?: systemDefaults?.dataType ?: inferCatalogDataType(
                valueType = immutable?.valueType ?: typed?.valueType ?: registryDef?.valueType ?: systemDefaults?.valueType,
                hasOptions = options.isNotEmpty(),
            )
            val valueType = immutable?.valueType ?: typed?.valueType ?: registryDef?.valueType ?: systemDefaults?.valueType ?: inferCatalogValueType(dataType)
            val valueSetType =
                immutable?.valueSetType ?: registryDef?.valueSetType ?: systemDefaults?.valueSetType ?: inferCatalogValueSetType(options.isNotEmpty())
            val requiredForCategory = categoryAttribute?.isRequiredForCategory == true
            val requiredForSearch =
                attributeDef?.requiredForSearch == true || requiredForCategory || systemDefaults?.requiredForSearch == true
            val requiredForOffer =
                attributeDef?.requiredForOffer == true || requiredForCategory || systemDefaults?.requiredForOffer == true
            val requiredForExpress =
                attributeDef?.requiredForExpress == true || requiredForCategory || systemDefaults?.requiredForExpress == true
            val facetEnabled =
                attributeDef?.facetEnabled == true || immutable?.isFacet == true || registryDef?.isFacet == true || systemDefaults?.facetEnabled == true
            val isIdentity =
                immutable?.isIdentity == true || registryDef?.isIdentity == true || systemDefaults?.isIdentity == true || normalizedCode in defaultIdentityAttributeCodes
            val isFacet = immutable?.isFacet == true || registryDef?.isFacet == true || attributeDef?.facetEnabled == true || systemDefaults?.isFacet == true
            val widgetHint = systemDefaults?.widgetHint ?: inferWidgetHint(
                dataType = dataType,
                valueType = valueType,
                options = options,
                minValue = typed?.minValue,
                maxValue = typed?.maxValue,
            )

            CatalogAttributeSpec(
                code = attributeDef?.code ?: rawCode.trim(),
                title = title,
                labels = labels,
                role = inferAttributeRole(
                    normalizedCode = normalizedCode,
                    isIdentity = isIdentity,
                    requiredForCategory = requiredForCategory,
                    requiredForSearch = requiredForSearch,
                    requiredForOffer = requiredForOffer,
                    requiredForExpress = requiredForExpress,
                ),
                usageScopes = inferAttributeUsageScopes(
                    normalizedCode = normalizedCode,
                    isIdentity = isIdentity,
                    isFacet = isFacet || facetEnabled,
                    requiredForCategory = requiredForCategory,
                    requiredForSearch = requiredForSearch,
                    requiredForOffer = requiredForOffer,
                    requiredForExpress = requiredForExpress,
                ),
                facetTemplateCode = inferFacetTemplateCode(
                    normalizedCode = normalizedCode,
                    valueType = valueType,
                    isFacet = isFacet || facetEnabled,
                    requiredForCategory = requiredForCategory,
                ),
                dataType = dataType,
                valueType = valueType,
                valueSetType = valueSetType,
                uiOrder = categoryAttribute?.uiOrder ?: systemDefaults?.uiOrder ?: Int.MAX_VALUE,
                requiredForCategory = requiredForCategory,
                requiredForSearch = requiredForSearch,
                requiredForOffer = requiredForOffer,
                requiredForExpress = requiredForExpress,
                requiredBy = attributeDef?.requiredBy,
                facetEnabled = facetEnabled,
                multiValued = attributeDef?.multiValued == true || systemDefaults?.multiValued == true,
                valueDictCode = attributeDef?.valueDictCode ?: dict?.code ?: registryDictionary?.attributeCode ?: systemDefaults?.valueDictCode,
                unit = immutable?.unit ?: registryDef?.unit ?: systemDefaults?.unit,
                normalization = immutable?.normalization ?: registryDef?.normalization ?: systemDefaults?.normalization,
                isIdentity = isIdentity,
                isFacet = isFacet,
                dictionaryRequired = immutable?.dictionaryRequired ?: systemDefaults?.dictionaryRequired ?: (valueSetType != Stage22ValueSetType.OPEN),
                enumOnly = typed?.enumOnly == true || systemDefaults?.enumOnly == true,
                regexPattern = typed?.regex ?: systemDefaults?.regexPattern,
                minValue = typed?.minValue ?: systemDefaults?.minValue,
                maxValue = typed?.maxValue ?: systemDefaults?.maxValue,
                widgetHint = widgetHint,
                options = options,
            )
        }
        .sortedWith(
            compareBy<CatalogAttributeSpec> { it.uiOrder }
                .thenBy { normalizeCatalogAttributeCode(it.code) },
        )
    val systemAttributeSpecs = allAttributeSpecs.filter { attribute ->
        normalizeCatalogAttributeCode(attribute.code) in defaultSystemAttributeCodeSet
    }
    val productAttributeSpecs = allAttributeSpecs.filterNot { attribute ->
        normalizeCatalogAttributeCode(attribute.code) in defaultSystemAttributeCodeSet
    }

    val editorialReadinessGate = evaluateCategoryReadiness(
        category = category,
        attributes = allAttributeSpecs,
        categoryAttributes = categoryAttributes,
        constraints = constraints,
    )
    val identityAttributeCodes = allAttributeSpecs
        .asSequence()
        .filter { it.isIdentity }
        .map { it.code }
        .distinct()
        .sortedBy { it.lowercase(Locale.ROOT) }
        .toList()
    val facetAttributeCodes = allAttributeSpecs
        .asSequence()
        .filter { it.isFacet || it.facetEnabled }
        .map { it.code }
        .distinct()
        .sortedBy { it.lowercase(Locale.ROOT) }
        .toList()

    return CatalogCategoryEffectiveSpec(
        category = category,
        readiness = editorialReadinessGate.readiness,
        attributes = productAttributeSpecs,
        systemAttributes = systemAttributeSpecs,
        requiredIfRules = requiredIfRules.sortedWith(
            compareBy<RequiredIfRule> { normalizeCatalogAttributeCode(it.requiredAttributeCode) }
                .thenBy { rule ->
                    rule.whenAll.joinToString("|") { condition ->
                        normalizeCatalogAttributeCode(condition.attributeCode) +
                            ":" + condition.op.name +
                            ":" + condition.values.joinToString(",") { value -> value.trim().lowercase(Locale.ROOT) }
                    }
                },
        ),
        constraints = constraints,
        meta = CatalogEffectiveSpecMeta(
            archetypes = CatalogPackV2RegistryLoader.archetypesFor(category.code),
            identityAttributeCodes = identityAttributeCodes,
            facetAttributeCodes = facetAttributeCodes,
            hasTypedConstraints = allAttributeSpecs.any { spec ->
                spec.enumOnly || spec.regexPattern != null || spec.minValue != null || spec.maxValue != null
            },
            hasDictionaries = allAttributeSpecs.any { it.options.isNotEmpty() },
            completenessGatePassed = editorialReadinessGate.readiness == CatalogCategoryReadiness.READY,
            editorialReadiness = editorialReadinessGate.readiness,
            operationalReadiness = defaultOperationalReadinessFor(editorialReadinessGate.readiness),
            editorialBlockingIssues = editorialReadinessGate.blockingIssues,
            readinessBlockingIssues = editorialReadinessGate.blockingIssues,
        ),
    )
}

fun CatalogCategoryEffectiveSpec.allAttributes(): List<CatalogAttributeSpec> =
    (attributes + systemAttributes)
        .sortedWith(
            compareBy<CatalogAttributeSpec> { it.uiOrder }
                .thenBy { normalizeCatalogAttributeCode(it.code) },
        )

private data class CatalogReadinessGateResult(
    val readiness: CatalogCategoryReadiness,
    val blockingIssues: List<String>,
)

private fun defaultOperationalReadinessFor(
    editorialReadiness: CatalogCategoryReadiness,
): CatalogCategoryReadiness = when (editorialReadiness) {
    CatalogCategoryReadiness.INTERNAL -> CatalogCategoryReadiness.INTERNAL
    else -> CatalogCategoryReadiness.READY
}

private data class CatalogSystemAttributeDefaults(
    val title: String,
    val labels: LocalizedText,
    val dataType: AttributeDataType,
    val valueType: Stage22ValueType,
    val valueSetType: Stage22ValueSetType,
    val uiOrder: Int,
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
)

private val defaultIdentityAttributeCodes = setOf(
    "brand",
    "model",
    "model_line",
    "product_name",
)

private val systemAttributeRegistry by lazy { CatalogSystemAttributeRegistryLoader.load() }

private val defaultSystemAttributeCodes = systemAttributeRegistry.attributesByCode.keys.toList()

private val defaultSystemAttributeCodeSet = defaultSystemAttributeCodes.toSet()

private val readinessOfferSignalIgnoredAttributeCodes = systemAttributeRegistry.ignoredForOfferSignalCodes

private val adminOnlyAttributeCodes = setOf(
    "apparel_type_group",
    "authenticity_status",
    "authenticity_state",
    "cabin_size_status",
    "canonical_product_key",
    "compatible_model_text",
    "compatibility_confidence",
    "data_quality_score",
    "defect_notes",
    "dedup_fingerprint",
    "device_identity_key",
    "evidence_policy",
    "evidence_level",
    "extraction_warnings",
    "identity_candidate_count",
    "identity_debug_trace",
    "identity_last_resolved_at",
    "identity_resolution_version",
    "image_embedding_id",
    "imei_hash",
    "listing_completeness_confidence",
    "model_name_text_raw",
    "normalization_source",
    "package_text",
    "raw_barcode_payload",
    "raw_model_candidates",
    "raw_ocr_spec_tokens",
    "raw_ocr_identity_tokens",
    "raw_source_spec_tokens",
    "raw_spec_tokens",
    "raw_title_spec_tokens",
    "raw_title_identity_tokens",
    "raw_size_text",
    "route_guardrail_trace",
    "serial_number_masked",
    "source_locale",
    "source_market",
    "source_evidence_fingerprint",
    "source_page_metadata",
    "source_url_identity_tokens",
    "normalized_spec_key",
    "spec_candidate_count",
    "spec_conflict_set",
    "spec_fingerprint",
    "spec_last_verified_at",
    "spec_review_reason",
    "spec_review_required",
    "spec_runtime_version",
    "spec_unit_parse_notes",
    "title_embedding_id",
    "unit_parser_version",
    "visual_evidence_flags",
    "vision_confidence",
    "visible_label_text",
)

private val identityCoreAttributeCodes = setOf(
    "identity_confidence",
    "identity_resolution_status",
)

private val specsCoreAttributeCodes = setOf(
    "measurement_system",
    "primary_spec_summary",
    "spec_completeness_score",
    "spec_confidence",
    "spec_evidence_level",
    "spec_profile_status",
    "spec_source",
    "unit_normalization_status",
)

private fun stage40ImmutableByCode(): Map<String, Stage40ImmutableAttribute> =
    CatalogSeed.stage40ImmutableSchema.attributes.associateBy { normalizeCatalogAttributeCode(it.attributeCode) }

private fun stage40TypedByCode(): Map<String, Stage40TypedConstraint> =
    CatalogSeed.stage40TypedConstraints.constraints.associateBy { normalizeCatalogAttributeCode(it.attributeCode) }

private fun normalizeCatalogAttributeCode(rawCode: String): String =
    rawCode.trim().lowercase(Locale.ROOT)

private fun preferredCatalogLabel(labels: LocalizedText): String? =
    labels.resolve(locale = "ru")?.trim()?.takeIf { it.isNotEmpty() }

private fun inferCatalogDataType(
    valueType: Stage22ValueType?,
    hasOptions: Boolean,
): AttributeDataType = when (valueType) {
    Stage22ValueType.BOOLEAN -> AttributeDataType.BOOL
    Stage22ValueType.NUMBER -> AttributeDataType.DECIMAL
    Stage22ValueType.ENUM -> AttributeDataType.ENUM
    Stage22ValueType.STRING -> if (hasOptions) AttributeDataType.ENUM else AttributeDataType.STRING
    null -> if (hasOptions) AttributeDataType.ENUM else AttributeDataType.STRING
}

private fun inferCatalogValueType(dataType: AttributeDataType): Stage22ValueType = when (dataType) {
    AttributeDataType.BOOL -> Stage22ValueType.BOOLEAN
    AttributeDataType.INT,
    AttributeDataType.DECIMAL,
        -> Stage22ValueType.NUMBER

    AttributeDataType.ENUM -> Stage22ValueType.ENUM
    AttributeDataType.STRING,
    AttributeDataType.STRING_LIST,
        -> Stage22ValueType.STRING
}

private fun inferCatalogValueSetType(hasOptions: Boolean): Stage22ValueSetType =
    if (hasOptions) Stage22ValueSetType.CLOSED else Stage22ValueSetType.OPEN

private fun inferWidgetHint(
    dataType: AttributeDataType,
    valueType: Stage22ValueType,
    options: List<CatalogValueOption>,
    minValue: Double?,
    maxValue: Double?,
): CatalogAttributeWidgetHint = when {
    dataType == AttributeDataType.BOOL || valueType == Stage22ValueType.BOOLEAN ->
        CatalogAttributeWidgetHint.TOGGLE

    valueType == Stage22ValueType.NUMBER && (minValue != null || maxValue != null) ->
        CatalogAttributeWidgetHint.RANGE_INPUT

    options.isNotEmpty() && options.size <= 6 ->
        CatalogAttributeWidgetHint.CHIPS

    options.isNotEmpty() ->
        CatalogAttributeWidgetHint.DROPDOWN

    else ->
        CatalogAttributeWidgetHint.INPUT
}

private fun inferAttributeRole(
    normalizedCode: String,
    isIdentity: Boolean,
    requiredForCategory: Boolean,
    requiredForSearch: Boolean,
    requiredForOffer: Boolean,
    requiredForExpress: Boolean,
): CatalogAttributeRole = when {
    normalizedCode in adminOnlyAttributeCodes -> CatalogAttributeRole.T3_SYSTEM_HIDDEN
    normalizedCode in defaultSystemAttributeCodeSet -> CatalogAttributeRole.T0_CORE
    normalizedCode in identityCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in specsCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in typeCriticalAttributeCodes -> CatalogAttributeRole.T1_TYPE_CRITICAL
    isIdentity -> CatalogAttributeRole.T0_CORE
    requiredForCategory || requiredForSearch || requiredForOffer || requiredForExpress -> CatalogAttributeRole.T1_TYPE_CRITICAL
    else -> CatalogAttributeRole.T2_ADVANCED
}

private fun inferAttributeUsageScopes(
    normalizedCode: String,
    isIdentity: Boolean,
    isFacet: Boolean,
    requiredForCategory: Boolean,
    requiredForSearch: Boolean,
    requiredForOffer: Boolean,
    requiredForExpress: Boolean,
): List<CatalogAttributeUsageScope> {
    if (normalizedCode in adminOnlyAttributeCodes) {
        return listOf(
            CatalogAttributeUsageScope.MATCHING_ONLY,
            CatalogAttributeUsageScope.AI_EXTRACTION_ONLY,
            CatalogAttributeUsageScope.ADMIN_REVIEW_ONLY,
        )
    }

    val scopes = linkedSetOf<CatalogAttributeUsageScope>()
    if (requiredForCategory || requiredForSearch || requiredForOffer || requiredForExpress) {
        scopes += CatalogAttributeUsageScope.USER_VISIBLE_REQUIRED
    } else {
        scopes += CatalogAttributeUsageScope.USER_VISIBLE_OPTIONAL
    }
    if (isFacet) {
        scopes += if (requiredForCategory || requiredForSearch || requiredForOffer || requiredForExpress) {
            CatalogAttributeUsageScope.PRIMARY_FACET
        } else {
            CatalogAttributeUsageScope.SECONDARY_FACET
        }
    }
    if (isIdentity) {
        scopes += CatalogAttributeUsageScope.MATCHING_ONLY
    }
    return scopes.toList()
}

private fun inferFacetTemplateCode(
    normalizedCode: String,
    valueType: Stage22ValueType,
    isFacet: Boolean,
    requiredForCategory: Boolean,
): String? = when {
    normalizedCode in setOf("brand", "compatible_phone_brand") -> "brand_optional"
    normalizedCode in setOf("price", "condition") -> "condition_price_base"
    normalizedCode.contains("compatib") || normalizedCode == "used_for" -> "compatibility_widget"
    normalizedCode.contains("allergen") || normalizedCode.contains("ingredient") -> "ingredient/allergen_warning"
    normalizedCode.contains("life_stage") ||
        normalizedCode in setOf("age_group", "age_from_months", "age_to_months", "school_grade_group") -> "life_stage_selector"
    normalizedCode.contains("size") ||
        normalizedCode.contains("length") ||
        normalizedCode in setOf("height_cm", "child_height_cm", "foot_length_cm", "insole_length_cm") -> "size_selector"
    valueType == Stage22ValueType.NUMBER && isFacet -> "numeric_range"
    valueType == Stage22ValueType.NUMBER -> "spec_range"
    isFacet && requiredForCategory -> "enum_primary"
    isFacet -> "enum_secondary"
    else -> null
}

private val typeCriticalAttributeCodes = setOf(
    "accessory_type",
    "apparel_type",
    "bag_type",
    "battery_capacity_mah",
    "battery_capacity_wh",
    "bluetooth_version",
    "chipset_text",
    "connector_type",
    "dimensions",
    "ean",
    "form_factor",
    "generation",
    "gtin",
    "identity_context_role",
    "identity_evidence_level",
    "identity_evidence_types",
    "identity_review_required",
    "identity_source",
    "model_aliases",
    "model_granularity",
    "model_number",
    "mpn",
    "network_generation",
    "os_family",
    "power_source",
    "power_watts",
    "ram_capacity",
    "refresh_rate_hz",
    "region_variant",
    "screen_resolution",
    "screen_size",
    "series",
    "shoe_type",
    "storage_capacity",
    "type_of_item",
    "upc",
    "variant_label",
    "water_resistance",
    "weight",
    "wi_fi_standard",
    "wireless_standard",
)

private fun resolveCatalogValueOptions(
    dictionary: AttributeValueDict?,
    registryDictionary: Stage22ValueDictionary?,
): List<CatalogValueOption> {
    val seeded = dictionary
        ?.entries
        .orEmpty()
        .sortedWith(
            compareByDescending<AttributeValueDictEntry> { it.rank }
                .thenBy { entry -> entry.canonicalValue.lowercase(Locale.ROOT) },
        )
        .map { entry ->
            CatalogValueOption(
                valueCode = entry.canonicalCode,
                labels = localizedTextOf("und" to entry.canonicalValue),
                aliases = entry.synonyms,
                rank = entry.rank,
            )
        }
    if (seeded.isNotEmpty()) return seeded

    return registryDictionary
        ?.entries
        .orEmpty()
        .sortedBy { entry -> entry.valueCode.lowercase(Locale.ROOT) }
        .map { entry ->
            CatalogValueOption(
                valueCode = entry.valueCode,
                labels = entry.labels.toLocalizedText(),
                aliases = entry.aliases,
            )
}
}

private fun systemAttributeDefaults(
    normalizedCode: String,
    uiOrderBase: Int,
): CatalogSystemAttributeDefaults? =
    systemAttributeRegistry.attributesByCode[normalizedCode]?.toSystemAttributeDefaults(uiOrderBase)

private fun CatalogSystemAttributeRegistryEntry.toSystemAttributeDefaults(
    uiOrderBase: Int,
): CatalogSystemAttributeDefaults = CatalogSystemAttributeDefaults(
    title = title,
    labels = labels,
    dataType = dataType,
    valueType = valueType,
    valueSetType = valueSetType,
    uiOrder = uiOrderBase + uiOrderOffset,
    requiredForSearch = requiredForSearch,
    requiredForOffer = requiredForOffer,
    requiredForExpress = requiredForExpress,
    facetEnabled = facetEnabled,
    multiValued = multiValued,
    valueDictCode = valueDictCode,
    unit = unit,
    normalization = normalization,
    isIdentity = isIdentity,
    isFacet = isFacet,
    dictionaryRequired = dictionaryRequired,
    enumOnly = enumOnly,
    regexPattern = regexPattern,
    minValue = minValue,
    maxValue = maxValue,
    widgetHint = widgetHint,
    options = options,
)

private fun evaluateCategoryReadiness(
    category: Category,
    attributes: List<CatalogAttributeSpec>,
    categoryAttributes: List<CategoryAttribute>,
    constraints: List<CatalogConstraints>,
): CatalogReadinessGateResult {
    if (category.status != CategoryStatus.ACTIVE) {
        return CatalogReadinessGateResult(
            readiness = CatalogCategoryReadiness.INTERNAL,
            blockingIssues = listOf("category_not_active"),
        )
    }

    val issues = mutableListOf<String>()
    if (attributes.isEmpty()) {
        issues += "missing_attributes"
    }
    if (categoryAttributes.isEmpty()) {
        issues += "missing_category_attribute_bindings"
    }
    if (attributes.none { it.isIdentity }) {
        issues += "missing_identity_attributes"
    }
    if (attributes.none { it.requiredForSearch }) {
        issues += "missing_search_required_attributes"
    }
    if (attributes.none { attribute ->
            normalizeCatalogAttributeCode(attribute.code) !in readinessOfferSignalIgnoredAttributeCodes &&
                (attribute.requiredForOffer || attribute.requiredForExpress || attribute.requiredForCategory)
        }
    ) {
        issues += "missing_offer_required_attributes"
    }

    val missingDictionaryOptions = attributes
        .asSequence()
        .filter { attribute ->
            attribute.dictionaryRequired || attribute.valueSetType != Stage22ValueSetType.OPEN
        }
        .filter { attribute -> attribute.options.isEmpty() }
        .map { attribute -> attribute.code }
        .sortedBy { it.lowercase(Locale.ROOT) }
        .toList()
    if (missingDictionaryOptions.isNotEmpty()) {
        issues += "missing_dictionary_options:${missingDictionaryOptions.joinToString(",")}"
    }

    if (isLeafCategory(category.code) && constraints.none { constraint ->
            constraint.scope == ConstraintScope.CATEGORY &&
                constraint.categoryCode?.equals(category.code, ignoreCase = true) == true
        }
    ) {
        issues += "missing_category_constraints"
    }

    return CatalogReadinessGateResult(
        readiness = if (issues.isEmpty()) CatalogCategoryReadiness.READY else CatalogCategoryReadiness.BETA,
        blockingIssues = issues,
    )
}

private fun isLeafCategory(categoryCode: String): Boolean {
    val normalizedCategoryCode = categoryCode.trim()
    if (normalizedCategoryCode.isEmpty()) return false
    return CatalogSeed.categories.none { category ->
        category.parentCode?.equals(normalizedCategoryCode, ignoreCase = true) == true
    }
}
