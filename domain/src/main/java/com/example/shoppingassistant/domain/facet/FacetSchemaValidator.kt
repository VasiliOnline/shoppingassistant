package com.example.shoppingassistant.domain.facet

import com.example.shoppingassistant.domain.catalog.Category
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class FacetSchemaIssueSeverity {
    FAIL,
    WARN,
    INFO,
}

data class FacetSchemaValidationIssue(
    val code: String,
    val message: String,
    val severity: FacetSchemaIssueSeverity = FacetSchemaIssueSeverity.FAIL,
)

data class FacetSchemaValidationStats(
    val categoriesCount: Int = 0,
    val definitionsCount: Int = 0,
    val presetsCount: Int = 0,
    val collectionsCount: Int = 0,
    val browseMappedCollectionsCount: Int = 0,
    val categoriesWithDefinitionsCount: Int = 0,
)

data class FacetSchemaValidationReport(
    val issues: List<FacetSchemaValidationIssue>,
    val stats: FacetSchemaValidationStats = FacetSchemaValidationStats(),
) {
    val failIssues: List<FacetSchemaValidationIssue>
        get() = issues.filter { it.severity == FacetSchemaIssueSeverity.FAIL }

    val warnIssues: List<FacetSchemaValidationIssue>
        get() = issues.filter { it.severity == FacetSchemaIssueSeverity.WARN }

    val infoIssues: List<FacetSchemaValidationIssue>
        get() = issues.filter { it.severity == FacetSchemaIssueSeverity.INFO }

    val isValid: Boolean
        get() = failIssues.isEmpty()

    fun summary(maxIssues: Int = 20): String {
        if (issues.isEmpty()) return "Facet schema validation passed."
        val ordered = issues.sortedWith(
            compareBy<FacetSchemaValidationIssue>({ severityOrder(it.severity) }, { it.code }),
        )
        val top = ordered.take(maxIssues.coerceAtLeast(1))
        val hidden = (ordered.size - top.size).coerceAtLeast(0)

        return buildString {
            appendLine(
                "Facet schema validation ${if (isValid) "completed with warnings" else "failed"}: " +
                    "${failIssues.size} fail(s), ${warnIssues.size} warning(s), ${infoIssues.size} info.",
            )
            top.forEachIndexed { idx, issue ->
                appendLine("${idx + 1}. [${issue.severity}] [${issue.code}] ${issue.message}")
            }
            if (hidden > 0) appendLine("... and $hidden more issue(s).")
            append(
                "Stats: categories=${stats.categoriesCount}, definitions=${stats.definitionsCount}, " +
                    "presets=${stats.presetsCount}, collections=${stats.collectionsCount}, " +
                    "browseMappedCollections=${stats.browseMappedCollectionsCount}, " +
                    "categoriesWithDefinitions=${stats.categoriesWithDefinitionsCount}.",
            )
        }
    }

    private fun severityOrder(severity: FacetSchemaIssueSeverity): Int = when (severity) {
        FacetSchemaIssueSeverity.FAIL -> 0
        FacetSchemaIssueSeverity.WARN -> 1
        FacetSchemaIssueSeverity.INFO -> 2
    }
}

class FacetSchemaValidator {
    private val facetKeyRegex = Regex("^[a-z][a-z0-9_]{1,63}$")
    private val codeRegex = Regex("^[A-Z][A-Z0-9._-]{2,63}$")

