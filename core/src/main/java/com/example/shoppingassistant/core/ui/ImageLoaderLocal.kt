package com.example.shoppingassistant.core.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader

// где-нибудь рядом в этом же файле (или в ui/theme), если ещё нет
@SuppressLint("CompositionLocalNaming")
val AppImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("ImageLoader not provided")
}
