package com.example.shoppingassistant.feature.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.chat.model.Product

@Composable
fun ProductCard(
    product: Product,
    onBuyClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(product.title, style = MaterialTheme.typography.titleMedium)
            Text("Цена: ${product.price} ₽")
            Text("Доставка: ${product.deliveryTime} дн.")
            Text("Рейтинг: ${product.sellerRating}")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onBuyClick) { Text("Купить") }
                TextButton(onClick = onSubscribeClick) { Text("⭐ Подписаться") }
                TextButton(onClick = onDetailsClick) { Text("Подробнее") }
            }
        }
    }
}