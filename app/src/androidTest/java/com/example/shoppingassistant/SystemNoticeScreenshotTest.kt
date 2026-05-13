package com.example.shoppingassistant

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.shoppingassistant.feature.ui.state.SystemNoticePatternsPreview
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemNoticeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capture_notice_patterns_png() {
        composeRule.setContent {
            AppTheme(useDarkTheme = false) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF7FAFF)),
                    color = Color(0xFFF7FAFF),
                ) {
                    SystemNoticePatternsPreview()
                }
            }
        }

        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.filesDir, "system-notice-patterns.png")
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        println("SYSTEM_NOTICE_SCREENSHOT=${output.absolutePath}")
    }
}
