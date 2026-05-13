package com.example.shoppingassistant.feature.pages.model

import com.example.shoppingassistant.domain.search.SearchTextNormalizer

internal fun parseFreeQueryAttributes(
    queryText: String,
    attributeDefs: List<AttributeDef>,
): Map<String, String> {
    if (attributeDefs.isEmpty()) return emptyMap()
    val tokens = SearchTextNormalizer.tokens(queryText)
    if (tokens.isEmpty()) return emptyMap()

    val consumed = BooleanArray(tokens.size)
    val parsed = linkedMapOf<String, String>()
    val maxNGram = minOf(maxAttributeValueTokenSpan(attributeDefs), tokens.size, 6)

    for (window in maxNGram downTo 1) {
        for (start in 0..(tokens.size - window)) {
            if ((start until start + window).any { index -> consumed[index] }) continue
            val fragment = tokens.subList(start, start + window).joinToString(" ")
            val matches = attributeDefs.mapNotNull { def ->
                matchFreeQueryAttributeValue(def, fragment)?.let { canonical ->
                    def.key to canonical
                }
            }
            if (matches.size != 1) continue
            val (key, canonical) = matches.first()
            if (parsed.containsKey(key)) continue
            parsed[key] = canonical
            for (index in start until start + window) {
                consumed[index] = true
            }
        }
    }

    tokens.forEachIndexed { index, token ->
        if (consumed[index]) return@forEachIndexed
        attributeDefs.forEach { def ->
            if (parsed.containsKey(def.key)) return@forEach
            val canonical = matchFreeQueryAttributeValueWithinToken(def, token) ?: return@forEach
            parsed[def.key] = canonical
        }
    }

    return parsed
}

internal fun matchFreeQueryAttributeValue(
    def: AttributeDef,
    text: String,
): String? {
    val normalized = text.trim().lowercase()
    if (normalized.isBlank()) return null
    val normalizedCompact = normalizeFreeQueryAttributeToken(normalized)
    val values = def.allowedValues.ifEmpty {
        def.options.map { ValueDef(code = it, label = it, synonyms = listOf(it)) }
    }
    val exactMatches = linkedSetOf<String>()
    values.forEach { valueDef ->
        if (valueDef.code.equals(normalized, ignoreCase = true)) exactMatches += valueDef.code
        if (valueDef.label.equals(normalized, ignoreCase = true)) exactMatches += valueDef.code
        if (valueDef.synonyms.any { synonym -> synonym.equals(normalized, ignoreCase = true) }) {
            exactMatches += valueDef.code
        }
        val codeCompact = normalizeFreeQueryAttributeToken(valueDef.code)
        val labelCompact = normalizeFreeQueryAttributeToken(valueDef.label)
        if (codeCompact.isNotBlank() && codeCompact == normalizedCompact) exactMatches += valueDef.code
        if (labelCompact.isNotBlank() && labelCompact == normalizedCompact) exactMatches += valueDef.code
        if (valueDef.synonyms.any { synonym ->
                val synonymCompact = normalizeFreeQueryAttributeToken(synonym)
                synonymCompact.isNotBlank() && synonymCompact == normalizedCompact
            }
        ) {
            exactMatches += valueDef.code
        }
    }
    if (exactMatches.size == 1) return exactMatches.first()
    if (exactMatches.size > 1) return null

    val prefixMatches = linkedSetOf<String>()
    values.forEach { valueDef ->
        val singleTokenForms = singleTokenPrefixForms(valueDef)
        if (normalized.length >= 3 &&
            singleTokenForms.any { form -> form.raw.startsWith(normalized, ignoreCase = true) }
        ) {
            prefixMatches += valueDef.code
        }
        if (normalizedCompact.length >= 3 &&
            singleTokenForms.any { form ->
                form.compact.isNotBlank() && form.compact.startsWith(normalizedCompact)
            }
        ) {
            prefixMatches += valueDef.code
        }
    }
    return prefixMatches.singleOrNull()
}

private fun matchFreeQueryAttributeValueWithinToken(
    def: AttributeDef,
    token: String,
): String? {
    val tokenCompact = normalizeFreeQueryAttributeToken(token)
    if (tokenCompact.length < 4) return null
    val values = def.allowedValues.ifEmpty {
        def.options.map { ValueDef(code = it, label = it, synonyms = listOf(it)) }
    }

    var bestCode: String? = null
    var bestScore = -1
    var ambiguous = false

    values.forEach { valueDef ->
        val bestFormLength = compactFormsForSubtokenMatch(valueDef)
            .filter { compact ->
                compact.length >= 3 &&
                    compact != tokenCompact &&
                    (tokenCompact.endsWith(compact) || tokenCompact.startsWith(compact))
            }
            .maxOfOrNull { it.length } ?: return@forEach

        when {
            bestFormLength > bestScore -> {
                bestCode = valueDef.code
                bestScore = bestFormLength
                ambiguous = false
            }

            bestFormLength == bestScore && bestCode != valueDef.code -> {
                ambiguous = true
            }
        }
    }

    return if (ambiguous) null else bestCode
}

private fun compactFormsForSubtokenMatch(valueDef: ValueDef): List<String> {
    val forms = linkedSetOf<String>()
    val labelCompact = normalizeFreeQueryAttributeToken(valueDef.label)
    if (labelCompact.isNotBlank()) {
        forms += labelCompact
    }
    valueDef.synonyms.forEach { synonym ->
        val synonymCompact = normalizeFreeQueryAttributeToken(synonym)
        if (synonymCompact.isNotBlank()) {
            forms += synonymCompact
        }
    }
    val codeCompact = normalizeFreeQueryAttributeToken(valueDef.code)
    if (codeCompact.isNotBlank() && valueDef.code.any { !it.isDigit() }) {
        forms += codeCompact
    }
    return forms.toList()
}

private fun singleTokenPrefixForms(valueDef: ValueDef): List<PrefixMatchForm> {
    val forms = linkedSetOf<PrefixMatchForm>()
    sequenceOf(valueDef.code, valueDef.label)
        .plus(valueDef.synonyms.asSequence())
        .forEach { rawForm ->
            val normalizedRaw = rawForm.trim()
            if (normalizedRaw.isBlank()) return@forEach
            if (SearchTextNormalizer.tokens(normalizedRaw).size != 1) return@forEach
            val compact = normalizeFreeQueryAttributeToken(normalizedRaw)
            if (compact.isBlank()) return@forEach
            forms += PrefixMatchForm(raw = normalizedRaw, compact = compact)
        }
    return forms.toList()
}

private data class PrefixMatchForm(
    val raw: String,
    val compact: String,
)

internal fun normalizeFreeQueryAttributeToken(raw: String): String =
    raw
        .lowercase()
        .replace("+", " plus ")
        .replace("[^\\p{L}\\p{N}]+".toRegex(), "")

private fun maxAttributeValueTokenSpan(attributeDefs: List<AttributeDef>): Int {
    var maxSpan = 1
    attributeDefs.forEach { def ->
        val values = def.allowedValues.ifEmpty {
            def.options.map { ValueDef(code = it, label = it, synonyms = listOf(it)) }
        }
        values.forEach { valueDef ->
            sequenceOf(valueDef.code, valueDef.label)
                .plus(valueDef.synonyms.asSequence())
                .forEach { form ->
                    val span = SearchTextNormalizer.tokens(form).size
                    if (span > maxSpan) {
                        maxSpan = span
                    }
                }
        }
    }
    return maxSpan
}
