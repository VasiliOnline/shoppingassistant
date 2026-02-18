package com.example.shoppingassistant.feature.pages.offers.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.pages.offers.tasks.OffersBadge

@Composable
fun OffersBadgeMenu(
    active: OffersBadge,
    onSelect: (OffersBadge) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeChip(
            label = "Лучшие цены",
            icon = Icons.Outlined.AttachMoney,
            selected = active == OffersBadge.BestPrice,
            onClick = { onSelect(OffersBadge.BestPrice) },
        )
        BadgeChip(
            label = "Рейтинг продавца",
            icon = Icons.Outlined.ThumbUp,
            selected = active == OffersBadge.BestRating,
            onClick = { onSelect(OffersBadge.BestRating) },
        )
        BadgeChip(
            label = "Ближе к вам",
            icon = Icons.Outlined.LocalShipping,
            selected = active == OffersBadge.Nearest,
            onClick = { onSelect(OffersBadge.Nearest) },
        )
        BadgeChip(
            label = "Сбросить",
            icon = Icons.Outlined.ViewWeek,
            selected = active == OffersBadge.None,
            onClick = { onSelect(OffersBadge.None) },
        )
    }
}

@Composable
private fun BadgeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        },
        label = "badgeBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
