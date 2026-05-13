package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.model.ValueFacet
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsTypedFacetValueOptionsTest {

    @Test
    fun keeps_only_runtime_values_when_runtime_available() {
        val merged = buildTypedFacetValueOptions(
            runtimeValues = listOf(
                ValueFacet(id = "w", name = "White", count = 12),
                ValueFacet(id = "b", name = "Black", count = 8),
            ),
            knownValues = listOf("white", "Blue"),
            valueType = FacetDataType.ENUM,
        )

        assertEquals(
            listOf(
                TypedFacetValueOption(value = "White", count = 12, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "Black", count = 8, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "Blue", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
            ),
            merged,
        )
    }

    @Test
    fun falls_back_to_boolean_defaults_when_no_values_available() {
        val merged = buildTypedFacetValueOptions(
            runtimeValues = emptyList(),
            availableSeedValues = emptyList(),
            knownValues = emptyList(),
            valueType = FacetDataType.BOOL,
        )

        assertEquals(
            listOf(
                TypedFacetValueOption(value = "false", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
                TypedFacetValueOption(value = "true", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
            ),
            merged,
        )
    }

    @Test
    fun falls_back_to_live_seed_values_when_runtime_empty() {
        val merged = buildTypedFacetValueOptions(
            runtimeValues = emptyList(),
            availableSeedValues = listOf("white", "Blue"),
            knownValues = listOf("white", "Blue", "Black"),
            valueType = FacetDataType.ENUM,
        )

        assertEquals(
            listOf(
                TypedFacetValueOption(value = "Blue", count = null, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "white", count = null, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "Black", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
            ),
            merged,
        )
    }

    @Test
    fun applies_limit_after_deduplication() {
        val merged = buildTypedFacetValueOptions(
            runtimeValues = emptyList(),
            availableSeedValues = listOf("one", "three", "four"),
            knownValues = listOf("one", "three", "four", "five"),
            valueType = FacetDataType.ENUM,
            limit = 3,
        )

        assertEquals(
            listOf(
                TypedFacetValueOption(value = "four", count = null, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "one", count = null, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "three", count = null, availability = TypedFacetValueAvailability.Available),
            ),
            merged,
        )
    }

    @Test
    fun keeps_selected_values_visible_but_marks_them_unavailable_when_absent_in_runtime_and_dictionary() {
        val merged = buildTypedFacetValueOptions(
            runtimeValues = listOf(
                ValueFacet(id = "w", name = "White", count = 12),
            ),
            knownValues = listOf("Black"),
            selectedValues = listOf("Gold"),
            valueType = FacetDataType.ENUM,
        )

        assertEquals(
            listOf(
                TypedFacetValueOption(value = "White", count = 12, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "Black", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
                TypedFacetValueOption(value = "Gold", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
            ),
            merged,
        )
    }

    @Test
    fun bool_values_always_include_true_and_false() {
        val merged = buildTypedFacetValueOptions(
            runtimeValues = listOf(
                ValueFacet(id = "yes", name = "true", count = 4),
            ),
            knownValues = emptyList(),
            valueType = FacetDataType.BOOL,
        )

        assertEquals(
            listOf(
                TypedFacetValueOption(value = "true", count = 4, availability = TypedFacetValueAvailability.Available),
                TypedFacetValueOption(value = "false", count = null, availability = TypedFacetValueAvailability.KnownUnavailable),
            ),
            merged,
        )
    }
}
