package com.example.shoppingassistant.domain.subscriptions

class UpdateSubscriptionTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(request: UpdateSubscriptionRequest): Boolean =
        repo.updateSubscription(request)
}

