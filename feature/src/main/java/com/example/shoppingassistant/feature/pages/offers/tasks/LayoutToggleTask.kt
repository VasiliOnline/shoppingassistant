package com.example.shoppingassistant.feature.pages.offers.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OffersLayoutToggle(
    layoutMode: Boolean, // true = horizontal, false = vertical
    onHorizontal: () -> Unit,
    onVertical: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = layoutMode,
            onClick = onHorizontal,
            label = {
                Icon(
                    imageVector = Icons.Outlined.ViewWeek,
                    contentDescription = "Горизонтальные карточки",
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme
                    .primary.copy(alpha = 0.18f),
            ),
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = !layoutMode,
            onClick = onVertical,
            label = {
                Icon(
                    imageVector = Icons.Outlined.ViewStream,
                    contentDescription = "Вертикальные карточки",
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme
                    .primary.copy(alpha = 0.18f),
            ),
        )
    }
}
