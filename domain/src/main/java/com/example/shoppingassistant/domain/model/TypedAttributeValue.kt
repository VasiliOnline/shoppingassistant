package com.example.shoppingassistant.domain.model

import java.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = TypedAttributeValueSerializer::class)
sealed interface TypedAttributeValue {
    fun asRawString(): String

    @Serializable
    data class Text(val value: String) : TypedAttributeValue {
        override fun asRawString(): String = value
    }

    @Serializable
    data class Number(val value: Double) : TypedAttributeValue {
        override fun asRawString(): String = formatNumber(value)
    }

    @Serializable
    data class Bool(val value: Boolean) : TypedAttributeValue {
        override fun asRawString(): String = value.toString()
    }

    companion object {
        fun fromRawString(raw: String): TypedAttributeValue {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return Text("")
            val lowered = trimmed.lowercase(Locale.ROOT)
            if (lowered == "true") return Bool(true)
            if (lowered == "false") return Bool(false)
            val number = trimmed.toDoubleOrNull()
            if (number != null && NUMBER_REGEX.matches(trimmed)) {
                return Number(number)
            }
            return Text(trimmed)
        }

        private val NUMBER_REGEX = Regex("^-?[0-9]+(?:\\.[0-9]+)?$")
    }
}

typealias TypedAttributes = Map<String, TypedAttributeValue>

fun TypedAttributeValue.asTextOrNull(): String? = when (this) {
    is TypedAttributeValue.Text -> value
    is TypedAttributeValue.Number -> asRawString()
    is TypedAttributeValue.Bool -> asRawString()
}

fun TypedAttributeValue.asDoubleOrNull(): Double? = when (this) {
    is TypedAttributeValue.Number -> value
    is TypedAttributeValue.Text -> value.trim().toDoubleOrNull()
    is TypedAttributeValue.Bool -> null
}

fun TypedAttributeValue.asFloatOrNull(): Float? = asDoubleOrNull()?.toFloat()

fun TypedAttributeValue.asBooleanOrNull(): Boolean? = when (this) {
    is TypedAttributeValue.Bool -> value
    is TypedAttributeValue.Text -> {
        when (value.trim().lowercase(Locale.ROOT)) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> null
        }
    }
    is TypedAttributeValue.Number -> null
}

object TypedAttributeValueSerializer : KSerializer<TypedAttributeValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TypedAttributeValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TypedAttributeValue) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("TypedAttributeValue supports only JSON serialization.")
        val json = when (value) {
            is TypedAttributeValue.Text -> JsonPrimitive(value.value)
            is TypedAttributeValue.Number -> {
                if (value.value.isFinite()) JsonPrimitive(value.value)
                else JsonPrimitive(value.value.toString())
            }
            is TypedAttributeValue.Bool -> JsonPrimitive(value.value)
        }
        jsonEncoder.encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): TypedAttributeValue {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("TypedAttributeValue supports only JSON deserialization.")
        val element = jsonDecoder.decodeJsonElement()
        return fromJsonElement(element)
    }

    private fun fromJsonElement(element: JsonElement): TypedAttributeValue {
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    TypedAttributeValue.Text(element.content)
                } else {
                    element.booleanOrNull?.let { return TypedAttributeValue.Bool(it) }
                    element.doubleOrNull?.let { return TypedAttributeValue.Number(it) }
                    TypedAttributeValue.Text(element.content)
                }
            }
            is JsonObject -> fromJsonObject(element)
            else -> TypedAttributeValue.Text(element.toString())
        }
    }

    private fun fromJsonObject(obj: JsonObject): TypedAttributeValue {
        val kindRaw = obj["kind"]?.jsonPrimitive?.contentOrNull
            ?: obj["type"]?.jsonPrimitive?.contentOrNull

        val text = obj["text"]?.jsonPrimitive?.contentOrNull
            ?: obj["value"]?.jsonPrimitive?.contentOrNull
            ?: obj["raw"]?.jsonPrimitive?.contentOrNull

        val bool = obj["bool"]?.jsonPrimitive?.booleanOrNull
            ?: obj["boolean"]?.jsonPrimitive?.booleanOrNull
            ?: obj["value"]?.jsonPrimitive?.booleanOrNull

        val number = obj["number"]?.jsonPrimitive?.doubleOrNull
            ?: obj["value"]?.jsonPrimitive?.doubleOrNull

        val normalizedKind = kindRaw?.trim()?.uppercase(Locale.ROOT)
        return when (normalizedKind) {
            "BOOLEAN", "BOOL" -> bool?.let { TypedAttributeValue.Bool(it) }
                ?: text?.let { TypedAttributeValue.fromRawString(it) }
                ?: TypedAttributeValue.Text("")
            "NUMBER", "NUMERIC", "DECIMAL", "INT" -> number?.let { TypedAttributeValue.Number(it) }
                ?: text?.let { TypedAttributeValue.fromRawString(it) }
                ?: TypedAttributeValue.Text("")
            "STRING", "TEXT", "ENUM" -> TypedAttributeValue.Text(text.orEmpty())
            else -> {
                bool?.let { return TypedAttributeValue.Bool(it) }
                number?.let { return TypedAttributeValue.Number(it) }
                TypedAttributeValue.Text(text.orEmpty())
            }
        }
    }
}

