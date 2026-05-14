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
            val resolvedOptions = resolveCatalogValueOptions(dict, registryDictionary)
            val options = when {
                systemDefaults?.options?.isNotEmpty() == true -> systemDefaults.options
                else -> resolvedOptions
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
                    categoryCode = category.code,
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
    "compatibility_candidate_hash",
    "compatibility_evidence_source",
    "compatibility_evidence_text",
    "compatibility_last_verified_at",
    "compatibility_normalized_key",
    "compatibility_review_status",
    "compatibility_rule_id",
    "compatibility_source_priority",
    "counterfeit_risk",
    "data_quality_score",
    "defect_notes",
    "dedup_key",
    "dedup_fingerprint",
    "device_identity_key",
    "blocked_route_reason",
    "evidence_tokens",
    "evidence_policy",
    "evidence_level",
    "extraction_warnings",
    "identity_candidate_count",
    "identity_confidence",
    "identity_debug_trace",
    "identity_evidence",
    "identity_last_resolved_at",
    "identity_resolution_version",
    "imei_evidence_hash",
    "image_embedding_id",
    "image_fingerprint",
    "imei_hash",
    "listing_completeness_confidence",
    "model_resolution_candidates",
    "model_name_text_raw",
    "normalization_confidence",
    "normalization_source",
    "normalized_title",
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
    "route_confidence",
    "route_guard_trace",
    "route_guardrail_trace",
    "serial_number_masked",
    "seller_claims_raw",
    "source_payload_hash",
    "source_confidence",
    "source_evidence",
    "source_hash",
    "source_locale",
    "source_market",
    "source_offer_id",
    "source_evidence_fingerprint",
    "source_page_metadata",
    "source_url",
    "source_url_identity_tokens",
    "ingestion_timestamp",
    "normalization_trace_id",
    "risk_flags",
    "moderation_status",
    "normalized_spec_key",
    "spec_confidence",
    "specs_confidence",
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
    "vision_evidence",
    "vision_evidence_level",
    "vision_no_guess_reason",
    "visible_label_text",
    "compliance_flags",
    "compatibility_target_id",
    "compatibility_hint_confidence",
    "compatible_model_ref",
    "extraction_evidence",
    "normalization_notes",
    "source_trace_id",
    "candidate_value_status",
    "model_seed_match_level",
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

private val compatibilityCoreAttributeCodes = setOf(
    "compatibility_confidence",
    "compatibility_evidence_type",
    "compatibility_mode",
    "compatibility_resolution_status",
    "compatibility_scope",
    "compatible_brand",
    "compatible_model_text",
    "compatible_target_category_code",
)

private val phoneCoreAttributeCodes = setOf(
    "model_name_text",
    "phone_type",
    "storage_capacity_gb",
)

private val phoneAccessoryCoreAttributeCodes = setOf(
    "compatible_device_category",
    "compatibility_evidence_level",
    "phone_accessory_type",
)

private val computerCoreAttributeCodes = setOf(
    "computer_type",
    "cpu_model_text",
    "ram_gb",
)

private val tabletCoreAttributeCodes = setOf(
    "connectivity",
    "screen_size_in",
    "tablet_type",
)

private val monitorCoreAttributeCodes = setOf(
    "connector_input_primary",
    "display_type",
    "panel_technology",
    "refresh_rate_hz",
    "resolution_class",
    "screen_size_in",
)

private val computerAccessoryCoreAttributeCodes = setOf(
    "color_family",
    "compatible_device_type",
    "computer_accessory_type",
    "connection_type",
    "connector_type",
)

private val pcComponentCoreAttributeCodes = setOf(
    "pc_component_type",
)

private val storageMemoryCoreAttributeCodes = setOf(
    "color_family",
    "form_factor",
    "interface_type",
    "storage_capacity_gb",
    "storage_type",
)

private val audioCoreAttributeCodes = setOf(
    "audio_type",
    "availability",
    "brand",
    "color_family",
    "condition",
    "connection_type",
    "form_factor",
    "model_name_text",
)

private val tvHomeTheaterCoreAttributeCodes = setOf(
    "availability",
    "av_type",
    "brand",
    "color_family",
    "condition",
    "display_technology",
    "model_name_text",
    "resolution",
    "screen_size_in",
)

private val pcComponentTypeCriticalAttributeCodes = setOf(
    "airflow_cfm",
    "antenna_included",
    "application_count",
    "argb_support",
    "atx_version",
    "audio_channels",
    "base_clock_ghz",
    "boost_clock_ghz",
    "bracket_size",
    "capture_interface",
    "card_interface",
    "cas_latency",
    "case_color",
    "case_size",
    "chipset",
    "cooler_height_mm",
    "cooler_type",
    "core_count",
    "cpu_generation",
    "cpu_series",
    "cpu_socket",
    "dac_resolution_bit",
    "display_output",
    "driver_required",
    "ecc_support",
    "efficiency_rating",
    "electrically_conductive",
    "encoder_support",
    "expansion_function",
    "fan_count",
    "fan_size_mm",
    "fan_thickness_mm",
    "gpu_bus_width_bit",
    "gpu_chipset",
    "gpu_length_mm",
    "gpu_memory_type",
    "gpu_series",
    "included_fans_count",
    "input_port",
    "integrated_graphics",
    "m2_slots",
    "max_capture_fps",
    "max_capture_resolution",
    "max_cpu_cooler_height_mm",
    "max_gpu_length_mm",
    "max_memory_gb",
    "memory_slots",
    "motherboard_socket",
    "motherboard_form_factor_support",
    "network_speed_gbps",
    "noise_level_db",
    "output_port",
    "passthrough_resolution",
    "pcie_5_support",
    "pcie_lanes",
    "pcie_version",
    "power_connector",
    "psu_form_factor",
    "psu_modularity",
    "psu_wattage_w",
    "pwm_support",
    "radiator_size_mm",
    "radiator_support_mm",
    "ram_capacity_gb",
    "ram_form_factor",
    "ram_kit_modules",
    "ram_speed_mhz",
    "ray_tracing_support",
    "recommended_psu_w",
    "rgb_lighting",
    "sample_rate_khz",
    "sata_ports",
    "side_panel_material",
    "socket_support",
    "tdp_rating_w",
    "tdp_w",
    "thermal_conductivity_w_mk",
    "thermal_paste_quantity_g",
    "thread_count",
    "unlocked_multiplier",
    "vram_gb",
    "wifi_bluetooth",
)

private val storageMemoryTypeCriticalAttributeCodes = setOf(
    "app_performance_class",
    "bay_count",
    "bus_protocol",
    "cache_mb",
    "card_reader_slots",
    "connector_type",
    "console_compatibility_hint",
    "dram_cache",
    "drive_height_mm",
    "enclosure_supported_form_factor",
    "enclosure_supported_interface",
    "encryption_support",
    "endurance_tbw",
    "health_status",
    "included_accessories",
    "memory_card_format",
    "nand_type",
    "nas_drive_bays",
    "nas_grade",
    "network_ports",
    "power_on_count",
    "raid_support",
    "random_read_iops",
    "random_write_iops",
    "read_speed_mb_s",
    "rpm",
    "rugged_protection",
    "seller_bundle",
    "speed_class",
    "storage_accessory_type",
    "storage_media_type",
    "storage_use_case",
    "surveillance_grade",
    "usage_hours",
    "video_speed_class",
    "warranty_months",
    "wear_level_percent",
    "write_speed_mb_s",
)

private val audioTypeCriticalAttributeCodes = setOf(
    "acoustic_design",
    "audio_accessory_type",
    "battery_life_hours",
    "bit_depth",
    "bundle_contents",
    "cartridge_included",
    "channel_config",
    "codec_support",
    "connector_type",
    "driver_size_mm",
    "driver_type",
    "impedance_ohm",
    "input_type",
    "mic_type",
    "microphone_included",
    "noise_control",
    "output_type",
    "phantom_power",
    "receiver_channels",
    "sample_rate_khz",
    "smart_assistant",
    "speaker_power_w",
    "speaker_type",
    "turntable_drive_type",
    "use_case",
    "water_resistance",
    "wireless_standard",
)

private val tvHomeTheaterTypeCriticalAttributeCodes = setOf(
    "audio_channel_config",
    "brightness_lumens",
    "disc_format",
    "display_panel_finish",
    "hdmi_ports",
    "hdmi_version",
    "hdr_standard",
    "max_load_kg",
    "max_output_resolution",
    "mount_type",
    "panel_defect_status",
    "projector_display_tech",
    "projector_light_source",
    "projector_type",
    "receiver_channels",
    "remote_target_type",
    "screen_diagonal_in",
    "screen_format",
    "screen_surface_type",
    "set_top_box_type",
    "smart_tv_platform",
    "streaming_os",
    "surround_format",
    "throw_type",
    "tuner_standard",
    "vesa_pattern",
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
    categoryCode: String,
    normalizedCode: String,
    isIdentity: Boolean,
    requiredForCategory: Boolean,
    requiredForSearch: Boolean,
    requiredForOffer: Boolean,
    requiredForExpress: Boolean,
): CatalogAttributeRole {
    val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
    return when {
    normalizedCode in adminOnlyAttributeCodes -> CatalogAttributeRole.T3_SYSTEM_HIDDEN
    normalizedCode in defaultSystemAttributeCodeSet -> CatalogAttributeRole.T0_CORE
    normalizedCode in identityCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in specsCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in compatibilityCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in phoneCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in phoneAccessoryCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in computerCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in tabletCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in monitorCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in computerAccessoryCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in pcComponentCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in storageMemoryCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCode in audioCoreAttributeCodes -> CatalogAttributeRole.T0_CORE
    normalizedCategoryCode == "TECH.TV_HOME_THEATER" && normalizedCode in tvHomeTheaterCoreAttributeCodes ->
        CatalogAttributeRole.T0_CORE
    normalizedCode in pcComponentTypeCriticalAttributeCodes -> CatalogAttributeRole.T1_TYPE_CRITICAL
    normalizedCode in storageMemoryTypeCriticalAttributeCodes -> CatalogAttributeRole.T1_TYPE_CRITICAL
    normalizedCode in audioTypeCriticalAttributeCodes -> CatalogAttributeRole.T1_TYPE_CRITICAL
    normalizedCategoryCode == "TECH.TV_HOME_THEATER" && normalizedCode in tvHomeTheaterTypeCriticalAttributeCodes ->
        CatalogAttributeRole.T1_TYPE_CRITICAL
    normalizedCode in typeCriticalAttributeCodes -> CatalogAttributeRole.T1_TYPE_CRITICAL
    isIdentity -> CatalogAttributeRole.T0_CORE
    requiredForCategory || requiredForSearch || requiredForOffer || requiredForExpress -> CatalogAttributeRole.T1_TYPE_CRITICAL
    else -> CatalogAttributeRole.T2_ADVANCED
}
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
    normalizedCode in setOf("brand", "compatible_brand", "compatible_phone_brand") -> "brand_optional"
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
    "adapter_direction",
    "amperage_max_a",
    "account_lock_status",
    "active_area",
    "ad_supported",
    "adjustable_angle",
    "adaptive_sync",
    "age_recommendation",
    "apparel_type",
    "aspect_ratio",
    "audiobook_support",
    "bag_type",
    "battery_health_bucket",
    "battery_health_percent",
    "battery_life_claim",
    "battery_capacity_mah",
    "battery_capacity_wh",
    "band_width_mm",
    "bluetooth_version",
    "brightness_nits",
    "built_in_speakers",
    "autofocus",
    "backlight_type",
    "battery_life_hours",
    "battery_type",
    "buttons_count",
    "case_size_mm",
    "case_material",
    "case_style",
    "chipset_text",
    "carrier",
    "chromeos_support_status",
    "charger_included",
    "charging_connector",
    "cellular_generation",
    "computer_accessory_type",
    "cover_form_factor",
    "cleaning_item_type",
    "color_gamut",
    "compatible_connector_type",
    "compatible_device_size_range",
    "compatible_family",
    "compatible_generation",
    "compatible_gtin",
    "compatible_line",
    "compatible_model",
    "compatible_model_number",
    "compatible_mpn",
    "compatible_os",
    "compatible_os_max_version",
    "compatible_os_min_version",
    "compatible_screen_size_range",
    "compatibility_os",
    "connector_type_2",
    "connector_type",
    "connection_type",
    "computer_type",
    "convertible_mode",
    "curved",
    "cpu_brand",
    "cpu_cores",
    "cpu_family",
    "cpu_model_text",
    "data_transfer_support",
    "delta_e",
    "desktop_form_factor",
    "dimensions",
    "dimensions_mm",
    "downstream_ports_count",
    "dpi_max",
    "dual_sim_support",
    "display_panel_type",
    "display_present",
    "display_refresh_rate_hz",
    "display_technology",
    "display_type",
    "dock_type",
    "ecc_memory_support",
    "enterprise_management_support",
    "fast_charge_standard",
    "factory_calibrated",
    "ean",
    "esim_support",
    "foldable",
    "form_factor",
    "frontlight",
    "generation",
    "gpu_brand",
    "gpu_model_text",
    "gpu_type",
    "graphics_memory_gb",
    "gtin",
    "hdr_support",
    "height_adjustable",
    "ergonomic_hand",
    "ethernet_speed",
    "fan_count",
    "keyboard_language",
    "keyboard_included",
    "keyboard_layout",
    "keyboard_size",
    "keyboard_support",
    "key_switch_type",
    "kvm_switch",
    "identity_context_role",
    "identity_evidence_level",
    "identity_evidence_types",
    "identity_review_required",
    "identity_source",
    "model_aliases",
    "model_granularity",
    "model_number",
    "monitor_accessory_type",
    "mpn",
    "lens_mount",
    "laptop_size_supported",
    "liquid_volume_ml",
    "magnetic_mount",
    "magsafe_accessory_type",
    "magsafe_compatible",
    "magnetic_mount",
    "memory_form_factor",
    "motherboard_form_factor",
    "mount_type",
    "mounting_type",
    "mouse_sensor_type",
    "microphone_present",
    "max_display_resolution",
    "max_load_kg",
    "network_generation",
    "mobile_network_generation",
    "network_lock_status",
    "original_oem",
    "os",
    "os_family",
    "os_version_text",
    "panel_technology",
    "part_quality",
    "parental_controls",
    "pen_technology",
    "physical_interface",
    "phone_accessory_type",
    "pivot",
    "palm_rejection",
    "polling_rate_hz",
    "port_set",
    "port_configuration",
    "portable_power_mode",
    "power_delivery_w",
    "power_delivery_profile",
    "power_delivery_watts",
    "power_source",
    "power_watts",
    "prebuilt_or_custom",
    "psu_wattage",
    "protector_coverage",
    "protector_material",
    "protocol_standard",
    "pressure_levels",
    "privacy_filter_type",
    "privacy_shutter",
    "ram_gb",
    "ram_capacity",
    "ram_type",
    "refresh_rate_hz",
    "rechargeable",
    "refurbished_status",
    "region_variant_compatibility",
    "region_variant",
    "required_connector_type",
    "repair_skill_level",
    "replacement_part_type",
    "response_time_ms",
    "rugged_case_included",
    "safe_for_screen",
    "screen_resolution",
    "screen_condition",
    "screen_size",
    "screen_size_inches",
    "screen_size_in",
    "screen_size_range_in",
    "screen_size_supported",
    "secondary_storage_capacity_gb",
    "series",
    "shoe_type",
    "sim_configuration",
    "sim_tool_type",
    "socket_type",
    "stand_form_factor",
    "stand_type",
    "strap_charm_type",
    "storage_capacity",
    "storage_interface",
    "stylus_protocol",
    "stylus_type",
    "stylus_included",
    "stylus_support",
    "swivel",
    "tablet_type",
    "thermal_profile",
    "thin_client_os",
    "tilt",
    "touchscreen",
    "touch_points",
    "ultrawide",
    "usb_c_power_delivery_w",
    "type_of_item",
    "upc",
    "variant_label",
    "vesa_supported",
    "vesa_mount",
    "webcam_built_in",
    "webcam_resolution",
    "weight_g",
    "voltage_max_v",
    "voltage_min_v",
    "water_resistance",
    "wattage_max_w",
    "weight",
    "wi_fi_standard",
    "wireless_charging_support",
    "wireless_standard",
    "workstation_class",
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
