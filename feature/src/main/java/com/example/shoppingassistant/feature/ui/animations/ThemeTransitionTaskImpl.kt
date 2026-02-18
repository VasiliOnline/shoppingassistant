package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun rememberThemeTransitionTask(): ThemeTransitionTask = remember { ThemeTransitionTaskImpl() }

private class ThemeTransitionTaskImpl : ThemeTransitionTask {
    @Composable
    override fun animateSurface(
        targetContainer: Color,
        targetContent: Color,
    ): Pair<Color, Color> {
        val container by animateColorAsState(
            targetValue = targetContainer,
            animationSpec = tween(durationMillis = 220),
            label = "containerColor",
        )
        val content by animateColorAsState(
            targetValue = targetContent,
            animationSpec = tween(durationMillis = 220),
            label = "contentColor",
        )
        return container to content
    }
}
