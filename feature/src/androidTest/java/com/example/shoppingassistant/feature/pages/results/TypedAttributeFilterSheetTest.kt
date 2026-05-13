package com.example.shoppingassistant.feature.pages.results

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.shoppingassistant.domain.facet.FacetDataType
import com.example.shoppingassistant.domain.model.TypedAttributeOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypedAttributeFilterSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun range_sheet_exposes_relational_operators() {
        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "price_range",
                    title = "Диапазон",
                    valueType = FacetDataType.RANGE,
                    runtimeValues = emptyList(),
                    draft = null,
                    onApply = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("results_typed_operator_field").performClick()
        composeRule.onNodeWithText(">").assertIsDisplayed()
        composeRule.onNodeWithText("<").assertIsDisplayed()
        composeRule.onNodeWithText("between").assertIsDisplayed()
    }

    @Test
    fun between_operator_applies_bounds() {
        var appliedDraft: TypedAttributeFilterDraft? = null

        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "price_range",
                    title = "Диапазон",
                    valueType = FacetDataType.RANGE,
                    runtimeValues = emptyList(),
                    draft = null,
                    onApply = { appliedDraft = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("results_typed_operator_field").performClick()
        composeRule.onNodeWithText("between").performClick()
        composeRule.onNodeWithTag("results_typed_from").performTextInput("100")
        composeRule.onNodeWithTag("results_typed_to").performTextInput("250")
        composeRule.onNodeWithTag("results_typed_apply").performClick()

        composeRule.runOnIdle {
            assertNotNull(appliedDraft)
            assertEquals(TypedAttributeOperator.BETWEEN, appliedDraft?.op)
            assertEquals("100", appliedDraft?.value)
            assertEquals("250", appliedDraft?.to)
        }
    }

    @Test
    fun enum_in_operator_applies_csv_values() {
        var appliedDraft: TypedAttributeFilterDraft? = null

        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "condition",
                    title = "Состояние",
                    valueType = FacetDataType.ENUM,
                    runtimeValues = emptyList(),
                    draft = null,
                    onApply = { appliedDraft = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("results_typed_operator_field").performClick()
        composeRule.onNodeWithText("in").performClick()
        composeRule.onNodeWithTag("results_typed_values_csv")
            .performTextInput("new,used")
        composeRule.onNodeWithTag("results_typed_apply").performClick()

        composeRule.runOnIdle {
            assertNotNull(appliedDraft)
            assertEquals(TypedAttributeOperator.IN, appliedDraft?.op)
            assertEquals("new,used", appliedDraft?.valuesCsv)
        }
    }

    @Test
    fun exists_operator_applies_without_value() {
        var appliedDraft: TypedAttributeFilterDraft? = null

        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "description",
                    title = "Описание",
                    valueType = FacetDataType.TEXT,
                    runtimeValues = emptyList(),
                    draft = null,
                    onApply = { appliedDraft = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("results_typed_operator_field").performClick()
        composeRule.onNodeWithText("exists").performClick()
        composeRule.onNodeWithTag("results_typed_apply").assertIsEnabled()
        composeRule.onNodeWithTag("results_typed_apply").performClick()

        composeRule.runOnIdle {
            assertNotNull(appliedDraft)
            assertEquals(TypedAttributeOperator.EXISTS, appliedDraft?.op)
            assertEquals("", appliedDraft?.value)
            assertEquals("", appliedDraft?.valuesCsv)
        }
    }

    @Test
    fun value_operator_requires_non_empty_value_to_apply() {
        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "description",
                    title = "Описание",
                    valueType = FacetDataType.TEXT,
                    runtimeValues = emptyList(),
                    draft = null,
                    onApply = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("results_typed_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("results_typed_value").performTextInput("iphone")
        composeRule.onNodeWithTag("results_typed_apply").assertIsEnabled()
    }

    @Test
    fun reset_action_clears_and_emits_null_draft() {
        var appliedDraft: TypedAttributeFilterDraft? = TypedAttributeFilterDraft(
            op = TypedAttributeOperator.EQ,
            value = "stale",
        )

        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "description",
                    title = "Описание",
                    valueType = FacetDataType.TEXT,
                    runtimeValues = emptyList(),
                    draft = appliedDraft,
                    onApply = { appliedDraft = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Очистить").performClick()

        composeRule.runOnIdle {
            assertEquals(null, appliedDraft)
        }
    }

    @Test
    fun selected_tokens_are_visible_and_can_be_removed_before_apply() {
        var appliedDraft: TypedAttributeFilterDraft? = null

        composeRule.setContent {
            MaterialTheme {
                TypedAttributeFilterSheet(
                    runtimeKey = "memory_gb",
                    title = "Память",
                    valueType = FacetDataType.ENUM,
                    runtimeValues = emptyList(),
                    draft = TypedAttributeFilterDraft(
                        op = TypedAttributeOperator.IN,
                        valuesCsv = "256, 512",
                    ),
                    onApply = { appliedDraft = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("256").assertIsDisplayed()
        composeRule.onNodeWithText("512").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Удалить фильтр 256").performClick()
        composeRule.onNodeWithTag("results_typed_apply").performClick()

        composeRule.runOnIdle {
            assertNotNull(appliedDraft)
            assertEquals(TypedAttributeOperator.IN, appliedDraft?.op)
            assertEquals("512", appliedDraft?.valuesCsv)
        }
    }
}