    fun validate(
        categories: List<Category>,
        definitions: List<FacetDefinition>,
        presets: List<FacetPreset>,
        collections: List<FacetCollection>,
    ): FacetSchemaValidationReport {
        val issues = mutableListOf<FacetSchemaValidationIssue>()
        val categoriesByCode = categories.associateBy { it.code.trim() }
        val parentCodes = categoriesByCode.values
            .mapNotNull { it.parentCode?.trim()?.takeIf(String::isNotBlank) }
            .toSet()
        val leafCodes = categoriesByCode.keys.filter { it !in parentCodes }.toSet()

        val definitionsByKey = validateDefinitions(
            definitions = definitions,
            categoriesByCode = categoriesByCode,
            leafCodes = leafCodes,
            issues = issues,
        )
        val presetsByCode = validatePresets(
            presets = presets,
            categoriesByCode = categoriesByCode,
            leafCodes = leafCodes,
            definitionsByKey = definitionsByKey,
            issues = issues,
        )
        val browseMappedCollections = validateCollections(
            collections = collections,
            categoriesByCode = categoriesByCode,
            leafCodes = leafCodes,
            presetsByCode = presetsByCode,
            issues = issues,
        )

        val categoriesWithDefinitions = definitions.asSequence()
            .flatMap { it.appliesToCategoryCodes.asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val missingLeafCoverage = leafCodes.filterNot { it in categoriesWithDefinitions }
        if (missingLeafCoverage.isNotEmpty()) {
            fail(
                issues,
                "FACET_DEFINITION_COVERAGE_INCOMPLETE",
                "Facet definitions miss ${missingLeafCoverage.size}/${leafCodes.size} leaf categories. Examples: ${missingLeafCoverage.take(8).joinToString(", ")}.",
            )
        } else if (leafCodes.isNotEmpty()) {
            info(
                issues,
                "FACET_DEFINITION_COVERAGE_COMPLETE",
                "Facet definitions cover all ${leafCodes.size} leaf categories.",
            )
        }

        val stats = FacetSchemaValidationStats(
            categoriesCount = categoriesByCode.size,
            definitionsCount = definitions.size,
            presetsCount = presets.size,
            collectionsCount = collections.size,
            browseMappedCollectionsCount = browseMappedCollections,
            categoriesWithDefinitionsCount = categoriesWithDefinitions.size,
        )
        return FacetSchemaValidationReport(issues = issues, stats = stats)
    }

    private fun validateDefinitions(
        definitions: List<FacetDefinition>,
        categoriesByCode: Map<String, Category>,
        leafCodes: Set<String>,
        issues: MutableList<FacetSchemaValidationIssue>,
    ): Map<String, FacetDefinition> {
        val byKey = LinkedHashMap<String, FacetDefinition>()

        definitions.forEach { definition ->
            val facetKey = definition.facetKey.trim()
            if (facetKey.isBlank()) {
                fail(issues, "FACET_KEY_BLANK", "Facet key must not be blank.")
                return@forEach
            }
            if (!facetKeyRegex.matches(facetKey)) {
                fail(
                    issues,
                    "FACET_KEY_FORMAT",
                    "Facet key '$facetKey' has invalid format. Expected lower_snake_case.",
                )
            }
            if (byKey.containsKey(facetKey)) {
                fail(issues, "FACET_KEY_DUPLICATE", "Facet key '$facetKey' is duplicated.")
            } else {
                byKey[facetKey] = definition
            }

            if (definition.titleRu.isBlank()) {
                fail(issues, "FACET_TITLE_BLANK", "Facet '$facetKey' must have non-blank titleRu.")
            }
            val effectiveFrom = parseIsoDate(definition.effectiveFrom)
            val effectiveTo = parseIsoDate(definition.effectiveTo)
            if (definition.effectiveFrom != null && effectiveFrom == null) {
                fail(
                    issues,
                    "FACET_EFFECTIVE_FROM_INVALID",
                    "Facet '$facetKey' has invalid effectiveFrom '${definition.effectiveFrom}'. Expected yyyy-MM-dd.",
                )
            }
            if (definition.effectiveTo != null && effectiveTo == null) {
                fail(
                    issues,
                    "FACET_EFFECTIVE_TO_INVALID",
                    "Facet '$facetKey' has invalid effectiveTo '${definition.effectiveTo}'. Expected yyyy-MM-dd.",
                )
            }
            if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
                fail(
                    issues,
                    "FACET_EFFECTIVE_WINDOW_INVALID",
                    "Facet '$facetKey' has invalid window: effectiveFrom '$effectiveFrom' after effectiveTo '$effectiveTo'.",
                )
            }
            if (definition.appliesToCategoryCodes.isEmpty()) {
                fail(issues, "FACET_APPLIES_EMPTY", "Facet '$facetKey' must reference at least one category.")
            }

            definition.appliesToCategoryCodes.forEach { rawCode ->
                val code = rawCode.trim()
                if (code.isBlank()) {
                    fail(issues, "FACET_APPLIES_BLANK", "Facet '$facetKey' contains blank category reference.")
                    return@forEach
                }
                if (!categoriesByCode.containsKey(code)) {
                    fail(
                        issues,
                        "FACET_CATEGORY_MISSING",
                        "Facet '$facetKey' points to missing category '$code'.",
                    )
                    return@forEach
                }
                if (code !in leafCodes) {
                    warn(
                        issues,
                        "FACET_CATEGORY_NON_LEAF",
                        "Facet '$facetKey' points to non-leaf category '$code'.",
                    )
                }
            }
        }

        return byKey
    }

