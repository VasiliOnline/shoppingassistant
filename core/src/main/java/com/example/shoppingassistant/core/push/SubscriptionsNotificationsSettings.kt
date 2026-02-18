package com.example.shoppingassistant.core.push

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationPriority {
    LOW,
    DEFAULT,
    HIGH,
}

@Serializable
data class TrackingNotificationsSettings(
    val pushEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 8,
    val groupNotifications: Boolean = true,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
)

interface TrackingNotificationsSettingsStorage {
    fun get(): TrackingNotificationsSettings
    fun set(settings: TrackingNotificationsSettings)
    fun update(block: (TrackingNotificationsSettings) -> TrackingNotificationsSettings) {
        set(block(get()))
    }
}

@Deprecated("Use TrackingNotificationsSettings")
typealias SubscriptionsNotificationsSettings = TrackingNotificationsSettings

@Deprecated("Use TrackingNotificationsSettingsStorage")
typealias SubscriptionsNotificationsSettingsStorage = TrackingNotificationsSettingsStorage
