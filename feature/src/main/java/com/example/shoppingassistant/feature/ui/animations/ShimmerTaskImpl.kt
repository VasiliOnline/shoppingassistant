package com.example.shoppingassistant.feature.ui.animations

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun rememberShimmerTask(): ShimmerTask = ShimmerTaskImpl()

private class ShimmerTaskImpl : ShimmerTask {
    @Composable
    override fun Modifier.shimmer(enabled: Boolean): Modifier = composed {
        if (!enabled || !ValueAnimator.areAnimatorsEnabled()) return@composed this
        val transition = rememberInfiniteTransition(label = "shimmerTransition")
        val shift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerShift",
        )

        val colors = listOf(
            Color.LightGray.copy(alpha = 0.35f),
            Color.LightGray.copy(alpha = 0.15f),
            Color.LightGray.copy(alpha = 0.35f),
        )

        this.drawWithContent {
            drawContent()
            val width = size.width
            val start = -width
            val end = width * 2
            val dx = start + (end - start) * shift
            drawRect(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(dx, 0f),
                    end = Offset(dx + width, size.height),
                ),
                alpha = 1f,
            )
        }
    }
}
