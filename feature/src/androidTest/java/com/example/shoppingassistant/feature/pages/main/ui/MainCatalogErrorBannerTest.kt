package com.example.shoppingassistant.feature.pages.main.ui

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
class MainCatalogErrorBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_catalog_error_and_retries() {
        var retryClicks = 0

        composeRule.setContent {
            MaterialTheme {
                MainCatalogErrorBanner(
                    message = "Не удалось загрузить каталог",
                    onRetry = { retryClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("main_catalog_error_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("main_catalog_error_message")
            .assertTextContains("Не удалось загрузить каталог")
        composeRule.onNodeWithTag("main_catalog_error_retry").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
        }
    }
}
