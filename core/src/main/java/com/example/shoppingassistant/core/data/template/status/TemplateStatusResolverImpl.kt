package com.example.shoppingassistant.core.data.template.status

import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.CategoryProfile
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.template.TemplateSnapshotMode
import com.example.shoppingassistant.domain.template.status.TemplateReadyAction
import com.example.shoppingassistant.domain.template.status.TemplateStatus
import com.example.shoppingassistant.domain.template.status.TemplateStatusContext
import com.example.shoppingassistant.domain.template.status.TemplateStatusResolver
import com.example.shoppingassistant.domain.template.status.TemplateStatusResult

class TemplateStatusResolverImpl(
    private val constraintsResolver: CatalogConstraintsResolver,
) : TemplateStatusResolver {

    private val expressRequired = listOf("price", "currency", "condition")
    private val linkRequired = listOf("price", "currency", "brand")

    override fun resolve(context: TemplateStatusContext): TemplateStatusResult {
        val template = context.template
        val attrs = template.attrs
            .mapNotNull { a -> a.value.takeIf { it.isNotBlank() }?.let { a.key to it } }
            .toMap()

        val requiredIfRules = context.requiredIfRules
            .ifEmpty { context.profile?.requiredIfRules.orEmpty() }

        val requiredOrdered = buildRequiredKeys(
            mode = template.mode,
            profile = context.profile,
            requiredIfRules = requiredIfRules,
            currentAttrs = attrs,
        )

        val errors = linkedMapOf<String, String>()
        requiredOrdered.forEach { key ->
            if (attrs[key].isNullOrBlank()) {
                errors[key] = "Нужно заполнить"
            }
        }

        val constraintsResult = constraintsResolver.evaluate(context.constraints, attrs)
        constraintsResult.violations.forEach { (key, reason) ->
            if (!errors.containsKey(key)) {
                errors[key] = reason
            }
        }

        val readyFor = if (errors.isEmpty()) {
            buildReadyActions(context, attrs, requiredIfRules, constraintsResult.violations.isEmpty())
        } else emptySet()
        val status = when {
            errors.isNotEmpty() -> TemplateStatus.DRAFT
            isLockedForActions(template.mode, readyFor) -> TemplateStatus.LOCKED_FOR_ACTIONS
            else -> TemplateStatus.VALID
        }

        val firstErrorKey = when {
            errors.isEmpty() -> null
            else -> requiredOrdered.firstOrNull { errors.containsKey(it) } ?: errors.keys.first()
        }

        return TemplateStatusResult(
            status = status,
            readyFor = readyFor,
            errors = errors,
            firstErrorKey = firstErrorKey,
        )
    }

    private fun buildReadyActions(
        context: TemplateStatusContext,
        attrs: Map<String, String>,
        requiredIfRules: List<RequiredIfRule>,
        constraintsOk: Boolean,
    ): Set<TemplateReadyAction> {
        val out = linkedSetOf<TemplateReadyAction>()
        if (!constraintsOk) return out

        fun isReadyFor(action: TemplateReadyAction): Boolean {
            val mode = when (action) {
                TemplateReadyAction.SEARCH, TemplateReadyAction.SUBSCRIPTION -> TemplateSnapshotMode.SEARCH
                TemplateReadyAction.EXPRESS -> TemplateSnapshotMode.EXPRESS
            }
            val required = buildRequiredKeys(mode, context.profile, requiredIfRules, attrs)
            val missing = required.any { key -> attrs[key].isNullOrBlank() }
            if (missing) return false
            if (action == TemplateReadyAction.EXPRESS && !context.hasPhotos) return false
            return true
        }

        TemplateReadyAction.values().forEach { action ->
            if (isReadyFor(action)) out.add(action)
        }
        return out
    }

    private fun buildRequiredKeys(
        mode: TemplateSnapshotMode,
        profile: CategoryProfile?,
        requiredIfRules: List<RequiredIfRule>,
        currentAttrs: Map<String, String>,
    ): List<String> {
        val ordered = mutableListOf<String>()
        val required = linkedSetOf<String>()

        val defsByCode = profile?.attributes.orEmpty().associateBy { it.code }
        profile?.categoryAttributes.orEmpty()
            .sortedBy { it.uiOrder }
            .forEach { catAttr ->
                val def = defsByCode[catAttr.attributeCode] ?: return@forEach
                val requiredByMode = when (mode) {
                    TemplateSnapshotMode.SEARCH -> def.requiredForSearch
                    TemplateSnapshotMode.OFFER -> def.requiredForOffer
                    TemplateSnapshotMode.EXPRESS -> def.requiredForExpress || def.requiredForOffer
                }
                if (requiredByMode || catAttr.isRequiredForCategory) {
                    if (required.add(def.code)) ordered.add(def.code)
                }
            }

        applyRequiredIfRules(required, ordered, requiredIfRules, currentAttrs)

        when (mode) {
            TemplateSnapshotMode.OFFER -> addExtra(required, ordered, linkRequired)
            TemplateSnapshotMode.EXPRESS -> addExtra(required, ordered, expressRequired)
            TemplateSnapshotMode.SEARCH -> Unit
        }

        return ordered
    }

    private fun applyRequiredIfRules(
        required: MutableSet<String>,
        ordered: MutableList<String>,
        rules: List<RequiredIfRule>,
        attrs: Map<String, String>,
    ) {
        if (rules.isEmpty()) return
        rules.forEach { rule ->
            val ok = rule.whenAll.all { cond ->
                val current = attrs[cond.attributeCode]
                matches(cond, current)
            }
            if (ok && required.add(rule.requiredAttributeCode)) {
                ordered.add(rule.requiredAttributeCode)
            }
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

    private fun addExtra(
        required: MutableSet<String>,
        ordered: MutableList<String>,
        extras: List<String>,
    ) {
        extras.forEach { key ->
            if (required.add(key)) ordered.add(key)
        }
    }

    private fun isLockedForActions(
        mode: TemplateSnapshotMode,
        readyFor: Set<TemplateReadyAction>,
    ): Boolean {
        val action = when (mode) {
            TemplateSnapshotMode.SEARCH -> TemplateReadyAction.SEARCH
            TemplateSnapshotMode.EXPRESS -> TemplateReadyAction.EXPRESS
            TemplateSnapshotMode.OFFER -> null
        }
        return action != null && readyFor.contains(action)
    }
}