    private fun validatePresets(
        presets: List<FacetPreset>,
        categoriesByCode: Map<String, Category>,
        leafCodes: Set<String>,
        definitionsByKey: Map<String, FacetDefinition>,
        issues: MutableList<FacetSchemaValidationIssue>,
    ): Map<String, FacetPreset> {
        val byCode = LinkedHashMap<String, FacetPreset>()

        presets.forEach { preset ->
            val presetCode = preset.presetCode.trim()
            if (presetCode.isBlank()) {
                fail(issues, "PRESET_CODE_BLANK", "Preset code must not be blank.")
                return@forEach
            }
            if (!codeRegex.matches(presetCode)) {
                fail(
                    issues,
                    "PRESET_CODE_FORMAT",
                    "Preset code '$presetCode' has invalid format. Expected upper code.",
                )
            }
            if (byCode.containsKey(presetCode)) {
                fail(issues, "PRESET_CODE_DUPLICATE", "Preset code '$presetCode' is duplicated.")
            } else {
                byCode[presetCode] = preset
            }

            if (preset.titleRu.isBlank()) {
                fail(issues, "PRESET_TITLE_BLANK", "Preset '$presetCode' must have non-blank titleRu.")
            }
            val effectiveFrom = parseIsoDate(preset.effectiveFrom)
            val effectiveTo = parseIsoDate(preset.effectiveTo)
            if (preset.effectiveFrom != null && effectiveFrom == null) {
                fail(
                    issues,
                    "PRESET_EFFECTIVE_FROM_INVALID",
                    "Preset '$presetCode' has invalid effectiveFrom '${preset.effectiveFrom}'. Expected yyyy-MM-dd.",
                )
            }
            if (preset.effectiveTo != null && effectiveTo == null) {
                fail(
                    issues,
                    "PRESET_EFFECTIVE_TO_INVALID",
                    "Preset '$presetCode' has invalid effectiveTo '${preset.effectiveTo}'. Expected yyyy-MM-dd.",
                )
            }
            if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
                fail(
                    issues,
                    "PRESET_EFFECTIVE_WINDOW_INVALID",
                    "Preset '$presetCode' has invalid window: effectiveFrom '$effectiveFrom' after effectiveTo '$effectiveTo'.",
                )
            }

            val categoryCode = preset.categoryCode.trim()
            if (categoryCode.isBlank()) {
                fail(issues, "PRESET_CATEGORY_BLANK", "Preset '$presetCode' has blank categoryCode.")
            } else if (!categoriesByCode.containsKey(categoryCode)) {
                fail(
                    issues,
                    "PRESET_CATEGORY_MISSING",
                    "Preset '$presetCode' points to missing category '$categoryCode'.",
                )
            } else if (categoryCode !in leafCodes) {
                warn(
                    issues,
                    "PRESET_CATEGORY_NON_LEAF",
                    "Preset '$presetCode' points to non-leaf category '$categoryCode'.",
                )
            }

            if (preset.rules.isEmpty()) {
                warn(issues, "PRESET_RULES_EMPTY", "Preset '$presetCode' has no facet rules.")
            }

            val seenRuleFacetKeys = HashSet<String>()
            preset.rules.forEach { rule ->
                val facetKey = rule.facetKey.trim()
                if (facetKey.isBlank()) {
                    fail(
                        issues,
                        "PRESET_RULE_FACET_BLANK",
                        "Preset '$presetCode' contains rule with blank facet key.",
                    )
                    return@forEach
                }
                if (!seenRuleFacetKeys.add(facetKey)) {
                    fail(
                        issues,
                        "PRESET_RULE_FACET_DUPLICATE",
                        "Preset '$presetCode' has duplicate rule for facet '$facetKey'.",
                    )
                }

                val definition = definitionsByKey[facetKey]
                if (definition == null) {
                    fail(
                        issues,
                        "PRESET_RULE_FACET_MISSING",
                        "Preset '$presetCode' references unknown facet '$facetKey'.",
                    )
                    return@forEach
                }

                if (!definition.appliesToCategoryCodes.any { it.equals(categoryCode, ignoreCase = true) }) {
                    warn(
                        issues,
                        "PRESET_RULE_OUT_OF_SCOPE",
                        "Preset '$presetCode' references facet '$facetKey' not scoped to '$categoryCode'.",
                    )
                }

                val includeValues = rule.includeValues
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                val includeBlankCount = rule.includeValues.size - includeValues.size
                if (includeBlankCount > 0) {
                    fail(
                        issues,
                        "PRESET_RULE_INCLUDE_BLANK",
                        "Preset '$presetCode' includes $includeBlankCount blank includeValues item(s) for '$facetKey'.",
                    )
                }

                val excludeValues = rule.excludeValues
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                val excludeBlankCount = rule.excludeValues.size - excludeValues.size
                if (excludeBlankCount > 0) {
                    fail(
                        issues,
                        "PRESET_RULE_EXCLUDE_BLANK",
                        "Preset '$presetCode' includes $excludeBlankCount blank excludeValues item(s) for '$facetKey'.",
                    )
                }

                val overlap = includeValues.toSet().intersect(excludeValues.toSet())
                if (overlap.isNotEmpty()) {
                    fail(
                        issues,
                        "PRESET_RULE_INCLUDE_EXCLUDE_CONFLICT",
                        "Preset '$presetCode' has include/exclude overlap for '$facetKey': ${overlap.take(5).joinToString(", ")}.",
                    )
                }

                if (rule.minValue != null && rule.maxValue != null && rule.minValue > rule.maxValue) {
                    fail(
                        issues,
                        "PRESET_RANGE_INVALID",
                        "Preset '$presetCode' has invalid range for '$facetKey': minValue > maxValue.",
                    )
                }

                val hasNumericRange = rule.minValue != null || rule.maxValue != null
                val hasSelectorPayload =
                    includeValues.isNotEmpty() || excludeValues.isNotEmpty() || hasNumericRange || rule.boolValue != null
                if (!hasSelectorPayload) {
                    fail(
                        issues,
                        "PRESET_RULE_EMPTY",
                        "Preset '$presetCode' rule for '$facetKey' is empty.",
                    )
                }

                if (definition.valueType == FacetDataType.BOOL && rule.boolValue == null) {
                    fail(
                        issues,
                        "PRESET_BOOL_MISSING",
                        "Preset '$presetCode' must set boolValue for boolean facet '$facetKey'.",
                    )
                }
                if (definition.valueType == FacetDataType.BOOL &&
                    (includeValues.isNotEmpty() || excludeValues.isNotEmpty() || hasNumericRange)
                ) {
                    fail(
                        issues,
                        "PRESET_BOOL_HAS_EXTRA_FIELDS",
                        "Preset '$presetCode' boolean facet '$facetKey' must use only boolValue.",
                    )
                }
                if (definition.valueType != FacetDataType.BOOL && rule.boolValue != null) {
                    fail(
                        issues,
                        "PRESET_NON_BOOL_HAS_BOOL_VALUE",
                        "Preset '$presetCode' non-boolean facet '$facetKey' cannot set boolValue.",
                    )
                }
            }
        }

