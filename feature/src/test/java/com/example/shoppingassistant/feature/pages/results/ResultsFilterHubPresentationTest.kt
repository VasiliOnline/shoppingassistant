package com.example.shoppingassistant.feature.pages.results

import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsFilterHubPresentationTest {

    @Test
    fun clean_filter_sheet_uses_done_action_state() {
        val state = resolveFilterHubApplyActionState(
            isDirty = false,
            isApplyEnabled = false,
            resultsCount = 12,
        )

        assertEquals("Готово", state.label)
        assertTrue(state.enabled)
    }

    @Test
    fun dirty_filter_sheet_keeps_show_results_label() {
        val state = resolveFilterHubApplyActionState(
            isDirty = true,
            isApplyEnabled = false,
        )

        assertEquals("Показать результаты", state.label)
        assertEquals(false, state.enabled)
    }

    @Test
    fun dirty_filter_sheet_uses_results_count_label_when_available() {
        val state = resolveFilterHubApplyActionState(
            isDirty = true,
            isApplyEnabled = true,
            resultsCount = 12,
        )

        assertEquals("Показать 12 результатов", state.label)
        assertTrue(state.enabled)
    }

    @Test
    fun formats_results_count_label_with_russian_plural_rules() {
        assertEquals("Показать 1 результат", formatFilterHubResultsCountLabel(1))
        assertEquals("Показать 2 результата", formatFilterHubResultsCountLabel(2))
        assertEquals("Показать 5 результатов", formatFilterHubResultsCountLabel(5))
        assertEquals("Показать 21 результат", formatFilterHubResultsCountLabel(21))
    }

    @Test
    fun scoped_typed_facet_summary_uses_available_value_count_when_unselected() {
        val summary = resolveTypedFacetHubRowSummary(
            explicitDraft = null,
            queryBackedDraft = null,
            autoAppliedDraft = null,
            defaultSummary = "Все",
            availableValueCount = 3,
            hasScopedContext = true,
        )

        assertEquals("3 варианта", summary)
    }

    @Test
    fun explicit_typed_facet_summary_outranks_scoped_count() {
        val summary = resolveTypedFacetHubRowSummary(
            explicitDraft = TypedAttributeFilterDraft(
                op = TypedAttributeOperator.EQ,
                value = "Cosmic Orange",
            ),
            queryBackedDraft = null,
            autoAppliedDraft = null,
            defaultSummary = "Все",
            availableValueCount = 3,
            hasScopedContext = true,
        )

        assertEquals("Cosmic Orange", summary)
    }
}
