package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.core.data.BrandModelRules
import com.example.shoppingassistant.domain.catalog.CatalogAttributeSpec
import com.example.shoppingassistant.domain.catalog.CatalogCategoryEffectiveSpec
import com.example.shoppingassistant.domain.catalog.allAttributes
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetDefinition
import com.example.shoppingassistant.domain.facet.runtimeFilterKey
import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import com.example.shoppingassistant.domain.model.ValueFacet
import com.example.shoppingassistant.domain.search.SearchTextNormalizer
import kotlinx.serialization.Serializable
import java.util.LinkedHashMap
import java.util.Locale
import java.time.LocalDate

internal enum class TypedFacetValueAvailability {
    Available,
    KnownUnavailable,
}

internal data class TypedFacetValueOption(
    val value: String,
    val count: Int? = null,
    val availability: TypedFacetValueAvailability = TypedFacetValueAvailability.Available,
)

internal data class TypedFacetValueSections(
    val available: List<TypedFacetValueOption> = emptyList(),
    val unavailable: List<TypedFacetValueOption> = emptyList(),
)

@Serializable
internal data class ResultsTypedFacetUniverseEntry(
    val runtimeKey: String,
    val title: String,
    val valueType: FacetDataType? = null,
    val knownValues: List<String> = emptyList(),
    val knownValueAliases: Map<String, List<String>> = emptyMap(),
    val seedAvailableValues: List<String> = emptyList(),
    val seedAvailabilityKnown: Boolean = false,
    val isClosedSet: Boolean = false,
)

@Serializable
internal data class ResultsTypedFacetUniverse(
    val entriesByRuntimeKey: Map<String, ResultsTypedFacetUniverseEntry> = emptyMap(),
)

internal enum class ResultsTypedFacetNoticeReason {
    UnavailableInScope,
    UnknownInCatalog,
}

internal data class ResultsTypedFacetNotice(
    val runtimeKey: String,
    val title: String,
    val requestedValue: String,
    val availableValues: List<String> = emptyList(),
    val reason: ResultsTypedFacetNoticeReason = ResultsTypedFacetNoticeReason.UnavailableInScope,
)

internal data class ResultsFacetResolution(
    val effectiveTypedAttributeFilters: Map<String, TypedAttributeFilterDraft> = emptyMap(),
    val queryBackedTypedAttributeFilters: Map<String, TypedAttributeFilterDraft> = emptyMap(),
    val autoAppliedTypedAttributeFilters: Map<String, TypedAttributeFilterDraft> = emptyMap(),
    val effectiveFreeformQueryAttributes: Map<String, String> = emptyMap(),
    val noticesByRuntimeKey: Map<String, ResultsTypedFacetNotice> = emptyMap(),
)

