package com.example.shoppingassistant.feature.ui.images

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.shoppingassistant.feature.ui.animations.ShimmerTask
import kotlin.math.absoluteValue

@Composable
fun rememberOfferImageTask(): OfferImageTask = OfferImageTaskImpl()

private class OfferImageTaskImpl : OfferImageTask {
    @Composable
    override fun Render(
        imageUrl: String?,
        placeholderKey: String,
        placeholderLabel: String?,
        contentDescription: String?,
        modifier: Modifier,
        shape: Shape,
        contentScale: ContentScale,
        shimmer: ShimmerTask?,
    ) {
        val backgroundBrush = remember(placeholderKey) { placeholderBrush(placeholderKey) }

        var isLoading by remember(imageUrl) { mutableStateOf(imageUrl != null) }

        Box(
            modifier = modifier
                .clip(shape)
                .background(backgroundBrush),
            contentAlignment = Alignment.Center,
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isLoading && shimmer != null) with(shimmer) { Modifier.shimmer() } else Modifier,
                        ),
                    onLoading = { isLoading = true },
                    onSuccess = { isLoading = false },
                    onError = { isLoading = false },
                )
            } else {
                isLoading = false
            }

            if (imageUrl.isNullOrBlank() || isLoading) {
                val label = placeholderLabel
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.take(1)
                    ?.uppercase()

                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

private fun placeholderBrush(key: String): Brush {
    val h = key.hashCode().absoluteValue
    val hueA = (h % 360).toFloat()
    val hueB = ((h / 7) % 360).toFloat()

    val a = Color.hsl(hueA, 0.55f, 0.42f)
    val b = Color.hsl(hueB, 0.55f, 0.30f)

    return Brush.linearGradient(listOf(a, b))
}

