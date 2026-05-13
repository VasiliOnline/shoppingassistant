package com.example.shoppingassistant.domain.search

import com.example.shoppingassistant.domain.catalog.QueryRouteType

enum class SearchRequestSource {
    RAW_TEXT,
    SUGGESTION,
}

enum class SearchSuggestionKind {
    AUTO_TEMPLATE,
    PRODUCT_ANCHOR,
    CATEGORY_ANCHOR,
    HISTORY_TEMPLATE,
    PRESET_TEMPLATE,
    AUTO_PRESET,
    TEXT_FIX,
    UNKNOWN,
}

enum class SearchIntentMode {
    QUERY,
    CATEGORY_ONLY,
}

data class SearchSuggestionCandidate(
    val kind: SearchSuggestionKind,
    val text: String,
    val categoryCode: String? = null,
    val attrs: Map<String, String> = emptyMap(),
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
)

data class SearchTemplateContext(
    val inputText: String,
    val categoryCode: String? = null,
    val selectedFilters: Map<String, String> = emptyMap(),
    val isLocked: Boolean = false,
)

data class SearchInterpretationRequest(
    val inputText: String,
    val source: SearchRequestSource,
    val selectedSuggestion: SearchSuggestionCandidate? = null,
    val suggestions: List<SearchSuggestionCandidate> = emptyList(),
    val templateContext: SearchTemplateContext? = null,
)

data class SearchRouteResult(
    val routeType: QueryRouteType,
    val primaryTargetCode: String? = null,
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
)

interface SearchInterpretationDependencies {
    suspend fun route(queryText: String): SearchRouteResult?
    suspend fun resolveBrowseCategory(browseCode: String): String?
    suspend fun resolveCategoryRedirect(categoryCode: String): String?
    suspend fun inferCategory(
        queryText: String,
        baseFilters: Map<String, String>,
    ): String?

    suspend fun parseAttributes(
        queryText: String,
        categoryCode: String?,
        baseFilters: Map<String, String>,
    ): Map<String, String>
}

data class SearchInterpretationProvenance(
    val source: SearchRequestSource,
    val suggestionKind: SearchSuggestionKind? = null,
    val usedTemplateContext: Boolean = false,
    val usedRouteCategory: Boolean = false,
    val usedInferredCategory: Boolean = false,
    val usedSuggestionCategory: Boolean = false,
    val usedParsedAttributes: Boolean = false,
)

data class InterpretedSearchIntent(
    val queryText: String,
    val categoryCode: String? = null,
    val attrs: Map<String, String> = emptyMap(),
    val facetCollectionCode: String? = null,
    val facetPresetCode: String? = null,
    val mode: SearchIntentMode = SearchIntentMode.QUERY,
    val provenance: SearchInterpretationProvenance,
)

