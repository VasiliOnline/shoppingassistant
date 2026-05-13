package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.CatalogSeed
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.Stage40DedupEntity
import com.example.shoppingassistant.domain.catalog.Stage40DedupTokenMode
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule
import com.example.shoppingassistant.domain.catalog.Stage22ValueType
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.exposed.sql.selectAll

data class Stage4IngestNormalizationOutcome(
    val normalizedAttributes: Map<String, String>,
    val normalizedCount: Int,
    val droppedCount: Int,
    val logicalDedupCount: Int,
    val unknownAttributeCount: Int,
    val reasonCodes: List<String>,
)

interface Stage4ExecutionLayer {
    fun normalizeAttributesForIngest(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String>

    fun normalizeAttributesForIngestStrict(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Stage4IngestNormalizationOutcome

    fun normalizeAttributesForSearch(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String>

    fun normalizeValueForSearch(
        attributeCode: String,
        value: String?,
    ): String?

    fun normalizeCatalogCode(
        value: String?,
    ): String?

    fun toTypedAttributes(
        attributes: Map<String, String>,
    ): Map<String, TypedAttributeValue>

    fun toRawStringAttributes(
        attributes: Map<String, TypedAttributeValue>,
    ): Map<String, String>

    fun renderDedupKey(
        entity: Stage40DedupEntity,
        fields: Map<String, String>,
        fallback: String,
    ): String
}

class Stage4ExecutionLayerImpl(
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() },
    private val appEnvResolver: () -> String = { resolveAppEnv() },
) : Stage4ExecutionLayer {
    private val cache = AtomicReference<CachedSnapshot?>()
    private val cacheLock = Any()

    override fun normalizeAttributesForIngest(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String> = normalizeAttributesForIngestStrict(
        categoryCode = categoryCode,
        attributes = attributes,
    ).normalizedAttributes

    override fun normalizeAttributesForIngestStrict(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Stage4IngestNormalizationOutcome = normalizeAttributesForIngestStrictInternal(
        categoryCode = categoryCode,
        attributes = attributes,
    )

    override fun normalizeAttributesForSearch(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Map<String, String> = normalizeAttributes(
        categoryCode = categoryCode,
        attributes = attributes,
        mode = NormalizationMode.SEARCH,
    )

    override fun normalizeValueForSearch(
        attributeCode: String,
        value: String?,
    ): String? {
        val normalizedAttributeCode = normalizeAttributeCode(attributeCode)
        if (normalizedAttributeCode.isEmpty()) return null
        return normalizeValue(
            snapshot = snapshot(),
            attributeCode = normalizedAttributeCode,
            rawValue = value,
            mode = NormalizationMode.SEARCH,
        )
    }

    override fun normalizeCatalogCode(
        value: String?,
    ): String? = normalizeCategoryCode(value).takeIf { it.isNotEmpty() }

    override fun toTypedAttributes(
        attributes: Map<String, String>,
    ): Map<String, TypedAttributeValue> {
        if (attributes.isEmpty()) return emptyMap()
        val runtimeSnapshot = snapshot()
        val typed = LinkedHashMap<String, TypedAttributeValue>()
        attributes.forEach { (rawKey, rawValue) ->
            val attributeCode = normalizeAttributeCode(rawKey)
            if (attributeCode.isEmpty()) return@forEach
            val trimmedValue = rawValue.trim()
            if (trimmedValue.isEmpty()) return@forEach
            val valueType = runtimeSnapshot.immutableByAttribute[attributeCode]?.valueType
            typed[attributeCode] = toTypedValue(trimmedValue, valueType)
        }
        return typed
    }

    override fun toRawStringAttributes(
        attributes: Map<String, TypedAttributeValue>,
    ): Map<String, String> {
        if (attributes.isEmpty()) return emptyMap()
        val raw = LinkedHashMap<String, String>()
        attributes.forEach { (rawKey, value) ->
            val attributeCode = normalizeAttributeCode(rawKey)
            if (attributeCode.isEmpty()) return@forEach
            val rawValue = value.asRawString().trim()
            if (rawValue.isEmpty()) return@forEach
            raw[attributeCode] = rawValue
        }
        return raw
    }

    override fun renderDedupKey(
        entity: Stage40DedupEntity,
        fields: Map<String, String>,
        fallback: String,
    ): String = snapshot().renderDedupKey(
        entity = entity.name,
        fields = fields.mapValues { (_, value) -> value.trim() },
        fallback = fallback,
    )

    private fun normalizeAttributesForIngestStrictInternal(
        categoryCode: String?,
        attributes: Map<String, String>,
    ): Stage4IngestNormalizationOutcome {
        if (attributes.isEmpty()) {
            return Stage4IngestNormalizationOutcome(
                normalizedAttributes = emptyMap(),
                normalizedCount = 0,
                droppedCount = 0,
                logicalDedupCount = 0,
                unknownAttributeCount = 0,
                reasonCodes = emptyList(),
            )
        }

        val snapshot = snapshot()
        val normalizedCategoryCode = normalizeCategoryCode(categoryCode)
        val allowList = snapshot.allowedAttributesByCategory[normalizedCategoryCode]
        val deduped = LinkedHashMap<String, Pair<String, String>>()
        val reasons = mutableListOf<String>()
        var unknownAttributeCount = 0
        var logicalDedupCount = 0

        attributes.forEach { (rawKey, rawValue) ->
            val attributeCode = normalizeAttributeCode(rawKey)
            if (attributeCode.isEmpty()) {
                reasons += "ATTRIBUTE_CODE_BLANK"
                return@forEach
            }

            val rule = snapshot.rulesByAttribute[attributeCode]
            val immutable = snapshot.immutableByAttribute[attributeCode]
            val isUnknownByCategory = allowList != null &&
                allowList.isNotEmpty() &&
                attributeCode !in allowList
            if (rule == null || immutable == null || isUnknownByCategory) {
                unknownAttributeCount += 1
                reasons += "UNKNOWN_ATTRIBUTE:$attributeCode"
                return@forEach
            }

            val typedConstraint = snapshot.typedConstraintsByAttribute[attributeCode]

            val normalizedValue = normalizeValue(
                snapshot = snapshot,
                attributeCode = attributeCode,
                rawValue = rawValue,
                mode = NormalizationMode.INGEST,
            )
            if (normalizedValue == null) {
                reasons += "UNKNOWN_CLOSED_SET_VALUE:$attributeCode"
                return@forEach
            }

            val (valueForType, unitReason) = normalizeValueWithUnitConstraint(
                value = normalizedValue,
                valueType = immutable.valueType,
                typedConstraint = typedConstraint,
            )
            if (unitReason != null || valueForType == null) {
                reasons += "UNIT_MISMATCH:$attributeCode"
                return@forEach
            }

            val compatibleValue = normalizeByValueType(
                value = valueForType,
                valueType = immutable.valueType,
            )
            if (compatibleValue == null) {
                reasons += "INCOMPATIBLE_VALUE:$attributeCode"
                return@forEach
            }

            val typedReason = validateTypedConstraint(
                attributeCode = attributeCode,
                value = compatibleValue,
                valueType = immutable.valueType,
                typedConstraint = typedConstraint,
            )
            if (typedReason != null) {
                reasons += "$typedReason:$attributeCode"
                return@forEach
            }

            val dedupKey = snapshot.renderDedupKey(
                entity = Stage40DedupEntity.CATEGORY_PROFILE_ATTRIBUTE.name,
                fields = mapOf(
                    "categoryCode" to normalizedCategoryCode,
                    "attributeCode" to attributeCode,
                ),
                fallback = "$normalizedCategoryCode|$attributeCode",
            )

            val previous = deduped.putIfAbsent(dedupKey, attributeCode to compatibleValue)
            if (previous != null) {
                logicalDedupCount += 1
                reasons += "LOGICAL_DEDUP_ATTRIBUTE:$attributeCode"
            }
        }

        val normalizedAttributes = if (deduped.isEmpty()) {
            emptyMap()
        } else {
            LinkedHashMap<String, String>().apply {
                deduped.values.forEach { (attributeCode, value) ->
                    put(attributeCode, value)
                }
            }
        }
        reasons += evaluateRequiredIfRules(
            categoryCode = normalizedCategoryCode,
            normalizedAttributes = normalizedAttributes,
            typedConstraints = snapshot.typedConstraintsByAttribute.values,
        )

        return Stage4IngestNormalizationOutcome(
            normalizedAttributes = normalizedAttributes,
            normalizedCount = normalizedAttributes.size,
            droppedCount = reasons.count { !it.startsWith("LOGICAL_DEDUP_ATTRIBUTE:") },
            logicalDedupCount = logicalDedupCount,
            unknownAttributeCount = unknownAttributeCount,
            reasonCodes = reasons.distinct(),
        )
    }

    private fun normalizeAttributes(
        categoryCode: String?,
        attributes: Map<String, String>,
        mode: NormalizationMode,
    ): Map<String, String> {
        if (attributes.isEmpty()) return emptyMap()

        val snapshot = snapshot()
        val normalizedCategoryCode = normalizeCategoryCode(categoryCode)
        val deduped = LinkedHashMap<String, Pair<String, String>>()
        attributes.forEach { (rawKey, rawValue) ->
            val attributeCode = normalizeAttributeCode(rawKey)
            if (attributeCode.isEmpty()) return@forEach

            val normalizedValue = normalizeValue(
                snapshot = snapshot,
                attributeCode = attributeCode,
                rawValue = rawValue,
                mode = mode,
            ) ?: return@forEach

            val dedupKey = snapshot.renderDedupKey(
                entity = Stage40DedupEntity.CATEGORY_PROFILE_ATTRIBUTE.name,
                fields = mapOf(
                    "categoryCode" to normalizedCategoryCode,
                    "attributeCode" to attributeCode,
                ),
                fallback = "$normalizedCategoryCode|$attributeCode",
            )

            deduped.putIfAbsent(dedupKey, attributeCode to normalizedValue)
        }

        if (deduped.isEmpty()) return emptyMap()
        return LinkedHashMap<String, String>().apply {
            deduped.values.forEach { (attributeCode, value) ->
                put(attributeCode, value)
            }
        }
    }

    private fun normalizeValue(
        snapshot: RuntimeSnapshot,
        attributeCode: String,
        rawValue: String?,
        mode: NormalizationMode,
    ): String? {
        val trimmed = rawValue?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val rule = snapshot.rulesByAttribute[attributeCode] ?: return trimmed
        if (!rule.dictionaryBacked) {
            val normalized = when (rule.dedupTokenMode) {
                Stage40DedupTokenMode.VALUE_CODE -> trimmed.uppercase(Locale.ROOT)
                Stage40DedupTokenMode.NORMALIZED_TEXT -> normalizeFreeText(trimmed)
            }
            return normalized.takeIf { it.isNotEmpty() }
        }

        val lookupToken = normalizeLookupToken(trimmed)
        val valueCode = snapshot.dictionaryTokensByAttribute[attributeCode]?.get(lookupToken)
        if (valueCode != null) {
            return valueCode
        }
        if (mode == NormalizationMode.INGEST && !rule.acceptsFreeText) {
            return null
        }

        val fallback = when (rule.dedupTokenMode) {
            Stage40DedupTokenMode.VALUE_CODE -> trimmed.uppercase(Locale.ROOT)
            Stage40DedupTokenMode.NORMALIZED_TEXT -> normalizeFreeText(trimmed)
        }
        return fallback.takeIf { it.isNotEmpty() }
    }

    private fun normalizeByValueType(
        value: String,
        valueType: Stage22ValueType,
    ): String? {
        return when (valueType) {
            Stage22ValueType.STRING, Stage22ValueType.ENUM -> value
            Stage22ValueType.NUMBER -> normalizeNumberValue(value)
            Stage22ValueType.BOOLEAN -> normalizeBooleanValue(value)
        }
    }

    private fun normalizeNumberValue(rawValue: String): String? {
        val normalized = rawValue.trim()
            .replace(',', '.')
            .replace(NUMBER_SPACES_REGEX, "")
        if (!NUMBER_REGEX.matches(normalized)) return null
        return normalized
    }

    private fun normalizeBooleanValue(rawValue: String): String? {
        val normalized = normalizeLookupToken(rawValue)
        return when (normalized) {
            "true", "1", "yes", "y", "да", "истина" -> "true"
            "false", "0", "no", "n", "нет", "ложь" -> "false"
            else -> null
        }
    }

    private fun normalizeValueWithUnitConstraint(
        value: String,
        valueType: Stage22ValueType,
        typedConstraint: RuntimeTypedConstraint?,
    ): Pair<String?, String?> {
        if (typedConstraint?.expectedUnit.isNullOrBlank() || valueType != Stage22ValueType.NUMBER) {
            return value to null
        }
        val expectedUnit = typedConstraint?.expectedUnit ?: return value to null
        val match = NUMBER_WITH_OPTIONAL_UNIT_REGEX.matchEntire(value.trim())
            ?: return if (UNIT_TOKEN_REGEX.containsMatchIn(value)) {
                null to "UNIT_MISMATCH"
            } else {
                value to null
            }
        val numberToken = match.groupValues[1]
        val unitToken = match.groupValues.getOrNull(2).orEmpty().trim()
        if (unitToken.isNotEmpty()) {
            val actualUnit = normalizeUnitToken(unitToken)
            val expectedNormalized = normalizeUnitToken(expectedUnit)
            if (actualUnit != expectedNormalized) {
                return null to "UNIT_MISMATCH"
            }
        }
        return numberToken to null
    }

    private fun validateTypedConstraint(
        attributeCode: String,
        value: String,
        valueType: Stage22ValueType,
        typedConstraint: RuntimeTypedConstraint?,
    ): String? {
        if (typedConstraint == null) return null

        if (typedConstraint.valueType != valueType) {
            return "INCOMPATIBLE_VALUE"
        }

        if (typedConstraint.enumOnly && valueType == Stage22ValueType.ENUM) {
            if (!ENUM_VALUE_REGEX.matches(value)) {
                return "PATTERN_MISMATCH"
            }
        }

        typedConstraint.regexPattern?.let { regexPattern ->
            val regex = runCatching { Regex(regexPattern) }.getOrNull()
            if (regex != null && !regex.matches(value)) {
                return "PATTERN_MISMATCH"
            }
        }

        if (typedConstraint.minValue != null || typedConstraint.maxValue != null) {
            val numeric = value.replace(',', '.').toDoubleOrNull()
                ?: return "OUT_OF_RANGE"
            val min = typedConstraint.minValue
            val max = typedConstraint.maxValue
            if (min != null && numeric < min) {
                return "OUT_OF_RANGE"
            }
            if (max != null && numeric > max) {
                return "OUT_OF_RANGE"
            }
        }

        return null
    }

    private fun evaluateRequiredIfRules(
        categoryCode: String,
        normalizedAttributes: Map<String, String>,
        typedConstraints: Collection<RuntimeTypedConstraint>,
    ): List<String> {
        if (categoryCode.isEmpty() || normalizedAttributes.isEmpty() || typedConstraints.isEmpty()) {
            return emptyList()
        }
        val reasons = mutableListOf<String>()
        typedConstraints.forEach { constraint ->
            if (constraint.requiredIfRules.isEmpty()) return@forEach
            val isRequired = constraint.requiredIfRules.any { rule ->
                rule.categoryCode == categoryCode &&
                    rule.whenAll.all { condition ->
                        val rawValue = normalizedAttributes[condition.attributeCode] ?: return@all false
                        val normalizedValue = normalizeLookupToken(rawValue)
                        when (condition.op) {
                            AttributeConditionOp.EQUALS_ANY -> condition.values.any { value ->
                                normalizedValue == value
                            }
                            AttributeConditionOp.STARTS_WITH_ANY -> condition.values.any { value ->
                                normalizedValue.startsWith(value)
                            }
                        }
                    }
            }
            if (isRequired && normalizedAttributes[constraint.attributeCode] == null) {
                reasons += "REQUIRED_IF_MISSING:${constraint.attributeCode}"
            }
        }
        return reasons
    }

    private fun normalizeUnitToken(rawUnit: String): String {
        val normalized = rawUnit.trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, "")
            .replace("°", "")
        return when (normalized) {
            "c", "celsius", "celcius", "с", "ц" -> "c"
            "g", "gr", "gram", "grams", "гр", "г" -> "g"
            "kg", "кг", "kilogram", "kilograms" -> "kg"
            "ml", "мл", "milliliter", "millilitre", "milliliters", "millilitres" -> "ml"
            "l", "lt", "liter", "litre", "liters", "litres", "л" -> "l"
            "day", "days", "d", "день", "дней", "сут", "сутки" -> "day"
            "kcal", "ккал" -> "kcal"
            else -> normalized
        }
    }

    private fun toTypedValue(
        rawValue: String,
        valueType: Stage22ValueType?,
    ): TypedAttributeValue {
        return when (valueType) {
            Stage22ValueType.NUMBER -> {
                val normalized = normalizeNumberValue(rawValue)
                val number = normalized?.toDoubleOrNull()
                if (number != null) TypedAttributeValue.Number(number)
                else TypedAttributeValue.Text(rawValue)
            }
            Stage22ValueType.BOOLEAN -> {
                when (normalizeBooleanValue(rawValue)) {
                    "true" -> TypedAttributeValue.Bool(true)
                    "false" -> TypedAttributeValue.Bool(false)
                    else -> TypedAttributeValue.Text(rawValue)
                }
            }
            Stage22ValueType.STRING, Stage22ValueType.ENUM -> TypedAttributeValue.Text(rawValue)
            null -> TypedAttributeValue.fromRawString(rawValue)
        }
    }

    private fun snapshot(): RuntimeSnapshot {
        val now = timeProviderMs()
        val cached = cache.get()
        if (cached != null && now - cached.loadedAtMs <= CACHE_TTL_MS) {
            return cached.snapshot
        }
        synchronized(cacheLock) {
            val secondRead = cache.get()
            if (secondRead != null && now - secondRead.loadedAtMs <= CACHE_TTL_MS) {
                return secondRead.snapshot
            }
            val loaded = loadSnapshot()
            cache.set(CachedSnapshot(loadedAtMs = now, snapshot = loaded))
            return loaded
        }
    }

    private fun loadSnapshot(): RuntimeSnapshot {
        val appEnv = appEnvResolver().trim().lowercase(Locale.ROOT)
        val dbSnapshot = RuntimeDbSnapshot(
            rules = loadRulesFromDb(),
            immutableAttributes = loadImmutableAttributesFromDb(),
            templates = loadTemplatesFromDb(),
            dictionaryTokens = loadDictionaryTokensFromDb(),
            allowedAttributesByCategory = loadAllowedAttributesByCategoryFromDb(),
            typedConstraints = loadTypedConstraintsFromDb(),
        )
        when (dbSnapshot.state()) {
            RuntimeDbSnapshotState.COMPLETE -> return dbSnapshot.toRuntimeSnapshot()
            RuntimeDbSnapshotState.EMPTY -> {
                if (isManagedEnv(appEnv)) {
                    throw IllegalStateException(
                        "Stage4 runtime DB snapshot is empty for managed env '$appEnv'. " +
                            "Apply Stage4 migrations/seeds before server startup.",
                    )
                }
                logSeedFallback(appEnv = appEnv, reason = "DB snapshot is empty")
                return loadSeedSnapshot()
            }
            RuntimeDbSnapshotState.PARTIAL -> {
                val missing = dbSnapshot.missingComponents()
                if (isManagedEnv(appEnv)) {
                    throw IllegalStateException(
                        "Stage4 runtime DB snapshot is partial for managed env '$appEnv' (missing: ${missing.joinToString(", ")}). " +
                            "Use all-db-or-all-seed policy by fixing DB snapshot completeness.",
                    )
                }
                logSeedFallback(
                    appEnv = appEnv,
                    reason = "DB snapshot is partial (missing: ${missing.joinToString(", ")})",
                )
                return loadSeedSnapshot()
            }
        }
    }

    private fun RuntimeDbSnapshot.toRuntimeSnapshot(): RuntimeSnapshot =
        RuntimeSnapshot(
            rulesByAttribute = rules.associateBy { it.attributeCode },
            immutableByAttribute = immutableAttributes.associateBy { it.attributeCode },
            templatesByEntity = templates.associateBy { it.entity },
            dictionaryTokensByAttribute = dictionaryTokens,
            allowedAttributesByCategory = allowedAttributesByCategory,
            typedConstraintsByAttribute = typedConstraints.associateBy { it.attributeCode },
        )

    private fun loadSeedSnapshot(): RuntimeSnapshot =
        RuntimeSnapshot(
            rulesByAttribute = loadRulesFromSeed().associateBy { it.attributeCode },
            immutableByAttribute = loadImmutableAttributesFromSeed().associateBy { it.attributeCode },
            templatesByEntity = loadTemplatesFromSeed().associateBy { it.entity },
            dictionaryTokensByAttribute = loadDictionaryTokensFromSeed(),
            allowedAttributesByCategory = loadAllowedAttributesByCategoryFromSeed(),
            typedConstraintsByAttribute = loadTypedConstraintsFromSeed().associateBy { it.attributeCode },
        )

    private fun RuntimeDbSnapshot.state(): RuntimeDbSnapshotState {
        val missing = missingComponents()
        if (missing.isEmpty()) return RuntimeDbSnapshotState.COMPLETE
        return if (missing.size == STAGE4_RUNTIME_COMPONENT_COUNT) {
            RuntimeDbSnapshotState.EMPTY
        } else {
            RuntimeDbSnapshotState.PARTIAL
        }
    }

    private fun RuntimeDbSnapshot.missingComponents(): List<String> {
        val missing = mutableListOf<String>()
        if (rules.isEmpty()) missing += "rules"
        if (immutableAttributes.isEmpty()) missing += "immutable_attributes"
        if (templates.isEmpty()) missing += "dedup_templates"
        if (dictionaryTokens.isEmpty()) missing += "dictionary_tokens"
        if (allowedAttributesByCategory.isEmpty()) missing += "allowed_attributes_by_category"
        if (typedConstraints.isEmpty()) missing += "typed_constraints"
        return missing
    }

    private fun isManagedEnv(appEnv: String): Boolean =
        appEnv in MANAGED_STAGE4_ENVS

    private fun logSeedFallback(
        appEnv: String,
        reason: String,
    ) {
        System.err.println(
            "[Stage4ExecutionLayer] Falling back to seed snapshot for env='$appEnv': $reason",
        )
    }

    private fun loadRulesFromDb(): List<RuntimeRule> = runCatching {
        CatalogStage4NormalizationRulesTable
            .selectAll()
            .mapNotNull { row ->
                val attributeCode = normalizeAttributeCode(row[CatalogStage4NormalizationRulesTable.attributeCode])
                if (attributeCode.isEmpty()) return@mapNotNull null
                RuntimeRule(
                    attributeCode = attributeCode,
                    dictionaryBacked = row[CatalogStage4NormalizationRulesTable.dictionaryBacked],
                    acceptsFreeText = row[CatalogStage4NormalizationRulesTable.acceptsFreeText],
                    dedupTokenMode = parseDedupTokenMode(row[CatalogStage4NormalizationRulesTable.dedupTokenMode]),
                )
            }
            .distinctBy { it.attributeCode }
    }.getOrElse { emptyList() }

    private fun loadRulesFromSeed(): List<RuntimeRule> =
        CatalogSeed.stage40NormalizationContract.rules
            .mapNotNull { rule ->
                val attributeCode = normalizeAttributeCode(rule.attributeCode)
                if (attributeCode.isEmpty()) return@mapNotNull null
                RuntimeRule(
                    attributeCode = attributeCode,
                    dictionaryBacked = rule.dictionaryBacked,
                    acceptsFreeText = rule.acceptsFreeText,
                    dedupTokenMode = rule.dedupTokenMode,
                )
            }
            .distinctBy { it.attributeCode }

    private fun loadImmutableAttributesFromDb(): List<RuntimeImmutableAttribute> = runCatching {
        CatalogStage4ImmutableAttributesTable
            .selectAll()
            .mapNotNull { row ->
                val attributeCode = normalizeAttributeCode(row[CatalogStage4ImmutableAttributesTable.attributeCode])
                if (attributeCode.isEmpty()) return@mapNotNull null
                RuntimeImmutableAttribute(
                    attributeCode = attributeCode,
                    valueType = parseValueType(row[CatalogStage4ImmutableAttributesTable.valueType]),
                )
            }
            .distinctBy { it.attributeCode }
    }.getOrElse { emptyList() }

    private fun loadImmutableAttributesFromSeed(): List<RuntimeImmutableAttribute> =
        CatalogSeed.stage40ImmutableSchema.attributes
            .mapNotNull { attribute ->
                val attributeCode = normalizeAttributeCode(attribute.attributeCode)
                if (attributeCode.isEmpty()) return@mapNotNull null
                RuntimeImmutableAttribute(
                    attributeCode = attributeCode,
                    valueType = attribute.valueType,
                )
            }
            .distinctBy { it.attributeCode }

    private fun loadTypedConstraintsFromDb(): List<RuntimeTypedConstraint> = runCatching {
        CatalogStage4TypedConstraintsTable
            .selectAll()
            .mapNotNull { row ->
                val attributeCode = normalizeAttributeCode(row[CatalogStage4TypedConstraintsTable.attributeCode])
                if (attributeCode.isEmpty()) return@mapNotNull null
                val requiredIfRules = normalizeRequiredIfRules(
                    row[CatalogStage4TypedConstraintsTable.requiredIf],
                )
                RuntimeTypedConstraint(
                    attributeCode = attributeCode,
                    valueType = parseValueType(row[CatalogStage4TypedConstraintsTable.valueType]),
                    enumOnly = row[CatalogStage4TypedConstraintsTable.enumOnly],
                    expectedUnit = row[CatalogStage4TypedConstraintsTable.expectedUnit]
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    regexPattern = row[CatalogStage4TypedConstraintsTable.regexPattern]
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    minValue = row[CatalogStage4TypedConstraintsTable.minValue],
                    maxValue = row[CatalogStage4TypedConstraintsTable.maxValue],
                    requiredIfRules = requiredIfRules,
                )
            }
            .distinctBy { it.attributeCode }
    }.getOrElse { emptyList() }

    private fun loadTypedConstraintsFromSeed(): List<RuntimeTypedConstraint> =
        CatalogSeed.stage40TypedConstraints.constraints
            .mapNotNull { constraint ->
                val attributeCode = normalizeAttributeCode(constraint.attributeCode)
                if (attributeCode.isEmpty()) return@mapNotNull null
                RuntimeTypedConstraint(
                    attributeCode = attributeCode,
                    valueType = constraint.valueType,
                    enumOnly = constraint.enumOnly,
                    expectedUnit = constraint.unit?.trim()?.takeIf { it.isNotEmpty() },
                    regexPattern = constraint.regex?.trim()?.takeIf { it.isNotEmpty() },
                    minValue = constraint.minValue,
                    maxValue = constraint.maxValue,
                    requiredIfRules = normalizeRequiredIfRules(constraint.requiredIf),
                )
            }
            .distinctBy { it.attributeCode }

    private fun loadAllowedAttributesByCategoryFromDb(): Map<String, Set<String>> = runCatching {
        val byCategory = LinkedHashMap<String, MutableSet<String>>()
        CategoryAttributesTable
            .selectAll()
            .forEach { row ->
                val categoryCode = normalizeCategoryCode(row[CategoryAttributesTable.categoryCode])
                val attributeCode = normalizeAttributeCode(row[CategoryAttributesTable.attributeCode])
                if (categoryCode.isEmpty() || attributeCode.isEmpty()) return@forEach
                byCategory.getOrPut(categoryCode) { LinkedHashSet() }.add(attributeCode)
            }
        byCategory.mapValues { (_, attributes) -> attributes.toSet() }
    }.getOrElse { emptyMap() }

    private fun loadAllowedAttributesByCategoryFromSeed(): Map<String, Set<String>> {
        val byCategory = LinkedHashMap<String, MutableSet<String>>()
        CatalogSeed.categoryWriteSpecs.forEach { spec ->
            val categoryCode = normalizeCategoryCode(spec.category.code)
            if (categoryCode.isEmpty()) return@forEach
            val bucket = byCategory.getOrPut(categoryCode) { LinkedHashSet() }
            spec.categoryAttributes.forEach { categoryAttribute ->
                val attributeCode = normalizeAttributeCode(categoryAttribute.attributeCode)
                if (attributeCode.isNotEmpty()) {
                    bucket.add(attributeCode)
                }
            }
        }
        return byCategory.mapValues { (_, attributes) -> attributes.toSet() }
    }

    private fun loadTemplatesFromDb(): List<RuntimeTemplate> = runCatching {
        CatalogStage4DedupTemplatesTable
            .selectAll()
            .mapNotNull { row ->
                val entity = row[CatalogStage4DedupTemplatesTable.entity].trim().uppercase(Locale.ROOT)
                if (entity.isEmpty()) return@mapNotNull null
                val templateExpr = row[CatalogStage4DedupTemplatesTable.templateExpr].trim()
                if (templateExpr.isEmpty()) return@mapNotNull null
                RuntimeTemplate(
                    entity = entity,
                    templateExpr = templateExpr,
                    fields = row[CatalogStage4DedupTemplatesTable.fieldNames]
                        .map { field -> field.trim() }
                        .filter { field -> field.isNotEmpty() },
                )
            }
            .distinctBy { it.entity }
    }.getOrElse { emptyList() }

    private fun loadTemplatesFromSeed(): List<RuntimeTemplate> =
        CatalogSeed.stage40DedupKeys.templates
            .map { template ->
                RuntimeTemplate(
                    entity = template.entity.name,
                    templateExpr = template.template.trim(),
                    fields = template.fields
                        .map { field -> field.trim() }
                        .filter { field -> field.isNotEmpty() },
                )
            }
            .filter { template -> template.templateExpr.isNotEmpty() }
            .distinctBy { it.entity }

    private fun loadDictionaryTokensFromDb(): Map<String, Map<String, String>> = runCatching {
        val tokensByAttribute = LinkedHashMap<String, LinkedHashMap<String, String>>()
        AttributeValueDictTable.selectAll().forEach { row ->
            val attributeCode = normalizeAttributeCode(row[AttributeValueDictTable.attributeCode])
            val valueCode = row[AttributeValueDictTable.canonicalCode].trim()
            if (attributeCode.isEmpty() || valueCode.isEmpty()) return@forEach

            val bucket = tokensByAttribute.getOrPut(attributeCode) { LinkedHashMap() }
            registerToken(bucket, valueCode, valueCode)
            registerToken(bucket, row[AttributeValueDictTable.canonicalValue], valueCode)
            row[AttributeValueDictTable.synonyms]
                .orEmpty()
                .forEach { alias ->
                    registerToken(bucket, alias, valueCode)
                }
        }
        tokensByAttribute.mapValues { entry -> LinkedHashMap(entry.value) }
    }.getOrElse { emptyMap() }

    private fun loadDictionaryTokensFromSeed(): Map<String, Map<String, String>> {
        val tokensByAttribute = LinkedHashMap<String, LinkedHashMap<String, String>>()
        CatalogSeed.categoryWriteSpecs.forEach { spec ->
            spec.valueDictionaries.forEach { dictionary ->
                val attributeCode = normalizeAttributeCode(dictionary.attributeCode)
                if (attributeCode.isEmpty()) return@forEach
                val bucket = tokensByAttribute.getOrPut(attributeCode) { LinkedHashMap() }
                dictionary.entries.forEach { entry ->
                    val valueCode = entry.canonicalCode.trim()
                    if (valueCode.isEmpty()) return@forEach
                    registerToken(bucket, valueCode, valueCode)
                    registerToken(bucket, entry.canonicalValue, valueCode)
                    entry.synonyms.forEach { alias ->
                        registerToken(bucket, alias, valueCode)
                    }
                }
            }
        }
        return tokensByAttribute.mapValues { entry -> LinkedHashMap(entry.value) }
    }

    private fun registerToken(
        bucket: MutableMap<String, String>,
        rawToken: String?,
        valueCode: String,
    ) {
        val normalizedToken = normalizeLookupToken(rawToken.orEmpty())
        if (normalizedToken.isEmpty()) return
        bucket.putIfAbsent(normalizedToken, valueCode)
    }

    private fun normalizeRequiredIfRules(
        rules: List<Stage40RequiredIfRule>,
    ): List<RuntimeRequiredIfRule> =
        rules
            .mapNotNull { rule ->
                val categoryCode = normalizeCategoryCode(rule.categoryCode)
                if (categoryCode.isEmpty()) return@mapNotNull null
                val whenAll = rule.whenAll
                    .mapNotNull { condition ->
                        val attributeCode = normalizeAttributeCode(condition.attributeCode)
                        val values = condition.values
                            .map { value -> normalizeLookupToken(value) }
                            .filter { value -> value.isNotEmpty() }
                            .distinct()
                        if (attributeCode.isEmpty() || values.isEmpty()) {
                            null
                        } else {
                            RuntimeRequiredIfCondition(
                                attributeCode = attributeCode,
                                op = condition.op,
                                values = values,
                            )
                        }
                    }
                    .sortedWith(
                        compareBy<RuntimeRequiredIfCondition> { it.attributeCode }
                            .thenBy { it.op.name }
                            .thenBy { it.values.joinToString("|") },
                    )
                if (whenAll.isEmpty()) return@mapNotNull null
                RuntimeRequiredIfRule(
                    categoryCode = categoryCode,
                    whenAll = whenAll,
                )
            }
            .distinct()
            .sortedWith(
                compareBy<RuntimeRequiredIfRule> { it.categoryCode }
                    .thenBy { it.whenAll.joinToString("|") { c -> "${c.attributeCode}:${c.op.name}:${c.values.joinToString(",")}" } },
            )

    private fun normalizeCategoryCode(value: String?): String =
        value?.trim()?.uppercase(Locale.ROOT).orEmpty()

    private fun normalizeAttributeCode(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(ATTRIBUTE_SEPARATOR_REGEX, "_")
            .replace(ATTRIBUTE_DISALLOWED_REGEX, "")
            .replace(MULTI_UNDERSCORE_REGEX, "_")
            .trim('_')

    private fun normalizeFreeText(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun normalizeLookupToken(value: String): String =
        value.trim()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace(DASHES_REGEX, " ")
            .replace(LOOKUP_DISALLOWED_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun parseDedupTokenMode(raw: String): Stage40DedupTokenMode =
        runCatching {
            Stage40DedupTokenMode.valueOf(raw.trim().uppercase(Locale.ROOT))
        }.getOrDefault(Stage40DedupTokenMode.NORMALIZED_TEXT)

    private fun parseValueType(raw: String): Stage22ValueType =
        runCatching {
            Stage22ValueType.valueOf(raw.trim().uppercase(Locale.ROOT))
        }.getOrDefault(Stage22ValueType.STRING)

    private data class CachedSnapshot(
        val loadedAtMs: Long,
        val snapshot: RuntimeSnapshot,
    )

    private data class RuntimeSnapshot(
        val rulesByAttribute: Map<String, RuntimeRule>,
        val immutableByAttribute: Map<String, RuntimeImmutableAttribute>,
        val templatesByEntity: Map<String, RuntimeTemplate>,
        val dictionaryTokensByAttribute: Map<String, Map<String, String>>,
        val allowedAttributesByCategory: Map<String, Set<String>>,
        val typedConstraintsByAttribute: Map<String, RuntimeTypedConstraint>,
    ) {
        fun renderDedupKey(
            entity: String,
            fields: Map<String, String>,
            fallback: String,
        ): String {
            val template = templatesByEntity[entity] ?: return fallback
            val rendered = PLACEHOLDER_REGEX.replace(template.templateExpr) { match ->
                val field = match.groupValues[1]
                fields[field].orEmpty()
            }.trim()
            return rendered.ifEmpty { fallback }
        }
    }

    private data class RuntimeDbSnapshot(
        val rules: List<RuntimeRule>,
        val immutableAttributes: List<RuntimeImmutableAttribute>,
        val templates: List<RuntimeTemplate>,
        val dictionaryTokens: Map<String, Map<String, String>>,
        val allowedAttributesByCategory: Map<String, Set<String>>,
        val typedConstraints: List<RuntimeTypedConstraint>,
    )

    private enum class RuntimeDbSnapshotState {
        COMPLETE,
        EMPTY,
        PARTIAL,
    }

    private data class RuntimeRule(
        val attributeCode: String,
        val dictionaryBacked: Boolean,
        val acceptsFreeText: Boolean,
        val dedupTokenMode: Stage40DedupTokenMode,
    )

    private data class RuntimeImmutableAttribute(
        val attributeCode: String,
        val valueType: Stage22ValueType,
    )

    private data class RuntimeTemplate(
        val entity: String,
        val templateExpr: String,
        val fields: List<String>,
    )

    private data class RuntimeTypedConstraint(
        val attributeCode: String,
        val valueType: Stage22ValueType,
        val enumOnly: Boolean,
        val expectedUnit: String?,
        val regexPattern: String?,
        val minValue: Double?,
        val maxValue: Double?,
        val requiredIfRules: List<RuntimeRequiredIfRule>,
    )

    private data class RuntimeRequiredIfRule(
        val categoryCode: String,
        val whenAll: List<RuntimeRequiredIfCondition>,
    )

    private data class RuntimeRequiredIfCondition(
        val attributeCode: String,
        val op: AttributeConditionOp,
        val values: List<String>,
    )

    private enum class NormalizationMode {
        INGEST,
        SEARCH,
    }

    private companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val STAGE4_RUNTIME_COMPONENT_COUNT = 6
        private val MANAGED_STAGE4_ENVS = setOf("prod", "production", "staging", "stage")
        private val PLACEHOLDER_REGEX = Regex("\\{([^{}]+)}")
        private val ATTRIBUTE_SEPARATOR_REGEX = Regex("[\\s\\-]+")
        private val ATTRIBUTE_DISALLOWED_REGEX = Regex("[^\\p{L}\\p{N}_]")
        private val MULTI_UNDERSCORE_REGEX = Regex("_+")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val DASHES_REGEX = Regex("[-‐‑‒–—]+")
        private val LOOKUP_DISALLOWED_REGEX = Regex("[^\\p{L}\\p{N}\\s]")
        private val NUMBER_SPACES_REGEX = Regex("\\s+")
        private val NUMBER_REGEX = Regex("^-?[0-9]+(?:\\.[0-9]+)?$")
        private val NUMBER_WITH_OPTIONAL_UNIT_REGEX = Regex("^\\s*(-?[0-9]+(?:[\\.,][0-9]+)?)\\s*([\\p{L}%°/]+)?\\s*$")
        private val UNIT_TOKEN_REGEX = Regex("[\\p{L}%°/]+")
        private val ENUM_VALUE_REGEX = Regex("^[A-Z0-9_]+$")

        private fun resolveAppEnv(): String =
            (System.getenv("APP_ENV")
                ?: System.getenv("APP_STAGE")
                ?: System.getenv("ENV")
                ?: "local")
                .trim()
                .lowercase(Locale.ROOT)
    }
}
