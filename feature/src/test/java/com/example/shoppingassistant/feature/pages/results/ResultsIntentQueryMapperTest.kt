package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.search.InterpretedSearchIntent
import com.example.shoppingassistant.domain.search.SearchInterpretationProvenance
import com.example.shoppingassistant.domain.search.SearchIntentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultsIntentQueryMapperTest {

    @Test
    fun builds_query_from_interpreted_brand_model_and_attrs() {
        val intent = InterpretedSearchIntent(
            queryText = "айфон 14 про серый 256гб",
            categoryCode = "TECH.SMARTPHONES",
            attrs = linkedMapOf(
                "brand" to "Apple",
                "model" to "iPhone 14 Pro",
                "color" to "Серый",
                "storage" to "256 ГБ",
            ),
            mode = SearchIntentMode.QUERY,
            provenance = SearchInterpretationProvenance(
                source = com.example.shoppingassistant.domain.search.SearchRequestSource.RAW_TEXT,
                usedParsedAttributes = true,
            ),
        )

        val query = intent.toResultsQuery()

        requireNotNull(query)
        assertEquals("Apple", query.brand)
        assertEquals("iPhone 14 Pro", query.model)
        assertEquals("Серый", query.attributes["color"]?.asRawString())
        assertEquals("256 ГБ", query.attributes["storage"]?.asRawString())
    }

    @Test
    fun falls_back_to_model_only_query_when_structured_attrs_are_absent() {
        val intent = InterpretedSearchIntent(
            queryText = "винтажный проигрыватель pioneer",
            mode = SearchIntentMode.QUERY,
            provenance = SearchInterpretationProvenance(
                source = com.example.shoppingassistant.domain.search.SearchRequestSource.RAW_TEXT,
            ),
        )

        val query = intent.toResultsQuery()

        requireNotNull(query)
        assertEquals("", query.brand)
        assertEquals("винтажный проигрыватель pioneer", query.model)
    }

    @Test
    fun falls_back_to_canonical_brand_model_rules_for_cyrillic_iphone_alias() {
        val intent = InterpretedSearchIntent(
            queryText = "айфон 17 про 256гб",
            mode = SearchIntentMode.QUERY,
            provenance = SearchInterpretationProvenance(
                source = com.example.shoppingassistant.domain.search.SearchRequestSource.RAW_TEXT,
            ),
        )

        val query = intent.toResultsQuery()

        requireNotNull(query)
        assertEquals("Apple", query.brand)
        assertEquals("iPhone 17 Pro 256гб", query.model)
    }

    @Test
    fun strips_parsed_attribute_tail_from_heuristic_model_match() {
        val intent = InterpretedSearchIntent(
            queryText = "Apple iPhone17 pro orange 512gb",
            attrs = linkedMapOf(
                "brand" to "Apple",
                "color" to "ORANGE",
                "memory_gb" to "512",
            ),
            mode = SearchIntentMode.QUERY,
            provenance = SearchInterpretationProvenance(
                source = com.example.shoppingassistant.domain.search.SearchRequestSource.RAW_TEXT,
                usedParsedAttributes = true,
            ),
        )

        val query = intent.toResultsQuery()

        requireNotNull(query)
        assertEquals("Apple", query.brand)
        assertEquals("iPhone 17 Pro", query.model)
        assertEquals("ORANGE", query.attributes["color"]?.asRawString())
        assertEquals("512", query.attributes["memory_gb"]?.asRawString())
    }

    @Test
    fun returns_null_for_category_only_intent() {
        val intent = InterpretedSearchIntent(
            queryText = "Электроника -> Смартфоны",
            categoryCode = "TECH.SMARTPHONES",
            mode = SearchIntentMode.CATEGORY_ONLY,
            provenance = SearchInterpretationProvenance(
                source = com.example.shoppingassistant.domain.search.SearchRequestSource.SUGGESTION,
            ),
        )

        assertNull(intent.toResultsQuery())
    }
}
