package com.example.shoppingassistant.server.catalog

import com.example.shoppingassistant.domain.catalog.AttributeDataType
import com.example.shoppingassistant.domain.catalog.AttributeDef
import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeValueDict
import com.example.shoppingassistant.domain.catalog.AttributeValueDictEntry
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryWriteSpec
import com.example.shoppingassistant.domain.catalog.CatalogWriteRepository
import com.example.shoppingassistant.domain.catalog.CatalogReadRepository
import com.example.shoppingassistant.domain.catalog.CatalogTaxonomyRepository
import com.example.shoppingassistant.domain.catalog.CategoryReplacementResolver
import com.example.shoppingassistant.domain.catalog.Category
import com.example.shoppingassistant.domain.catalog.CategoryAttribute
import com.example.shoppingassistant.domain.catalog.CategorySegment
import com.example.shoppingassistant.domain.catalog.CategoryStatus
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfCondition
import com.example.shoppingassistant.domain.catalog.Stage40RequiredIfRule
import com.example.shoppingassistant.domain.catalog.toCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope
import com.example.shoppingassistant.server.db.DatabaseFactory
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.andWhere
import java.util.Locale

/**
 * Каталог категорий/атрибутов в Postgres (Exposed).
 */
class CatalogRepositoryImpl :
    CatalogReadRepository,
    CatalogTaxonomyRepository,
    CatalogWriteRepository {

    override suspend fun listCategories(): List<Category> = DatabaseFactory.dbQuery {
        CategoriesTable.selectAll().map { it.toCategory() }
    }

    internal suspend fun loadCategoryWriteSpec(categoryCode: String): CatalogCategoryWriteSpec? = DatabaseFactory.dbQuery {
        val categoryQuery = CategoriesTable.selectAll()
        categoryQuery.andWhere { CategoriesTable.code eq categoryCode }
        val categoryRow = categoryQuery.singleOrNull()
            ?: return@dbQuery null

        val catAttrsQuery = CategoryAttributesTable.selectAll()
        catAttrsQuery.andWhere { CategoryAttributesTable.categoryCode eq categoryCode }
        val catAttrsRows = catAttrsQuery.toList()

        val attrCodes = catAttrsRows.map { it[CategoryAttributesTable.attributeCode] }.distinct()
        val attrDefs = if (attrCodes.isEmpty()) {
            emptyList()
        } else {
            val attrDefsQuery = AttributeDefsTable.selectAll()
            attrDefsQuery.andWhere { AttributeDefsTable.code inList attrCodes }
            attrDefsQuery.map { it.toAttributeDef() }
        }
        val dicts = loadDicts(attrCodes)
        val requiredIfRules = loadRequiredIfRules(
            categoryCode = categoryCode,
            attributeCodes = attrCodes,
        )

        CatalogCategoryWriteSpec(
            category = categoryRow.toCategory(),
            attributes = attrDefs,
            categoryAttributes = catAttrsRows.map { it.toCategoryAttribute() },
            valueDictionaries = dicts,
            requiredIfRules = requiredIfRules,
        )
    }

    override suspend fun getCategoryEffectiveSpec(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): CatalogCategoryEffectiveSpec? {
        val spec = loadCategoryWriteSpec(categoryCode) ?: return null
        val constraints = loadConstraints(
            categoryCode = categoryCode,
            brand = brand,
            model = model,
        )
        val effectiveSpec = spec.toCategoryEffectiveSpec(constraints = constraints)
        return applyOperationalReadiness(
            spec = effectiveSpec,
            report = loadOperationalReadinessReport(categoryCode = effectiveSpec.category.code),
        )
    }

    override suspend fun resolveCategoryCode(
        categoryCode: String,
        maxHops: Int,
    ) = CategoryReplacementResolver.resolve(
        requestedCode = categoryCode,
        categories = listCategories(),
        maxHops = maxHops,
    )

    internal suspend fun loadConstraints(
        categoryCode: String,
        brand: String?,
        model: String?,
    ): List<CatalogConstraints> = DatabaseFactory.dbQuery {
        val normalizedCategory = categoryCode.trim()
        if (normalizedCategory.isBlank()) return@dbQuery emptyList()

        val allConstraints = CatalogConstraintsTable
            .selectAll()
            .map { it.toCatalogConstraints() }

        CatalogConstraintsSelection.select(
            constraints = allConstraints,
            categoryCode = normalizedCategory,
            brand = brand,
            model = model,
        )
    }

    override suspend fun upsertCategorySpec(spec: CatalogCategoryWriteSpec): Unit = DatabaseFactory.dbQuery {
        upsertCategory(spec.category)
        spec.attributes.forEach { upsertAttributeDef(it) }

        CategoryAttributesTable.deleteWhere { CategoryAttributesTable.categoryCode eq spec.category.code }
        CategoryAttributesTable.batchInsert(spec.categoryAttributes) { attr ->
            this[CategoryAttributesTable.categoryCode] = attr.categoryCode
            this[CategoryAttributesTable.attributeCode] = attr.attributeCode
            this[CategoryAttributesTable.uiOrder] = attr.uiOrder
            this[CategoryAttributesTable.isRequired] = attr.isRequiredForCategory
        }

        val dictAttrCodes = spec.valueDictionaries.map { it.attributeCode }.distinct()
        if (dictAttrCodes.isNotEmpty()) {
            AttributeValueDictTable.deleteWhere { AttributeValueDictTable.attributeCode inList dictAttrCodes }
        }
        AttributeValueDictTable.batchInsert(
            spec.valueDictionaries.flatMap { dict ->
                dict.entries.map { entry -> dict.attributeCode to entry }
            },
        ) { (attrCode, entry) ->
            this[AttributeValueDictTable.attributeCode] = attrCode
            this[AttributeValueDictTable.canonicalCode] = entry.canonicalCode
            this[AttributeValueDictTable.canonicalValue] = entry.canonicalValue
            this[AttributeValueDictTable.synonyms] = entry.synonyms
        }
        upsertRequiredIfRules(spec)

        Unit
    }

    private fun upsertCategory(category: Category) {
        val updated = CategoriesTable.update({ CategoriesTable.code eq category.code }) { stmt ->
            stmt[segment] = category.segment.name
            stmt[status] = category.status.name
            stmt[titleLocalized] = category.title
            stmt[titleRu] = category.title.resolve(locale = "ru", fallback = category.code)
            stmt[titleEn] = category.title["en"]
            stmt[parentCode] = category.parentCode
            stmt[description] = category.description
            stmt[replacementCode] = category.replacementCode
        }
        if (updated == 0) {
            CategoriesTable.insert { stmt ->
                stmt[code] = category.code
                stmt[segment] = category.segment.name
                stmt[status] = category.status.name
                stmt[titleLocalized] = category.title
                stmt[titleRu] = category.title.resolve(locale = "ru", fallback = category.code)
                stmt[titleEn] = category.title["en"]
                stmt[parentCode] = category.parentCode
                stmt[description] = category.description
                stmt[replacementCode] = category.replacementCode
            }
        }
    }

    private fun upsertAttributeDef(def: AttributeDef) {
        val updated = AttributeDefsTable.update({ AttributeDefsTable.code eq def.code }) { stmt ->
            stmt[title] = def.title
            stmt[dataType] = def.dataType.name
            stmt[requiredForSearch] = def.requiredForSearch
            stmt[requiredForOffer] = def.requiredForOffer
            stmt[requiredForExpress] = def.requiredForExpress
            stmt[requiredBy] = def.requiredBy
            stmt[facetEnabled] = def.facetEnabled
            stmt[multiValued] = def.multiValued
            stmt[valueDictCode] = def.valueDictCode
        }
        if (updated == 0) {
            AttributeDefsTable.insertIgnore { stmt ->
                stmt[code] = def.code
                stmt[title] = def.title
                stmt[dataType] = def.dataType.name
                stmt[requiredForSearch] = def.requiredForSearch
                stmt[requiredForOffer] = def.requiredForOffer
                stmt[requiredForExpress] = def.requiredForExpress
                stmt[requiredBy] = def.requiredBy
                stmt[facetEnabled] = def.facetEnabled
                stmt[multiValued] = def.multiValued
                stmt[valueDictCode] = def.valueDictCode
            }
        }
    }

    private fun loadDicts(attributeCodes: List<String>): List<AttributeValueDict> {
        if (attributeCodes.isEmpty()) return emptyList()
        val q = AttributeValueDictTable.selectAll()
        q.andWhere { AttributeValueDictTable.attributeCode inList attributeCodes }
        val rows = q.toList()
        return rows
            .groupBy { it[AttributeValueDictTable.attributeCode] }
            .map { (attrCode, dictRows) ->
                AttributeValueDict(
                    attributeCode = attrCode,
                    entries = dictRows.map { row ->
                        AttributeValueDictEntry(
                            canonicalCode = row[AttributeValueDictTable.canonicalCode],
                            canonicalValue = row[AttributeValueDictTable.canonicalValue],
                            synonyms = row[AttributeValueDictTable.synonyms] ?: emptyList(),
                        )
                    },
                    code = attrCode,
                )
            }
    }

    private fun loadRequiredIfRules(
        categoryCode: String,
        attributeCodes: List<String>,
    ): List<RequiredIfRule> {
        if (attributeCodes.isEmpty()) return emptyList()
        val normalizedCategoryCode = categoryCode.trim().uppercase(Locale.ROOT)
        if (normalizedCategoryCode.isEmpty()) return emptyList()

        val q = CatalogStage4TypedConstraintsTable.selectAll()
        q.andWhere { CatalogStage4TypedConstraintsTable.attributeCode inList attributeCodes }
        val rules = mutableListOf<RequiredIfRule>()

        q.forEach { row ->
            val requiredAttributeCode = row[CatalogStage4TypedConstraintsTable.attributeCode]
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: return@forEach

            row[CatalogStage4TypedConstraintsTable.requiredIf]
                .forEach { stageRule ->
                    val ruleCategoryCode = stageRule.categoryCode.trim().uppercase(Locale.ROOT)
                    if (ruleCategoryCode != normalizedCategoryCode) return@forEach

                    val whenAll = stageRule.whenAll
                        .mapNotNull { condition ->
                            val conditionAttributeCode = condition.attributeCode.trim().takeIf { it.isNotEmpty() }
                                ?: return@mapNotNull null
                            val values = condition.values
                                .map { value -> value.trim() }
                                .filter { value -> value.isNotEmpty() }
                                .distinct()
                            if (values.isEmpty()) {
                                null
                            } else {
                                AttributeCondition(
                                    attributeCode = conditionAttributeCode,
                                    op = condition.op,
                                    values = values,
                                )
                            }
                        }
                    if (whenAll.isEmpty()) return@forEach

                    rules += RequiredIfRule(
                        requiredAttributeCode = requiredAttributeCode,
                        whenAll = whenAll,
                    )
                }
        }

        return rules
            .distinctBy { rule ->
                buildString {
                    append(rule.requiredAttributeCode.trim().lowercase(Locale.ROOT))
                    append("|")
                    append(
                        rule.whenAll.joinToString(";") { condition ->
                            "${condition.attributeCode.trim().lowercase(Locale.ROOT)}:${condition.op.name}:${condition.values.joinToString(",") { value -> value.trim().lowercase(Locale.ROOT) }}"
                        },
                    )
                }
            }
            .sortedWith(
                compareBy<RequiredIfRule> { it.requiredAttributeCode.lowercase(Locale.ROOT) }
                    .thenBy { rule ->
                        rule.whenAll.joinToString(";") { condition ->
                            "${condition.attributeCode.lowercase(Locale.ROOT)}:${condition.op.name}:${condition.values.joinToString(",") { value -> value.lowercase(Locale.ROOT) }}"
                        }
                    },
            )
    }

    private fun upsertRequiredIfRules(spec: CatalogCategoryWriteSpec) {
        val normalizedCategoryCode = spec.category.code.trim().uppercase(Locale.ROOT)
        if (normalizedCategoryCode.isEmpty()) return

        val pendingByAttributeKey = LinkedHashMap<String, PendingRequiredIfRules>()
        spec.requiredIfRules.forEach { rule ->
            val requiredAttributeCode = rule.requiredAttributeCode.trim()
            if (requiredAttributeCode.isEmpty()) return@forEach
            val stageRule = toStage40RequiredIfRule(
                categoryCode = normalizedCategoryCode,
                rule = rule,
            ) ?: return@forEach
            val key = normalizeAttributeKey(requiredAttributeCode)
            val pending = pendingByAttributeKey.getOrPut(key) {
                PendingRequiredIfRules(
                    attributeCode = requiredAttributeCode,
                    rules = mutableListOf(),
                )
            }
            pending.rules += stageRule
        }
        pendingByAttributeKey.values.forEach { pending ->
            val normalizedRules = normalizeStage40RequiredIfRules(pending.rules)
            pending.rules.clear()
            pending.rules.addAll(normalizedRules)
        }

        val now = System.currentTimeMillis()
        CatalogStage4TypedConstraintsTable.selectAll().forEach { row ->
            val attributeCode = row[CatalogStage4TypedConstraintsTable.attributeCode]
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: return@forEach
            val attributeKey = normalizeAttributeKey(attributeCode)
            val existingRules = normalizeStage40RequiredIfRules(
                row[CatalogStage4TypedConstraintsTable.requiredIf],
            )
            val rulesWithoutCategory = existingRules.filterNot { rule ->
                rule.categoryCode.equals(normalizedCategoryCode, ignoreCase = true)
            }
            val replacementRules = pendingByAttributeKey.remove(attributeKey)?.rules.orEmpty()
            val mergedRules = normalizeStage40RequiredIfRules(rulesWithoutCategory + replacementRules)
            if (mergedRules != existingRules) {
                CatalogStage4TypedConstraintsTable.update({
                    CatalogStage4TypedConstraintsTable.attributeCode eq row[CatalogStage4TypedConstraintsTable.attributeCode]
                }) { stmt ->
                    stmt[CatalogStage4TypedConstraintsTable.requiredIf] = mergedRules
                    stmt[CatalogStage4TypedConstraintsTable.updatedAt] = now
                }
            }
        }

        if (pendingByAttributeKey.isEmpty()) return

        val immutableByAttributeKey = CatalogStage4ImmutableAttributesTable.selectAll()
            .mapNotNull { row ->
                val attributeCode = row[CatalogStage4ImmutableAttributesTable.attributeCode]
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                normalizeAttributeKey(attributeCode) to ImmutableStage4Attribute(
                    attributeCode = attributeCode,
                    valueType = row[CatalogStage4ImmutableAttributesTable.valueType],
                )
            }
            .toMap()

        val missingImmutableAttributes = mutableListOf<String>()
        pendingByAttributeKey.values
            .sortedBy { it.attributeCode.lowercase(Locale.ROOT) }
            .forEach { pending ->
                val immutable = immutableByAttributeKey[normalizeAttributeKey(pending.attributeCode)]
                if (immutable == null) {
                    missingImmutableAttributes += pending.attributeCode
                    return@forEach
                }
                CatalogStage4TypedConstraintsTable.insert {
                    it[CatalogStage4TypedConstraintsTable.attributeCode] = immutable.attributeCode
                    it[CatalogStage4TypedConstraintsTable.valueType] = immutable.valueType
                    it[CatalogStage4TypedConstraintsTable.enumOnly] = false
                    it[CatalogStage4TypedConstraintsTable.expectedUnit] = null
                    it[CatalogStage4TypedConstraintsTable.regexPattern] = null
                    it[CatalogStage4TypedConstraintsTable.minValue] = null
                    it[CatalogStage4TypedConstraintsTable.maxValue] = null
                    it[CatalogStage4TypedConstraintsTable.requiredIf] = pending.rules
                    it[CatalogStage4TypedConstraintsTable.updatedAt] = now
                }
            }

        check(missingImmutableAttributes.isEmpty()) {
            "Cannot persist requiredIfRules for category=$normalizedCategoryCode: missing stage4 immutable attributes " +
                missingImmutableAttributes.sorted().joinToString(", ")
        }
    }

    private fun toStage40RequiredIfRule(
        categoryCode: String,
        rule: RequiredIfRule,
    ): Stage40RequiredIfRule? {
        val normalizedWhenAll = rule.whenAll
            .mapNotNull { condition ->
                val attributeCode = condition.attributeCode.trim()
                if (attributeCode.isEmpty()) return@mapNotNull null
                val values = condition.values
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotEmpty() }
                if (values.isEmpty()) return@mapNotNull null
                Stage40RequiredIfCondition(
                    attributeCode = attributeCode,
                    op = condition.op,
                    values = values,
                )
            }
            .sortedWith(
                compareBy<Stage40RequiredIfCondition> { it.attributeCode.lowercase(Locale.ROOT) }
                    .thenBy { it.op.name }
                    .thenBy { it.values.joinToString("|") { value -> value.lowercase(Locale.ROOT) } },
            )
        if (normalizedWhenAll.isEmpty()) return null
        return Stage40RequiredIfRule(
            categoryCode = categoryCode,
            whenAll = normalizedWhenAll,
        )
    }

    private fun normalizeStage40RequiredIfRules(
        rules: List<Stage40RequiredIfRule>,
    ): List<Stage40RequiredIfRule> =
        rules
            .mapNotNull { rule ->
                val categoryCode = rule.categoryCode.trim().uppercase(Locale.ROOT)
                if (categoryCode.isEmpty()) return@mapNotNull null
                val whenAll = rule.whenAll
                    .mapNotNull { condition ->
                        val attributeCode = condition.attributeCode.trim()
                        if (attributeCode.isEmpty()) return@mapNotNull null
                        val values = condition.values
                            .map { value -> value.trim() }
                            .filter { value -> value.isNotEmpty() }
                        if (values.isEmpty()) return@mapNotNull null
                        Stage40RequiredIfCondition(
                            attributeCode = attributeCode,
                            op = condition.op,
                            values = values,
                        )
                    }
                    .sortedWith(
                        compareBy<Stage40RequiredIfCondition> { it.attributeCode.lowercase(Locale.ROOT) }
                            .thenBy { it.op.name }
                            .thenBy { it.values.joinToString("|") { value -> value.lowercase(Locale.ROOT) } },
                    )
                if (whenAll.isEmpty()) return@mapNotNull null
                Stage40RequiredIfRule(
                    categoryCode = categoryCode,
                    whenAll = whenAll,
                )
            }
            .distinct()
            .sortedWith(
                compareBy<Stage40RequiredIfRule> { it.categoryCode }
                    .thenBy { rule ->
                        rule.whenAll.joinToString("|") { condition ->
                            "${condition.attributeCode.lowercase(Locale.ROOT)}:${condition.op.name}:" +
                                condition.values.joinToString(",") { value -> value.lowercase(Locale.ROOT) }
                        }
                    },
            )

    private fun normalizeAttributeKey(attributeCode: String): String =
        attributeCode.trim().lowercase(Locale.ROOT)

    private data class PendingRequiredIfRules(
        val attributeCode: String,
        val rules: MutableList<Stage40RequiredIfRule>,
    )

    private data class ImmutableStage4Attribute(
        val attributeCode: String,
        val valueType: String,
    )
}

