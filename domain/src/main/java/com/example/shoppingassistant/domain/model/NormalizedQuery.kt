package com.example.shoppingassistant.domain.model

import kotlinx.serialization.Serializable

/**
 * Нормализованный запрос. brandKey/modelKey — машинные ключи (индексы БД/сравнение).
 */
@Serializable
data class NormalizedQuery(
    val brand: String,
    val model: String,
    val attributes: Map<String, TypedAttributeValue> = emptyMap(),
    val brandKey: String = Normalization.key(brand),
    val modelKey: String = Normalization.key(model)
)

fun NormalizedQuery.rawAttributes(): Map<String, String> =
    attributes.toRawStringAttributes()

/** Единые правила нормализации ключей/атрибутов. */
object Normalization {
    private val nonAlnum = Regex("[^\\p{Alnum}]+")
    private val attrSeparator = Regex("[\\s\\-]+")
    private val invalidAttrChars = Regex("[^\\p{Alnum}_]+")
    private val duplicateUnderscore = Regex("_+")

    fun key(raw: String): String = raw.trim()
        .lowercase()
        .replace(nonAlnum, "-")
        .trim('-')

    fun normalizeAttrs(attrs: Map<String, String>): Map<String, String> =
        attrs.mapNotNull { (k, v) ->
            val normalizedKey = attributeKey(k)
            if (normalizedKey.isEmpty()) {
                null
            } else {
                normalizedKey to v.trim()
            }
        }.toMap(LinkedHashMap())

    fun normalizeTypedAttrs(attrs: Map<String, String>): Map<String, TypedAttributeValue> =
        normalizeAttrs(attrs).toTypedAttributesGuess()

    fun attributeKey(raw: String): String = raw.trim()
        .lowercase()
        .replace(attrSeparator, "_")
        .replace(invalidAttrChars, "")
        .replace(duplicateUnderscore, "_")
        .trim('_')
}
