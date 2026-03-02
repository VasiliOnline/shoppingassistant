package com.example.shoppingassistant.domain.catalog

import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal enum class Stage22Visibility {
    VISIBLE,
    HIDDEN,
}

internal data class Stage22AttributeUsage(
    val attributeCode: String,
    val required: Boolean = false,
    val uiOrder: Int = 0,
    val visibility: Stage22Visibility = Stage22Visibility.VISIBLE,
)

internal data class Stage22CategoryProfile(
    val categoryCode: String,
    val attributes: List<Stage22AttributeUsage>,
    val identityAttributes: List<String> = emptyList(),
    val facetAttributes: List<String> = emptyList(),
    val sourceL0: String? = null,
)

internal data class Stage22RequiredIfCondition(
    val attributeCode: String,
    val valueCode: String,
)

internal data class Stage22RequiredIfRule(
    val whenAll: List<Stage22RequiredIfCondition>,
    val requireAttributeCode: String,
)

internal data class Stage22ConditionalValueRule(
    val whenAll: List<AttributeCondition>,
    val apply: List<Stage22AttributeValueRule>,
)

internal data class Stage22AttributeValueRule(
    val attributeCode: String,
    val allowedValueCodes: List<String> = emptyList(),
    val forbiddenValueCodes: List<String> = emptyList(),
)

internal data class Stage22EffectiveConstraints(
    val allowedValueCodesByAttribute: Map<String, List<String>>,
    val forbiddenValueCodesByAttribute: Map<String, List<String>>,
    val requiredIfRules: List<Stage22RequiredIfRule>,
    val conditionalRules: List<Stage22ConditionalValueRule>,
)

internal data class Stage22EffectiveSpecMeta(
    val isFallback: Boolean,
    val sourceL0: String? = null,
    val coverageInfo: String? = null,
)

internal data class Stage22EffectiveCategorySpec(
    val categoryCode: String,
    val attributes: List<Stage22AttributeUsage>,
    val identityAttributes: List<String>,
    val facetAttributes: List<String>,
    val constraints: Stage22EffectiveConstraints,
    val dictionariesRef: Map<String, Stage22ValueDictionary>,
    val meta: Stage22EffectiveSpecMeta,
)

