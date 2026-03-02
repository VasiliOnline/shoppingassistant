package com.example.shoppingassistant.feature.pages.results

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultsCatalogErrorBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renders_degraded_catalog_state_and_retries() {
        var retryClicks = 0

        composeRule.setContent {
            MaterialTheme {
                ResultsCatalogErrorBanner(
                    message = "Не удалось загрузить часть данных каталога",
                    onRetry = { retryClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("results_catalog_error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("results_catalog_error_banner")
            .assertTextContains("Не удалось загрузить часть данных каталога")
        composeRule.onNodeWithTag("results_catalog_error_retry").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
        }
    }
}