internal fun resolveResultsFacetResolution(
    query: NormalizedQuery?,
    rawQueryText: String? = null,
    presetAttributes: Map<String, String>,
    explicitTypedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
    facetDefinitions: List<FacetDefinition>,
    typedFacetUniverse: ResultsTypedFacetUniverse,
    runtimeAttributeFacets: Map<String, List<ValueFacet>> = emptyMap(),
    relaxedQueryTypedFacetKeys: Set<String> = emptySet(),
    noticePriorityTypedFacetKeys: List<String> = emptyList(),
): ResultsFacetResolution {
    val runtimeKeyByTypedKey = buildResultsResolutionRuntimeKeyMap(facetDefinitions)
    val typedFacetKeys = buildResultsResolutionTypedFacetKeys(facetDefinitions)

    val normalizedExplicitTypedFilters = explicitTypedAttributeFilters.entries
        .mapNotNull { (rawKey, draft) ->
            val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
            if (normalizedKey.isBlank()) {
                null
            } else {
                val runtimeKey = runtimeKeyByTypedKey[normalizedKey] ?: normalizedKey
                runtimeKey to draft
            }
        }
        .toMap(LinkedHashMap())

    val presetRuntimeKeys = presetAttributes.keys
        .mapNotNull { rawKey ->
            val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
            if (normalizedKey.isBlank()) {
                null
            } else {
                runtimeKeyByTypedKey[normalizedKey] ?: normalizedKey
            }
        }
        .toCollection(LinkedHashSet())

    val normalizedRelaxedKeys = relaxedQueryTypedFacetKeys
        .map { rawKey -> rawKey.trim().lowercase(Locale.ROOT) }
        .filter { key -> key.isNotBlank() }
        .toCollection(LinkedHashSet())

    val effectiveFreeformQueryAttributes = LinkedHashMap<String, String>()
    val queryBackedTypedFilters = LinkedHashMap<String, TypedAttributeFilterDraft>()
    val notices = LinkedHashMap<String, ResultsTypedFacetNotice>()
    val requestedTypedKeys = LinkedHashSet<String>()
    val modelRuntimeKey = runtimeKeyByTypedKey["model"] ?: "model"

    val explicitRequestedModel = query
        ?.model
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    val requestedModel = explicitRequestedModel ?: resolveRequestedModelForFacetNotice(
        query = query,
        rawQueryText = rawQueryText,
    )
    val sanitizedRequestedModel = requestedModel
        ?.let { requestedValue ->
            sanitizeRequestedModelForFacetNotice(
                requestedValue = requestedValue,
                query = query,
                runtimeKeyByTypedKey = runtimeKeyByTypedKey,
                typedFacetUniverse = typedFacetUniverse,
            )
        }
        ?.takeIf { value -> value.isNotBlank() }
        ?: requestedModel
    if (
        sanitizedRequestedModel != null &&
        modelRuntimeKey in typedFacetKeys &&
        modelRuntimeKey !in normalizedExplicitTypedFilters.keys &&
        modelRuntimeKey !in presetRuntimeKeys
    ) {
        requestedTypedKeys += modelRuntimeKey
        val universeEntry = typedFacetUniverse.entriesByRuntimeKey[modelRuntimeKey]
        val availableValues = resolveAvailableFacetValues(
            runtimeKey = modelRuntimeKey,
            runtimeAttributeFacets = runtimeAttributeFacets,
            universeEntry = universeEntry,
        )
        val availabilityKnown = runtimeAttributeFacets.containsKey(modelRuntimeKey) ||
            universeEntry?.seedAvailabilityKnown == true
        val requestedMatchesAvailable = availableValues.any { value ->
            modelRequestedValueMatchesKnownCandidate(
                requestedValue = sanitizedRequestedModel,
                candidateValue = value,
                aliases = universeEntry?.knownValueAliases?.get(value).orEmpty(),
            )
        }
        val requestedMatchesKnown = universeEntry
            ?.knownValues
            .orEmpty()
            .any { value ->
                modelRequestedValueMatchesKnownCandidate(
                    requestedValue = sanitizedRequestedModel,
                    candidateValue = value,
                    aliases = universeEntry?.knownValueAliases?.get(value).orEmpty(),
                )
            }
        val showNotice = explicitRequestedModel != null || shouldSurfaceRequestedFacetNotice(
            rawQueryText = rawQueryText,
            requestedValue = sanitizedRequestedModel,
            universeEntry = universeEntry,
        )
        val matchedCanonicalModel = resolveCanonicalRequestedFacetValue(
            requestedValue = sanitizedRequestedModel,
            availableValues = availableValues,
            universeEntry = universeEntry,
            matcher = ::modelRequestedValueMatchesKnownCandidate,
        ) ?: sanitizedRequestedModel

        when {
            modelRuntimeKey in normalizedRelaxedKeys -> {
                if (showNotice) {
                    notices[modelRuntimeKey] = ResultsTypedFacetNotice(
                        runtimeKey = modelRuntimeKey,
                        title = universeEntry?.title ?: modelRuntimeKey,
                        requestedValue = sanitizedRequestedModel,
                        availableValues = availableValues,
                    )
                }
            }

            availabilityKnown && requestedMatchesAvailable -> {
                queryBackedTypedFilters[modelRuntimeKey] = TypedAttributeFilterDraft(
                    op = TypedAttributeOperator.EQ,
                    value = matchedCanonicalModel,
                )
            }

            universeEntry?.isClosedSet == true && !requestedMatchesKnown -> {
                if (showNotice) {
                    notices[modelRuntimeKey] = ResultsTypedFacetNotice(
                        runtimeKey = modelRuntimeKey,
                        title = universeEntry.title,
                        requestedValue = sanitizedRequestedModel,
                        availableValues = universeEntry.knownValues,
                        reason = ResultsTypedFacetNoticeReason.UnknownInCatalog,
                    )
                }
            }

            availabilityKnown && !requestedMatchesAvailable -> {
                if (showNotice) {
                    notices[modelRuntimeKey] = ResultsTypedFacetNotice(
                        runtimeKey = modelRuntimeKey,
                        title = universeEntry?.title ?: modelRuntimeKey,
                        requestedValue = sanitizedRequestedModel,
                        availableValues = availableValues,
                    )
                }
            }

            universeEntry?.isClosedSet == true && requestedMatchesKnown -> {
                queryBackedTypedFilters[modelRuntimeKey] = TypedAttributeFilterDraft(
                    op = TypedAttributeOperator.EQ,
                    value = matchedCanonicalModel,
                )
            }
        }
    }

    query?.attributes
        ?.forEach { (rawKey, rawValue) ->
            val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
            if (
                normalizedKey.isBlank() ||
                normalizedKey == "brand" ||
                normalizedKey == "model" ||
                normalizedKey == "model_line" ||
                normalizedKey == "condition"
            ) {
                return@forEach
            }

            val rawTextValue = rawValue.asRawString().trim()
            if (rawTextValue.isBlank()) return@forEach

            val runtimeKey = runtimeKeyByTypedKey[normalizedKey]
            if (runtimeKey == null || runtimeKey !in typedFacetKeys) {
                effectiveFreeformQueryAttributes[normalizedKey] = rawTextValue
                return@forEach
            }

            if (runtimeKey in normalizedExplicitTypedFilters.keys || runtimeKey in presetRuntimeKeys) {
                return@forEach
            }

            requestedTypedKeys += runtimeKey
            val universeEntry = typedFacetUniverse.entriesByRuntimeKey[runtimeKey]
            val availableValues = resolveAvailableFacetValues(
                runtimeKey = runtimeKey,
                runtimeAttributeFacets = runtimeAttributeFacets,
                universeEntry = universeEntry,
            )
            val availabilityKnown = runtimeAttributeFacets.containsKey(runtimeKey) ||
                universeEntry?.seedAvailabilityKnown == true
            val requestedMatchesAvailable = availableValues.any { value ->
                requestedFacetValueMatchesKnownCandidate(
                    requestedValue = rawTextValue,
                    candidateValue = value,
                    aliases = universeEntry?.knownValueAliases?.get(value).orEmpty(),
                )
            }
            val requestedMatchesKnown = universeEntry
                ?.knownValues
                .orEmpty()
                .any { value ->
                    requestedFacetValueMatchesKnownCandidate(
                        requestedValue = rawTextValue,
                        candidateValue = value,
                        aliases = universeEntry?.knownValueAliases?.get(value).orEmpty(),
                    )
                }
            val showNotice = shouldSurfaceRequestedFacetNotice(
                rawQueryText = rawQueryText,
                requestedValue = rawTextValue,
                universeEntry = universeEntry,
            )
            val matchedCanonicalValue = resolveCanonicalRequestedFacetValue(
                requestedValue = rawTextValue,
                availableValues = availableValues,
                universeEntry = universeEntry,
                matcher = ::requestedFacetValueMatchesKnownCandidate,
            ) ?: rawTextValue

            when {
                runtimeKey in normalizedRelaxedKeys -> {
                    if (showNotice) {
                        notices[runtimeKey] = ResultsTypedFacetNotice(
                            runtimeKey = runtimeKey,
                            title = universeEntry?.title ?: runtimeKey,
                            requestedValue = rawTextValue,
                            availableValues = availableValues,
                        )
                    }
                }

                availabilityKnown && requestedMatchesAvailable -> {
                    queryBackedTypedFilters[runtimeKey] = TypedAttributeFilterDraft(
                        op = TypedAttributeOperator.EQ,
                        value = matchedCanonicalValue,
                    )
                }

                universeEntry?.isClosedSet == true && !requestedMatchesKnown -> {
                    if (showNotice) {
                        notices[runtimeKey] = ResultsTypedFacetNotice(
                            runtimeKey = runtimeKey,
                            title = universeEntry.title,
                            requestedValue = rawTextValue,
                            availableValues = universeEntry.knownValues,
                            reason = ResultsTypedFacetNoticeReason.UnknownInCatalog,
                        )
                    }
                }

                availabilityKnown -> {
                    if (showNotice) {
                        notices[runtimeKey] = ResultsTypedFacetNotice(
                            runtimeKey = runtimeKey,
                            title = universeEntry?.title ?: runtimeKey,
                            requestedValue = rawTextValue,
                            availableValues = availableValues,
                        )
                    }
                }

                universeEntry?.isClosedSet == true && requestedMatchesKnown -> {
                    queryBackedTypedFilters[runtimeKey] = TypedAttributeFilterDraft(
                        op = TypedAttributeOperator.EQ,
                        value = matchedCanonicalValue,
                    )
                }

                else -> {
                    queryBackedTypedFilters[runtimeKey] = TypedAttributeFilterDraft(
                        op = TypedAttributeOperator.EQ,
                        value = matchedCanonicalValue,
                    )
                }
            }
        }

    val primaryNotices = selectPrimaryTypedFacetNotices(
        notices = notices,
        noticePriorityTypedFacetKeys = noticePriorityTypedFacetKeys,
        fallbackPrimaryRuntimeKey = modelRuntimeKey,
    )

    val autoAppliedTypedFilters = LinkedHashMap<String, TypedAttributeFilterDraft>()
    val occupiedRuntimeKeys = LinkedHashSet<String>().apply {
        addAll(presetRuntimeKeys)
        addAll(normalizedExplicitTypedFilters.keys)
        addAll(requestedTypedKeys)
    }
    facetDefinitions.forEach { definition ->
        val runtimeKey = definition.resultsResolutionRuntimeKey()
        if (runtimeKey.isBlank() || runtimeKey in resultsResolutionSystemFacetKeys) return@forEach
        if (runtimeKey in occupiedRuntimeKeys) return@forEach
        val availableValues = runtimeAttributeFacets[runtimeKey]
            .orEmpty()
            .mapNotNull { facet ->
                facet.name.trim().takeIf { value -> value.isNotBlank() }
            }
            .distinctBy { value -> value.lowercase(Locale.ROOT) }
        if (availableValues.size != 1) return@forEach
        autoAppliedTypedFilters[runtimeKey] = TypedAttributeFilterDraft(
            op = TypedAttributeOperator.EQ,
            value = availableValues.single(),
        )
    }

    return ResultsFacetResolution(
        effectiveTypedAttributeFilters = buildMap {
            putAll(normalizedExplicitTypedFilters)
            queryBackedTypedFilters.forEach { (runtimeKey, draft) ->
                putIfAbsent(runtimeKey, draft)
            }
            autoAppliedTypedFilters.forEach { (runtimeKey, draft) ->
                putIfAbsent(runtimeKey, draft)
            }
        },
        queryBackedTypedAttributeFilters = queryBackedTypedFilters,
        autoAppliedTypedAttributeFilters = autoAppliedTypedFilters,
        effectiveFreeformQueryAttributes = effectiveFreeformQueryAttributes,
        noticesByRuntimeKey = primaryNotices,
    )
}