internal class Stage22ProfileResolver(
    private val registry: Stage22RegistrySnapshot,
    profiles: List<Stage22CategoryProfile>,
) {
    private val profileByCategory: Map<String, Stage22CategoryProfile> =
        profiles
            .groupBy { it.categoryCode }
            .mapValues { (_, values) ->
                values.minWithOrNull(
                    compareBy<Stage22CategoryProfile> { it.sourceL0.orEmpty() }
                        .thenBy { it.categoryCode }
                        .thenBy { profile -> profile.attributes.joinToString("|") { "${it.attributeCode}:${it.uiOrder}:${it.required}:${it.visibility}" } }
                        .thenBy { profile -> profile.identityAttributes.joinToString("|") }
                        .thenBy { profile -> profile.facetAttributes.joinToString("|") },
                ) ?: values.first()
            }

    fun resolve(categoryCode: String): Stage22ResolvedProfile {
        val resolvedCode = categoryCode.trim()
        val fallback = fallbackProfile(resolvedCode)
        val categoryProfile = profileByCategory[resolvedCode] ?: return fallback
        if (categoryProfile.attributes.isEmpty()) return fallback

        val mergedUsage = LinkedHashMap<String, Stage22AttributeUsage>()
        fallback.attributes.forEach { mergedUsage[it.attributeCode] = it }
        categoryProfile.attributes.forEach { usage ->
            mergedUsage[usage.attributeCode] = usage
        }

        val effectiveAttributes = mergedUsage.values
            .sortedWith(compareBy<Stage22AttributeUsage> { it.uiOrder }.thenBy { it.attributeCode })

        val effectiveIdentity = selectDimensionAttributes(
            profileValues = categoryProfile.identityAttributes,
            fallbackValues = fallback.identityAttributes,
            effectiveAttributes = effectiveAttributes,
        )
        val effectiveFacet = selectDimensionAttributes(
            profileValues = categoryProfile.facetAttributes,
            fallbackValues = fallback.facetAttributes,
            effectiveAttributes = effectiveAttributes,
        )

        return Stage22ResolvedProfile(
            categoryCode = resolvedCode,
            attributes = effectiveAttributes,
            identityAttributes = effectiveIdentity,
            facetAttributes = effectiveFacet,
            isFallback = false,
            sourceL0 = categoryProfile.sourceL0 ?: fallback.sourceL0,
        )
    }

    private fun fallbackProfile(categoryCode: String): Stage22ResolvedProfile {
        val preferred = listOf("product_name", "brand", "model")
        val selected = preferred.filter { registry.attributes.containsKey(it) }
        val fallbackCodes = if (selected.isNotEmpty()) {
            selected
        } else {
            registry.attributes.values
                .asSequence()
                .filter { it.isIdentity }
                .map { it.attributeCode }
                .take(3)
                .toList()
        }

        val fallbackAttributes = fallbackCodes.mapIndexed { index, attributeCode ->
            Stage22AttributeUsage(
                attributeCode = attributeCode,
                required = attributeCode == "product_name",
                uiOrder = (index + 1) * 10,
                visibility = Stage22Visibility.VISIBLE,
            )
        }
        val identityAttributes = fallbackAttributes
            .map { it.attributeCode }
            .filter { code -> registry.attributes[code]?.isIdentity == true }
        val facetAttributes = fallbackAttributes
            .map { it.attributeCode }
            .filter { code -> registry.attributes[code]?.isFacet == true }

        return Stage22ResolvedProfile(
            categoryCode = categoryCode,
            attributes = fallbackAttributes,
            identityAttributes = identityAttributes,
            facetAttributes = facetAttributes,
            isFallback = true,
            sourceL0 = categoryCode.substringBefore('.').ifBlank { null },
        )
    }

    private fun selectDimensionAttributes(
        profileValues: List<String>,
        fallbackValues: List<String>,
        effectiveAttributes: List<Stage22AttributeUsage>,
    ): List<String> {
        val allowed = effectiveAttributes.map { it.attributeCode }.toSet()
        val preferred = if (profileValues.isNotEmpty()) profileValues else fallbackValues
        return preferred
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it in allowed }
            .distinct()
            .sorted()
            .toList()
    }
}

