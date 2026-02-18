package com.example.shoppingassistant.domain.model

data class PriceBreakdown(
    val item: Money,           // цена товара
    val shipping: Money,       // доставка
    val fees: Money,           // комиссии/налоги
    val total: Money,          // item+shipping+fees
    val currency: String,      // "RUB","EUR","USD"
    val taxIncluded: Boolean   // цена включает налог?
)