private fun selectPrimaryTypedFacetNotices(
    notices: Map<String, ResultsTypedFacetNotice>,
    noticePriorityTypedFacetKeys: List<String>,
    fallbackPrimaryRuntimeKey: String,
): Map<String, ResultsTypedFacetNotice> {
    if (notices.isEmpty()) return emptyMap()
    val normalizedPriority = noticePriorityTypedFacetKeys
        .map { key -> key.trim().lowercase(Locale.ROOT) }
        .filter { key -> key.isNotEmpty() }
    if (normalizedPriority.isNotEmpty()) {
        val prioritizedRuntimeKey = normalizedPriority.firstOrNull { runtimeKey -> runtimeKey in notices.keys }
        if (prioritizedRuntimeKey != null) {
            return linkedMapOf(prioritizedRuntimeKey to requireNotNull(notices[prioritizedRuntimeKey]))
        }
    }
    if (fallbackPrimaryRuntimeKey in notices.keys) {
        return linkedMapOf(fallbackPrimaryRuntimeKey to requireNotNull(notices[fallbackPrimaryRuntimeKey]))
    }
    val firstEntry = notices.entries.first()
    return linkedMapOf(firstEntry.key to firstEntry.value)
}

private fun shouldSurfaceRequestedFacetNotice(
    rawQueryText: String?,
    requestedValue: String,
    universeEntry: ResultsTypedFacetUniverseEntry?,
): Boolean {
    val normalizedQuery = normalizeResultsNoticeText(rawQueryText)
    if (normalizedQuery.isBlank()) return true
    val compactQuery = SearchTextNormalizer.normalizeToken(rawQueryText.orEmpty())

    val candidates = LinkedHashSet<String>().apply {
        add(requestedValue)
        universeEntry?.knownValueAliases
            ?.entries
            ?.firstOrNull { (value, _) -> value.equals(requestedValue, ignoreCase = true) }
            ?.value
            .orEmpty()
            .forEach { alias -> add(alias) }
    }

    return candidates.any { candidate ->
        val normalizedCandidate = normalizeResultsNoticeText(candidate)
        if (normalizedCandidate.isBlank()) return@any false
        val compactCandidate = SearchTextNormalizer.normalizeToken(candidate)
        containsResultsNoticePhrase(normalizedQuery, normalizedCandidate) ||
            (compactCandidate.length >= 2 && compactQuery.contains(compactCandidate))
    }
}

