package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope

internal data class Stage22SeedValidationIssue(
    val code: String,
    val message: String,
)

internal data class Stage22SeedValidationReport(
    val issues: List<Stage22SeedValidationIssue>,
) {
    val isValid: Boolean
        get() = issues.isEmpty()

    fun summary(maxIssues: Int = 20): String {
        if (issues.isEmpty()) return "Stage 2.2 seed validation passed."
        val top = issues.take(maxIssues.coerceAtLeast(1))
        val hidden = (issues.size - top.size).coerceAtLeast(0)
        return buildString {
            appendLine("Stage 2.2 seed validation failed: ${issues.size} issue(s).")
            top.forEachIndexed { index, issue ->
                appendLine("${index + 1}. [${issue.code}] ${issue.message}")
            }
            if (hidden > 0) {
                append("... and $hidden more issue(s).")
            }
        }
    }
}

internal interface Stage22AliasNormalizer {
    fun normalize(alias: String): String
}

internal object DefaultStage22AliasNormalizer : Stage22AliasNormalizer {
    override fun normalize(alias: String): String = Stage21QueryTextNormalizer.normalize(alias)
}

internal class Stage22SeedValidator(
    private val aliasNormalizer: Stage22AliasNormalizer = DefaultStage22AliasNormalizer,
) {
    fun validate(
        categories: List<Category>,
        packages: List<Stage22PackageData>,
    ): Stage22SeedValidationReport =
        validate(
            categories = categories,
            registry = GenericStage22PackageLoader.loadRegistrySnapshot(),
            packages = packages,
            globalConstraints = GenericStage22PackageLoader.loadGlobalConstraints(),
        )

    fun validate(
        categories: List<Category>,
        registry: Stage22RegistrySnapshot,
        packages: List<Stage22PackageData>,
        globalConstraints: List<CatalogConstraints> = emptyList(),
    ): Stage22SeedValidationReport {
        val issues = mutableListOf<Stage22SeedValidationIssue>()
        val stage22Profiles = packages.flatMap { packageData -> packageData.toStage22Profiles(registry) }

        validateVersioning(registry.meta, issues)
        validateAttributeDefIntegrity(registry.attributes.values.toList(), issues)
        validateClosedSetIntegrity(registry, issues)
        validateDeterminism(packages, issues)
        validateProfileIntegrity(categories, stage22Profiles, issues)
        validateProfileReferential(registry, stage22Profiles, issues)
        validateConstraintsConflictGate(
            categories = categories,
            registry = registry,
            packages = packages,
            globalConstraints = globalConstraints,
            issues = issues,
        )

        if (packages.isNotEmpty() || globalConstraints.isNotEmpty()) {
            validateReferentialIntegrity(
                categories = categories,
                registry = registry,
                packages = packages,
                globalConstraints = globalConstraints,
                issues = issues,
            )
        }

        return Stage22SeedValidationReport(issues = issues)
    }

    fun validateOrThrow(
        categories: List<Category>,
        packages: List<Stage22PackageData>,
    ) {
        val report = validate(categories = categories, packages = packages)
        if (!report.isValid) {
            throw IllegalStateException(report.summary())
        }
    }

    fun validateOrThrow(
        categories: List<Category>,
        registry: Stage22RegistrySnapshot,
        packages: List<Stage22PackageData>,
        globalConstraints: List<CatalogConstraints> = emptyList(),
    ) {
        val report = validate(
            categories = categories,
            registry = registry,
            packages = packages,
            globalConstraints = globalConstraints,
        )
        if (!report.isValid) {
            throw IllegalStateException(report.summary())
        }
    }

    private fun validateVersioning(
        meta: Stage22RegistryMeta,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val versionRegex = Regex("""^\d+\.\d+\.\d+$""")
        if (!versionRegex.matches(meta.schemaVersion.trim())) {
            issue(
                issues,
                "VERSIONING_SCHEMA_INVALID",
                "registry_meta.schemaVersion must be semantic version (x.y.z), got '${meta.schemaVersion}'.",
            )
        }
        if (!versionRegex.matches(meta.dataVersion.trim())) {
            issue(
                issues,
                "VERSIONING_DATA_INVALID",
                "registry_meta.dataVersion must be semantic version (x.y.z), got '${meta.dataVersion}'.",
            )
        }
        if (meta.generatedAt.trim().isBlank()) {
            issue(
                issues,
                "VERSIONING_GENERATED_AT_MISSING",
                "registry_meta.generatedAt must be non-empty.",
            )
        }
    }

    private fun validateAttributeDefIntegrity(
        attributes: List<Stage22AttributeDef>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val seenCodes = HashSet<String>()
        val unitRegex = Regex("""^[A-Za-z0-9./^_-]+$""")

        attributes.forEach { attribute ->
            val attributeCode = attribute.attributeCode.trim()
            if (attributeCode.isBlank()) {
                issue(issues, "ATTRIBUTE_CODE_BLANK", "Attribute code must not be blank.")
                return@forEach
            }
            if (!seenCodes.add(attributeCode)) {
                issue(
                    issues,
                    "ATTRIBUTE_CODE_DUPLICATE",
                    "Duplicate attributeCode '$attributeCode' in attributes registry.",
                )
            }

            val hasRuOrEn = attribute.labels.keys.any { locale ->
                locale.equals("ru", ignoreCase = true) || locale.equals("en", ignoreCase = true)
            }
            if (!hasRuOrEn) {
                issue(
                    issues,
                    "ATTRIBUTE_LABELS_MISSING",
                    "Attribute '$attributeCode' must define at least one label for 'ru' or 'en'.",
                )
            }

            val unit = attribute.unit?.trim().orEmpty()
            if (unit.isNotEmpty() && !unitRegex.matches(unit)) {
                issue(
                    issues,
                    "ATTRIBUTE_UNIT_INVALID",
                    "Attribute '$attributeCode' has invalid unit '$unit'.",
                )
            }

            if (attribute.valueType != Stage22ValueType.ENUM && attribute.valueSetType != Stage22ValueSetType.OPEN) {
                issue(
                    issues,
                    "ATTRIBUTE_VALUE_SET_TYPE_INVALID",
                    "Attribute '$attributeCode' uses valueSetType '${attribute.valueSetType}' " +
                        "but valueType '${attribute.valueType}'. Only ENUM supports CLOSED/SEMI_CLOSED.",
                )
            }
        }
    }

    private fun validateClosedSetIntegrity(
        registry: Stage22RegistrySnapshot,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        registry.attributes.values.forEach { attribute ->
            if (attribute.valueSetType == Stage22ValueSetType.OPEN) return@forEach
            val dictionary = registry.dictionaries[attribute.attributeCode]
            if (dictionary == null) {
                issue(
                    issues,
                    "CLOSED_SET_DICTIONARY_MISSING",
                    "Attribute '${attribute.attributeCode}' requires dictionary for ${attribute.valueSetType}.",
                )
                return@forEach
            }
            if (dictionary.entries.isEmpty()) {
                issue(
                    issues,
                    "CLOSED_SET_DICTIONARY_EMPTY",
                    "Dictionary for '${attribute.attributeCode}' must contain at least one entry.",
                )
            }
        }

        registry.dictionaries.values.forEach { dictionary ->
            val attributeCode = dictionary.attributeCode
            if (!registry.attributes.containsKey(attributeCode)) {
                issue(
                    issues,
                    "DICTIONARY_ATTRIBUTE_UNKNOWN",
                    "Dictionary references unknown attributeCode '$attributeCode'.",
                )
            }

            val valueCodeSeen = HashSet<String>()
            val aliasToValueCode = LinkedHashMap<String, String>()

            dictionary.entries.forEach { entry ->
                val valueCode = entry.valueCode.trim()
                if (valueCode.isBlank()) {
                    issue(
                        issues,
                        "DICTIONARY_VALUE_CODE_BLANK",
                        "Dictionary '$attributeCode' contains blank valueCode.",
                    )
                    return@forEach
                }
                if (!valueCodeSeen.add(valueCode)) {
                    issue(
                        issues,
                        "DICTIONARY_VALUE_CODE_DUPLICATE",
                        "Dictionary '$attributeCode' contains duplicate valueCode '$valueCode'.",
                    )
                }

                entry.aliases.forEach { alias ->
                    val normalizedAlias = aliasNormalizer.normalize(alias)
                    if (normalizedAlias.isBlank()) {
                        issue(
                            issues,
                            "DICTIONARY_ALIAS_BLANK",
                            "Dictionary '$attributeCode' has blank alias for valueCode '$valueCode'.",
                        )
                        return@forEach
                    }
                    val previousValueCode = aliasToValueCode.putIfAbsent(normalizedAlias, valueCode)
                    if (previousValueCode != null && previousValueCode != valueCode) {
                        issue(
                            issues,
                            "ALIAS_COLLISION",
                            "Dictionary '$attributeCode' alias '$normalizedAlias' maps to both " +
                                "'$previousValueCode' and '$valueCode'.",
                        )
                    }
                }
            }
        }
    }

    private fun validateDeterminism(
        packages: List<Stage22PackageData>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val packageCodes = packages.map { it.descriptor.l0Code }
        val duplicateCodes = packageCodes.groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicateCodes.isNotEmpty()) {
            issue(
                issues,
                "DETERMINISM_DUPLICATE_PACKAGE",
                "Stage 2.2 package descriptors contain duplicates: ${duplicateCodes.sorted().joinToString(", ")}.",
            )
        }

        val sortedCodes = packageCodes.sorted()
        if (packageCodes != sortedCodes) {
            issue(
                issues,
                "DETERMINISM_PACKAGE_ORDER",
                "Stage 2.2 package loading order must be deterministic (sorted by l0Code).",
            )
        }
    }

    private fun validateProfileIntegrity(
        categories: List<Category>,
        profiles: List<Stage22CategoryProfile>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val categoryCodes = categories.map { category -> category.code }.toSet()
        val parentCodes = categories
            .mapNotNull { category -> category.parentCode?.trim()?.takeIf { it.isNotEmpty() } }
            .toSet()
        val leafCodes = categoryCodes - parentCodes

        val seenByL0AndCategory = HashSet<String>()
        profiles.forEach { profile ->
            val categoryCode = profile.categoryCode.trim()
            val l0Code = profile.sourceL0?.trim().orEmpty().ifEmpty { categoryCode.substringBefore('.') }
            val key = "$l0Code|$categoryCode"
            if (!seenByL0AndCategory.add(key)) {
                issue(
                    issues,
                    "PROFILE_CATEGORY_DUPLICATE_IN_L0",
                    "Duplicate profile for category '$categoryCode' in L0 '$l0Code'.",
                )
            }
            if (categoryCode in leafCodes && profile.attributes.isEmpty()) return@forEach

            val sorted = profile.attributes
                .sortedWith(compareBy<Stage22AttributeUsage> { it.uiOrder }.thenBy { it.attributeCode })
            if (profile.attributes != sorted) {
                issue(
                    issues,
                    "PROFILE_ATTRIBUTES_ORDER_NON_DETERMINISTIC",
                    "Profile '$categoryCode' attributes must be ordered by uiOrder then attributeCode.",
                )
            }
        }
    }

    private fun validateProfileReferential(
        registry: Stage22RegistrySnapshot,
        profiles: List<Stage22CategoryProfile>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        profiles.forEach { profile ->
            profile.attributes.forEach { usage ->
                val attributeCode = usage.attributeCode.trim()
                val attributeDef = registry.attributes[attributeCode]
                if (attributeDef == null) {
                    issue(
                        issues,
                        "PROFILE_ATTRIBUTE_UNKNOWN",
                        "Profile '${profile.categoryCode}' references unknown attribute '$attributeCode'.",
                    )
                    return@forEach
                }
                if (attributeDef.valueType == Stage22ValueType.ENUM && attributeDef.valueSetType != Stage22ValueSetType.OPEN) {
                    val dictionary = registry.dictionaries[attributeCode]
                    if (dictionary == null || dictionary.entries.isEmpty()) {
                        issue(
                            issues,
                            "PROFILE_ENUM_DICTIONARY_MISSING",
                            "Profile '${profile.categoryCode}' uses ENUM attribute '$attributeCode' " +
                                "with ${attributeDef.valueSetType} but dictionary is missing.",
                        )
                    }
                }
            }
        }
    }

    private fun validateConstraintsConflictGate(
        categories: List<Category>,
        registry: Stage22RegistrySnapshot,
        packages: List<Stage22PackageData>,
        globalConstraints: List<CatalogConstraints>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val categoryConstraints = packages.flatMap { it.constraints }
        val resolver = Stage22ConstraintsResolver(
            registry = registry,
            globalConstraints = globalConstraints,
            categoryConstraints = categoryConstraints,
        )
        categories.forEach { category ->
            try {
                resolver.resolve(category.code)
            } catch (error: IllegalStateException) {
                issue(
                    issues,
                    "CONSTRAINTS_CONFLICT",
                    "Conflict detected for category '${category.code}': ${error.message}",
                )
            }
        }
    }

    private fun validateReferentialIntegrity(
        categories: List<Category>,
        registry: Stage22RegistrySnapshot,
        packages: List<Stage22PackageData>,
        globalConstraints: List<CatalogConstraints>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val categoryCodes = categories.map { it.code }.toSet()
        val profiles = packages.flatMap { it.profiles }
        val profileByCategory = LinkedHashMap<String, CategoryProfile>()

        profiles.forEach { profile ->
            val categoryCode = profile.category.code
            if (!categoryCodes.contains(categoryCode)) {
                issue(
                    issues,
                    "REFERENTIAL_PROFILE_CATEGORY_UNKNOWN",
                    "Profile references unknown category '$categoryCode'.",
                )
            }

            val duplicate = profileByCategory.putIfAbsent(categoryCode, profile)
            if (duplicate != null) {
                issue(
                    issues,
                    "REFERENTIAL_PROFILE_CATEGORY_DUPLICATE",
                    "Duplicate profile for category '$categoryCode'.",
                )
            }

            val attributeCodes = HashSet<String>()
            profile.attributes.forEach { attribute ->
                if (!attributeCodes.add(attribute.code)) {
                    issue(
                        issues,
                        "REFERENTIAL_PROFILE_ATTRIBUTE_DUPLICATE",
                        "Profile '$categoryCode' has duplicate attribute '${attribute.code}'.",
                    )
                }
            }

            val expectedL0 = profile.category.code.substringBefore(".")
            if (!expectedL0.equals(profile.category.segment.name, ignoreCase = false)) {
                issue(
                    issues,
                    "REFERENTIAL_PROFILE_SEGMENT_MISMATCH",
                    "Profile '$categoryCode' has segment '${profile.category.segment}' but code root '$expectedL0'.",
                )
            }
        }

        val knownAttributeCodes = registry.attributes.keys

        val allConstraints = buildList {
            addAll(globalConstraints)
            addAll(packages.flatMap { it.constraints })
        }
        allConstraints.forEach { constraint ->
            validateConstraintScope(constraint, categoryCodes, issues)
            validateConstraintAttributes(
                constraint = constraint,
                profileByCategory = profileByCategory,
                knownAttributeCodes = knownAttributeCodes,
                registry = registry,
                issues = issues,
            )
        }
    }

    private fun validateConstraintScope(
        constraint: CatalogConstraints,
        categoryCodes: Set<String>,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val categoryCode = constraint.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val brand = constraint.brand?.trim()?.takeIf { it.isNotEmpty() }
        val model = constraint.model?.trim()?.takeIf { it.isNotEmpty() }

        when (constraint.scope) {
            ConstraintScope.GLOBAL -> Unit
            ConstraintScope.CATEGORY -> {
                if (categoryCode == null) {
                    issue(
                        issues,
                        "REFERENTIAL_CONSTRAINT_CATEGORY_REQUIRED",
                        "CATEGORY constraint must define categoryCode.",
                    )
                }
            }

            ConstraintScope.BRAND -> {
                if (categoryCode == null) {
                    issue(
                        issues,
                        "REFERENTIAL_CONSTRAINT_CATEGORY_REQUIRED",
                        "BRAND constraint must define categoryCode.",
                    )
                }
                if (brand == null) {
                    issue(
                        issues,
                        "REFERENTIAL_CONSTRAINT_BRAND_REQUIRED",
                        "BRAND constraint must define brand.",
                    )
                }
            }

            ConstraintScope.MODEL -> {
                if (categoryCode == null) {
                    issue(
                        issues,
                        "REFERENTIAL_CONSTRAINT_CATEGORY_REQUIRED",
                        "MODEL constraint must define categoryCode.",
                    )
                }
                if (brand == null) {
                    issue(
                        issues,
                        "REFERENTIAL_CONSTRAINT_BRAND_REQUIRED",
                        "MODEL constraint must define brand.",
                    )
                }
                if (model == null) {
                    issue(
                        issues,
                        "REFERENTIAL_CONSTRAINT_MODEL_REQUIRED",
                        "MODEL constraint must define model.",
                    )
                }
            }
        }

        if (categoryCode != null && !categoryCodes.contains(categoryCode)) {
            issue(
                issues,
                "REFERENTIAL_CONSTRAINT_CATEGORY_UNKNOWN",
                "Constraint references unknown category '$categoryCode'.",
            )
        }
    }

    private fun validateConstraintAttributes(
        constraint: CatalogConstraints,
        profileByCategory: Map<String, CategoryProfile>,
        knownAttributeCodes: Set<String>,
        registry: Stage22RegistrySnapshot,
        issues: MutableList<Stage22SeedValidationIssue>,
    ) {
        val categoryCode = constraint.categoryCode?.trim()?.takeIf { it.isNotEmpty() }
        val categoryAttributes = categoryCode
            ?.let { profileByCategory[it]?.attributes.orEmpty().map { attribute -> attribute.code }.toSet() }
            .orEmpty()

        val checkAttributeCode: (String) -> Unit = { attributeCode ->
            val exists = when {
                categoryCode != null && categoryAttributes.isNotEmpty() ->
                    categoryAttributes.contains(attributeCode) || knownAttributeCodes.contains(attributeCode)

                else -> knownAttributeCodes.contains(attributeCode)
            }
            if (!exists) {
                issue(
                    issues,
                    "REFERENTIAL_ATTRIBUTE_UNKNOWN",
                    "Constraint references unknown attribute '$attributeCode'" +
                        (categoryCode?.let { " for category '$it'" } ?: " at GLOBAL scope") +
                        ".",
                )
            }
        }

        val checkClosedValues: (String, List<String>, String) -> Unit = check@{ attributeCode, values, source ->
            val attributeDef = registry.attributes[attributeCode] ?: return@check
            if (attributeDef.valueSetType == Stage22ValueSetType.OPEN) return@check
            val allowedValueCodes = registry.dictionaries[attributeCode]
                ?.entries
                ?.map { entry -> entry.valueCode }
                ?.toSet()
                .orEmpty()
            if (allowedValueCodes.isEmpty()) return@check

            values.forEach { value ->
                val valueCode = value.trim()
                if (!allowedValueCodes.contains(valueCode)) {
                    issue(
                        issues,
                        "REFERENTIAL_VALUE_CODE_UNKNOWN",
                        "Constraint $source references unknown valueCode '$valueCode' " +
                            "for attribute '$attributeCode'.",
                    )
                }
            }
        }

        constraint.attributeConstraints.forEach { attributeConstraint ->
            checkAttributeCode(attributeConstraint.attributeCode)
            checkClosedValues(
                attributeConstraint.attributeCode,
                attributeConstraint.allowedValues + attributeConstraint.forbiddenValues,
                "attributeConstraints",
            )
        }

        constraint.compatibilityRules.forEach { rule ->
            rule.whenAll.forEach { condition ->
                checkAttributeCode(condition.attributeCode)
                checkClosedValues(
                    condition.attributeCode,
                    condition.values,
                    "compatibility.whenAll",
                )
            }
            rule.apply.forEach { attributeConstraint ->
                checkAttributeCode(attributeConstraint.attributeCode)
                checkClosedValues(
                    attributeConstraint.attributeCode,
                    attributeConstraint.allowedValues + attributeConstraint.forbiddenValues,
                    "compatibility.apply",
                )
            }
        }
    }

    private fun issue(
        issues: MutableList<Stage22SeedValidationIssue>,
        code: String,
        message: String,
    ) {
        issues += Stage22SeedValidationIssue(code = code, message = message)
    }
}
