package com.example.shoppingassistant.feature.pages.model

data class Product(
    val id: String = "",
    val title: String = "" ,
    //val category: String,
    val brand: String,
    val model: String ,
    val categoryCode: String? = null,
    val price: Double = 0.0,
    val deliveryTime: Int = 0,
    val sellerRating: Double = 0.0,
)
