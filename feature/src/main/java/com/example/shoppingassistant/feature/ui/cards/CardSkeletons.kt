package com.example.shoppingassistant.feature.ui.cards

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OfferCardSkeleton(
    modifier: Modifier = Modifier,
    density: CardDensity = CardDensity.Regular,
) {
    val metrics = CardTokens.metrics(density)
    val alpha = rememberSkeletonAlpha()
    val shape = RoundedCornerShape(metrics.cornerRadius)
    val blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Surface(shape = shape, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.contentPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(metrics.mediaAspectRatio)
                    .background(blockColor, shape = RoundedCornerShape(12.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .background(blockColor, RoundedCornerShape(6.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .background(blockColor, RoundedCornerShape(6.dp)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(blockColor, RoundedCornerShape(12.dp)),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(20.dp)
                    .background(blockColor, RoundedCornerShape(6.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .background(blockColor, RoundedCornerShape(6.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(metrics.badgeHeight)
                    .background(blockColor, RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
fun CompactCardSkeleton(
    modifier: Modifier = Modifier,
    density: CardDensity = CardDensity.Dense,
) {
    val metrics = CardTokens.metrics(density)
    val alpha = rememberSkeletonAlpha()
    val shape = RoundedCornerShape(metrics.cornerRadius)
    val blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Surface(shape = shape, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.contentPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.rowGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(metrics.mediaAspectRatio)
                    .background(blockColor, RoundedCornerShape(12.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(18.dp)
                    .background(blockColor, RoundedCornerShape(6.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp)
                    .background(blockColor, RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.height(metrics.badgeHeight))
        }
    }
}

@Composable
fun ScenarioCardSkeleton(
    modifier: Modifier = Modifier,
    density: CardDensity = CardDensity.Regular,
) {
    val metrics = CardTokens.metrics(density)
    val alpha = rememberSkeletonAlpha()
    val shape = RoundedCornerShape(metrics.cornerRadius)
    val blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Surface(shape = shape, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(blockColor, RoundedCornerShape(14.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .background(blockColor, RoundedCornerShape(6.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(blockColor, RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeletonAlpha")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlphaValue",
    )
    return alpha
}
