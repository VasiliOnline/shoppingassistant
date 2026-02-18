package com.example.shoppingassistant.domain.subscriptions

class ListNotificationsPageTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(limit: Int, offset: Int): SubscriptionNotificationsPage =
        repo.listNotificationsPage(limit = limit, offset = offset)
}

