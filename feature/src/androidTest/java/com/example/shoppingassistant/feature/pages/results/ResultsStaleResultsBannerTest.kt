package com.example.shoppingassistant.feature.pages.results

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsStaleResultsBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_stale_state_and_retries() {
        var retryClicks = 0

        composeRule.setContent {
            MaterialTheme {
                ResultsStaleResultsBanner(
                    message = "Показаны последние загруженные результаты",
                    updatedAtLabel = "Обновлено 5 мин назад",
                    onRetry = { retryClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("results_stale_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("results_stale_banner")
            .assertTextContains("Показаны последние загруженные результаты")
        composeRule.onNodeWithTag("results_stale_banner")
            .assertTextContains("Обновлено 5 мин назад")
        composeRule.onNodeWithTag("results_stale_banner_retry").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
        }
    }

    @Test
    fun hides_updated_at_when_label_is_missing() {
        composeRule.setContent {
            MaterialTheme {
                ResultsStaleResultsBanner(
                    message = "Показаны последние загруженные результаты",
                    updatedAtLabel = null,
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("results_stale_banner").assertIsDisplayed()
        composeRule.onAllNodesWithText("Обновлено", substring = true).assertCountEquals(0)
    }
}
