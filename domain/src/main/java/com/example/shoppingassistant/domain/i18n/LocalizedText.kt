package com.example.shoppingassistant.domain.i18n

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Locale

@Serializable(with = LocalizedTextSerializer::class)
data class LocalizedText(
    private val entries: Map<String, String> = emptyMap(),
) {
    companion object {
        val Empty = LocalizedText()

        fun of(vararg entries: Pair<String, String?>): LocalizedText =
            entries
                .mapNotNull { (locale, label) ->
                    label?.let { locale to it }
                }
                .toMap()
                .toLocalizedText()
    }

    operator fun get(locale: String): String? =
        asMap()[normalizeLocalizedLocale(locale)]

    fun isBlank(): Boolean = normalizedValues().isEmpty()

    val values: List<String>
        get() = asMap().values.toList()

    fun asMap(): Map<String, String> = normalizedValues()

    fun resolve(
        locale: String? = null,
        fallback: String? = null,
    ): String? {
        val normalized = normalizedValues()
        if (normalized.isEmpty()) return fallback

        preferredLocalizedLocales(locale)
            .asSequence()
            .mapNotNull { normalized[it] }
            .firstOrNull()
            ?.let { return it }

        return normalized["ru"]
            ?: normalized["en"]
            ?: normalized["und"]
            ?: normalized.values.firstOrNull()
            ?: fallback
    }

    private fun normalizedValues(): Map<String, String> = normalizeLocalizedValues(entries)
}

fun localizedTextOf(vararg entries: Pair<String, String?>): LocalizedText =
    LocalizedText.of(*entries)

fun Map<String, String>.toLocalizedText(): LocalizedText =
    LocalizedText(this)

internal fun normalizeLocalizedValues(values: Map<String, String>): Map<String, String> =
    buildMap {
        values.forEach { (locale, label) ->
            val normalizedLocale = normalizeLocalizedLocale(locale)
            val normalizedLabel = label.trim()
            if (normalizedLocale.isNotEmpty() && normalizedLabel.isNotEmpty()) {
                put(normalizedLocale, normalizedLabel)
            }
        }
    }

internal fun normalizeLocalizedLocale(locale: String?): String =
    locale?.trim()?.lowercase(Locale.ROOT).orEmpty()

internal fun preferredLocalizedLocales(locale: String?): List<String> {
    val normalized = normalizeLocalizedLocale(locale)
    if (normalized.isBlank()) return emptyList()

    val language = normalized.substringBefore('-')
    return buildList {
        add(normalized)
        if (language.isNotBlank() && language != normalized) {
            add(language)
        }
    }
}

private object LocalizedTextSerializer : KSerializer<LocalizedText> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: LocalizedText,
    ) {
        encoder.encodeSerializableValue(delegate, value.asMap())
    }

    override fun deserialize(decoder: Decoder): LocalizedText =
        decoder.decodeSerializableValue(delegate).toLocalizedText()
}
