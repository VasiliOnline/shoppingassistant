package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.model.NormalizedQuery
import com.example.shoppingassistant.domain.model.TypedAttributeValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsQueryMaterializationTest {

    @Test
    fun materializes_brand_model_and_typed_attributes_into_structured_filters() {
        val materialized = materializeResultsStructuredQuery(
            NormalizedQuery(
                brand = "Apple",
                model = "iPhone 17 Pro",
                attributes = mapOf(
                    "color" to TypedAttributeValue.Text("ORANGE"),
                    "memory_gb" to TypedAttributeValue.Text("512"),
                    "condition" to TypedAttributeValue.Text("used"),
                ),
            ),
        )

        assertEquals("Apple", materialized.brand)
        assertEquals("used", materialized.condition)
        assertEquals("iPhone 17 Pro", materialized.typedAttributeFilters["model"]?.value)
        assertEquals("ORANGE", materialized.typedAttributeFilters["color"]?.value)
        assertEquals("512", materialized.typedAttributeFilters["memory_gb"]?.value)
    }

    @Test
    fun typed_query_keys_include_model_and_non_system_attributes_only() {
        val keys = resultsStructuredQueryTypedFacetKeys(
            NormalizedQuery(
                brand = "Apple",
                model = "iPhone 17 Pro",
                attributes = mapOf(
                    "color" to TypedAttributeValue.Text("ORANGE"),
                    "condition" to TypedAttributeValue.Text("used"),
                ),
            ),
        )

        assertEquals(setOf("model", "color"), keys)
    }
}
