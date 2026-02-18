package com.example.shoppingassistant.core.push

import android.content.Context

class TrackingPushStateStorageImpl(
    context: Context,
) : TrackingPushStateStorage {

    private val prefs = context.getSharedPreferences("tracking_push", Context.MODE_PRIVATE)

    override fun getLastNotifiedId(): Long = prefs.getLong("last_notified_id", 0L)

    override fun setLastNotifiedId(id: Long) {
        prefs.edit().putLong("last_notified_id", id).apply()
    }
}

@Deprecated("Use TrackingPushStateStorageImpl")
typealias SubscriptionsPushStateStorageImpl = TrackingPushStateStorageImpl