private fun resolveRequestedModelForFacetNotice(
    query: NormalizedQuery?,
    rawQueryText: String?,
): String? {
    val explicitModel = query
        ?.model
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    if (explicitModel != null) return explicitModel

    val normalizedQueryBrand = query
        ?.brand
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
    val heuristicQuery = rawQueryText
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?.let { raw -> BrandModelRules.fromKnownFamily(raw) }
        ?: return null
    val heuristicBrand = heuristicQuery.brand.trim().takeIf { value -> value.isNotEmpty() }
    if (
        normalizedQueryBrand != null &&
        heuristicBrand != null &&
        !normalizedQueryBrand.equals(heuristicBrand, ignoreCase = true)
    ) {
        return null
    }
    return heuristicQuery.model.trim().takeIf { value -> value.isNotEmpty() }
}

private fun sanitizeRequestedModelForFacetNotice(
    requestedValue: String,
    query: NormalizedQuery?,
    runtimeKeyByTypedKey: Map<String, String>,
    typedFacetUniverse: ResultsTypedFacetUniverse,
): String {
    var sanitized = requestedValue.trim()
    if (sanitized.isEmpty()) return sanitized

    query?.brand
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?.let { brand ->
            sanitized = removeLeadingFacetPhrase(sanitized, brand)
        }

    val removablePhrases = buildModelTailRemovalPhrases(
        query = query,
        runtimeKeyByTypedKey = runtimeKeyByTypedKey,
        typedFacetUniverse = typedFacetUniverse,
    )
    var changed = true
    while (changed) {
        changed = false
        removablePhrases.forEach { phrase ->
            val updated = removeTrailingFacetPhrase(sanitized, phrase)
            if (updated != sanitized) {
                sanitized = updated
                changed = true
            }
        }
    }

    return sanitized
        .replace("\\s+".toRegex(), " ")
        .trim()
        .trim('-', ',', '/', ';', ':')
        .trim()
}

