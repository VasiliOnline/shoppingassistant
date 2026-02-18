package com.example.shoppingassistant.feature.pages.chat

/**
 * Контракты UI-экрана чата с продавцом.
 */
data class ChatProps(
    val sellerName: String,
    val sellerStatus: String? = null,
    val sellerAvatarUrl: String? = null,
    val offerTitle: String,
    val offerPrice: Double? = null,
    val onBack: () -> Unit,
)

data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
)
