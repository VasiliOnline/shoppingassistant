package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.facet.FacetSelectionMode
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultsTypedFacetEditorModeTest {

    @Test
    fun memory_gb_prefers_multi_choice_when_known_values_exist() {
        val mode = resolveTypedFacetEditorMode(
            runtimeKey = "memory_gb",
            valueType = FacetDataType.RANGE,
            selectionMode = null,
            uiWidget = null,
            availableValueCount = 0,
            knownValueCount = 5,
            draft = null,
        )

        assertEquals(TypedFacetEditorMode.MultiChoice, mode)
    }

    @Test
    fun color_prefers_multi_choice_when_known_values_exist() {
        val mode = resolveTypedFacetEditorMode(
            runtimeKey = "color",
            valueType = FacetDataType.ENUM,
            selectionMode = null,
            uiWidget = null,
            availableValueCount = 4,
            knownValueCount = 12,
            draft = null,
        )

        assertEquals(TypedFacetEditorMode.MultiChoice, mode)
    }

    @Test
    fun model_prefers_multi_choice_when_known_values_exist() {
        val mode = resolveTypedFacetEditorMode(
            runtimeKey = "model",
            valueType = FacetDataType.TEXT,
            selectionMode = null,
            uiWidget = null,
            availableValueCount = 3,
            knownValueCount = 10,
            draft = null,
        )

        assertEquals(TypedFacetEditorMode.MultiChoice, mode)
    }

    @Test
    fun explicit_single_selection_mode_keeps_single_choice_editor() {
        val mode = resolveTypedFacetEditorMode(
            runtimeKey = "color",
            valueType = FacetDataType.ENUM,
            selectionMode = FacetSelectionMode.SINGLE,
            uiWidget = null,
            availableValueCount = 4,
            knownValueCount = 12,
            draft = null,
        )

        assertEquals(TypedFacetEditorMode.SingleChoice, mode)
    }

    @Test
    fun incompatible_range_operator_keeps_generic_editor() {
        val mode = resolveTypedFacetEditorMode(
            runtimeKey = "memory_gb",
            valueType = FacetDataType.RANGE,
            selectionMode = null,
            uiWidget = null,
            availableValueCount = 0,
            knownValueCount = 5,
            draft = TypedAttributeFilterDraft(
                op = TypedAttributeOperator.BETWEEN,
                value = "128",
                to = "512",
            ),
        )

        assertEquals(TypedFacetEditorMode.Generic, mode)
    }
}
