// Last synced: 2025-12-20 13:35:49
package com.example.shoppingassistant.feature.pages.main.state

import com.example.shoppingassistant.core.data.link.LinkTemplateRaw
import com.example.shoppingassistant.domain.catalog.AttributeCondition
import com.example.shoppingassistant.domain.catalog.AttributeConditionOp
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraintsResolver
import com.example.shoppingassistant.domain.search.SearchTextNormalizer

/**
 * Реализация TemplateEngine в стиле Tasks.
 */
class TemplateEngineTaskImpl(
    private val constraintsResolver: CatalogConstraintsResolver? = null,
) : TemplateEngine {

    private val expressRequired = setOf("price", "currency", "condition")
    private val linkRequired = setOf("price", "currency", "brand")

    override fun onInputTextChanged(
        prev: UiTemplate,
        newText: String,
        dict: CategoryDictionary,
    ): UiTemplate {
        val required = requiredKeys(prev.mode, dict, prev.attributes)
        val normalized = normalizeInput(newText)

        // Пока модель не зафиксирована — поле ведёт себя как обычный поиск, без «поедания» атрибутов.
        if (!prev.isLocked) {
            val errors = validateRequired(prev.attributes, required)
            return prev.copy(
                inputText = normalized.trimStart(),
                requiredKeys = required,
                errorKeys = errors.keys,
                errorMessages = errors,
            )
        }

        val lockedTitle = prev.lockedTitle.orEmpty()
        val headerOk = isHeaderIntact(normalized, lockedTitle)
        if (!headerOk) {
            // Заголовок сломан — выходим из режима шаблона.
            return UiTemplate(
                inputText = normalized.trim(),
                mode = TemplateMode.SearchOrSubscribe,
            )
        }

        val (cleanedInput, updatedAttrs) = consumeTokensIntoAttributes(
            fullText = normalized,
            lockedTitle = lockedTitle,
            existing = prev.attributes,
            dict = dict,
        )
        val errors = validateRequired(updatedAttrs, required) + validateConstraints(updatedAttrs, dict)

        return prev.copy(
            inputText = cleanedInput,
            attributes = updatedAttrs,
            requiredKeys = required,
            errorKeys = errors.keys,
            errorMessages = errors,
        )
    }

    override fun onAttributeChanged(
        prev: UiTemplate,
        code: String,
        newValue: String?,
        dict: CategoryDictionary,
    ): UiTemplate {
        val attrDict = dict.attributes[code]
        val required = requiredKeys(prev.mode, dict, prev.attributes)
        val mutable = prev.attributes.toMutableMap()

        if (newValue.isNullOrBlank()) {
            mutable.remove(code)
        } else {
            val resolved = resolveCanonical(newValue, attrDict)
            if (resolved == null) {
                val raw = newValue.trim()
                if (raw.isNotBlank()) {
                    mutable[code] = TemplateAttribute(
                        code = code,
                        canonicalValue = raw,
                        displayValue = raw,
                        source = ValueSource.UserSelected,
                    )
                }
                val errors = validateRequired(mutable, required) +
                    mapOf(code to "Значение не из каталога") +
                    validateConstraints(mutable, dict)
                return prev.copy(
                    attributes = mutable,
                    requiredKeys = required,
                    errorKeys = errors.keys,
                    errorMessages = errors,
                )
            }
            mutable[code] = TemplateAttribute(
                code = code,
                canonicalValue = resolved.first,
                displayValue = resolved.second,
                source = ValueSource.UserSelected,
            )
        }

        val updatedRequired = requiredKeys(prev.mode, dict, mutable)
        val errors = validateRequired(mutable, updatedRequired) + validateConstraints(mutable, dict)
        return prev.copy(
            attributes = mutable,
            requiredKeys = updatedRequired,
            errorKeys = errors.keys,
            errorMessages = errors,
        )
    }

    override fun attachLinkTemplate(
        prev: UiTemplate,
        linkTemplate: LinkTemplateRaw,
        dict: CategoryDictionary,
    ): UiTemplate {
        val incoming = parseLinkAttributes(linkTemplate, dict)
        val merged = mergeAttributes(
            existing = prev.attributes,
            parsed = incoming,
        )
        val title = linkTemplate.title.orEmpty()
        val baseTitle = listOfNotNull(linkTemplate.brand, linkTemplate.model)
            .joinToString(" ")
            .ifBlank { title }
            .ifBlank { prev.inputText }
        val normalizedTitle = normalizeInput(baseTitle)
        val cleanedTitle = stripAttributesFromTitle(normalizedTitle, dict).ifBlank { normalizedTitle }
        val required = requiredKeys(TemplateMode.OfferFromLink, dict, merged)
        val errors = validateRequired(merged, required) + validateConstraints(merged, dict)

        return prev.copy(
            inputText = cleanedTitle,
            lockedTitle = cleanedTitle,
            isLocked = true,
            attributes = merged,
            categoryCode = linkTemplate.categoryCode.takeIf { it.isNotBlank() },
            linkMeta = TemplateLinkMeta(
                url = linkTemplate.sourceMeta.url,
                sourceType = linkTemplate.sourceMeta.sourceType,
                domainName = linkTemplate.sourceMeta.domainName,
                canonicalUrl = linkTemplate.sourceMeta.canonicalUrl,
                listingId = linkTemplate.sourceMeta.listingId,
            ),
            linkTemplate = linkTemplate,
            mode = TemplateMode.OfferFromLink,
            primaryPhotoUrl = linkTemplate.imageUrls.firstOrNull(),
            photoUrls = linkTemplate.imageUrls,
            requiredKeys = required,
            errorKeys = errors.keys,
            errorMessages = errors,
        )
    }

    override fun validateForOfferFromLink(
        template: UiTemplate,
        dict: CategoryDictionary,
    ): ValidationResult {
        val required = requiredKeys(TemplateMode.OfferFromLink, dict, template.attributes)
        val errors = linkedMapOf<String, String>()
        val attrs = template.attributes

        required.forEach { key ->
            val v = attrs[key]?.canonicalValue
            if (v.isNullOrBlank()) {
                errors[key] = "Нужно заполнить"
            }
        }

        val priceVal = attrs["price"]?.canonicalValue?.toDoubleOrNull()
        if (attrs.containsKey("price") && priceVal == null) {
            errors["price"] = "Введите число"
        }
        val currency = attrs["currency"]?.canonicalValue
        if (currency != null && currency.length != 3) {
            errors["currency"] = "Код валюты в ISO-4217"
        }

        errors.putAll(validateConstraints(attrs, dict))

        return ValidationResult(
            isValid = errors.isEmpty(),
            missingKeys = errors.keys,
            errorMessages = errors,
        )
    }

    private fun normalizeInput(text: String): String {
        return SearchTextNormalizer.normalizeForEditing(text)
    }

    private fun normalizeToken(token: String): String =
        SearchTextNormalizer.normalizeToken(token)

    private fun isHeaderIntact(currentText: String, lockedTitle: String): Boolean {
        if (lockedTitle.isBlank()) return false
        val trimmed = currentText.trimStart()
        return trimmed.startsWith(lockedTitle, ignoreCase = true)
    }

    private fun consumeTokensIntoAttributes(
        fullText: String,
        lockedTitle: String,
        existing: Map<String, TemplateAttribute>,
        dict: CategoryDictionary,
    ): Pair<String, Map<String, TemplateAttribute>> {
        val trimmedFull = fullText.trimStart()
        val endsWithSpace = fullText.endsWith(" ")
        val prefixLength = lockedTitle.length.coerceAtMost(trimmedFull.length)
        val suffix = trimmedFull.substring(prefixLength).let { after ->
            if (after.startsWith(" ")) after.drop(1) else after
        }
        if (suffix.isBlank()) {
            val base = if (endsWithSpace) "$lockedTitle " else lockedTitle
            return base to existing
        }

        val tokens = "\\S+".toRegex().findAll(suffix)
            .map { match -> match.value to match.range }
            .toList()
        val completed = if (endsWithSpace) tokens else tokens.dropLast(1)

        if (completed.isEmpty()) return fullText.trimStart() to existing

        val attrs = existing.toMutableMap()
        val removal = mutableListOf<IntRange>()

        completed.forEach { (token, range) ->
            val matched = findAttributeForToken(token, dict)
            if (matched != null) {
                val attrDict = dict.attributes[matched.first]
                attrs[matched.first] = TemplateAttribute(
                    code = matched.first,
                    canonicalValue = matched.second,
                    displayValue = attrDict?.displayByCanonical?.get(matched.second) ?: matched.second,
                    source = ValueSource.ParsedFromText,
                )
                var start = range.first
                while (start > 0 && suffix[start - 1].isWhitespace()) start--
                removal.add(start..range.last)
            }
        }

        if (removal.isEmpty()) return fullText.trimStart() to attrs

        val cleanedSuffix = buildString {
            var cursor = 0
            removal.sortedBy { it.first }.forEach { r ->
                if (cursor < r.first) append(suffix.substring(cursor, r.first))
                cursor = r.last + 1
            }
            if (cursor < suffix.length) append(suffix.substring(cursor))
        }.trimStart()

        var cleanedInput = listOfNotNull(
            lockedTitle.takeIf { it.isNotBlank() },
            cleanedSuffix.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { lockedTitle }

        if (endsWithSpace && cleanedSuffix.isBlank() && !cleanedInput.endsWith(" ")) {
            cleanedInput += " "
        }

        return cleanedInput to attrs
    }

    private fun findAttributeForToken(token: String, dict: CategoryDictionary): Pair<String, String>? {
        val norm = normalizeToken(token)
        val entries = dict.tokenIndex[norm].orEmpty()
        entries.forEach { entry ->
            val attrDict = dict.attributes[entry.attributeCode]
            if (attrDict?.canonicalValues?.contains(entry.canonical) == true) {
                return entry.attributeCode to entry.canonical
            }
        }
        return null
    }

    private fun stripAttributesFromTitle(rawTitle: String, dict: CategoryDictionary): String {
        if (rawTitle.isBlank()) return rawTitle
        val tokens = SearchTextNormalizer.tokens(rawTitle)
        if (tokens.isEmpty()) return rawTitle.trim()

        val kept = tokens.filter { findAttributeForToken(it, dict) == null }
        return kept.joinToString(" ").trim()
    }

    private fun resolveCanonical(raw: String, dict: AttributeDict?): Pair<String, String>? {
        if (dict == null) return null
        val trimmed = raw.trim()
        if (dict.canonicalValues.isEmpty()) {
            return trimmed.takeIf { it.isNotBlank() }?.let { it to it }
        }
        val direct = dict.canonicalValues.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (direct != null) {
            return direct to (dict.displayByCanonical[direct] ?: direct)
        }

        val normToken = normalizeToken(trimmed)
        dict.tokenToCanonical[normToken]?.let { canonical ->
            return canonical to (dict.displayByCanonical[canonical] ?: canonical)
        }
        return null
    }

    private fun mergeAttributes(
        existing: Map<String, TemplateAttribute>,
        parsed: Map<String, TemplateAttribute>,
    ): Map<String, TemplateAttribute> {
        val result = existing.toMutableMap()
        parsed.forEach { (code, attr) ->
            val current = result[code]
            if (current == null || current.source == ValueSource.ParsedFromText) {
                result[code] = attr
            }
        }
        return result
    }

    private fun parseLinkAttributes(
        template: LinkTemplateRaw,
        dict: CategoryDictionary,
    ): Map<String, TemplateAttribute> {
        val result = linkedMapOf<String, TemplateAttribute>()
        val all = linkedMapOf<String, String>()
        all.putAll(template.attributes)
        template.brand?.let { all.putIfAbsent("brand", it) }
        template.model?.let { all.putIfAbsent("model", it) }
        template.price?.let { all["price"] = it.toString() }
        template.currency?.let { all["currency"] = it }

        all.forEach { (code, value) ->
            val attrDict = dict.attributes[code]
            val canonical = resolveCanonical(value, attrDict)
            if (canonical != null) {
                result[code] = TemplateAttribute(
                    code = code,
                    canonicalValue = canonical.first,
                    displayValue = canonical.second,
                    source = ValueSource.FromSuggestion,
                )
            }
        }
        return result
    }

    private fun requiredKeys(mode: TemplateMode, dict: CategoryDictionary): Set<String> {
        return requiredKeys(mode, dict, emptyMap())
    }

    private fun requiredKeys(
        mode: TemplateMode,
        dict: CategoryDictionary,
        currentAttributes: Map<String, TemplateAttribute>,
    ): Set<String> {
        val base = when (mode) {
            TemplateMode.SearchOrSubscribe -> dict.attributes.values.filter { it.isRequiredForSearch }
            TemplateMode.OfferFromLink -> dict.attributes.values.filter { it.isRequiredForOffer }
            TemplateMode.ExpressFromPhoto, TemplateMode.OfferFromVoice ->
                dict.attributes.values.filter { it.isRequiredForExpress || it.isRequiredForOffer }
        }.map { it.code }.toMutableSet()

        applyRequiredIfRules(base, dict.requiredIfRules, currentAttributes)

        when (mode) {
            TemplateMode.OfferFromLink -> base.addAll(linkRequired)
            TemplateMode.ExpressFromPhoto, TemplateMode.OfferFromVoice -> base.addAll(expressRequired)
            else -> Unit
        }
        return base
    }

    private fun applyRequiredIfRules(
        required: MutableSet<String>,
        rules: List<RequiredIfRule>,
        attrs: Map<String, TemplateAttribute>,
    ) {
        if (rules.isEmpty()) return
        rules.forEach { rule ->
            val ok = rule.whenAll.all { cond ->
                val current = attrs[cond.attributeCode]?.canonicalValue
                matches(cond, current)
            }
            if (ok) required.add(rule.requiredAttributeCode)
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

    private fun validateRequired(
        attributes: Map<String, TemplateAttribute>,
        required: Set<String>,
    ): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        required.forEach { key ->
            val v = attributes[key]?.canonicalValue
            if (v.isNullOrBlank()) {
                errors[key] = "Нужно заполнить"
            }
        }
        return errors
    }

    private fun validateConstraints(
        attributes: Map<String, TemplateAttribute>,
        dict: CategoryDictionary,
    ): Map<String, String> {
        val resolver = constraintsResolver ?: return emptyMap()
        if (dict.constraints.isEmpty()) return emptyMap()
        val values = attributes.mapNotNull { (key, attr) ->
            attr.canonicalValue?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
        return resolver.evaluate(dict.constraints, values).violations
    }
}
