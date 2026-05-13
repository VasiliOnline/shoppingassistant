package com.example.shoppingassistant.feature.pages.main.context

import com.example.shoppingassistant.feature.pages.main.state.AttributeDict
import com.example.shoppingassistant.feature.pages.main.state.CategoryDictionary
import com.example.shoppingassistant.feature.pages.main.state.TokenIndexEntry
import com.example.shoppingassistant.domain.catalog.RequiredIfRule
import com.example.shoppingassistant.feature.pages.model.AttributeDef
import com.example.shoppingassistant.domain.catalog.constraints.CatalogConstraints
import com.example.shoppingassistant.feature.pages.model.ValueDef

/**
 * Собирает CategoryDictionary из списка AttributeDef (каталог атрибутов категории).
 */
fun buildCategoryDictionary(
    categoryCode: String?,
    defs: List<AttributeDef>,
    requiredIfRules: List<RequiredIfRule> = emptyList(),
    constraints: List<CatalogConstraints> = emptyList(),
): CategoryDictionary {
    val effectiveCategory = categoryCode?.takeIf { it.isNotBlank() }
    val filtered = defs.filterNot { def ->
        def.key == "category" || def.key.startsWith("category_level_")
    }
    val attrDicts = filtered.associate { def ->
        val canonicalValues = canonicalValues(def)
        val displayByCanonical = displayByCanonical(def)
        val tokenMap = buildTokenMap(def, canonicalValues)
        def.key to AttributeDict(
            code = def.key,
            isRequiredForOffer = def.requiredForOffer,
            isRequiredForSearch = def.requiredForSearch,
            isRequiredForExpress = def.requiredForExpress,
            tokenToCanonical = tokenMap,
            canonicalValues = canonicalValues.toSet(),
            displayByCanonical = displayByCanonical,
        )
    }

    val tokenIndex = mutableMapOf<String, MutableList<TokenIndexEntry>>()
    attrDicts.values.forEach { attr ->
        attr.tokenToCanonical.forEach { (token, canonical) ->
            tokenIndex.getOrPut(token) { mutableListOf() }
                .add(TokenIndexEntry(attr.code, canonical))
        }
    }

    return CategoryDictionary(
        categoryCode = effectiveCategory ?: "DEFAULT",
        attributes = attrDicts,
        tokenIndex = tokenIndex.mapValues { it.value },
        requiredIfRules = requiredIfRules,
        constraints = constraints,
    )
}

private fun canonicalValues(def: AttributeDef): List<String> {
    return if (def.allowedValues.isNotEmpty()) {
        def.allowedValues.map(ValueDef::code)
    } else {
        def.options
    }.map { it.trim() }.filter { it.isNotBlank() }
}

private fun displayByCanonical(def: AttributeDef): Map<String, String> =
    if (def.allowedValues.isNotEmpty()) {
        def.allowedValues.associate { value -> value.code to value.label }
    } else {
        def.options.associateWith { it }
    }

private fun buildTokenMap(def: AttributeDef, canonicalValues: List<String>): Map<String, String> {
    val tokenMap = linkedMapOf<String, String>()

    fun addTokens(value: String, canonical: String) {
        val tokens = value.split("[\\s/,]+".toRegex()).map { normalizeToken(it) }.filter { it.isNotBlank() }
        tokens.forEach { token -> tokenMap.putIfAbsent(token, canonical) }
        val compact = normalizeToken(value.replace("\\s+".toRegex(), ""))
        if (compact.isNotBlank()) tokenMap.putIfAbsent(compact, canonical)
    }

    def.allowedValues.forEach { v ->
        addTokens(v.code, v.code)
        addTokens(v.label, v.code)
        v.synonyms.forEach { syn -> addTokens(syn, v.code) }
    }
    if (def.allowedValues.isEmpty()) {
        canonicalValues.forEach { canon ->
            addTokens(canon, canon)
        }
    }
    return tokenMap
}

private fun normalizeToken(token: String): String =
    token.lowercase().replace("[^\\p{L}\\p{N}]+".toRegex(), "")
