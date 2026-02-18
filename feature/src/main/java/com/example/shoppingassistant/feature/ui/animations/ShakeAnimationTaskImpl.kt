package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun rememberShakeAnimationTask(): ShakeAnimationTask = remember { ShakeAnimationTaskImpl() }

private class ShakeAnimationTaskImpl : ShakeAnimationTask {
    @Composable
    override fun Modifier.shake(triggerKey: Int): Modifier {
        val offset = remember { Animatable(0f) }

        LaunchedEffect(triggerKey) {
            offset.snapTo(0f)
            val amplitudes = listOf(0f, 12f, -12f, 9f, -7f, 4f, -2f, 0f)
            amplitudes.forEach { value ->
                offset.animateTo(
                    targetValue = value,
                    animationSpec = tween(durationMillis = 38, easing = LinearEasing),
                )
            }
        }

        return this.graphicsLayer(translationX = offset.value)
    }
}
