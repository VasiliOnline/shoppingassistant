package com.example.shoppingassistant.domain.subscriptions

class MarkAllNotificationsReadTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke() = repo.markAllNotificationsRead()
}