        return byCode
    }

    private fun validateCollections(
        collections: List<FacetCollection>,
        categoriesByCode: Map<String, Category>,
        leafCodes: Set<String>,
        presetsByCode: Map<String, FacetPreset>,
        issues: MutableList<FacetSchemaValidationIssue>,
    ): Int {
        val collectionCodes = HashSet<String>()
        val browseCodes = HashSet<String>()
        var browseMappedCount = 0

        collections.forEach { collection ->
            val code = collection.collectionCode.trim()
            if (code.isBlank()) {
                fail(issues, "COLLECTION_CODE_BLANK", "Collection code must not be blank.")
                return@forEach
            }
            if (!codeRegex.matches(code)) {
                fail(
                    issues,
                    "COLLECTION_CODE_FORMAT",
                    "Collection code '$code' has invalid format. Expected upper code.",
                )
            }
            if (!collectionCodes.add(code)) {
                fail(issues, "COLLECTION_CODE_DUPLICATE", "Collection code '$code' is duplicated.")
            }

            if (collection.titleRu.isBlank()) {
                fail(issues, "COLLECTION_TITLE_BLANK", "Collection '$code' must have non-blank titleRu.")
            }

            val categoryCode = collection.categoryCode.trim()
            if (categoryCode.isBlank()) {
                fail(issues, "COLLECTION_CATEGORY_BLANK", "Collection '$code' has blank categoryCode.")
            } else if (!categoriesByCode.containsKey(categoryCode)) {
                fail(
                    issues,
                    "COLLECTION_CATEGORY_MISSING",
                    "Collection '$code' points to missing category '$categoryCode'.",
                )
            } else if (categoryCode !in leafCodes) {
                warn(
                    issues,
                    "COLLECTION_CATEGORY_NON_LEAF",
                    "Collection '$code' points to non-leaf category '$categoryCode'.",
                )
            }

            val browseCode = collection.browseCode?.trim()?.takeIf { it.isNotEmpty() }
            if (browseCode != null) {
                browseMappedCount += 1
                if (!browseCodes.add(browseCode)) {
                    warn(
                        issues,
                        "COLLECTION_BROWSE_DUPLICATE",
                        "Browse code '$browseCode' is attached to multiple collections.",
                    )
                }
            }

            val presetCode = collection.presetCode?.trim()?.takeIf { it.isNotEmpty() }
            if (browseCode == null && presetCode == null) {
                fail(
                    issues,
                    "COLLECTION_ORPHAN",
                    "Collection '$code' must have browseCode and/or presetCode.",
                )
            }

            if (presetCode != null) {
                val preset = presetsByCode[presetCode]
                if (preset == null) {
                    fail(
                        issues,
                        "COLLECTION_PRESET_MISSING",
                        "Collection '$code' references unknown preset '$presetCode'.",
                    )
                } else if (!preset.categoryCode.equals(categoryCode, ignoreCase = true)) {
                    fail(
                        issues,
                        "COLLECTION_PRESET_CATEGORY_MISMATCH",
                        "Collection '$code' category '$categoryCode' mismatches preset '$presetCode' category '${preset.categoryCode}'.",
                    )
                }
            }
        }

        return browseMappedCount
    }

    private fun fail(
        issues: MutableList<FacetSchemaValidationIssue>,
        code: String,
        message: String,
    ) {
        issues += FacetSchemaValidationIssue(code = code, message = message, severity = FacetSchemaIssueSeverity.FAIL)
    }

    private fun warn(
        issues: MutableList<FacetSchemaValidationIssue>,
        code: String,
        message: String,
    ) {
        issues += FacetSchemaValidationIssue(code = code, message = message, severity = FacetSchemaIssueSeverity.WARN)
    }

    private fun info(
        issues: MutableList<FacetSchemaValidationIssue>,
        code: String,
        message: String,
    ) {
        issues += FacetSchemaValidationIssue(code = code, message = message, severity = FacetSchemaIssueSeverity.INFO)
    }

    private fun parseIsoDate(raw: String?): LocalDate? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
