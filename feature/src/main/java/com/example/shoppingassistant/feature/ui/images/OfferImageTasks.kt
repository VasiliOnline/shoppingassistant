package com.example.shoppingassistant.feature.ui.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import com.example.shoppingassistant.feature.ui.animations.ShimmerTask

interface OfferImageTask {
    @Composable
    fun Render(
        imageUrl: String?,
        placeholderKey: String,
        placeholderLabel: String? = null,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        shape: Shape,
        contentScale: ContentScale = ContentScale.Crop,
        shimmer: ShimmerTask? = null,
    )
}

