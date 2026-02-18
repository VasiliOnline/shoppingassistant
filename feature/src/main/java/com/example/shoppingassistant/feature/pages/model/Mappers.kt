package com.example.shoppingassistant.feature.pages.model

import com.example.shoppingassistant.domain.model.ProductDto

fun ProductDto.toUi(): Product = Product(
    id = this.id,
    title = this.title,
    brand = this.brand ?: "",
    model = this.model ?: "",
    categoryCode = null,
    price = this.price ?: 0.0,
    deliveryTime = this.deliveryTime ?: Int.MAX_VALUE,
    sellerRating = this.sellerRating ?: 0.0
)
