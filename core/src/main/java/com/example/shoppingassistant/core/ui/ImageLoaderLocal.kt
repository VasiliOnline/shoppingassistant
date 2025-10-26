package com.example.shoppingassistant.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader

val AppImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("AppImageLoader not provided")
}
