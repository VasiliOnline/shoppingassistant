package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource

private const val MODAL_DURATION = 220

@Composable
fun rememberModalContainerTask(): ModalContainerTask = remember { ModalContainerTaskImpl() }

private class ModalContainerTaskImpl : ModalContainerTask {
    @Composable
    override fun Render(
        visible: Boolean,
        title: String,
        subtitle: String?,
        icon: (@Composable () -> Unit)?,
        onDismiss: () -> Unit,
        actions: (@Composable () -> Unit)?,
        content: @Composable () -> Unit,
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = MODAL_DURATION)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = MODAL_DURATION),
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = MODAL_DURATION - 40)) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(durationMillis = MODAL_DURATION - 40),
                    ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { /* block scrim clicks */ },
                        )
                        .clearAndSetSemantics { }, // убираем дублирование заголовка на scrim
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .alpha(0.92f),
                                contentAlignment = Alignment.Center,
                            ) {
                                icon()
                            }
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) {
                            content()
                        }

                        if (actions != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                            ) {
                                actions()
                            }
                        }
                    }
                }
            }
        }
    }
}
