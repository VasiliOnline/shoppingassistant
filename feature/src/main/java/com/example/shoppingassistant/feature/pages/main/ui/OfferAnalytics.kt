// Last synced: 2025-11-26
package com.example.shoppingassistant.feature.pages.main.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.ExplainedItem

/**
 * Заглушка вкладки "Аналитика" до подключения сервисов трендов/price history.
 */
@Composable
fun OfferAnalyticsPlaceholder(
    item: ExplainedItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Text(
            text = "Аналитика",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Для товара: ${item.dto.title}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Данные по трендам и свежести будут добавлены после интеграции с сервисом аналитики и price history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