fun Map<String, String>.toTypedAttributesGuess(): TypedAttributes =
    entries
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isEmpty()) return@mapNotNull null
            normalizedKey to TypedAttributeValue.fromRawString(value)
        }
        .toMap(LinkedHashMap())

fun Map<String, String>.toTypedAttributeFiltersGuess(): Map<String, TypedAttributeFilter> =
    entries
        .mapNotNull { (key, rawValue) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isEmpty()) return@mapNotNull null
            val filter = guessTypedAttributeFilter(rawValue) ?: return@mapNotNull null
            normalizedKey to filter
        }
        .toMap(LinkedHashMap())

fun Map<String, TypedAttributeValue>.toRawStringAttributes(): Map<String, String> =
    entries
        .mapNotNull { (key, value) ->
            val normalizedKey = key.trim()
            if (normalizedKey.isEmpty()) return@mapNotNull null
            normalizedKey to value.asRawString()
        }
        .toMap(LinkedHashMap())

private fun formatNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val longValue = value.toLong()
    if (value == longValue.toDouble()) return longValue.toString()
    return value.toString()
}

private fun guessTypedAttributeFilter(raw: String): TypedAttributeFilter? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    val lowered = value.lowercase(Locale.ROOT)
    if (lowered == "exists") {
        return TypedAttributeFilter(op = TypedAttributeOperator.EXISTS)
    }
    if (lowered == "not_exists" || lowered == "!exists") {
        return TypedAttributeFilter(op = TypedAttributeOperator.NOT_EXISTS)
    }

    operatorPrefixes.forEach { (prefix, op) ->
        if (!value.startsWith(prefix)) return@forEach
        val parsed = parseTypedFilterValue(value.removePrefix(prefix)) ?: return null
        return TypedAttributeFilter(op = op, value = parsed)
    }

    val rangeMatch = numericRangeRegex.matchEntire(value)
    if (rangeMatch != null) {
        val from = parseTypedFilterValue(rangeMatch.groupValues[1]) as? TypedAttributeValue.Number
        val to = parseTypedFilterValue(rangeMatch.groupValues[2]) as? TypedAttributeValue.Number
        if (from != null || to != null) {
            return TypedAttributeFilter(op = TypedAttributeOperator.BETWEEN, from = from, to = to)
        }
    }

    val parts = value
        .split(',', ';', '|')
        .map { part -> part.trim() }
        .filter { part -> part.isNotEmpty() }
    if (parts.size > 1) {
        val values = parts.mapNotNull(::parseTypedFilterValue).distinct()
        if (values.isNotEmpty()) {
            return TypedAttributeFilter(op = TypedAttributeOperator.IN, values = values)
        }
    }

    if (value.contains('*')) {
        val query = value.replace("*", "").trim()
        if (query.isNotEmpty()) {
            return TypedAttributeFilter(
                op = TypedAttributeOperator.CONTAINS,
                value = TypedAttributeValue.Text(query),
            )
        }
    }

    val typed = parseTypedFilterValue(value) ?: return null
    return TypedAttributeFilter(op = TypedAttributeOperator.EQ, value = typed)
}

private fun parseTypedFilterValue(raw: String): TypedAttributeValue? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val normalizedNumber = trimmed.replace(',', '.')
    val parsedNumber = normalizedNumber.toDoubleOrNull()
    if (parsedNumber != null && signedNumberRegex.matches(normalizedNumber)) {
        return TypedAttributeValue.Number(parsedNumber)
    }
    return TypedAttributeValue.fromRawString(trimmed)
}

private val operatorPrefixes = listOf(
    ">=" to TypedAttributeOperator.GTE,
    "<=" to TypedAttributeOperator.LTE,
    ">" to TypedAttributeOperator.GT,
    "<" to TypedAttributeOperator.LT,
)

private val signedNumberRegex = Regex("^-?[0-9]+(?:\\.[0-9]+)?$")
private val numericRangeRegex = Regex("^(-?[0-9]+(?:[\\.,][0-9]+)?)\\s*(?:\\.\\.|[-–—])\\s*(-?[0-9]+(?:[\\.,][0-9]+)?)$")
