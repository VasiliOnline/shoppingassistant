package com.example.shoppingassistant.core.data.catalog.constraints

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.constraints.AttributeValueConstraint
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintCheckResult
import com.example.shoppingassistant.domain.catalog.constraints.ConstraintScope

class CatalogConstraintsResolverImpl : CatalogConstraintsResolver {

    override fun evaluate(
        constraints: List<CatalogConstraints>,
        currentAttributes: Map<String, String>,
    ): ConstraintCheckResult {
        if (constraints.isEmpty()) {
            return ConstraintCheckResult(
                allowedValuesByAttribute = emptyMap(),
                forbiddenValuesByAttribute = emptyMap(),
            )
        }

        val allowedByAttr = linkedMapOf<String, MutableSet<String>>()
        val forbiddenByAttr = linkedMapOf<String, MutableSet<String>>()
        val reasonByAttr = linkedMapOf<String, String>()
        val forbiddenReasons = linkedMapOf<String, MutableMap<String, String>>()

        val byAttr = constraints
            .flatMap { c -> c.attributeConstraints.map { ScopedConstraint(c.scope, it) } }
            .groupBy { it.constraint.attributeCode }

        byAttr.forEach { (attr, scoped) ->
            val byScope = scoped.groupBy { it.scope }
            val allowed = selectAllowed(byScope)
            if (allowed.isNotEmpty()) {
                allowedByAttr[attr] = allowed.toMutableSet()
                selectReason(byScope, allow = true)?.let { reasonByAttr[attr] = it }
            }

            val forbidden = scoped
                .flatMap { it.constraint.forbiddenValues }
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (forbidden.isNotEmpty()) {
                forbiddenByAttr[attr] = forbidden
            }

            scoped.forEach { scopedConstraint ->
                val reason = scopedConstraint.constraint.reason ?: return@forEach
                scopedConstraint.constraint.forbiddenValues
                    .filter { it.isNotBlank() }
                    .forEach { value ->
                        forbiddenReasons
                            .getOrPut(attr) { linkedMapOf() }
                            .putIfAbsent(normalizeValue(value), reason)
                    }
            }
        }

        val rules = constraints
            .flatMap { c -> c.compatibilityRules.map { rule -> ScopedRule(c.scope, rule) } }
            .sortedByDescending { scopePriority(it.scope) }

        rules.forEach { scopedRule ->
            if (!matchesAll(scopedRule.rule.whenAll, currentAttributes)) return@forEach
            scopedRule.rule.apply.forEach { constraint ->
                applyConstraint(
                    constraint = constraint,
                    allowedByAttr = allowedByAttr,
                    forbiddenByAttr = forbiddenByAttr,
                    reasonByAttr = reasonByAttr,
                    forbiddenReasons = forbiddenReasons,
                )
            }
        }

        allowedByAttr.forEach { (attr, allowed) ->
            val forbidden = forbiddenByAttr[attr] ?: return@forEach
            allowed.removeAll(forbidden)
        }

        val violations = computeViolations(
            currentAttributes = currentAttributes,
            allowedByAttr = allowedByAttr,
            forbiddenByAttr = forbiddenByAttr,
            reasonByAttr = reasonByAttr,
            forbiddenReasons = forbiddenReasons,
        )

        return ConstraintCheckResult(
            allowedValuesByAttribute = allowedByAttr.mapValues { it.value.toList() },
            forbiddenValuesByAttribute = forbiddenByAttr.mapValues { it.value.toList() },
            violations = violations,
        )
    }

    private fun applyConstraint(
        constraint: AttributeValueConstraint,
        allowedByAttr: MutableMap<String, MutableSet<String>>,
        forbiddenByAttr: MutableMap<String, MutableSet<String>>,
        reasonByAttr: MutableMap<String, String>,
        forbiddenReasons: MutableMap<String, MutableMap<String, String>>,
    ) {
        val attr = constraint.attributeCode
        if (constraint.allowedValues.isNotEmpty()) {
            val allowed = allowedByAttr.getOrPut(attr) { mutableSetOf() }
            if (allowed.isEmpty()) {
                allowed.addAll(constraint.allowedValues.filter { it.isNotBlank() })
            } else {
                allowed.retainAll(constraint.allowedValues.filter { it.isNotBlank() }.toSet())
            }
            constraint.reason?.let { reasonByAttr[attr] = it }
        }

        if (constraint.forbiddenValues.isNotEmpty()) {
            val forbidden = forbiddenByAttr.getOrPut(attr) { mutableSetOf() }
            forbidden.addAll(constraint.forbiddenValues.filter { it.isNotBlank() })
            constraint.reason?.let { reason ->
                constraint.forbiddenValues
                    .filter { it.isNotBlank() }
                    .forEach { value ->
                        forbiddenReasons
                            .getOrPut(attr) { linkedMapOf() }
                            .putIfAbsent(normalizeValue(value), reason)
                    }
            }
        }
    }

