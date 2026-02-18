package com.example.shoppingassistant.domain.subscriptions

class ListNotificationsTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(): List<SubscriptionNotification> = repo.listNotifications()
}

