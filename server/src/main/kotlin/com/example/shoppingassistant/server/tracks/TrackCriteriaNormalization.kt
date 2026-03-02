package com.example.shoppingassistant.server.tracks

import com.example.shoppingassistant.domain.tracks.TrackFilters
import com.example.shoppingassistant.domain.tracks.TrackAttributeRange
import com.example.shoppingassistant.domain.tracks.TrackType
import java.security.MessageDigest
import java.util.Locale

data class TrackOfferCriteriaFilters(
    val location: String?,
    val condition: String?,
    val conditions: List<String>,
    val deliveryChannels: List<String>,
    val sellerQuery: String?,
)

data class TrackDedupTarget(
    val type: TrackType,
    val matchKey: String?,
    val categoryCode: String?,
    val attributes: Map<String, String>,
    val attributesMulti: Map<String, List<String>>,
    val attributesRange: Map<String, TrackAttributeRange>,
    val queryText: String?,
)

object TrackOfferCriteriaNormalizer {
    private val deliverySplitRegex = Regex("[,;/|+]")

    fun toOfferCriteriaFilters(filters: TrackFilters): TrackOfferCriteriaFilters {
        val location = sanitizeText(filters.region)
        val condition = normalizeCondition(filters.condition)
        return TrackOfferCriteriaFilters(
            location = location,
            condition = condition,
            conditions = condition?.let(::listOf) ?: emptyList(),
            deliveryChannels = normalizeDeliveryChannels(filters.delivery),
            sellerQuery = sanitizeText(filters.seller),
        )
    }

    private fun sanitizeText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotBlank() }

    private fun normalizeCondition(raw: String?): String? {
        val normalized = sanitizeText(raw)?.lowercase(Locale.ROOT) ?: return null
        return when (normalized) {
            "new",
            "новый",
            "новая",
            "новое",
            -> "new"

            "like_new",
            "likenew",
            "как новый",
            "как новая",
            "как новое",
            -> "like_new"

            "used",
            "б/у",
            "бу",
            "подержанный",
            -> "used"

            else -> normalized
        }
    }

    private fun normalizeDeliveryChannels(raw: String?): List<String> {
        val normalized = sanitizeText(raw)?.lowercase(Locale.ROOT) ?: return emptyList()
        val channels = LinkedHashSet<String>()
        if (normalized.contains("delivery") || normalized.contains("достав")) {
            channels += "delivery"
        }
        if (normalized.contains("pickup") || normalized.contains("самовы")) {
            channels += "pickup"
        }
        if (channels.isEmpty()) {
            normalized
                .split(deliverySplitRegex)
                .map { token -> token.trim() }
                .filter { token -> token.isNotBlank() }
                .forEach { token ->
                    when {
                        token == "delivery" || token.contains("достав") -> channels += "delivery"
                        token == "pickup" || token.contains("самовы") -> channels += "pickup"
                    }
                }
        }
        if (channels.isEmpty()) {
            channels += normalized
        }
        return channels.toList()
    }
}

object TrackDedupKeyFactory {
    fun normalizeTarget(
        type: TrackType,
        matchKeyRaw: String?,
        categoryCodeRaw: String?,
        attributesRaw: Map<String, String> = emptyMap(),
        attributesMultiRaw: Map<String, List<String>> = emptyMap(),
        attributesRangeRaw: Map<String, TrackAttributeRange> = emptyMap(),
        queryTextRaw: String? = null,
    ): TrackDedupTarget? {
        val normalizedAttributes = normalizeAttributes(attributesRaw)
        val normalizedMulti = normalizeAttributesMulti(attributesMultiRaw)
        val normalizedRanges = normalizeAttributesRange(attributesRangeRaw)
        val normalizedQueryText = normalizeQueryText(queryTextRaw)
        return when (type) {
            TrackType.PRODUCT -> {
                val normalizedCategoryCode = normalizeCategoryCode(categoryCodeRaw) ?: return null
                val normalizedMatchKey = normalizeMatchKey(matchKeyRaw)
                TrackDedupTarget(
                    type = TrackType.PRODUCT,
                    matchKey = normalizedMatchKey,
                    categoryCode = normalizedCategoryCode,
                    attributes = normalizedAttributes,
                    attributesMulti = normalizedMulti,
                    attributesRange = normalizedRanges,
                    queryText = normalizedQueryText,
                )
            }
            TrackType.CATEGORY -> {
                val normalizedCategoryCode = normalizeCategoryCode(categoryCodeRaw) ?: return null
                TrackDedupTarget(
                    type = TrackType.CATEGORY,
                    matchKey = null,
                    categoryCode = normalizedCategoryCode,
                    attributes = normalizedAttributes,
                    attributesMulti = normalizedMulti,
                    attributesRange = normalizedRanges,
                    queryText = normalizedQueryText,
                )
            }
            else -> null
        }
    }

    fun buildDedupKey(target: TrackDedupTarget, filters: TrackFilters): String {
        val semanticFingerprint = buildSemanticFingerprint(target, filters)
        val digest = MessageDigest.getInstance("SHA-256").digest(semanticFingerprint.toByteArray())
        val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "v2:$hex"
    }

