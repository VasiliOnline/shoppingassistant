package com.example.shoppingassistant.feature.pages.useroffers.backlog

enum class UserOffersBacklogPriority {
    P0,
    P1,
    P2,
}

data class UserOffersBacklogItem(
    val id: String,
    val priority: UserOffersBacklogPriority,
    val title: String,
    val details: List<String> = emptyList(),
)

interface UserOffersBacklogTask {
    fun items(): List<UserOffersBacklogItem>
}
