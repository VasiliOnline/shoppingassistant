package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class SwipeBackEdge {
    Start,
    End,
}

@Composable
fun SwipeBackSurface(
    enabled: Boolean = true,
    edge: SwipeBackEdge = SwipeBackEdge.Start,
    onDismiss: () -> Unit,
    background: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val isDragging = remember { mutableStateOf(false) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val edgeWidthPx = with(density) { 28.dp.toPx() }
        val closeThreshold = widthPx * 0.32f
        val minOffset = if (edge == SwipeBackEdge.Start) 0f else -widthPx
        val maxOffset = if (edge == SwipeBackEdge.Start) widthPx else 0f

        LaunchedEffect(enabled) {
            if (!enabled) {
                offsetX.snapTo(0f)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val progress = (kotlin.math.abs(offsetX.value) / widthPx).coerceIn(0f, 1f)
            val backgroundScale = 0.97f + (0.03f * progress)
            val scrimAlpha = 0.24f * (1f - progress)
            if (background != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = backgroundScale
                            scaleY = backgroundScale
                        },
                ) {
                    background()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .shadow(
                    elevation = if (kotlin.math.abs(offsetX.value) > 0f) 12.dp else 0.dp,
                    shape = MaterialTheme.shapes.large,
                    clip = false,
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            isDragging.value = if (edge == SwipeBackEdge.Start) {
                                start.x <= edgeWidthPx
                            } else {
                                start.x >= widthPx - edgeWidthPx
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isDragging.value) return@detectHorizontalDragGestures
                            val next = (offsetX.value + dragAmount).coerceIn(minOffset, maxOffset)
                            scope.launch { offsetX.snapTo(next) }
                        },
                        onDragEnd = {
                            if (!isDragging.value) return@detectHorizontalDragGestures
                            val shouldClose = kotlin.math.abs(offsetX.value) > closeThreshold
                            scope.launch {
                                if (shouldClose) {
                                    val target = if (edge == SwipeBackEdge.Start) widthPx else -widthPx
                                    offsetX.animateTo(target, tween(durationMillis = 200))
                                    onDismiss()
                                } else {
                                    offsetX.animateTo(0f, tween(durationMillis = 200))
                                }
                                isDragging.value = false
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, tween(durationMillis = 180))
                                isDragging.value = false
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
