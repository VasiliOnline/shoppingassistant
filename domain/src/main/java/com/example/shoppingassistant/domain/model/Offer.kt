package com.example.shoppingassistant.domain.model

import com.example.shoppingassistant.domain.model.PriceBreakdown

data class Offer(
    val id: String,
    val productId: String,
    val sellerName: String,
    val shopName: String?,
    val ratingStars: Float,     // 0..5
    val deliveryDays: Int,      // целые дни
    val price: PriceBreakdown,  // см. A1
    val attributes: Map<String, TypedAttributeValue>,
    val source: SourceMeta
)
data class SourceMeta(
    val domain: String,               // "example.com"
    val country: String,              // "RU","DE","US"...
    val lastUpdatedMinutesAgo: Int?,
    val trustScore: Float?
)