private fun buildModelTailRemovalPhrases(
    query: NormalizedQuery?,
    runtimeKeyByTypedKey: Map<String, String>,
    typedFacetUniverse: ResultsTypedFacetUniverse,
): List<String> {
    val phrases = LinkedHashSet<String>()
    query?.attributes.orEmpty().forEach { (rawKey, rawValue) ->
        val normalizedKey = rawKey.trim().lowercase(Locale.ROOT)
        val runtimeKey = runtimeKeyByTypedKey[normalizedKey] ?: normalizedKey
        if (runtimeKey == "model") return@forEach
        val rawTextValue = rawValue.asRawString().trim()
        if (rawTextValue.isBlank()) return@forEach

        phrases += rawTextValue
        phrases += buildModelTailValueVariants(runtimeKey, rawTextValue)

        val universeEntry = typedFacetUniverse.entriesByRuntimeKey[runtimeKey]
        universeEntry
            ?.knownValues
            .orEmpty()
            .forEach { candidateValue ->
                val aliases = universeEntry?.knownValueAliases?.get(candidateValue).orEmpty()
                if (
                    requestedFacetValueMatchesKnownCandidate(
                        requestedValue = rawTextValue,
                        candidateValue = candidateValue,
                        aliases = aliases,
                    )
                ) {
                    phrases += candidateValue
                    phrases.addAll(aliases)
                    phrases.addAll(buildModelTailValueVariants(runtimeKey, candidateValue))
                }
            }
    }

    typedFacetUniverse.entriesByRuntimeKey
        .filterKeys { runtimeKey -> runtimeKey != "model" }
        .values
        .forEach { entry ->
            entry.knownValues.forEach { knownValue ->
                phrases += knownValue
                phrases.addAll(entry.knownValueAliases[knownValue].orEmpty())
                phrases.addAll(buildModelTailValueVariants(entry.runtimeKey, knownValue))
            }
        }

    return phrases
        .asSequence()
        .map { phrase -> phrase.trim() }
        .filter { phrase -> phrase.length >= 2 }
        .distinctBy { phrase -> phrase.lowercase(Locale.ROOT) }
        .sortedByDescending { phrase -> phrase.length }
        .toList()
}

private fun buildModelTailValueVariants(
    runtimeKey: String,
    rawValue: String,
): List<String> {
    val trimmed = rawValue.trim()
    if (trimmed.isEmpty()) return emptyList()
    val digitsOnly = trimmed.filter { char -> char.isDigit() }
    return when (runtimeKey.trim().lowercase(Locale.ROOT)) {
        "memory_gb", "ram_gb" -> if (digitsOnly.isBlank()) emptyList() else listOf(
            digitsOnly,
            "$digitsOnly gb",
            "${digitsOnly}gb",
            "$digitsOnly гб",
            "${digitsOnly}гб",
        )

        "refresh_rate_hz" -> if (digitsOnly.isBlank()) emptyList() else listOf(
            digitsOnly,
            "$digitsOnly hz",
            "${digitsOnly}hz",
            "$digitsOnly гц",
            "${digitsOnly}гц",
        )

        else -> emptyList()
    }
}

