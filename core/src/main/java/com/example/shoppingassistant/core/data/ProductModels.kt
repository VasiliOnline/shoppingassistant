package com.example.shoppingassistant.core.data

data class ProductBrief(
    val id: String,
    val title: String,
    val imageUrls: List<String>,
    val price: Double?,
    val deliveryDays: Int?,
    val sellerRating: Double?,
    val attributes: Map<String, String> = emptyMap()
)