package com.example.shoppingassistant.domain.catalog.constraints

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import kotlinx.serialization.Serializable

@Serializable
enum class ConstraintScope {
    GLOBAL,
    CATEGORY,
    BRAND,
    MODEL,
}

/**
 * Allowed/forbidden values for a single attribute.
 * Values are canonical display strings (same as AttributeValueDictEntry.canonicalValue).
 */
@Serializable
data class AttributeValueConstraint(
    val attributeCode: String,
    val allowedValues: List<String> = emptyList(),
    val forbiddenValues: List<String> = emptyList(),
    val reason: String? = null,
)

/**
 * Conditional constraints applied when all conditions match.
 */
@Serializable
data class CompatibilityRule(
    val whenAll: List<AttributeCondition>,
    val apply: List<AttributeValueConstraint> = emptyList(),
)

/**
 * Constraints bundle for catalog/category/brand/model scopes.
 */
@Serializable
data class CatalogConstraints(
    val scope: ConstraintScope,
    val categoryCode: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val attributeConstraints: List<AttributeValueConstraint> = emptyList(),
    val compatibilityRules: List<CompatibilityRule> = emptyList(),
)

@Serializable
data class ConstraintCheckResult(
    val allowedValuesByAttribute: Map<String, List<String>>,
    val forbiddenValuesByAttribute: Map<String, List<String>>,
    val violations: Map<String, String> = emptyMap(),
)

/**
 * Resolves constraints into allowed/forbidden values and violations.
 */
interface CatalogConstraintsResolver {
    fun evaluate(
        constraints: List<CatalogConstraints>,
        currentAttributes: Map<String, String>,
    ): ConstraintCheckResult
}
