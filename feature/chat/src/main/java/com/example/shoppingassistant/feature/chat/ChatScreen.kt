package com.example.shoppingassistant.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.shoppingassistant.core.network.GptApi
import com.example.shoppingassistant.feature.chat.ui.ProductCard
import com.example.shoppingassistant.feature.chat.model.Product

@Composable
fun ChatScreen(navController: NavController) {
    val products = remember { mutableStateListOf<Product>() }
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp),
            reverseLayout = true
        ) {
            items(products.reversed()) { product ->
                ProductCard(
                    product = product,
                    onBuyClick = { /* TODO */ },
                    onSubscribeClick = { /* TODO */ },
                    onDetailsClick = { /* TODO */ }
                )
            }
        }

        Row(modifier = Modifier.padding(8.dp)) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите запрос...") }
            )
            IconButton(onClick = {
                if (input.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val normalized = GptApi.normalizeQuery(input)
                        val product = Product(
                            id = "1",
                            title = "iPhone 15 Pro 256GB Black",
                            category = normalized.category,
                            price = 119990.0,
                            deliveryTime = 2,
                            sellerRating = 4.8
                        )
                        withContext(Dispatchers.Main) { products.add(product) }
                    }
                    input = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}