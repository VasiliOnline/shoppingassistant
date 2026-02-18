package com.example.shoppingassistant.domain.subscriptions

class DeleteSubscriptionTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(id: Long) = repo.deleteSubscription(id)
}

