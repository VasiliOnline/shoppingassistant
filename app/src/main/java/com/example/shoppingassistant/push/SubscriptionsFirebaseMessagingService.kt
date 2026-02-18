// Last synced: 2025-12-18 20:46:51
package com.example.shoppingassistant.push

import android.util.Log
import com.example.shoppingassistant.core.push.TrackingNotificationsChannel
import com.example.shoppingassistant.core.push.TrackingNotificationsSettings
import com.example.shoppingassistant.core.push.TrackingNotificationsSettingsStorage
import com.example.shoppingassistant.core.push.TrackingPushNotifier
import com.example.shoppingassistant.core.push.TrackingPushStateStorage
import com.example.shoppingassistant.core.push.TrackingPushTokensRemoteDataSource
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.Calendar
import kotlin.math.abs

class TrackingFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        if (koin == null) {
            Log.w("TrackingFCM", "Koin is not started yet; skip token registration")
            return
        }

        val registrar = koin.get<TrackingPushTokensRemoteDataSource>()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { registrar.registerToken(platform = "ANDROID", token = token) }
                .onFailure { t ->
                    Log.w("TrackingFCM", "Failed to register FCM token: ${t.message}", t)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val body = message.notification?.body
            ?: message.data["message"]
            ?: return

        val notificationId = message.data["notificationId"]?.toLongOrNull()
        val koin = runCatching { GlobalContext.get() }.getOrNull()

        if (notificationId != null && koin != null) {
            runCatching {
                val state = koin.get<TrackingPushStateStorage>()
                val prev = state.getLastNotifiedId()
                if (notificationId > prev) state.setLastNotifiedId(notificationId)
            }
        }

        TrackingNotificationsChannel.ensureCreated(this)

        val notifier = TrackingPushNotifier(this)
        if (!notifier.canPostNotifications()) return

        val settings = runCatching {
            koin?.get<TrackingNotificationsSettingsStorage>()?.get()
        }.getOrNull() ?: TrackingNotificationsSettings()

        if (!settings.pushEnabled) return
        if (settings.quietHoursEnabled && isQuietNow(settings)) return

        val id = abs(notificationId?.hashCode() ?: (message.messageId?.hashCode() ?: body.hashCode()))
        notifier.notifyText(
            id = id,
            title = "Сработало отслеживание",
            message = body,
            settings = settings,
        )
    }

    private fun isQuietNow(settings: TrackingNotificationsSettings): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = settings.quietHoursStart.coerceIn(0, 23)
        val end = settings.quietHoursEnd.coerceIn(0, 23)

        return when {
            start == end -> true
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}