private fun removeLeadingFacetPhrase(
    source: String,
    phrase: String,
): String {
    val trimmedPhrase = phrase.trim()
    if (trimmedPhrase.isEmpty()) return source
    val regex = Regex("^\\s*(?iu:${Regex.escape(trimmedPhrase)})[\\s,;/\\\\-]*")
    return regex.replace(source, "").trim()
}

private fun removeTrailingFacetPhrase(
    source: String,
    phrase: String,
): String {
    val trimmedPhrase = phrase.trim()
    if (trimmedPhrase.isEmpty()) return source
    val regex = Regex("[\\s,;/\\\\-]*(?iu:${Regex.escape(trimmedPhrase)})\\s*$")
    return regex.replace(source, "").trim()
}

internal fun requestedFacetValueMatchesKnownCandidate(
    requestedValue: String,
    candidateValue: String,
    aliases: List<String> = emptyList(),
): Boolean {
    val normalizedRequested = normalizeResultsNoticeText(requestedValue)
    if (normalizedRequested.isBlank()) return false
    val compactRequested = SearchTextNormalizer.normalizeToken(requestedValue)
    return sequenceOf(candidateValue, *aliases.toTypedArray()).any { candidate ->
        val normalizedCandidate = normalizeResultsNoticeText(candidate)
        if (normalizedCandidate.isBlank()) return@any false
        val compactCandidate = SearchTextNormalizer.normalizeToken(candidate)
        normalizedRequested == normalizedCandidate ||
            containsResultsNoticePhrase(normalizedRequested, normalizedCandidate) ||
            containsResultsNoticePhrase(normalizedCandidate, normalizedRequested) ||
            (compactCandidate.length >= 2 && compactRequested.contains(compactCandidate)) ||
            (compactRequested.length >= 2 && compactCandidate.contains(compactRequested))
    }
}

internal fun modelRequestedValueMatchesKnownCandidate(
    requestedValue: String,
    candidateValue: String,
    aliases: List<String> = emptyList(),
): Boolean {
    val normalizedRequested = normalizeResultsNoticeText(requestedValue)
    if (normalizedRequested.isBlank()) return false
    val compactRequested = SearchTextNormalizer.normalizeToken(requestedValue)
    return sequenceOf(candidateValue, *aliases.toTypedArray()).any { candidate ->
        val normalizedCandidate = normalizeResultsNoticeText(candidate)
        if (normalizedCandidate.isBlank()) return@any false
        val compactCandidate = SearchTextNormalizer.normalizeToken(candidate)
        normalizedRequested == normalizedCandidate ||
            containsResultsNoticePhrase(normalizedRequested, normalizedCandidate) ||
            (compactCandidate.length >= 4 && compactRequested.contains(compactCandidate))
    }
}

private fun resolveCanonicalRequestedFacetValue(
    requestedValue: String,
    availableValues: List<String>,
    universeEntry: ResultsTypedFacetUniverseEntry?,
    matcher: (String, String, List<String>) -> Boolean,
): String? {
    val preferredMatch = availableValues.firstOrNull { candidate ->
        matcher(
            requestedValue,
            candidate,
            universeEntry?.knownValueAliases?.get(candidate).orEmpty(),
        )
    }
    if (preferredMatch != null) return preferredMatch
    return universeEntry
        ?.knownValues
        .orEmpty()
        .firstOrNull { candidate ->
            matcher(
                requestedValue,
                candidate,
                universeEntry?.knownValueAliases?.get(candidate).orEmpty(),
            )
        }
}

private fun normalizeResultsNoticeText(raw: String?): String =
    raw
        ?.trim()
        ?.takeIf { value -> value.isNotEmpty() }
        ?.let { value ->
            SearchTextNormalizer.normalize(value)
                .lowercase(Locale.ROOT)
                .replace('ё', 'е')
        }
        .orEmpty()

private fun containsResultsNoticePhrase(
    normalizedQuery: String,
    normalizedPhrase: String,
): Boolean {
    if (normalizedQuery == normalizedPhrase) return true
    return normalizedQuery.startsWith("$normalizedPhrase ") ||
        normalizedQuery.endsWith(" $normalizedPhrase") ||
        normalizedQuery.contains(" $normalizedPhrase ")
}

