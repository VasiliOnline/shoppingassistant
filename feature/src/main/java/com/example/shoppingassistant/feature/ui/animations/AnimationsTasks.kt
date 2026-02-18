package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Контракт анимированного модального контейнера с кастомным scrim и scale+fade.
 */
interface ModalContainerTask {
    @Composable
    fun Render(
        visible: Boolean,
        title: String,
        subtitle: String? = null,
        icon: (@Composable () -> Unit)? = null,
        onDismiss: () -> Unit,
        actions: (@Composable () -> Unit)? = null,
        content: @Composable () -> Unit,
    )
}

/**
 * Контракт анимации успеха (галочка в круге с лёгким pulse).
 */
interface SuccessAnimationTask {
    @Composable
    fun Render(modifier: Modifier = Modifier)
}

/**
 * Контракт для shake-анимации: применяем к Modifier и дергаем по ключу.
 */
interface ShakeAnimationTask {
    @Composable
    fun Modifier.shake(triggerKey: Int): Modifier
}

/**
 * Контракты появлений и перестановок для списков/карточек.
 */
interface ListAnimationsTask {
    fun itemEnter(): androidx.compose.animation.EnterTransition
    fun itemExit(): androidx.compose.animation.ExitTransition
    fun androidx.compose.foundation.lazy.LazyItemScope.staggeredItem(
        index: Int,
        baseDelayMs: Int = 40,
    ): Modifier

    fun androidx.compose.foundation.lazy.LazyItemScope.animatedPlacement(
        durationMs: Int = 250,
    ): Modifier
}

/**
 * Контракт для эффекта нажатия (scale + тень) без изменения кликов.
 */
interface PressFeedbackTask {
    @Composable
    fun Modifier.pressEffect(
        interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
        enabled: Boolean = true,
    ): Modifier
}

/**
 * Контракт для shimmer-эффектов.
 */
interface ShimmerTask {
    @Composable
    fun Modifier.shimmer(enabled: Boolean = true): Modifier
}

/**
 * Контракт для анимированных цветов темы (плавный переход).
 */
interface ThemeTransitionTask {
    @Composable
    fun animateSurface(
        targetContainer: androidx.compose.ui.graphics.Color,
        targetContent: androidx.compose.ui.graphics.Color,
    ): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>
}

/**
 * Контракт для скролл-эффектов: коллапс app bar и параллакс.
 */
interface ScrollEffectsTask {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun collapsingScrollBehavior(): TopAppBarScrollBehavior

    fun Modifier.parallax(offsetPx: Float, factor: Float = 0.5f): Modifier
}

/**
 * Контракт для шаговых переходов (AnimatedContent) вверх/вниз + fade.
 */
interface StepTransitionTask {
    fun transition(isForward: Boolean = true): androidx.compose.animation.ContentTransform
}
