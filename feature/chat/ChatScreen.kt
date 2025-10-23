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
                    onBuyClick = { /* TODO: покупка */ },
                    onSubscribeClick = { /* TODO: подписка */ },
                    onDetailsClick = { /* TODO: подробнее */ }
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
                    // вместо моков — запрос к GPT
                    CoroutineScope(Dispatchers.IO).launch {
                        val normalized = GptApi.normalizeQuery(input)
                        // TODO: запрос к маркетплейсу через Aggregator
                        val mockProduct = Product(
                            id = "1",
                            title = "iPhone 15 Pro 256GB Black",
                            category = normalized.category,
                            price = 119990.0,
                            deliveryTime = 2,
                            sellerRating = 4.8
                        )
                        withContext(Dispatchers.Main) {
                            products.add(mockProduct)
                        }
                    }
                    input = ""
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
