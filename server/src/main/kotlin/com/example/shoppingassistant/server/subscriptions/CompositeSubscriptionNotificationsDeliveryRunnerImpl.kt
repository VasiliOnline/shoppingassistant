package com.example.shoppingassistant.server.subscriptions

class CompositeSubscriptionNotificationsDeliveryRunnerImpl(
    private val runners: List<SubscriptionNotificationsDeliveryRunner>,
) : SubscriptionNotificationsDeliveryRunner {
    override suspend fun runOnce(): SubscriptionDeliveryRunResult {
        var picked = 0
        var sent = 0
        var failed = 0
        var skipped = 0

        runners.forEach { runner ->
            val r = runner.runOnce()
            picked += r.picked
            sent += r.sent
            failed += r.failed
            skipped += r.skipped
        }

        return SubscriptionDeliveryRunResult(
            picked = picked,
            sent = sent,
            failed = failed,
            skipped = skipped,
        )
    }
}

