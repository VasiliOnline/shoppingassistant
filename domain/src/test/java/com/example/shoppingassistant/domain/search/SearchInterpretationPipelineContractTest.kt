package com.example.shoppingassistant.domain.search

import com.example.shoppingassistant.domain.catalog.QueryRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInterpretationPipelineContractTest {

    private val pipeline = SearchInterpretationPipeline()

    @Test
    fun raw_text_uses_exact_auto_template_when_route_empty() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "  iphone   16 pro  ",
                source = SearchRequestSource.RAW_TEXT,
                suggestions = listOf(
                    SearchSuggestionCandidate(
                        kind = SearchSuggestionKind.AUTO_TEMPLATE,
                        text = "iphone 16 pro",
                        categoryCode = "TECH.PHONES",
                        attrs = mapOf("brand" to "Apple", "model" to "iPhone 16 Pro", "color" to "серый"),
                    ),
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals("iphone 16 pro", intent.queryText)
        assertEquals("TECH.PHONES", intent.categoryCode)
        assertEquals("Apple", intent.attrs["brand"])
        assertEquals("iPhone 16 Pro", intent.attrs["model"])
        assertEquals("серый", intent.attrs["color"])
        assertTrue(intent.provenance.usedSuggestionCategory)
        assertTrue(intent.provenance.usedParsedAttributes)
    }

    @Test
    fun raw_text_exact_suggestion_category_has_priority_over_inference() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "iphone 16 pro",
                source = SearchRequestSource.RAW_TEXT,
                suggestions = listOf(
                    SearchSuggestionCandidate(
                        kind = SearchSuggestionKind.AUTO_TEMPLATE,
                        text = "iphone 16 pro",
                        categoryCode = "TECH.PHONES",
                    ),
                ),
            ),
            deps = fakeDeps(inferredCategory = "KIDS.STROLLERS_CARSEATS"),
        )

        assertEquals("TECH.PHONES", intent.categoryCode)
        assertTrue(intent.provenance.usedSuggestionCategory)
        assertFalse(intent.provenance.usedInferredCategory)
    }

    @Test
    fun raw_text_does_not_auto_apply_history_template_match() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "iphone 16 pro",
                source = SearchRequestSource.RAW_TEXT,
                suggestions = listOf(
                    SearchSuggestionCandidate(
                        kind = SearchSuggestionKind.HISTORY_TEMPLATE,
                        text = "iphone 16 pro",
                        categoryCode = "KIDS.STROLLERS_CARSEATS",
                        attrs = mapOf("color" to "серый"),
                    ),
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals("iphone 16 pro", intent.queryText)
        assertEquals(null, intent.categoryCode)
        assertTrue(intent.attrs.isEmpty())
        assertFalse(intent.provenance.usedSuggestionCategory)
    }

    @Test
    fun raw_text_does_not_reuse_unlocked_template_context() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "iphone 16 pro",
                source = SearchRequestSource.RAW_TEXT,
                templateContext = SearchTemplateContext(
                    inputText = "iphone 16 pro",
                    isLocked = false,
                    categoryCode = "KIDS.STROLLERS_CARSEATS",
                    selectedFilters = mapOf("color" to "серый"),
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals("iphone 16 pro", intent.queryText)
        assertEquals(null, intent.categoryCode)
        assertTrue(intent.attrs.isEmpty())
        assertFalse(intent.provenance.usedTemplateContext)
    }

    @Test
    fun raw_text_locked_template_category_has_priority_over_inference() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "iphone 16 pro",
                source = SearchRequestSource.RAW_TEXT,
                templateContext = SearchTemplateContext(
                    inputText = "iphone 16 pro",
                    isLocked = true,
                    categoryCode = "TECH.PHONES",
                ),
            ),
            deps = fakeDeps(inferredCategory = "KIDS.STROLLERS_CARSEATS"),
        )

        assertEquals("TECH.PHONES", intent.categoryCode)
        assertTrue(intent.provenance.usedTemplateContext)
        assertFalse(intent.provenance.usedInferredCategory)
    }

    @Test
    fun suggestion_category_anchor_returns_category_only_mode() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "Детям",
                source = SearchRequestSource.SUGGESTION,
                selectedSuggestion = SearchSuggestionCandidate(
                    kind = SearchSuggestionKind.CATEGORY_ANCHOR,
                    text = "Детям",
                    categoryCode = "KIDS.STROLLERS_CARSEATS",
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals(SearchIntentMode.CATEGORY_ONLY, intent.mode)
        assertEquals("KIDS.STROLLERS_CARSEATS", intent.categoryCode)
        assertTrue(intent.attrs.isEmpty())
    }

    @Test
    fun suggestion_history_or_preset_keeps_attrs_and_category() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "Apple iPhone 16 Pro",
                source = SearchRequestSource.SUGGESTION,
                selectedSuggestion = SearchSuggestionCandidate(
                    kind = SearchSuggestionKind.HISTORY_TEMPLATE,
                    text = "Apple iPhone 16 Pro",
                    categoryCode = "TECH.PHONES",
                    attrs = mapOf("brand" to "Apple", "model" to "iPhone 16 Pro", "memory_gb" to "256"),
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals("TECH.PHONES", intent.categoryCode)
        assertEquals("Apple", intent.attrs["brand"])
        assertEquals("iPhone 16 Pro", intent.attrs["model"])
        assertEquals("256", intent.attrs["memory_gb"])
        assertEquals(SearchSuggestionKind.HISTORY_TEMPLATE, intent.provenance.suggestionKind)
    }

    @Test
    fun suggestion_preset_keeps_attrs_and_category() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "iPhone 16 Pro 256",
                source = SearchRequestSource.SUGGESTION,
                selectedSuggestion = SearchSuggestionCandidate(
                    kind = SearchSuggestionKind.PRESET_TEMPLATE,
                    text = "iPhone 16 Pro 256",
                    categoryCode = "TECH.PHONES",
                    attrs = mapOf("brand" to "Apple", "model" to "iPhone 16 Pro", "memory_gb" to "256"),
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals("TECH.PHONES", intent.categoryCode)
        assertEquals("Apple", intent.attrs["brand"])
        assertEquals("iPhone 16 Pro", intent.attrs["model"])
        assertEquals("256", intent.attrs["memory_gb"])
        assertEquals(SearchSuggestionKind.PRESET_TEMPLATE, intent.provenance.suggestionKind)
    }

    @Test
    fun suggestion_category_has_priority_over_inferred_category() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "айфон 16 про серый",
                source = SearchRequestSource.SUGGESTION,
                selectedSuggestion = SearchSuggestionCandidate(
                    kind = SearchSuggestionKind.PRODUCT_ANCHOR,
                    text = "айфон 16 про серый",
                    categoryCode = "TECH.PHONES",
                ),
            ),
            deps = fakeDeps(inferredCategory = "KIDS.STROLLERS_CARSEATS"),
        )

        assertEquals("TECH.PHONES", intent.categoryCode)
        assertTrue(intent.provenance.usedSuggestionCategory)
        assertFalse(intent.provenance.usedInferredCategory)
    }

    @Test
    fun text_fix_suggestion_normalizes_mixed_language_input() {
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "  IPHONE   16   ПРО   серый ",
                source = SearchRequestSource.SUGGESTION,
                selectedSuggestion = SearchSuggestionCandidate(
                    kind = SearchSuggestionKind.TEXT_FIX,
                    text = "iPhone 16 Pro серый",
                ),
            ),
            deps = fakeDeps(),
        )

        assertEquals("iPhone 16 Pro серый", intent.queryText)
        assertEquals(SearchIntentMode.QUERY, intent.mode)
        assertEquals(SearchRequestSource.SUGGESTION, intent.provenance.source)
        assertEquals(SearchSuggestionKind.TEXT_FIX, intent.provenance.suggestionKind)
    }

    @Test
    fun route_category_has_higher_priority_than_suggestion_or_template() {
        val deps = fakeDeps(
            route = SearchRouteResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = "TECH.PHONES",
            ),
        )
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "iphone 16",
                source = SearchRequestSource.RAW_TEXT,
                suggestions = listOf(
                    SearchSuggestionCandidate(
                        kind = SearchSuggestionKind.AUTO_TEMPLATE,
                        text = "iphone 16",
                        categoryCode = "KIDS.STROLLERS_CARSEATS",
                    ),
                ),
                templateContext = SearchTemplateContext(
                    inputText = "iphone 16",
                    isLocked = true,
                    categoryCode = "HOME.FURNITURE",
                ),
            ),
            deps = deps,
        )

        assertEquals("TECH.PHONES", intent.categoryCode)
        assertTrue(intent.provenance.usedRouteCategory)
    }

    @Test
    fun category_redirect_is_applied_before_return() {
        val deps = fakeDeps(
            route = SearchRouteResult(
                routeType = QueryRouteType.OPEN_CATEGORY,
                primaryTargetCode = "TECH.OLD",
            ),
            redirectedCategory = "TECH.NEW",
        )
        val intent = runPipeline(
            request = SearchInterpretationRequest(
                inputText = "old phone",
                source = SearchRequestSource.RAW_TEXT,
            ),
            deps = deps,
        )

        assertEquals("TECH.NEW", intent.categoryCode)
    }

    private fun fakeDeps(
        route: SearchRouteResult? = null,
        browseCategory: String? = null,
        inferredCategory: String? = null,
        redirectedCategory: String? = null,
        parsedAttrs: Map<String, String> = emptyMap(),
    ): SearchInterpretationDependencies = object : SearchInterpretationDependencies {
        override suspend fun route(queryText: String): SearchRouteResult? = route

        override suspend fun resolveBrowseCategory(browseCode: String): String? = browseCategory

        override suspend fun resolveCategoryRedirect(categoryCode: String): String? = redirectedCategory

        override suspend fun inferCategory(
            queryText: String,
            baseFilters: Map<String, String>,
        ): String? = inferredCategory

        override suspend fun parseAttributes(
            queryText: String,
            categoryCode: String?,
            baseFilters: Map<String, String>,
        ): Map<String, String> = if (parsedAttrs.isEmpty()) baseFilters else parsedAttrs
    }

    private fun runPipeline(
        request: SearchInterpretationRequest,
        deps: SearchInterpretationDependencies,
    ): InterpretedSearchIntent = kotlinx.coroutines.runBlocking {
        pipeline.interpret(request, deps)
    }
}