    fun buildSemanticFingerprint(target: TrackDedupTarget, filters: TrackFilters): String {
        val criteriaFilters = TrackOfferCriteriaNormalizer.toOfferCriteriaFilters(filters)
        val normalizedTargetAttributes = target.attributes.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) {
                    null
                } else {
                    normalizedKey to normalizedValue
                }
            }
            .sortedBy { (key, _) -> key.lowercase(Locale.ROOT) }
        val targetAttributesFingerprint = normalizedTargetAttributes.joinToString(separator = "|") { (key, value) ->
            "${canonicalToken(key)}=${canonicalToken(value)}"
        }
        val normalizedTargetMulti = target.attributesMulti.entries
            .mapNotNull { (key, values) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) return@mapNotNull null
                val normalizedValues = values
                    .asSequence()
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotBlank() }
                    .map(::canonicalToken)
                    .distinct()
                    .sorted()
                    .toList()
                if (normalizedValues.isEmpty()) null else normalizedKey to normalizedValues
            }
            .sortedBy { (key, _) -> key.lowercase(Locale.ROOT) }
        val targetMultiFingerprint = normalizedTargetMulti.joinToString(separator = "|") { (key, values) ->
            "${canonicalToken(key)}=[${values.joinToString(",")}]"
        }
        val normalizedTargetRanges = target.attributesRange.entries
            .mapNotNull { (key, range) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) return@mapNotNull null
                val normalizedRange = TrackAttributeRange(
                    min = range.min?.trim()?.takeIf { it.isNotBlank() },
                    max = range.max?.trim()?.takeIf { it.isNotBlank() },
                    unit = range.unit?.trim()?.takeIf { it.isNotBlank() },
                )
                if (normalizedRange.min == null && normalizedRange.max == null && normalizedRange.unit == null) {
                    null
                } else {
                    normalizedKey to normalizedRange
                }
            }
            .sortedBy { (key, _) -> key.lowercase(Locale.ROOT) }
        val targetRangesFingerprint = normalizedTargetRanges.joinToString(separator = "|") { (key, range) ->
            "${canonicalToken(key)}=${canonicalToken(range.min)}..${canonicalToken(range.max)}@${canonicalToken(range.unit)}"
        }
        val normalizedRuntimeExtra = filters.extra.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) {
                    null
                } else {
                    normalizedKey to normalizedValue
                }
            }
            .sortedBy { (key, _) -> key.lowercase(Locale.ROOT) }
        val runtimeExtraFingerprint = normalizedRuntimeExtra.joinToString(separator = "|") { (key, value) ->
            "${canonicalToken(key)}=${canonicalToken(value)}"
        }
        return buildString {
            append("type=").append(target.type.name.lowercase(Locale.ROOT))
            append("|match=").append(canonicalToken(target.matchKey))
            append("|category=").append(canonicalToken(target.categoryCode))
            append("|target_attrs=").append(targetAttributesFingerprint)
            append("|target_attrs_multi=").append(targetMultiFingerprint)
            append("|target_attrs_range=").append(targetRangesFingerprint)
            append("|target_query=").append(canonicalToken(target.queryText))
            append("|location=").append(canonicalToken(criteriaFilters.location))
            append("|condition=").append(canonicalToken(criteriaFilters.condition))
            append("|delivery=").append(criteriaFilters.deliveryChannels.joinToString(","))
            append("|seller=").append(canonicalToken(criteriaFilters.sellerQuery))
            append("|runtime_extra=").append(runtimeExtraFingerprint)
        }
    }

    private fun canonicalToken(value: String?): String =
        value?.trim()?.lowercase(Locale.ROOT).orEmpty()

    private fun normalizeMatchKey(raw: String?): String? {
        return raw
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeCategoryCode(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)

    private fun normalizeAttributes(values: Map<String, String>): Map<String, String> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = key.trim()
                val normalizedValue = value.trim()
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
            .toMap(LinkedHashMap())
    }

    private fun normalizeAttributesMulti(values: Map<String, List<String>>): Map<String, List<String>> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, rawValues) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) return@mapNotNull null
                val normalizedValues = rawValues
                    .asSequence()
                    .map { value -> value.trim() }
                    .filter { value -> value.isNotBlank() }
                    .distinct()
                    .toList()
                if (normalizedValues.isEmpty()) null else normalizedKey to normalizedValues
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
            .toMap(LinkedHashMap())
    }

    private fun normalizeAttributesRange(values: Map<String, TrackAttributeRange>): Map<String, TrackAttributeRange> {
        if (values.isEmpty()) return emptyMap()
        return values.entries
            .mapNotNull { (key, range) ->
                val normalizedKey = key.trim()
                if (normalizedKey.isBlank()) return@mapNotNull null
                val normalizedRange = TrackAttributeRange(
                    min = range.min?.trim()?.takeIf { it.isNotBlank() },
                    max = range.max?.trim()?.takeIf { it.isNotBlank() },
                    unit = range.unit?.trim()?.takeIf { it.isNotBlank() },
                )
                if (normalizedRange.min == null && normalizedRange.max == null && normalizedRange.unit == null) {
                    null
                } else {
                    normalizedKey to normalizedRange
                }
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
            .toMap(LinkedHashMap())
    }

    private fun normalizeQueryText(raw: String?): String? =
        raw?.replace("\\s+".toRegex(), " ")?.trim()?.takeIf { it.isNotBlank() }
}