internal fun buildTypedFacetValueOptions(
    runtimeValues: List<ValueFacet>,
    availableSeedValues: List<String> = emptyList(),
    knownValues: List<String> = emptyList(),
    selectedValues: List<String> = emptyList(),
    valueType: FacetDataType?,
    limit: Int = Int.MAX_VALUE,
): List<TypedFacetValueOption> {
    val availableOptions = LinkedHashMap<String, TypedFacetValueOption>()
    val runtimeOptions = runtimeValues
        .asSequence()
        .mapNotNull { facet ->
            val value = facet.name.trim()
            if (value.isBlank()) null else value to facet.count.coerceAtLeast(0)
        }
        .sortedWith(
            compareByDescending<Pair<String, Int>> { pair -> pair.second }
                .thenBy { pair -> pair.first.lowercase(Locale.ROOT) },
        )
        .toList()

    if (runtimeOptions.isNotEmpty()) {
        runtimeOptions.forEach { (value, count) ->
            val key = value.lowercase(Locale.ROOT)
                availableOptions.putIfAbsent(
                    key,
                    TypedFacetValueOption(
                        value = value,
                        count = count,
                    availability = TypedFacetValueAvailability.Available,
                ),
            )
        }
    } else {
        availableSeedValues
            .asSequence()
            .map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .sortedBy { value -> value.lowercase(Locale.ROOT) }
            .forEach { value ->
                val key = value.lowercase(Locale.ROOT)
                availableOptions.putIfAbsent(
                    key,
                    TypedFacetValueOption(
                        value = value,
                        count = null,
                        availability = TypedFacetValueAvailability.Available,
                    ),
                )
            }
    }

    val out = LinkedHashMap<String, TypedFacetValueOption>()
    availableOptions.forEach { (key, option) -> out[key] = option }

    val universeValues = when {
        knownValues.isNotEmpty() -> knownValues
        valueType == FacetDataType.BOOL -> listOf("true", "false")
        else -> emptyList()
    }
    universeValues
        .asSequence()
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .sortedBy { value -> value.lowercase(Locale.ROOT) }
        .forEach { value ->
            val key = value.lowercase(Locale.ROOT)
            out.putIfAbsent(
                key,
                TypedFacetValueOption(
                    value = value,
                    count = null,
                    availability = TypedFacetValueAvailability.KnownUnavailable,
                ),
            )
        }

    selectedValues
        .asSequence()
        .map { value -> value.trim() }
        .filter { value -> value.isNotEmpty() }
        .forEach { value ->
            val key = value.lowercase(Locale.ROOT)
            out.putIfAbsent(
                key,
                TypedFacetValueOption(
                    value = value,
                    count = null,
                    availability = TypedFacetValueAvailability.KnownUnavailable,
                ),
            )
        }

    return out.values.take(limit)
}

internal fun List<TypedFacetValueOption>.partitionByAvailability(): TypedFacetValueSections =
    TypedFacetValueSections(
        available = filter { option -> option.availability == TypedFacetValueAvailability.Available },
        unavailable = filter { option -> option.availability == TypedFacetValueAvailability.KnownUnavailable },
    )

internal fun collectRuntimeRelaxedQueryTypedFacetKeys(
    queryBackedTypedAttributeFilters: Map<String, TypedAttributeFilterDraft>,
    runtimeAttributeFacets: Map<String, List<ValueFacet>>,
): Set<String> {
    if (queryBackedTypedAttributeFilters.isEmpty() || runtimeAttributeFacets.isEmpty()) return emptySet()
    return queryBackedTypedAttributeFilters.entries
        .mapNotNull { (runtimeKey, draft) ->
            val requestedValue = draft.singleExactValueOrNull() ?: return@mapNotNull null
            if (!runtimeAttributeFacets.containsKey(runtimeKey)) return@mapNotNull null
            val availableValues = runtimeAttributeFacets[runtimeKey]
                .orEmpty()
                .mapNotNull { facet ->
                    facet.name.trim().takeIf { value -> value.isNotBlank() }
                }
            if (availableValues.any { value -> value.equals(requestedValue, ignoreCase = true) }) {
                null
            } else {
                runtimeKey
            }
        }
        .toCollection(LinkedHashSet())
}

internal fun TypedAttributeFilterDraft.singleExactValueOrNull(): String? = when (op) {
    TypedAttributeOperator.EQ,
    TypedAttributeOperator.CONTAINS,
    -> value.trim().takeIf { candidate -> candidate.isNotEmpty() }

    TypedAttributeOperator.IN -> {
        val values = parseFacetCsv(valuesCsv)
        if (values.size == 1) values.single() else null
    }

    else -> null
}

