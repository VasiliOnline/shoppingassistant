package com.example.shoppingassistant.domain.search

import java.util.Locale

object SearchTextNormalizer {
    private val whitespaceRegex = Regex("\\s+")
    private val nonAlphaNumericRegex = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(raw: String): String =
        raw.replace(whitespaceRegex, " ").trim()

    fun normalizeForEditing(raw: String): String {
        if (raw.isBlank()) return ""
        val hasTrailingSpace = raw.last().isWhitespace()
        val normalized = normalize(raw.trimStart())
        return if (hasTrailingSpace && normalized.isNotEmpty()) "$normalized " else normalized
    }

    fun normalizeKey(raw: String, locale: Locale = Locale.ROOT): String =
        normalize(raw).lowercase(locale)

    fun normalizeToken(raw: String, locale: Locale = Locale.ROOT): String =
        normalizeKey(raw, locale).replace(nonAlphaNumericRegex, "")

    fun equalsNormalized(
        left: String,
        right: String,
        ignoreCase: Boolean = true,
    ): Boolean {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        return if (ignoreCase) {
            normalizedLeft.equals(normalizedRight, ignoreCase = true)
        } else {
            normalizedLeft == normalizedRight
        }
    }

    fun tokens(raw: String): List<String> =
        normalize(raw)
            .split(' ')
            .map { token -> token.trim() }
            .filter { token -> token.isNotEmpty() }
}