class SearchInterpretationPipeline {
    suspend fun interpret(
        request: SearchInterpretationRequest,
        deps: SearchInterpretationDependencies,
    ): InterpretedSearchIntent {
        val requestInput = SearchTextNormalizer.normalize(request.inputText)
        val effectiveInput = when (request.source) {
            SearchRequestSource.SUGGESTION -> {
                request.selectedSuggestion
                    ?.text
                    ?.takeIf { value -> value.isNotBlank() }
                    ?: request.inputText
            }
            SearchRequestSource.RAW_TEXT -> request.inputText
        }
        val normalizedInput = SearchTextNormalizer.normalize(effectiveInput)
        val suggestion = when (request.source) {
            SearchRequestSource.SUGGESTION -> request.selectedSuggestion
            SearchRequestSource.RAW_TEXT -> resolveRawSuggestionMatch(
                query = requestInput,
                suggestions = request.suggestions,
            )
        }

        val mode = if (suggestion?.kind == SearchSuggestionKind.CATEGORY_ANCHOR) {
            SearchIntentMode.CATEGORY_ONLY
        } else {
            SearchIntentMode.QUERY
        }

        val canReuseTemplateContext = request.source == SearchRequestSource.RAW_TEXT &&
            request.templateContext?.let { context ->
                context.isLocked &&
                    SearchTextNormalizer.equalsNormalized(context.inputText, normalizedInput)
            } == true

        val suggestionAttrs = normalizeFilters(suggestion?.attrs.orEmpty())
        val templateFilters = if (canReuseTemplateContext) {
            sanitizeTemplateFilters(request.templateContext?.selectedFilters.orEmpty())
        } else {
            emptyMap()
        }
        val baseFilters = when {
            templateFilters.isNotEmpty() -> templateFilters
            suggestionAttrs.isNotEmpty() -> suggestionAttrs
            else -> emptyMap()
        }

        val route = if (mode == SearchIntentMode.QUERY && normalizedInput.isNotBlank()) {
            deps.route(normalizedInput)
        } else {
            null
        }
        var routedCollectionCode = route?.facetCollectionCode?.trim()?.takeIf { value -> value.isNotEmpty() }
        val routedPresetCode = route?.facetPresetCode?.trim()?.takeIf { value -> value.isNotEmpty() }
        val routedCategoryCode = when (route?.routeType) {
            QueryRouteType.OPEN_CATEGORY -> route.primaryTargetCode?.trim()?.takeIf { value -> value.isNotEmpty() }
            QueryRouteType.OPEN_BROWSE -> {
                val browseCode = route.primaryTargetCode?.trim()?.takeIf { value -> value.isNotEmpty() }
                if (browseCode == null) {
                    null
                } else {
                    routedCollectionCode = routedCollectionCode ?: browseCode
                    deps.resolveBrowseCategory(browseCode)?.trim()?.takeIf { value -> value.isNotEmpty() }
                }
            }

            QueryRouteType.RUN_SEARCH,
            null,
            -> null
        }

        val suggestionCategoryCode = suggestion
            ?.categoryCode
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
        val inferredCategoryCode = if (
            mode == SearchIntentMode.QUERY &&
            routedCategoryCode == null &&
            normalizedInput.isNotBlank()
        ) {
            deps.inferCategory(normalizedInput, baseFilters)?.trim()?.takeIf { value -> value.isNotEmpty() }
        } else {
            null
        }
        val templateCategoryCode = if (canReuseTemplateContext) {
            request.templateContext?.categoryCode?.trim()?.takeIf { value -> value.isNotEmpty() }
        } else {
            null
        }

        val categorySource = when (request.source) {
            SearchRequestSource.SUGGESTION -> when {
                routedCategoryCode != null -> CATEGORY_SOURCE_ROUTE
                suggestionCategoryCode != null -> CATEGORY_SOURCE_SUGGESTION
                inferredCategoryCode != null -> CATEGORY_SOURCE_INFERRED
                templateCategoryCode != null -> CATEGORY_SOURCE_TEMPLATE
                else -> CATEGORY_SOURCE_NONE
            }

            SearchRequestSource.RAW_TEXT -> when {
                routedCategoryCode != null -> CATEGORY_SOURCE_ROUTE
                suggestionCategoryCode != null -> CATEGORY_SOURCE_SUGGESTION
                templateCategoryCode != null -> CATEGORY_SOURCE_TEMPLATE
                inferredCategoryCode != null -> CATEGORY_SOURCE_INFERRED
                else -> CATEGORY_SOURCE_NONE
            }
        }

        val resolvedCategoryCode = when (categorySource) {
            CATEGORY_SOURCE_ROUTE -> routedCategoryCode
            CATEGORY_SOURCE_SUGGESTION -> suggestionCategoryCode
            CATEGORY_SOURCE_INFERRED -> inferredCategoryCode
            CATEGORY_SOURCE_TEMPLATE -> templateCategoryCode
            else -> null
        }
        val redirectedCategoryCode = resolvedCategoryCode?.let { code ->
            deps.resolveCategoryRedirect(code)
                ?.trim()
                ?.takeIf { value -> value.isNotEmpty() }
                ?: code
        }

        val parsedAttrs = if (mode == SearchIntentMode.QUERY && normalizedInput.isNotBlank()) {
            normalizeFilters(
                deps.parseAttributes(
                    queryText = normalizedInput,
                    categoryCode = redirectedCategoryCode,
                    baseFilters = baseFilters,
                ),
            )
        } else {
            emptyMap()
        }

        val effectiveAttrs = when {
            parsedAttrs.isNotEmpty() -> parsedAttrs
            suggestionAttrs.isNotEmpty() -> suggestionAttrs
            else -> emptyMap()
        }

        return InterpretedSearchIntent(
            queryText = normalizedInput,
            categoryCode = redirectedCategoryCode,
            attrs = effectiveAttrs,
            facetCollectionCode = routedCollectionCode,
            facetPresetCode = routedPresetCode,
            mode = mode,
            provenance = SearchInterpretationProvenance(
                source = request.source,
                suggestionKind = suggestion?.kind,
                usedTemplateContext = canReuseTemplateContext && (
                    templateFilters.isNotEmpty() || templateCategoryCode != null
                    ),
                usedRouteCategory = categorySource == CATEGORY_SOURCE_ROUTE,
                usedInferredCategory = categorySource == CATEGORY_SOURCE_INFERRED,
                usedSuggestionCategory = categorySource == CATEGORY_SOURCE_SUGGESTION,
                usedParsedAttributes = effectiveAttrs.isNotEmpty(),
            ),
        )
    }

