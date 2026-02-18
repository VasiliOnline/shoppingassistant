package com.example.shoppingassistant.feature.ui.animations

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

fun rememberStepTransitionTask(): StepTransitionTask = StepTransitionTaskImpl()

private class StepTransitionTaskImpl : StepTransitionTask {
    @OptIn(ExperimentalAnimationApi::class)
    override fun transition(isForward: Boolean): ContentTransform {
        val direction = if (isForward) 1 else -1
        return (
            slideInVertically { full -> full / 3 * direction } + fadeIn()
            ) togetherWith (
            slideOutVertically { full -> -full / 3 * direction } + fadeOut()
            )
    }
}