internal fun loadKnownValuesFromEffectiveSpec(
    spec: CatalogCategoryEffectiveSpec?,
    runtimeKey: String,
    attributeCode: String?,
): List<String> {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    val normalizedAttributeCode = attributeCode?.trim()?.lowercase(Locale.ROOT)
    val attributeSpec = spec
        ?.allAttributes()
        ?.firstOrNull { attribute ->
            val candidateCode = attribute.code.trim().lowercase(Locale.ROOT)
            candidateCode == normalizedRuntimeKey || candidateCode == normalizedAttributeCode
        }
        ?: return emptyList()
    return attributeSpec.resultsKnownValues()
}

internal fun CatalogAttributeSpec.resultsKnownValues(
    locale: String = Locale.getDefault().toLanguageTag(),
): List<String> = options
    .asSequence()
    .mapNotNull { option ->
        option.labels.resolve(locale = locale)
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: option.valueCode.trim().takeIf { value -> value.isNotEmpty() }
    }
    .distinctBy { value -> value.lowercase(Locale.ROOT) }
    .sortedBy { value -> value.lowercase(Locale.ROOT) }
    .toList()

internal fun CatalogAttributeSpec.resultsKnownValueAliases(
    locale: String = Locale.getDefault().toLanguageTag(),
): Map<String, List<String>> = options
    .mapNotNull { option ->
        val displayValue = option.labels.resolve(locale = locale)
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?: option.valueCode.trim().takeIf { value -> value.isNotEmpty() }
            ?: return@mapNotNull null
        val aliases = (option.aliases + listOf(displayValue, option.valueCode))
            .asSequence()
            .map { alias -> alias.trim() }
            .filter { alias -> alias.isNotEmpty() }
            .distinctBy { alias -> alias.lowercase(Locale.ROOT) }
            .toList()
        displayValue to aliases
    }
    .associate { (value, aliases) -> value to aliases }

internal fun FacetDefinition.resultsResolutionRuntimeKey(): String =
    runtimeFilterKey().trim().lowercase(Locale.ROOT)

internal fun FacetDefinition.resultsResolutionFacetKey(): String =
    facetKey.trim().lowercase(Locale.ROOT)

internal fun buildResultsResolutionRuntimeKeyMap(
    definitions: List<FacetDefinition>,
): Map<String, String> = definitions
    .asSequence()
    .filter { definition -> definition.isResolutionActiveForToday() }
    .filterNot { definition -> definition.ui.hidden }
    .flatMap { definition ->
        sequenceOf(
            definition.resultsResolutionFacetKey(),
            definition.resultsResolutionRuntimeKey(),
        )
            .filter { key -> key.isNotBlank() && key !in resultsResolutionSystemFacetKeys }
            .map { key -> key to definition.resultsResolutionRuntimeKey() }
    }
    .toMap(LinkedHashMap())

internal fun buildResultsResolutionTypedFacetKeys(
    definitions: List<FacetDefinition>,
): Set<String> = definitions
    .asSequence()
    .filter { definition -> definition.isResolutionActiveForToday() }
    .filterNot { definition -> definition.ui.hidden }
    .map { definition -> definition.resultsResolutionRuntimeKey() }
    .filter { runtimeKey -> runtimeKey.isNotBlank() && runtimeKey !in resultsResolutionSystemFacetKeys }
    .toCollection(LinkedHashSet())

internal fun resolveAvailableFacetValues(
    runtimeKey: String,
    runtimeAttributeFacets: Map<String, List<ValueFacet>>,
    universeEntry: ResultsTypedFacetUniverseEntry?,
): List<String> {
    val normalizedRuntimeKey = runtimeKey.trim().lowercase(Locale.ROOT)
    val runtimeValues = runtimeAttributeFacets[normalizedRuntimeKey]
        .orEmpty()
        .mapNotNull { facet ->
            facet.name.trim().takeIf { value -> value.isNotBlank() }
        }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
    if (runtimeAttributeFacets.containsKey(normalizedRuntimeKey)) {
        return runtimeValues
    }
    return universeEntry
        ?.seedAvailableValues
        .orEmpty()
        .mapNotNull { value ->
            value.trim().takeIf { candidate -> candidate.isNotBlank() }
        }
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
}

private val resultsResolutionSystemFacetKeys: Set<String> = setOf(
    "brand",
    "price",
    "price_rub",
    "condition",
    "delivery_channel",
    "delivery",
    "purchase_format",
    "seller_trust",
)

private fun FacetDefinition.isResolutionActiveForToday(today: LocalDate = LocalDate.now()): Boolean {
    val startsAt = runCatching { effectiveFrom?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) }
        .getOrNull()
    val endsAt = runCatching { effectiveTo?.trim()?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse) }
        .getOrNull()
    if (startsAt != null && today.isBefore(startsAt)) return false
    if (endsAt != null && today.isAfter(endsAt)) return false
    return true
}
