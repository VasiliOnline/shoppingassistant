package com.example.shoppingassistant.domain.subscriptions

class AddSubscriptionTask(private val repo: SubscriptionsRepository) {
    suspend operator fun invoke(request: AddSubscriptionRequest): AddSubscriptionResult =
        repo.addSubscription(request)
}

