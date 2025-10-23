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
