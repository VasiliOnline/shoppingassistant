package com.example.shoppingassistant.domain.subscriptions

class ListSubscriptionsTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(): List<Subscription> = repo.listSubscriptions()
}