    private fun computeViolations(
        currentAttributes: Map<String, String>,
        allowedByAttr: Map<String, Set<String>>,
        forbiddenByAttr: Map<String, Set<String>>,
        reasonByAttr: Map<String, String>,
        forbiddenReasons: Map<String, Map<String, String>>,
    ): Map<String, String> {
        val violations = linkedMapOf<String, String>()
        currentAttributes.forEach { (attr, rawValue) ->
            val value = rawValue.trim()
            if (value.isBlank()) return@forEach

            val forbidden = forbiddenByAttr[attr]
            if (!forbidden.isNullOrEmpty()) {
                val found = forbidden.firstOrNull { it.equals(value, ignoreCase = true) }
                if (found != null) {
                    val reason = forbiddenReasons[attr]?.get(normalizeValue(found))
                        ?: reasonByAttr[attr]
                        ?: "Значение недоступно"
                    violations[attr] = reason
                    return@forEach
                }
            }

            val allowed = allowedByAttr[attr]
            if (!allowed.isNullOrEmpty() && allowed.none { it.equals(value, ignoreCase = true) }) {
                val reason = reasonByAttr[attr] ?: "Значение недоступно"
                violations[attr] = reason
            }
        }
        return violations
    }

    private fun matchesAll(conditions: List<AttributeCondition>, attrs: Map<String, String>): Boolean {
        if (conditions.isEmpty()) return true
        return conditions.all { cond ->
            val current = attrs[cond.attributeCode]
            matches(cond, current)
        }
    }

    private fun matches(cond: AttributeCondition, currentValue: String?): Boolean {
        if (currentValue.isNullOrBlank()) return false
        return when (cond.op) {
            AttributeConditionOp.EQUALS_ANY ->
                cond.values.any { v -> currentValue.equals(v, ignoreCase = true) }
            AttributeConditionOp.STARTS_WITH_ANY ->
                cond.values.any { v -> currentValue.startsWith(v, ignoreCase = true) }
        }
    }

    private fun scopePriority(scope: ConstraintScope): Int = when (scope) {
        ConstraintScope.GLOBAL -> 0
        ConstraintScope.CATEGORY -> 1
        ConstraintScope.BRAND -> 2
        ConstraintScope.MODEL -> 3
    }

    private fun selectAllowed(byScope: Map<ConstraintScope, List<ScopedConstraint>>): List<String> {
        for (scope in listOf(ConstraintScope.MODEL, ConstraintScope.BRAND, ConstraintScope.CATEGORY, ConstraintScope.GLOBAL)) {
            val allowed = byScope[scope]
                .orEmpty()
                .flatMap { it.constraint.allowedValues }
                .filter { it.isNotBlank() }
                .distinct()
            if (allowed.isNotEmpty()) return allowed
        }
        return emptyList()
    }

    private fun selectReason(byScope: Map<ConstraintScope, List<ScopedConstraint>>, allow: Boolean): String? {
        for (scope in listOf(ConstraintScope.MODEL, ConstraintScope.BRAND, ConstraintScope.CATEGORY, ConstraintScope.GLOBAL)) {
            val reason = byScope[scope]
                .orEmpty()
                .mapNotNull { scoped ->
                    val values = if (allow) scoped.constraint.allowedValues else scoped.constraint.forbiddenValues
                    if (values.isNotEmpty()) scoped.constraint.reason else null
                }
                .firstOrNull()
            if (reason != null) return reason
        }
        return null
    }

    private fun normalizeValue(value: String): String = value.trim().lowercase()

    private data class ScopedConstraint(
        val scope: ConstraintScope,
        val constraint: AttributeValueConstraint,
    )

    private data class ScopedRule(
        val scope: ConstraintScope,
        val rule: com.example.shoppingassistant.domain.catalog.constraints.CompatibilityRule,
    )
}