internal class Stage22ConstraintsResolver(
    private val registry: Stage22RegistrySnapshot,
    globalConstraints: List<CatalogConstraints>,
    categoryConstraints: List<CatalogConstraints>,
    private val referenceDate: LocalDate = LocalDate.now(),
) {
    private val globalRules: List<CatalogConstraints> = globalConstraints
        .filter { it.scope == ConstraintScope.GLOBAL }
        .filter { it.isEffective(referenceDate) }
        .sortedWith(compareBy<CatalogConstraints> { it.categoryCode.orEmpty() }.thenBy { it.brand.orEmpty() }.thenBy { it.model.orEmpty() }.thenBy { it.deterministicKey() })

    private val categoryRulesByCode: Map<String, List<CatalogConstraints>> = categoryConstraints
        .filter { it.scope == ConstraintScope.CATEGORY }
        .filter { it.isEffective(referenceDate) }
        .groupBy { it.categoryCode.orEmpty() }
        .mapValues { (_, rules) ->
            rules.sortedWith(compareBy<CatalogConstraints> { it.categoryCode.orEmpty() }.thenBy { it.brand.orEmpty() }.thenBy { it.model.orEmpty() }.thenBy { it.deterministicKey() })
        }

    fun resolve(categoryCode: String): Stage22EffectiveConstraints {
        val resolvedCode = categoryCode.trim()
        val rules = buildList {
            addAll(globalRules)
            addAll(categoryRulesByCode[resolvedCode].orEmpty())
        }

        val allowedByAttribute = LinkedHashMap<String, LinkedHashSet<String>>()
        val forbiddenByAttribute = LinkedHashMap<String, LinkedHashSet<String>>()
        val requiredIfRules = mutableListOf<Stage22RequiredIfRule>()
        val conditionalRules = mutableListOf<Stage22ConditionalValueRule>()

        rules.forEach { rule ->
            rule.attributeConstraints.forEach { attributeRule ->
                val attributeCode = attributeRule.attributeCode.trim()
                if (attributeCode.isEmpty()) return@forEach

                val allowed = normalizeValueCodes(attributeCode, attributeRule.allowedValues)
                val forbidden = normalizeValueCodes(attributeCode, attributeRule.forbiddenValues)

                if (allowed.isNotEmpty()) {
                    val bucket = allowedByAttribute.getOrPut(attributeCode) { linkedSetOf() }
                    bucket += allowed
                }
                if (forbidden.isNotEmpty()) {
                    val bucket = forbiddenByAttribute.getOrPut(attributeCode) { linkedSetOf() }
                    bucket += forbidden
                }
            }

            rule.compatibilityRules.forEach { compatibility ->
                val conditionalApply = compatibility.apply.map { attributeRule ->
                    Stage22AttributeValueRule(
                        attributeCode = attributeRule.attributeCode.trim(),
                        allowedValueCodes = normalizeValueCodes(attributeRule.attributeCode, attributeRule.allowedValues),
                        forbiddenValueCodes = normalizeValueCodes(attributeRule.attributeCode, attributeRule.forbiddenValues),
                    )
                }.filter { it.attributeCode.isNotEmpty() }

                if (conditionalApply.isNotEmpty()) {
                    conditionalRules += Stage22ConditionalValueRule(
                        whenAll = compatibility.whenAll,
                        apply = conditionalApply.sortedBy { it.attributeCode },
                    )
                }

                compatibility.whenAll.forEach { condition ->
                    val requireTargets = conditionalApply
                        .filter { it.allowedValueCodes.isNotEmpty() || it.forbiddenValueCodes.isNotEmpty() }
                        .map { it.attributeCode }
                    if (requireTargets.isEmpty()) return@forEach
                    val normalizedValues = normalizeValueCodes(condition.attributeCode, condition.values)
                    normalizedValues.forEach { valueCode ->
                        requireTargets.forEach { requireAttributeCode ->
                            requiredIfRules += Stage22RequiredIfRule(
                                whenAll = listOf(
                                    Stage22RequiredIfCondition(
                                        attributeCode = condition.attributeCode.trim(),
                                        valueCode = valueCode,
                                    ),
                                ),
                                requireAttributeCode = requireAttributeCode,
                            )
                        }
                    }
                }
            }
        }

        val normalizedAllowed = LinkedHashMap<String, List<String>>()
        val normalizedForbidden = LinkedHashMap<String, List<String>>()
        val allAttributes = (allowedByAttribute.keys + forbiddenByAttribute.keys).toSortedSet()
        allAttributes.forEach { attributeCode ->
            val allowed = allowedByAttribute[attributeCode].orEmpty().toMutableSet()
            val forbidden = forbiddenByAttribute[attributeCode].orEmpty().toMutableSet()
            val intersection = allowed.intersect(forbidden)
            if (intersection.isNotEmpty()) {
                throw IllegalStateException(
                    "Conflicting constraints for '$attributeCode' in category '$resolvedCode': " +
                        "values present in both allowed and forbidden: ${intersection.sorted().joinToString(", ")}",
                )
            }

            if (allowed.isNotEmpty()) {
                normalizedAllowed[attributeCode] = allowed.toList().sorted()
            }
            if (forbidden.isNotEmpty()) {
                normalizedForbidden[attributeCode] = forbidden.toList().sorted()
            }
        }

        return Stage22EffectiveConstraints(
            allowedValueCodesByAttribute = normalizedAllowed,
            forbiddenValueCodesByAttribute = normalizedForbidden,
            requiredIfRules = requiredIfRules
                .distinct()
                .sortedWith(compareBy<Stage22RequiredIfRule> { it.requireAttributeCode }.thenBy { it.whenAll.joinToString { c -> "${c.attributeCode}:${c.valueCode}" } }),
            conditionalRules = conditionalRules
                .distinct()
                .sortedBy { it.whenAll.joinToString { condition -> "${condition.attributeCode}:${condition.values.joinToString(",")}" } },
        )
    }

    private fun normalizeValueCodes(
        attributeCode: String,
        values: List<String>,
    ): List<String> {
        val dictionary = registry.dictionaries[attributeCode]
        val allowedValueCodes = dictionary
            ?.entries
            ?.map { it.valueCode.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

        return values
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { value ->
                if (allowedValueCodes.isEmpty()) {
                    value
                } else if (value in allowedValueCodes) {
                    value
                } else {
                    val sampleCodes = allowedValueCodes
                        .asSequence()
                        .sorted()
                        .take(8)
                        .joinToString(", ")
                    throw IllegalStateException(
                        "Constraint value '$value' for attribute '$attributeCode' must be valueCode " +
                            "from dictionary. Example valueCodes: [$sampleCodes].",
                    )
                }
            }
            .distinct()
            .toList()
    }

    private fun CatalogConstraints.deterministicKey(): String = buildString {
        append(scope.name)
        append("|")
        append(categoryCode.orEmpty())
        append("|")
        append(brand.orEmpty())
        append("|")
        append(model.orEmpty())
        append("|")
        append(effectiveFrom.orEmpty())
        append("|")
        append(effectiveTo.orEmpty())
        append("|")
        append(
            attributeConstraints
                .sortedBy { it.attributeCode }
                .joinToString(";") { rule ->
                    rule.attributeCode + ":" +
                        rule.allowedValues.sorted().joinToString(",") + "!" +
                        rule.forbiddenValues.sorted().joinToString(",")
                },
        )
        append("|")
        append(
            compatibilityRules.joinToString(";") { rule ->
                val whenKey = rule.whenAll.joinToString(",") { cond ->
                    cond.attributeCode + ":" + cond.values.sorted().joinToString(":")
                }
                val applyKey = rule.apply.joinToString(",") { apply ->
                    apply.attributeCode + ":" +
                        apply.allowedValues.sorted().joinToString(":") + "!" +
                        apply.forbiddenValues.sorted().joinToString(":")
                }
                "$whenKey->$applyKey"
            },
        )
    }

    private fun CatalogConstraints.isEffective(onDate: LocalDate): Boolean {
        val from = parseIsoDateOrNull(effectiveFrom)
        val to = parseIsoDateOrNull(effectiveTo)
        if (from != null && onDate.isBefore(from)) return false
        if (to != null && onDate.isAfter(to)) return false
        return true
    }

    private fun parseIsoDateOrNull(raw: String?): LocalDate? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

internal class Stage22EffectiveSpecEngine private constructor(
    private val categories: List<Category>,
    private val registry: Stage22RegistrySnapshot,
    profiles: List<Stage22CategoryProfile>,
    globalConstraints: List<CatalogConstraints>,
    categoryConstraints: List<CatalogConstraints>,
) {
    private val categoryCodes = categories.map { it.code }.toSet()
    private val profileResolver = Stage22ProfileResolver(
        registry = registry,
        profiles = profiles,
    )
    private val constraintsResolver = Stage22ConstraintsResolver(
        registry = registry,
        globalConstraints = globalConstraints,
        categoryConstraints = categoryConstraints,
    )
    private val cache = ConcurrentHashMap<String, Stage22EffectiveCategorySpec>()
    private val buildCounter = AtomicInteger(0)

    fun getEffectiveSpec(categoryCode: String): Stage22EffectiveCategorySpec {
        val resolvedCode = categoryCode.trim()
        require(resolvedCode.isNotEmpty()) { "categoryCode must not be blank" }
        require(categoryCodes.contains(resolvedCode)) { "Unknown categoryCode '$resolvedCode'" }

        return cache.computeIfAbsent(resolvedCode) {
            buildCounter.incrementAndGet()
            buildEffectiveSpec(resolvedCode)
        }
    }

    internal fun cacheSize(): Int = cache.size

    internal fun computedSpecCount(): Int = buildCounter.get()

    private fun buildEffectiveSpec(categoryCode: String): Stage22EffectiveCategorySpec {
        val profile = profileResolver.resolve(categoryCode)
        val constraints = constraintsResolver.resolve(categoryCode)
        val dictionariesRef = profile.attributes
            .mapNotNull { usage ->
                registry.dictionaries[usage.attributeCode]?.let { dictionary ->
                    usage.attributeCode to dictionary
                }
            }
            .toMap(LinkedHashMap())

        return Stage22EffectiveCategorySpec(
            categoryCode = categoryCode,
            attributes = profile.attributes,
            identityAttributes = profile.identityAttributes,
            facetAttributes = profile.facetAttributes,
            constraints = constraints,
            dictionariesRef = dictionariesRef,
            meta = Stage22EffectiveSpecMeta(
                isFallback = profile.isFallback,
                sourceL0 = profile.sourceL0 ?: categoryCode.substringBefore('.'),
                coverageInfo = if (profile.isFallback) "fallback_profile" else "category_profile",
            ),
        )
    }

    companion object {
        fun fromSeed(
            categories: List<Category> = CatalogSeed.categories,
            registry: Stage22RegistrySnapshot = Stage22RegistryLoader.loadSnapshot(),
            packages: List<Stage22PackageData> = GenericStage22PackageLoader.loadAll(),
            globalConstraints: List<CatalogConstraints> = GenericStage22PackageLoader.loadGlobalConstraints(),
        ): Stage22EffectiveSpecEngine {
            val profiles = packages
                .asSequence()
                .flatMap { packageData -> packageData.toStage22Profiles(registry).asSequence() }
                .toList()
            val categoryConstraints = packages.flatMap { it.constraints }
            return Stage22EffectiveSpecEngine(
                categories = categories,
                registry = registry,
                profiles = profiles,
                globalConstraints = globalConstraints,
                categoryConstraints = categoryConstraints,
            )
        }
    }
}

internal object Stage22EffectiveSpecProvider {
    private val engine: Stage22EffectiveSpecEngine by lazy { Stage22EffectiveSpecEngine.fromSeed() }

    fun get(categoryCode: String): Stage22EffectiveCategorySpec = engine.getEffectiveSpec(categoryCode)
}

internal data class Stage22ResolvedProfile(
    val categoryCode: String,
    val attributes: List<Stage22AttributeUsage>,
    val identityAttributes: List<String>,
    val facetAttributes: List<String>,
    val isFallback: Boolean,
    val sourceL0: String?,
)

internal fun Stage22PackageData.toStage22Profiles(registry: Stage22RegistrySnapshot): List<Stage22CategoryProfile> =
    profiles.map { profile ->
        val categoryAttributeByCode = profile.categoryAttributes
            .associateBy { categoryAttribute -> categoryAttribute.attributeCode }
        val attributeUsage = profile.attributes.mapIndexed { index, attributeDef ->
            val categoryAttribute = categoryAttributeByCode[attributeDef.code]
            Stage22AttributeUsage(
                attributeCode = attributeDef.code.trim(),
                required = categoryAttribute?.isRequiredForCategory == true ||
                    attributeDef.requiredForOffer ||
                    attributeDef.requiredForSearch ||
                    attributeDef.requiredForExpress,
                uiOrder = categoryAttribute?.uiOrder ?: ((index + 1) * 10),
                visibility = Stage22Visibility.VISIBLE,
            )
        }.sortedWith(compareBy<Stage22AttributeUsage> { it.uiOrder }.thenBy { it.attributeCode })

        val available = attributeUsage.map { it.attributeCode }.toSet()
        val identityAttributes = attributeUsage
            .asSequence()
            .map { it.attributeCode }
            .filter { code -> registry.attributes[code]?.isIdentity == true }
            .plus(sequenceOf("brand", "model", "product_name").filter { it in available })
            .distinct()
            .sorted()
            .toList()

        val facetAttributes = attributeUsage
            .asSequence()
            .map { it.attributeCode }
            .filter { code ->
                registry.attributes[code]?.isFacet == true ||
                    profile.attributes.firstOrNull { it.code == code }?.facetEnabled == true
            }
            .distinct()
            .sorted()
            .toList()

        Stage22CategoryProfile(
            categoryCode = profile.category.code,
            attributes = attributeUsage,
            identityAttributes = identityAttributes,
            facetAttributes = facetAttributes,
            sourceL0 = descriptor.l0Code,
        )
    }
