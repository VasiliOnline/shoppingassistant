package com.example.shoppingassistant.domain.subscriptions

class MarkNotificationReadTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(id: Long) = repo.markNotificationRead(id)
}

