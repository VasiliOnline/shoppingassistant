package com.example.shoppingassistant.domain.catalog

internal fun humanizeCompactRegistryRemainder(raw: String): String =
    raw
        .replace("(?<=\\d)(?=\\p{L})".toRegex(), " ")
        .replace("(?<=\\p{L})(?=\\d)".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

internal fun matchesCompactRegistryPrefix(
    compactQuery: String,
    compactAlias: String,
): Boolean {
    if (!compactQuery.startsWith(compactAlias)) return false
    if (compactQuery.length == compactAlias.length) return true
    return !compactQuery[compactAlias.length].isLetter()
}
