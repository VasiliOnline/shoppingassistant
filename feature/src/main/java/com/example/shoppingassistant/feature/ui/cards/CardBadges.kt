package com.example.shoppingassistant.feature.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun CardBadgesRow(
    badges: List<CardBadge>,
    maxCount: Int,
    density: CardDensity,
    modifier: Modifier = Modifier,
) {
    val visibleBadges = badges
        .filter { it.label.isNotBlank() }
        .sortedByDescending { it.priority }
        .take(maxCount)

    AnimatedVisibility(
        visible = visibleBadges.isNotEmpty(),
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(160)),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            visibleBadges.forEach { badge ->
                CardBadgeChip(label = badge.label)
            }
        }
    }

    if (visibleBadges.isEmpty()) {
        Spacer(modifier = Modifier.height(CardTokens.metrics(density).badgeHeight))
    }
}

@Composable
fun CardBadgeChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    if (label.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val background = colors.surfaceVariant.copy(alpha = 0.7f)
    val content = colors.onSurfaceVariant

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .sizeIn(minHeight = 22.dp)
            .background(background, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