private fun ResultRow.toCategory(): Category = Category(
    code = this[CategoriesTable.code],
    segment = runCatching { CategorySegment.valueOf(this[CategoriesTable.segment]) }.getOrDefault(CategorySegment.OTHER),
    status = runCatching { CategoryStatus.valueOf(this[CategoriesTable.status]) }.getOrDefault(CategoryStatus.ACTIVE),
    title = localizedTextFromStorage(
        localized = this[CategoriesTable.titleLocalized],
        titleRu = this[CategoriesTable.titleRu],
        titleEn = this[CategoriesTable.titleEn],
    ),
    parentCode = this[CategoriesTable.parentCode],
    description = this[CategoriesTable.description],
    replacementCode = this[CategoriesTable.replacementCode],
)

private fun ResultRow.toAttributeDef(): AttributeDef = AttributeDef(
    code = this[AttributeDefsTable.code],
    title = this[AttributeDefsTable.title],
    dataType = runCatching { AttributeDataType.valueOf(this[AttributeDefsTable.dataType]) }.getOrElse { AttributeDataType.STRING },
    requiredForSearch = this[AttributeDefsTable.requiredForSearch],
    requiredForOffer = this[AttributeDefsTable.requiredForOffer],
    requiredForExpress = this[AttributeDefsTable.requiredForExpress],
    requiredBy = this[AttributeDefsTable.requiredBy],
    facetEnabled = this[AttributeDefsTable.facetEnabled],
    multiValued = this[AttributeDefsTable.multiValued],
    valueDictCode = this[AttributeDefsTable.valueDictCode],
)

private fun ResultRow.toCategoryAttribute(): CategoryAttribute = CategoryAttribute(
    categoryCode = this[CategoryAttributesTable.categoryCode],
    attributeCode = this[CategoryAttributesTable.attributeCode],
    uiOrder = this[CategoryAttributesTable.uiOrder],
    isRequiredForCategory = this[CategoryAttributesTable.isRequired],
)

private fun ResultRow.toCatalogConstraints(): CatalogConstraints = CatalogConstraints(
    scope = runCatching { ConstraintScope.valueOf(this[CatalogConstraintsTable.scope]) }.getOrDefault(ConstraintScope.CATEGORY),
    categoryCode = this[CatalogConstraintsTable.categoryCode],
    brand = this[CatalogConstraintsTable.brand],
    model = this[CatalogConstraintsTable.model],
    effectiveFrom = this[CatalogConstraintsTable.effectiveFrom],
    effectiveTo = this[CatalogConstraintsTable.effectiveTo],
    attributeConstraints = this[CatalogConstraintsTable.attributeConstraints],
    compatibilityRules = this[CatalogConstraintsTable.compatibilityRules],
)

