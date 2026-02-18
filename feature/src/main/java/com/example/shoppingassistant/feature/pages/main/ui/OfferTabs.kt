// Last synced: 2025-11-26
package com.example.shoppingassistant.feature.pages.main.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.ExplainedItem

/**
 * Вкладки карточки оффера: характеристики, описание, аналитика.
 * Данные пока ограничены ProductDto (title/brand/model/price/rating),
 * поэтому наполнение табов минимальное и безопасное.
 */
@Composable
fun OfferTabs(
    item: ExplainedItem,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf("Характеристики", "Описание", "Аналитика")
    var selected by remember { mutableStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(title) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (selected) {
            0 -> SpecsTab(item)
            1 -> DescriptionTab(item)
            2 -> OfferAnalyticsPlaceholder(item)
        }
    }
}

@Composable
private fun SpecsTab(item: ExplainedItem) {
    val dto = item.dto
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Бренд: ${dto.brand ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Модель: ${dto.model ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Цена: ${dto.price?.let { "%.0f".format(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
        Text("Рейтинг: ${dto.sellerRating?.let { "%.1f/5" .format(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DescriptionTab(item: ExplainedItem) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(
            text = item.dto.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Описание недоступно: источник не передаёт описание товара.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
