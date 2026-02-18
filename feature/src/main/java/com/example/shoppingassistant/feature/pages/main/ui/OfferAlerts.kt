// Last synced: 2025-11-27
package com.example.shoppingassistant.feature.pages.main.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Виджет подписки на алерты: UI-слой принимает обработчик и сообщения статуса.
 */
@Composable
fun OfferAlerts(
    onAlertRequest: (Int) -> Unit,
    statusMessage: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Отслеживание снижения цены",
            style = MaterialTheme.typography.titleMedium,
        )
        listOf(5, 10, 15).forEach { percent ->
            Button(onClick = { onAlertRequest(percent) }) {
                Text("Сообщить, если цена снизится на $percent%")
            }
        }
        statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Для отправки алерта требуется авторизация.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