    private fun resolveRawSuggestionMatch(
        query: String,
        suggestions: List<SearchSuggestionCandidate>,
    ): SearchSuggestionCandidate? {
        if (query.isBlank() || suggestions.isEmpty()) return null
        val matches = suggestions.filter { candidate ->
            candidate.kind in rawTextReusableSuggestionKinds &&
                SearchTextNormalizer.equalsNormalized(candidate.text, query)
        }
        if (matches.isEmpty()) return null
        return matches.minByOrNull { candidate -> suggestionPriority(candidate.kind) }
    }

    private fun suggestionPriority(kind: SearchSuggestionKind): Int = when (kind) {
        SearchSuggestionKind.AUTO_TEMPLATE -> 0
        SearchSuggestionKind.PRODUCT_ANCHOR -> 1
        SearchSuggestionKind.HISTORY_TEMPLATE -> 2
        SearchSuggestionKind.PRESET_TEMPLATE -> 3
        SearchSuggestionKind.AUTO_PRESET -> 4
        SearchSuggestionKind.TEXT_FIX -> 5
        SearchSuggestionKind.CATEGORY_ANCHOR -> 6
        SearchSuggestionKind.UNKNOWN -> 99
    }

    private fun sanitizeTemplateFilters(filters: Map<String, String>): Map<String, String> =
        normalizeFilters(filters).filterKeys { key ->
            !key.startsWith("category_level_") && key != "category_selector" && key != "category"
        }

    private fun normalizeFilters(filters: Map<String, String>): Map<String, String> =
        filters
            .mapNotNull { (rawKey, rawValue) ->
                val key = SearchTextNormalizer.normalizeKey(rawKey)
                val value = SearchTextNormalizer.normalize(rawValue)
                if (key.isBlank() || value.isBlank()) null else key to value
            }
            .toMap(LinkedHashMap())

    private companion object {
        private const val CATEGORY_SOURCE_ROUTE: String = "route"
        private const val CATEGORY_SOURCE_SUGGESTION: String = "suggestion"
        private const val CATEGORY_SOURCE_INFERRED: String = "inferred"
        private const val CATEGORY_SOURCE_TEMPLATE: String = "template"
        private const val CATEGORY_SOURCE_NONE: String = "none"

        private val rawTextReusableSuggestionKinds: Set<SearchSuggestionKind> = setOf(
            SearchSuggestionKind.AUTO_TEMPLATE,
            SearchSuggestionKind.PRODUCT_ANCHOR,
            SearchSuggestionKind.TEXT_FIX,
        )
    }
}
