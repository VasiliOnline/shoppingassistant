package com.example.shoppingassistant.feature.pages.useroffers.bulk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.pages.useroffers.UserOfferBulkAction

internal class UserOffersBulkTaskImpl : UserOffersBulkTask {
    @Composable
    override fun ActionsBar(
        selectedCount: Int,
        showActivate: Boolean,
        showPause: Boolean,
        onAction: (UserOfferBulkAction) -> Unit,
        onClear: () -> Unit,
        modifier: Modifier,
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "Снять выделение")
                }
                Text(
                    text = "Выбрано: $selectedCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                if (showPause) {
                    IconButton(onClick = { onAction(UserOfferBulkAction.PAUSE) }) {
                        Icon(imageVector = Icons.Outlined.PauseCircle, contentDescription = "Пауза")
                    }
                }
                if (showActivate) {
                    IconButton(onClick = { onAction(UserOfferBulkAction.ACTIVATE) }) {
                        Icon(imageVector = Icons.Outlined.PlayCircle, contentDescription = "Активировать")
                    }
                }
                IconButton(onClick = { onAction(UserOfferBulkAction.RENEW) }) {
                    Icon(imageVector = Icons.Outlined.Update, contentDescription = "Продлить")
                }
                IconButton(onClick = { onAction(UserOfferBulkAction.CHANGE_PRICE) }) {
                    Icon(imageVector = Icons.Outlined.AttachMoney, contentDescription = "Изменить цену")
                }
                IconButton(onClick = { onAction(UserOfferBulkAction.EXPORT) }) {
                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = "Экспорт")
                }
                IconButton(onClick = { onAction(UserOfferBulkAction.DELETE) }) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Удалить")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun PriceSheet(
        visible: Boolean,
        selectedCount: Int,
        priceInput: String,
        onPriceInputChange: (String) -> Unit,
        onApply: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (!visible) return
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Изменить цену для $selectedCount товаров",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = onPriceInputChange,
                    singleLine = true,
                    label = { Text("Новая цена") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApply) {
                        Text("Применить")
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun rememberUserOffersBulkTask(): UserOffersBulkTask = UserOffersBulkTaskImpl()
