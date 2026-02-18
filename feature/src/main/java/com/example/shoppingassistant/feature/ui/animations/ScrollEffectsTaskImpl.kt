package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun rememberScrollEffectsTask(): ScrollEffectsTask = remember { ScrollEffectsTaskImpl() }

private class ScrollEffectsTaskImpl : ScrollEffectsTask {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun collapsingScrollBehavior(): TopAppBarScrollBehavior {
        val state = rememberTopAppBarState()
        return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state)
    }

    override fun Modifier.parallax(offsetPx: Float, factor: Float): Modifier =
        this.graphicsLayer {
            translationY = offsetPx * factor
        }
}
