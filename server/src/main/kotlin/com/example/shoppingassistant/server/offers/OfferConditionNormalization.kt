package com.example.shoppingassistant.server.offers

import java.util.Locale

internal fun normalizeOfferCondition(value: String?): String? {
    val raw = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (raw.isBlank()) return null
    val collapsed = raw
        .replace("ё", "е")
        .replace("-", "_")
        .replace(" ", "_")
        .replace("/", "_")
        .replace(Regex("_+"), "_")
        .trim('_')
    val normalized = when (collapsed) {
        "new", "brand_new", "новый", "новое" -> "new"
        "like_new", "likenew", "как_новый", "как_новое", "почти_новый" -> "like_new"
        "used", "бу", "б_у", "second_hand", "secondhand" -> "used"
        else -> collapsed
    }
    return normalized.takeIf { it.isNotBlank() }
}

internal fun expandOfferConditionAliases(value: String?): Set<String> {
    val normalized = normalizeOfferCondition(value) ?: return emptySet()
    val aliases = when (normalized) {
        "new" -> listOf("new", "brand new", "brand_new", "новый", "новое")
        "like_new" -> listOf("like_new", "like new", "как новый", "как новое")
        "used" -> listOf("used", "б/у", "бу", "б_у", "second hand", "secondhand")
        else -> listOf(normalized)
    }
    return aliases
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()
}
