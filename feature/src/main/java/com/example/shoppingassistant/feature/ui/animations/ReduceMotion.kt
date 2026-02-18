package com.example.shoppingassistant.feature.ui.animations

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberReduceMotionEnabled(): Boolean = remember {
    !ValueAnimator.areAnimatorsEnabled()
}
