package com.example.shoppingassistant.feature.pages.offers.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.core.data.ExplainedItem
import com.example.shoppingassistant.feature.pages.main.ui.OfferAlerts
import com.example.shoppingassistant.feature.pages.main.ui.OfferTabs

@Composable
fun OfferDetailsPanel(
    item: ExplainedItem,
    onClose: () -> Unit,
    alertMessage: String?,
    alertIsError: Boolean,
    onAlertRequest: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Детали предложения",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onClose) {
                Text("Закрыть")
            }
        }
        OfferTabs(item = item)
        OfferAlerts(
            onAlertRequest = onAlertRequest,
            statusMessage = alertMessage,
            isError = alertIsError,
        )
    }
}
